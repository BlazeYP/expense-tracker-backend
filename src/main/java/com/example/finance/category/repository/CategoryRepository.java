package com.example.finance.category.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.example.finance.category.model.Category;

public interface CategoryRepository extends MongoRepository<Category, String> {

    Optional<Category> findByNameIgnoreCase(String name);

    List<Category> findByActiveTrue();
}
