package com.example.finance.categorization.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.example.finance.categorization.model.CategorizationRule;

public interface RuleRepository extends MongoRepository<CategorizationRule, String> {

    List<CategorizationRule> findByActiveTrueOrderByPriorityAsc();
}
