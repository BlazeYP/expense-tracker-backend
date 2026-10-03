package com.example.finance.transaction.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.example.finance.transaction.model.Transaction;

public interface TransactionRepository extends MongoRepository<Transaction, String> {

    boolean existsByDeduplicationHash(String hash);

    List<Transaction> findByTransactionDateTimeBetween(Instant start, Instant end);

    @Query("{ '$or': [ { 'category.categoryId': null }, { 'category.source': 'DEFAULT' } ] }")
    List<Transaction> findUncategorized();
}
