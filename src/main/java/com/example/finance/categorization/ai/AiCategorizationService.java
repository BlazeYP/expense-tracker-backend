package com.example.finance.categorization.ai;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.example.finance.categorization.ai.AiCategorizationDtos.BatchResponse;
import com.example.finance.categorization.ai.AiCategorizationDtos.ItemResult;
import com.example.finance.categorization.ai.AiCategorizationDtos.Request;
import com.example.finance.category.model.Category;
import com.example.finance.category.repository.CategoryRepository;
import com.example.finance.transaction.model.Transaction;
import com.example.finance.transaction.repository.TransactionRepository;

@Service
public class AiCategorizationService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final AiCategorizationProperties properties;
    private final AiCategorizationProvider provider;

    public AiCategorizationService(
            TransactionRepository transactionRepository,
            CategoryRepository categoryRepository,
            AiCategorizationProperties properties,
            List<AiCategorizationProvider> providers
    ) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.properties = properties;
        this.provider = providers.stream()
                .filter(candidate -> candidate.supports(properties.getProvider()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown AI categorization provider '"
                        + properties.getProvider() + "'. Available providers: "
                        + providers.stream().map(AiCategorizationProvider::name).sorted().toList()));
    }

    public BatchResponse categorize(Request request) {
        validateRequest(request);
        if (!properties.isEnabled() || properties.getApiKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI categorization is not configured; set APP_AI_CATEGORIZATION_ENABLED and APP_AI_CATEGORIZATION_API_KEY");
        }

        List<Category> categories = categoryRepository.findByActiveTrue();
        if (categories.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No active categories are available");
        }
        Map<String, Category> categoriesById = categories.stream()
                .filter(category -> category.id != null)
                .collect(Collectors.toMap(category -> category.id, Function.identity(), (first, ignored) -> first));

        List<Transaction> eligible = transactionRepository.findAll().stream()
                .filter(this::isEligible)
                .filter(transaction -> !request.onlyDefaultCategory
                        || transaction.category == null
                        || transaction.category.source == Transaction.CategorySource.DEFAULT)
                .limit(request.limit)
                .toList();

        if (eligible.isEmpty()) {
            return new BatchResponse(0, 0, 0, 0, 0, 0, request.dryRun, List.of());
        }

        List<ItemResult> results = new ArrayList<>();
        int updated = 0;
        int proposed = 0;
        int skipped = 0;
        int failed = 0;
        try {
            Map<String, String> transactionIdsByReference = new java.util.HashMap<>();
            List<AiCategorizationProvider.TransactionInput> providerTransactions = new ArrayList<>();
            for (int index = 0; index < eligible.size(); index++) {
                Transaction transaction = eligible.get(index);
                String reference = "tx_" + index;
                transactionIdsByReference.put(reference, transaction.id);
                providerTransactions.add(new AiCategorizationProvider.TransactionInput(reference,
                        transaction.type == null ? null : transaction.type.name(), transaction.amount,
                        transaction.currency, transaction.description,
                        transaction.counterparty == null ? null : transaction.counterparty.originalName));
            }
            List<AiCategorizationProvider.CategoryInput> providerCategories = categories.stream()
                    .map(category -> new AiCategorizationProvider.CategoryInput(category.id, category.name,
                            category.description, category.transactionType))
                    .toList();
            Map<String, AiCategorizationProvider.Proposal> providerProposals = provider.categorize(
                    providerTransactions, providerCategories);
            Map<String, Proposal> proposals = new java.util.HashMap<>();
            providerProposals.forEach((reference, proposal) -> {
                String transactionId = transactionIdsByReference.get(reference);
                if (transactionId != null) proposals.put(transactionId,
                        new Proposal(proposal.categoryId(), proposal.confidence(), proposal.reason()));
            });
            for (Transaction transaction : eligible) {
                Proposal proposal = proposals.get(transaction.id);
                if (proposal == null) {
                    failed++;
                    results.add(new ItemResult(transaction.id, null, null, null,
                            "The model did not return a result for this transaction", "FAILED"));
                    continue;
                }
                Category category = categoriesById.get(proposal.categoryId());
                if (category == null) {
                    failed++;
                    results.add(new ItemResult(transaction.id, proposal.categoryId(), null, proposal.confidence(),
                            "The model selected an unknown or inactive category", "FAILED"));
                    continue;
                }
                if (proposal.confidence() < request.minimumConfidence) {
                    skipped++;
                    results.add(new ItemResult(transaction.id, category.id, category.name, proposal.confidence(),
                            proposal.reason(), "LOW_CONFIDENCE"));
                    continue;
                }
                if (request.dryRun) {
                    proposed++;
                    results.add(new ItemResult(transaction.id, category.id, category.name, proposal.confidence(),
                            proposal.reason(), "PROPOSED"));
                    continue;
                }
                saveProposal(transaction, category, proposal);
                updated++;
                results.add(new ItemResult(transaction.id, category.id, category.name, proposal.confidence(),
                        proposal.reason(), "UPDATED"));
            }
        } catch (Exception exception) {
            for (Transaction transaction : eligible) {
                if (results.stream().noneMatch(result -> result.transactionId().equals(transaction.id))) {
                    failed++;
                    results.add(new ItemResult(transaction.id, null, null, null,
                            "AI categorization failed; no category was changed", "FAILED"));
                }
            }
        }

        return new BatchResponse(eligible.size(), eligible.size(), updated, proposed, skipped, failed,
                request.dryRun, results);
    }

    private void validateRequest(Request request) {
        if (request.limit < 1 || request.limit > properties.getMaxBatchSize()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "limit must be between 1 and " + properties.getMaxBatchSize());
        }
        if (!Double.isFinite(request.minimumConfidence)
                || request.minimumConfidence < 0 || request.minimumConfidence > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "minimumConfidence must be between 0 and 1");
        }
    }

    private boolean isEligible(Transaction transaction) {
        return transaction.id != null
                && "DEBIT".equalsIgnoreCase(transaction.type.toString())
                && (transaction.category == null || !transaction.category.manuallyOverridden)
                && (transaction.categorization == null
                        || transaction.categorization.source != Transaction.CategorySource.MANUAL);
    }

    private void saveProposal(Transaction transaction, Category category, Proposal proposal) {
        Transaction.CategoryInfo info = new Transaction.CategoryInfo();
        info.categoryId = category.id;
        info.categoryName = category.name;
        info.source = Transaction.CategorySource.AI;
        info.confidence = proposal.confidence();
        info.reason = proposal.reason();
        info.manuallyOverridden = false;
        transaction.category = info;
        if (transaction.categorization == null) transaction.categorization = new Transaction.Categorization();
        transaction.categorization.source = Transaction.CategorySource.AI;
        transaction.categorization.ruleId = null;
        transaction.categorization.confidence = proposal.confidence();
        transaction.categorization.categorizedAt = Instant.now();
        transaction.updatedAt = Instant.now();
        transactionRepository.save(transaction);
    }

    private record Proposal(String categoryId, double confidence, String reason) {}
}
