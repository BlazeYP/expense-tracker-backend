package com.example.finance.merchant.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.finance.merchant.model.Merchant;
import com.example.finance.merchant.service.MerchantService;

@RestController
@RequestMapping("/api/v1/merchants")
public class MerchantController {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @GetMapping
    public List<Merchant> findAll() {
        return merchantService.findAll();
    }

    @PostMapping
    public Merchant create(@RequestBody Merchant merchant) {
        merchant.id = null;
        return merchantService.save(merchant);
    }

    @PutMapping("/{id}")
    public Merchant update(@PathVariable String id, @RequestBody Merchant merchant) {
        merchant.id = id;
        return merchantService.save(merchant);
    }
}
