package com.example.finance.auth.repository;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import com.example.finance.auth.model.AppUser;

public interface AppUserRepository extends MongoRepository<AppUser, String> {
    Optional<AppUser> findByGoogleSubject(String googleSubject);
}
