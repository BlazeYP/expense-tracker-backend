package com.example.finance.category.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.example.finance.category.model.Category;

public interface CategoryRepository extends MongoRepository<Category, String> {

    Optional<Category> findByUserIdAndNameIgnoreCase(String userId, String name);

    List<Category> findByUserIdAndActiveTrue(String userId);

    List<Category> findAllByUserId(String userId);

    Optional<Category> findByIdAndUserId(String id, String userId);
}
