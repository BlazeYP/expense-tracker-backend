package com.example.finance.categorization.ai;

import java.util.List;

public final class AiCategorizationDtos {
    private AiCategorizationDtos() {}

    public static class Request {
        public int limit = 100;
        public double minimumConfidence = 0.70;
        public boolean dryRun = false;
        public boolean onlyDefaultCategory = true;
    }

    public record ItemResult(
            String transactionId,
            String categoryId,
            String categoryName,
            Double confidence,
            String reason,
            String status
    ) {}

    public record BatchResponse(
            int scanned,
            int processed,
            int updated,
            int proposed,
            int skipped,
            int failed,
            boolean dryRun,
            List<ItemResult> results
    ) {}
}
