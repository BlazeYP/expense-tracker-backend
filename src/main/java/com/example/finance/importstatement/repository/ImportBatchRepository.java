package com.example.finance.importstatement.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.example.finance.importstatement.model.ImportBatch;

public interface ImportBatchRepository extends MongoRepository<ImportBatch, String> {

    Optional<ImportBatch> findByUserIdAndFileHash(String userId, String hash);
}
