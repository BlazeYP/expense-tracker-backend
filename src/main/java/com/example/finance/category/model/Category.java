package com.example.finance.category.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("categories")
public class Category {

    @Id
    public String id;
    public String userId;
    public String name;
    public String description;
    public String transactionType;
    public String parentCategoryId;
    public boolean active = true;
    public boolean systemDefined = false;
    public Instant createdAt = Instant.now();
    public Instant updatedAt = Instant.now();

    public Category() {
    }
}
