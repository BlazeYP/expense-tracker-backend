package com.example.finance.transaction.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.finance.transaction.model.Transaction;
import com.example.finance.transaction.service.TransactionService;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public List<Transaction> findAll() {
        return transactionService.findAll();
    }

    @GetMapping("/{id}")
    public Transaction findById(@PathVariable String id) {
        return transactionService.findById(id);
    }

    @PatchMapping("/{id}/category")
    public Transaction overrideCategory(@PathVariable String id, @RequestBody Map<String, String> body) {
        return transactionService.overrideCategory(id, body.get("categoryId"));
    }

    @PostMapping("/{id}/recategorize")
    public Transaction recategorize(@PathVariable String id) {
        return transactionService.recategorize(id);
    }

    @PostMapping("/recategorize")
    public TransactionService.BulkRecategorizeResult recategorizeUncategorized() {
        return transactionService.recategorizeUncategorized();
    }
}
