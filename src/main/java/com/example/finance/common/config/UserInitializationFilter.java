package com.example.finance.common.config;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.example.finance.category.service.CategorySeeder;
import com.example.finance.transaction.model.Transaction;
import com.example.finance.importstatement.model.ImportBatch;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

final class UserInitializationFilter extends OncePerRequestFilter {
    private final CategorySeeder categorySeeder;
    private final MongoTemplate mongoTemplate;
    private final Set<String> initialized = ConcurrentHashMap.newKeySet();

    UserInitializationFilter(CategorySeeder categorySeeder, MongoTemplate mongoTemplate) {
        this.categorySeeder = categorySeeder;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/v1/auth/")
                && SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt) {
            String userId = jwt.getToken().getSubject();
            if (initialized.add(userId)) {
                try {
                    ensureUserScopedIndexes(Transaction.class, "deduplicationHash");
                    ensureUserScopedIndexes(ImportBatch.class, "fileHash");
                    if (categorySeeder.needsSeeding(userId)) categorySeeder.seedDefaults(userId);
                } catch (RuntimeException exception) {
                    initialized.remove(userId);
                    throw exception;
                }
            }
        }
        chain.doFilter(request, response);
    }

    private void ensureUserScopedIndexes(Class<?> documentType, String uniqueField) {
        var operations = mongoTemplate.indexOps(documentType);
        for (IndexInfo index : operations.getIndexInfo()) {
            if (index.isUnique() && index.getIndexFields().size() == 1
                    && uniqueField.equals(index.getIndexFields().get(0).getKey())) {
                operations.dropIndex(index.getName());
            }
        }
        operations.createIndex(new Index()
                .on("userId", Sort.Direction.ASC)
                .on(uniqueField, Sort.Direction.ASC)
                .unique()
                .named("userId_" + uniqueField + "_unique"));
    }
}
