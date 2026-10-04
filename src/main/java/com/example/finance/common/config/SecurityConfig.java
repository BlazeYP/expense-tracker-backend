package com.example.finance.common.config;

import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import com.example.finance.category.service.CategorySeeder;

@Configuration
public class SecurityConfig {
    @Bean
    SecretKey appJwtKey(@Value("${app.auth.jwt-secret}") String encoded) {
        byte[] bytes = Base64.getDecoder().decode(encoded);
        if (bytes.length < 32) throw new IllegalStateException("APP_AUTH_JWT_SECRET must decode to at least 32 bytes");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
    @Bean JwtEncoder jwtEncoder(SecretKey appJwtKey) { return new NimbusJwtEncoder(new ImmutableSecret<>(appJwtKey)); }
    @Bean JwtDecoder jwtDecoder(SecretKey appJwtKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(appJwtKey).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer("expense-tracker");
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuer,
                new JwtClaimValidator<String>("sub", value -> value != null && !value.isBlank())));
        return decoder;
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, CategorySeeder categorySeeder,
            org.springframework.data.mongodb.core.MongoTemplate mongoTemplate) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/google", "/actuator/health", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> { }))
                .addFilterAfter(new UserInitializationFilter(categorySeeder, mongoTemplate), BearerTokenAuthenticationFilter.class)
                .build();
    }
}
