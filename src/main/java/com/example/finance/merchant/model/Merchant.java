package com.example.finance.merchant.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("merchants")
public class Merchant {

    @Id
    public String id;
    public String userId;
    public String originalName;
    public String normalizedName;
    public String defaultCategoryId;
    public List<String> aliases = new ArrayList<>();
    public boolean active = true;
    public Instant createdAt = Instant.now();
    public Instant updatedAt = Instant.now();

    public Merchant() {
    }
}
