package com.example.finance.merchant.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.example.finance.merchant.model.Merchant;

public interface MerchantRepository extends MongoRepository<Merchant, String> {

    Optional<Merchant> findByNormalizedNameAndActiveTrue(String name);

    List<Merchant> findByActiveTrue();
}
