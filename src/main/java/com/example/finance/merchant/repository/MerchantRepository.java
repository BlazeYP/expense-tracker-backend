package com.example.finance.merchant.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.example.finance.merchant.model.Merchant;

public interface MerchantRepository extends MongoRepository<Merchant, String> {

    Optional<Merchant> findByUserIdAndNormalizedNameAndActiveTrue(String userId, String name);

    List<Merchant> findByUserIdAndActiveTrue(String userId);

    List<Merchant> findAllByUserId(String userId);

    Optional<Merchant> findByIdAndUserId(String id, String userId);
}
