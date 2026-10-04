package com.example.finance.category.service;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.finance.category.model.Category;
import com.example.finance.category.repository.CategoryRepository;
import com.example.finance.common.security.CurrentUser;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<Category> findAll() {
        return categoryRepository.findAllByUserId(CurrentUser.id());
    }

    public Category create(Category category) {
        if (category.name == null || category.name.isBlank()) {
            throw new IllegalArgumentException("Category name is required");
        }
        category.id = null;
        category.userId = CurrentUser.id();
        Instant now = Instant.now();
        category.createdAt = now;
        category.updatedAt = now;
        return categoryRepository.save(category);
    }

    public Category update(String id, Category incoming) {
        Category category = categoryRepository.findByIdAndUserId(id, CurrentUser.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        category.name = incoming.name;
        category.description = incoming.description;
        category.transactionType = incoming.transactionType;
        category.parentCategoryId = incoming.parentCategoryId;
        category.active = incoming.active;
        category.updatedAt = Instant.now();
        return categoryRepository.save(category);
    }

    public void deactivate(String id) {
        Category category = categoryRepository.findByIdAndUserId(id, CurrentUser.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        category.active = false;
        category.updatedAt = Instant.now();
        categoryRepository.save(category);
    }
}
