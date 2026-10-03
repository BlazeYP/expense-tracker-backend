package com.example.finance.importstatement.parser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.finance.transaction.model.Transaction;

@Component
public class GPayStatementParser implements StatementParser {

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ofPattern("d/M/uuuu H:mm[:ss]"),
            DateTimeFormatter.ofPattern("d/M/uuuu h:mm a"),
            DateTimeFormatter.ofPattern("d-MMM-uuuu h:mm a"),
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("M/d/uuuu H:mm[:ss]"),
            DateTimeFormatter.ofPattern("M/d/uuuu"),
            DateTimeFormatter.ofPattern("d-MMM-uuuu H:mm[:ss]"),
            DateTimeFormatter.ofPattern("d-MMM-uuuu"),
            DateTimeFormatter.ofPattern("d MMM, uuuu h:mm a", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dMMM,uuuu h:mma", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dMMMuuuu h:mma", Locale.ENGLISH)
    );

    // PDFBox layout:
    // 01 Mar, 2026 Paid to Zomato ₹193.70
    // 03:51 PM UPI Transaction ID: 606022277025
    // Paid by State Bank of India 2469
    private static final Pattern PDF_TRANSACTION = Pattern.compile(
            "(?m)(\\d{1,2}\\s+[A-Za-z]{3},\\s*\\d{4})\\s+"
                    + "((?:Paid\\s+to|Received\\s+from)\\s+.+?)\\s+"
                    + "₹\\s*([\\d,]+(?:\\.\\d+)?)\\s*\\R\\s*"
                    + "(\\d{1,2}:\\d{2}\\s*(?:AM|PM))\\s+"
                    + "UPI\\s+Transaction\\s+ID:\\s*(\\d+)\\s*\\R\\s*"
                    + "((?:Paid\\s+by|Paid\\s+to)\\s+[^\\n\\r]+)",
            Pattern.CASE_INSENSITIVE
    );

    private final ZoneId zone;

    public GPayStatementParser(@Value("${app.timezone:Asia/Kolkata}") String timezone) {
        this.zone = ZoneId.of(timezone);
    }

    @Override
    public boolean supports(String fileName, byte[] content) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf") || looksLikePdf(content)) {
            return true;
        }
        String text = new String(content, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        boolean csvName = name.isBlank() || name.endsWith(".csv");
        return csvName && (text.contains("upi") || text.contains("transaction") || text.contains("amount"));
    }

    @Override
    public ParsedStatement parse(String fileName, byte[] bytes) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf") || looksLikePdf(bytes)) {
            return parsePdf(bytes);
        }
        return parseCsv(bytes);
    }

    private ParsedStatement parsePdf(byte[] bytes) {
        String text;
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            text = stripper.getText(document);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read PDF statement");
        }

        List<RawTransaction> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        LocalDate minDate = null;
        LocalDate maxDate = null;
        int total = 0;

        Matcher matcher = PDF_TRANSACTION.matcher(text);
        while (matcher.find()) {
            total++;
            try {
                String dateText = clean(matcher.group(1));
                String detail = clean(matcher.group(2));
                String amountText = clean(matcher.group(3));
                String timeText = clean(matcher.group(4)).toUpperCase(Locale.ROOT);
                String upiTransactionId = clean(matcher.group(5));
                String accountLine = clean(matcher.group(6));
                Instant instant = parseDate(dateText + " " + timeText);
                BigDecimal amount = parseAmount(amountText);

                boolean credit = detail.toLowerCase(Locale.ROOT).startsWith("received from");
                String counterparty = credit
                        ? stripPrefix(detail, "Received from")
                        : stripPrefix(detail, "Paid to");
                String bankLine = accountLine.replaceFirst("(?i)^Paid (by|to)\\s*", "");
                String last4 = extractLast4(bankLine);
                String bankName = last4.isBlank()
                        ? bankLine.trim()
                        : bankLine.replaceAll("\\d{4}\\s*$", "").trim();

                Map<String, String> original = new LinkedHashMap<>();
                original.put("date", dateText);
                original.put("time", timeText);
                original.put("details", detail);
                original.put("upiTransactionId", upiTransactionId);
                original.put("account", accountLine);
                original.put("amount", amountText);

                rows.add(new RawTransaction(
                        instant,
                        credit ? Transaction.Type.CREDIT : Transaction.Type.DEBIT,
                        amount,
                        detail,
                        counterparty,
                        upiTransactionId,
                        "",
                        bankName,
                        last4,
                        original
                ));

                LocalDate localDate = instant.atZone(zone).toLocalDate();
                minDate = minDate == null || localDate.isBefore(minDate) ? localDate : minDate;
                maxDate = maxDate == null || localDate.isAfter(maxDate) ? localDate : maxDate;
            } catch (Exception exception) {
                errors.add("PDF txn " + total + ": " + exception.getMessage());
            }
        }

        if (total == 0) {
            throw new IllegalArgumentException("No GPay transactions found in PDF statement");
        }

        return new ParsedStatement("GPAY", minDate, maxDate, rows, total, errors);
    }

    private ParsedStatement parseCsv(byte[] bytes) {
        List<RawTransaction> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int total = 0;
        LocalDate minDate = null;
        LocalDate maxDate = null;

        try (
                Reader reader = new InputStreamReader(new ByteArrayInputStream(stripBom(bytes)), StandardCharsets.UTF_8);
                CSVParser csv = CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setIgnoreEmptyLines(true)
                        .setTrim(true)
                        .build()
                        .parse(reader)
        ) {
            Map<String, String> headers = new HashMap<>();
            for (String header : csv.getHeaderMap().keySet()) {
                headers.put(normalizeHeader(header), header);
            }

            String dateColumn = pick(headers, "date", "transaction date", "datetime", "date time", "transaction date time");
            String timeColumn = pick(headers, "time", "transaction time");
            String amountColumn = pick(headers, "amount", "transaction amount", "amount inr", "amount (inr)", "inr amount");
            String typeColumn = pick(headers, "type", "transaction type", "debit credit");
            String descriptionColumn = pick(headers, "description", "transaction details", "details", "remarks", "merchant");

            if (dateColumn == null || amountColumn == null) {
                throw new IllegalArgumentException("CSV must include date and amount columns");
            }

            for (CSVRecord record : csv) {
                total++;
                try {
                    Map<String, String> original = new LinkedHashMap<>();
                    for (String header : csv.getHeaderMap().keySet()) {
                        original.put(header, clean(record.get(header)));
                    }

                    String dateText = clean(record.get(dateColumn));
                    if (timeColumn != null && !clean(record.get(timeColumn)).isBlank() && !dateText.contains(":")) {
                        dateText += " " + clean(record.get(timeColumn));
                    }

                    String amountText = clean(record.get(amountColumn));
                    Instant instant = parseDate(dateText);
                    BigDecimal value = parseAmount(amountText);
                    if (value.signum() < 0) {
                        value = value.abs();
                    }

                    String typeText = typeColumn == null ? "" : clean(record.get(typeColumn)).toLowerCase(Locale.ROOT);
                    String direction = typeText + " " + amountText.toLowerCase(Locale.ROOT);
                    Transaction.Type type = isCredit(direction) ? Transaction.Type.CREDIT : Transaction.Type.DEBIT;

                    String description = descriptionColumn == null ? "" : clean(record.get(descriptionColumn));
                    String counterparty = first(
                            original,
                            headers,
                            "counterparty name",
                            "counterparty",
                            "name",
                            "paid to",
                            "received from",
                            "merchant name"
                    );
                    if (description.isBlank()) {
                        description = counterparty;
                    }

                    String upiTransactionId = first(
                            original,
                            headers,
                            "upi transaction id",
                            "upi trans id",
                            "transaction id",
                            "upi txn id",
                            "upi transaction reference"
                    );
                    String upiId = first(original, headers, "upi id", "vpa", "counterparty upi id");
                    String bankName = first(original, headers, "bank name", "account bank");
                    String last4 = extractLast4(first(original, headers, "account", "account number", "bank account"));

                    rows.add(new RawTransaction(
                            instant,
                            type,
                            value,
                            description,
                            counterparty,
                            upiTransactionId,
                            upiId,
                            bankName,
                            last4,
                            original
                    ));

                    LocalDate localDate = instant.atZone(zone).toLocalDate();
                    minDate = minDate == null || localDate.isBefore(minDate) ? localDate : minDate;
                    maxDate = maxDate == null || localDate.isAfter(maxDate) ? localDate : maxDate;
                } catch (Exception exception) {
                    errors.add("Row " + record.getRecordNumber() + ": " + exception.getMessage());
                }
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read CSV statement");
        }

        return new ParsedStatement("GPAY", minDate, maxDate, rows, total, errors);
    }

    private static boolean looksLikePdf(byte[] content) {
        return content != null
                && content.length >= 5
                && content[0] == '%'
                && content[1] == 'P'
                && content[2] == 'D'
                && content[3] == 'F';
    }

    private static String stripPrefix(String value, String prefix) {
        if (value.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return value.substring(prefix.length()).trim();
        }
        return value.trim();
    }

    private static boolean isCredit(String direction) {
        return direction.contains("credit")
                || direction.contains("received")
                || direction.contains("refund")
                || direction.contains("cashback")
                || direction.contains(" cr");
    }

    private static byte[] stripBom(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 255) == 239
                && (bytes[1] & 255) == 187
                && (bytes[2] & 255) == 191) {
            return Arrays.copyOfRange(bytes, 3, bytes.length);
        }
        return bytes;
    }

    private static String normalizeHeader(String header) {
        return header.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String pick(Map<String, String> headers, String... keys) {
        for (String key : keys) {
            if (headers.containsKey(normalizeHeader(key))) {
                return headers.get(normalizeHeader(key));
            }
        }
        return null;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String first(Map<String, String> row, Map<String, String> headers, String... keys) {
        String header = pick(headers, keys);
        return header == null ? "" : clean(row.get(header));
    }

    private static String extractLast4(String value) {
        Matcher matcher = Pattern.compile("(\\d{4})\\D*$").matcher(value);
        return matcher.find() ? matcher.group(1) : "";
    }

    private Instant parseDate(String value) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                try {
                    return OffsetDateTime.parse(value, formatter).toInstant();
                } catch (Exception ignored) {
                    // try next shape
                }
                try {
                    return LocalDateTime.parse(value, formatter).atZone(zone).toInstant();
                } catch (Exception ignored) {
                    // try next shape
                }
                return LocalDate.parse(value, formatter).atStartOfDay(zone).toInstant();
            } catch (Exception ignored) {
                // try next formatter
            }
        }
        throw new IllegalArgumentException("Malformed date: " + value);
    }

    private BigDecimal parseAmount(String value) {
        try {
            String normalized = value.replaceAll("[₹,\\s]", "").replace("INR", "").replace("₹", "");
            return new BigDecimal(normalized);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Malformed amount: " + value);
        }
    }
}
