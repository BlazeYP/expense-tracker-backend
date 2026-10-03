package com.example.finance.importstatement.service;

import java.io.IOException;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.finance.categorization.service.CategorizationService;
import com.example.finance.importstatement.model.ImportBatch;
import com.example.finance.importstatement.parser.ParsedStatement;
import com.example.finance.importstatement.parser.RawTransaction;
import com.example.finance.importstatement.parser.StatementParser;
import com.example.finance.importstatement.repository.ImportBatchRepository;
import com.example.finance.transaction.model.Transaction;
import com.example.finance.transaction.repository.TransactionRepository;

@Service
public class ImportService {

    private static final long MAX_FILE_SIZE_BYTES = 15L * 1024 * 1024;

    private final ImportBatchRepository importBatchRepository;
    private final TransactionRepository transactionRepository;
    private final List<StatementParser> parsers;
    private final CategorizationService categorizationService;
    private final String currency;

    public ImportService(
            ImportBatchRepository importBatchRepository,
            TransactionRepository transactionRepository,
            List<StatementParser> parsers,
            CategorizationService categorizationService,
            @Value("${app.currency:INR}") String currency
    ) {
        this.importBatchRepository = importBatchRepository;
        this.transactionRepository = transactionRepository;
        this.parsers = parsers;
        this.categorizationService = categorizationService;
        this.currency = currency;
    }

    public ImportBatch importFile(MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("A non-empty statement file is required");
            }
            if (file.getSize() > MAX_FILE_SIZE_BYTES) {
                throw new IllegalArgumentException("File exceeds 15 MB");
            }

            byte[] bytes = file.getBytes();
            String hash = sha256(bytes);
            Optional<ImportBatch> existing = importBatchRepository.findByFileHash(hash);
            if (existing.isPresent()) {
                ImportBatch prior = existing.get();
                if (prior.status != ImportBatch.Status.FAILED) {
                    return prior;
                }
                importBatchRepository.delete(prior);
            }

            StatementParser parser = parsers.stream()
                    .filter(candidate -> candidate.supports(file.getOriginalFilename(), bytes))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unsupported statement format; upload a GPay CSV or PDF statement"));

            ImportBatch batch = new ImportBatch();
            batch.source = "GPAY";
            batch.fileName = Objects.toString(file.getOriginalFilename(), "statement.csv");
            batch.fileHash = hash;
            importBatchRepository.save(batch);

            try {
                ParsedStatement statement = parser.parse(batch.fileName, bytes);
                batch.statementPeriodStart = statement.statementPeriodStart();
                batch.statementPeriodEnd = statement.statementPeriodEnd();
                batch.totalRows = statement.totalRows();
                batch.parsedRows = statement.transactions().size();
                batch.errors.addAll(statement.errors());
                batch.failedRows = statement.errors().size();

                for (RawTransaction raw : statement.transactions()) {
                    try {
                        Transaction transaction = map(raw, batch.id);
                        if (transactionRepository.existsByDeduplicationHash(transaction.deduplicationHash)) {
                            batch.duplicateRows++;
                            continue;
                        }
                        transactionRepository.save(transaction);
                        batch.importedRows++;
                    } catch (Exception exception) {
                        batch.failedRows++;
                        batch.errors.add("Transaction could not be saved: " + exception.getMessage());
                    }
                }

                if (batch.failedRows > 0) {
                    batch.status = batch.importedRows > 0
                            ? ImportBatch.Status.PARTIAL_SUCCESS
                            : ImportBatch.Status.FAILED;
                } else {
                    batch.status = ImportBatch.Status.COMPLETED;
                }
            } catch (Exception exception) {
                batch.status = ImportBatch.Status.FAILED;
                batch.errors.add(exception.getMessage() == null ? "Statement parsing failed" : exception.getMessage());
            }

            batch.completedAt = Instant.now();
            return importBatchRepository.save(batch);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read uploaded file");
        }
    }

    private Transaction map(RawTransaction raw, String batchId) {
        Transaction transaction = new Transaction();
        transaction.source = "GPAY";
        transaction.transactionDateTime = raw.transactionDateTime();
        transaction.type = raw.type();
        transaction.amount = raw.amount().setScale(2, RoundingMode.HALF_UP);
        if (transaction.amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        transaction.currency = currency;
        transaction.description = raw.description();
        transaction.upiTransactionId = raw.upiTransactionId();
        transaction.importBatchId = batchId;
        transaction.counterparty.originalName = raw.counterpartyName();
        transaction.counterparty.normalizedName = CategorizationService.normalize(raw.counterpartyName());
        transaction.counterparty.upiId = raw.upiId();
        transaction.account.bankName = raw.bankName();
        transaction.account.last4 = raw.last4();
        transaction.originalData.putAll(raw.originalRowData());
        transaction.deduplicationHash = sha256(deduplicationKey(raw).getBytes(StandardCharsets.UTF_8));
        transaction.category = categorizationService.categorize(transaction);
        transaction.categorization.source = transaction.category.source;
        transaction.categorization.ruleId = transaction.category.ruleId;
        transaction.categorization.confidence = transaction.category.confidence;
        return transaction;
    }

    private static String deduplicationKey(RawTransaction raw) {
        String uniquePart = raw.upiTransactionId() != null && !raw.upiTransactionId().isBlank()
                ? raw.upiTransactionId()
                : raw.transactionDateTime() + "|" + raw.type() + "|" + raw.amount() + "|" + raw.description();
        return ("GPAY|"
                + Objects.toString(raw.bankName(), "")
                + "|"
                + Objects.toString(raw.last4(), "")
                + "|"
                + uniquePart).toLowerCase(Locale.ROOT);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
