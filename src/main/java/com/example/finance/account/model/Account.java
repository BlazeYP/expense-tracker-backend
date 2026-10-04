package com.example.finance.account.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("accounts")
public class Account {

    @Id
    public String id;
    public String userId;
    public String provider;
    public String bankName;
    public String accountType;
    public String last4;
    public String currency = "INR";
    public boolean active = true;
    public Instant createdAt = Instant.now();
    public Instant updatedAt = Instant.now();

    public Account() {
    }
}
