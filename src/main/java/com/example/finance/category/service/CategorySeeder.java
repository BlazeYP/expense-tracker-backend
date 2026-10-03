package com.example.finance.category.service;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.example.finance.category.model.Category;
import com.example.finance.category.repository.CategoryRepository;

@Component
public class CategorySeeder implements ApplicationRunner {

    private static final List<String> DEFAULT_CATEGORY_NAMES = List.of(
            "Food & Dining",
            "Restaurants",
            "Groceries",
            "Food Delivery",
            "Transportation",
            "Fuel",
            "Cab",
            "Public Transport",
            "Shopping",
            "Electronics",
            "Clothing",
            "General Shopping",
            "Bills & Utilities",
            "Electricity",
            "Internet",
            "Mobile",
            "Rent",
            "Entertainment",
            "Healthcare",
            "Travel",
            "Education",
            "Investments",
            "Salary",
            "Refund",
            "Cashback",
            "Transfer",
            "Other"
    );

    private final CategoryRepository categoryRepository;

    public CategorySeeder(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String name : DEFAULT_CATEGORY_NAMES) {
            if (categoryRepository.findByNameIgnoreCase(name).isEmpty()) {
                Category category = new Category();
                category.name = name;
                category.systemDefined = true;
                category.active = true;
                categoryRepository.save(category);
            }
        }
    }
}
