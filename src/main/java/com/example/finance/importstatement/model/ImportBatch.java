package com.example.finance.importstatement.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("import_batches")
public class ImportBatch {

    @Id
    public String id;
    public String source;
    public String fileName;

    @Indexed(unique = true)
    public String fileHash;

    public LocalDate statementPeriodStart;
    public LocalDate statementPeriodEnd;
    public Status status = Status.PROCESSING;
    public int totalRows;
    public int parsedRows;
    public int importedRows;
    public int duplicateRows;
    public int failedRows;
    public List<String> errors = new ArrayList<>();
    public Instant startedAt = Instant.now();
    public Instant completedAt;
    public Instant createdAt = Instant.now();

    public ImportBatch() {
    }

    public enum Status {
        PROCESSING,
        COMPLETED,
        PARTIAL_SUCCESS,
        FAILED
    }
}
