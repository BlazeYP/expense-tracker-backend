package com.example.finance.categorization.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("categorization_rules")
public class CategorizationRule {

    @Id
    public String id;
    public String name;
    public String categoryId;
    public String transactionType;
    public int priority = 100;
    public boolean active = true;
    public List<Condition> conditions = new ArrayList<>();
    public Instant createdAt = Instant.now();
    public Instant updatedAt = Instant.now();

    public CategorizationRule() {
    }

    public static class Condition {

        public String field;
        public String operator;
        public String value;

        public Condition() {
        }

        public Condition(String field, String operator, String value) {
            this.field = field;
            this.operator = operator;
            this.value = value;
        }
    }
}
