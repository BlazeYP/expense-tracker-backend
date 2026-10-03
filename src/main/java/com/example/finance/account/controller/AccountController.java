package com.example.finance.account.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.finance.account.model.Account;
import com.example.finance.account.repository.AccountRepository;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountRepository accountRepository;

    public AccountController(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @GetMapping
    public List<Account> findAll() {
        return accountRepository.findAll();
    }

    @PostMapping
    public Account create(@RequestBody Account account) {
        account.id = null;
        account.last4 = normalizeLast4(account.last4);
        Instant now = Instant.now();
        account.updatedAt = now;
        account.createdAt = now;
        return accountRepository.save(account);
    }

    @PutMapping("/{id}")
    public Account update(@PathVariable String id, @RequestBody Account account) {
        Account existing = accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        existing.provider = account.provider;
        existing.bankName = account.bankName;
        existing.accountType = account.accountType;
        existing.last4 = account.last4;
        existing.currency = account.currency;
        existing.active = account.active;
        existing.updatedAt = Instant.now();
        return accountRepository.save(existing);
    }

    private static String normalizeLast4(String last4) {
        if (last4 == null) {
            return null;
        }
        String digits = last4.replaceAll("\\D", "");
        if (digits.length() > 4) {
            return digits.substring(digits.length() - 4);
        }
        return digits;
    }
}
