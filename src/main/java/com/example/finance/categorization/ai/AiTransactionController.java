package com.example.finance.categorization.ai;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.finance.categorization.ai.AiCategorizationDtos.BatchResponse;
import com.example.finance.categorization.ai.AiCategorizationDtos.Request;

@RestController
@RequestMapping("/api/v1/transactions")
public class AiTransactionController {

    private final AiCategorizationService aiCategorizationService;

    public AiTransactionController(AiCategorizationService aiCategorizationService) {
        this.aiCategorizationService = aiCategorizationService;
    }

    @PostMapping("/ai-recategorize")
    public BatchResponse recategorizeWithAi(@RequestBody(required = false) Request request) {
        return aiCategorizationService.categorize(request == null ? new Request() : request);
    }
}
