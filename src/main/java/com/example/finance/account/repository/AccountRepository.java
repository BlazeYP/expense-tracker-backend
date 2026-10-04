package com.example.finance.account.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.example.finance.account.model.Account;

public interface AccountRepository extends MongoRepository<Account, String> {
    java.util.List<Account> findAllByUserId(String userId);
    java.util.Optional<Account> findByIdAndUserId(String id, String userId);
}
