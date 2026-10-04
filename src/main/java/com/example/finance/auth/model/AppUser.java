package com.example.finance.auth.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("users")
public class AppUser {
    @Id public String id;
    @Indexed(unique = true) public String googleSubject;
    @Indexed(unique = true) public String email;
    public String name;
    public String picture;
    public Instant createdAt = Instant.now();
    public Instant updatedAt = Instant.now();
}
