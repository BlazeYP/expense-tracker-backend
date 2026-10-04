package com.example.finance.categorization.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.finance.categorization.model.CategorizationRule;
import com.example.finance.categorization.repository.RuleRepository;
import com.example.finance.common.security.CurrentUser;

@RestController
@RequestMapping("/api/v1/rules")
public class RuleController {

    private final RuleRepository ruleRepository;

    public RuleController(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @GetMapping
    public List<CategorizationRule> findAll() {
        return ruleRepository.findAllByUserId(CurrentUser.id());
    }

    @PostMapping
    public CategorizationRule create(@RequestBody CategorizationRule rule) {
        if (rule.categoryId == null || rule.conditions == null || rule.conditions.isEmpty()) {
            throw new IllegalArgumentException("categoryId and at least one condition are required");
        }
        rule.id = null;
        rule.userId = CurrentUser.id();
        Instant now = Instant.now();
        rule.createdAt = now;
        rule.updatedAt = now;
        return ruleRepository.save(rule);
    }

    @PutMapping("/{id}")
    public CategorizationRule update(@PathVariable String id, @RequestBody CategorizationRule rule) {
        CategorizationRule existing = ruleRepository.findByIdAndUserId(id, CurrentUser.id())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Rule not found"));
        rule.id = id;
        rule.userId = existing.userId;
        rule.createdAt = existing.createdAt;
        rule.updatedAt = Instant.now();
        return ruleRepository.save(rule);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        CategorizationRule existing = ruleRepository.findByIdAndUserId(id, CurrentUser.id())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Rule not found"));
        ruleRepository.delete(existing);
    }
}
