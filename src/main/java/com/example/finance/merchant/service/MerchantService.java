package com.example.finance.merchant.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.finance.categorization.service.CategorizationService;
import com.example.finance.merchant.model.Merchant;
import com.example.finance.merchant.repository.MerchantRepository;
import com.example.finance.common.security.CurrentUser;

@Service
public class MerchantService {

    private final MerchantRepository merchantRepository;

    public MerchantService(MerchantRepository merchantRepository) {
        this.merchantRepository = merchantRepository;
    }

    public List<Merchant> findAll() {
        return merchantRepository.findAllByUserId(CurrentUser.id());
    }

    public Merchant save(Merchant merchant) {
        if (merchant.originalName == null || merchant.originalName.isBlank()) {
            throw new IllegalArgumentException("originalName is required");
        }
        if (merchant.id == null) {
            merchant.userId = CurrentUser.id();
        } else {
            Merchant existing = merchantRepository.findByIdAndUserId(merchant.id, CurrentUser.id())
                    .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.NOT_FOUND, "Merchant not found"));
            merchant.userId = existing.userId;
            merchant.createdAt = existing.createdAt;
        }
        merchant.normalizedName = CategorizationService.normalize(merchant.originalName);
        merchant.updatedAt = Instant.now();
        if (merchant.createdAt == null) {
            merchant.createdAt = merchant.updatedAt;
        }
        return merchantRepository.save(merchant);
    }
}
