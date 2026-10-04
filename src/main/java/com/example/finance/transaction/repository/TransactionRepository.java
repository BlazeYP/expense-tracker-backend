package com.example.finance.transaction.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.example.finance.transaction.model.Transaction;

public interface TransactionRepository extends MongoRepository<Transaction, String> {

    boolean existsByUserIdAndDeduplicationHash(String userId, String hash);

    List<Transaction> findAllByUserId(String userId);

    java.util.Optional<Transaction> findByIdAndUserId(String id, String userId);

    List<Transaction> findByUserIdAndTransactionDateTimeBetween(String userId, Instant start, Instant end);

    @Query("{ 'userId': ?0, '$or': [ { 'category.categoryId': null }, { 'category.source': 'DEFAULT' } ] }")
    List<Transaction> findUncategorizedByUserId(String userId);
}
