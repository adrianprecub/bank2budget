package com.bank2budget.service;

import com.bank2budget.config.CategorizationProperties;
import com.bank2budget.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fallback categorization using the OpenAI Chat Completions API.
 * <p>
 * Sends uncategorized transactions as a batch to GPT and parses
 * the response — one category per line, in the same order as input.
 * <p>
 * Configured via {@code app.categorization.ai.api-key} and {@code app.categorization.ai.model}.
 * If the API key is not set, this service is a no-op.
 */
@Service
public class AiCategorizationService {

    private static final Logger log = LoggerFactory.getLogger(AiCategorizationService.class);
    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";

    /** Maximum transactions to send in a single AI request (to stay within token limits). */
    private static final int MAX_BATCH_SIZE = 50;

    private final CategorizationProperties.Ai aiConfig;
    private final RestClient restClient;

    public AiCategorizationService(CategorizationProperties properties) {
        this.aiConfig = properties.ai();
        this.restClient = RestClient.builder().build();
    }

    public boolean isConfigured() {
        return aiConfig.isConfigured();
    }

    /**
     * Categorizes transactions using OpenAI. Returns a list of category strings
     * in the same order as input. Unknown/unclassifiable transactions get empty string.
     *
     * @param transactions   list of uncategorized transactions
     * @param categoryNames  all known category names (for the AI prompt)
     */
    public List<String> categorize(List<Transaction> transactions, List<String> categoryNames) {
        if (!isConfigured() || transactions.isEmpty()) {
            return transactions.stream().map(_ -> "").toList();
        }

        List<String> allResults = new ArrayList<>();

        // Process in batches to avoid token limits
        for (int i = 0; i < transactions.size(); i += MAX_BATCH_SIZE) {
            int end = Math.min(i + MAX_BATCH_SIZE, transactions.size());
            List<Transaction> batch = transactions.subList(i, end);
            List<String> batchResults = categorizeBatch(batch, categoryNames);
            allResults.addAll(batchResults);
        }

        return allResults;
    }

    private List<String> categorizeBatch(List<Transaction> batch, List<String> categoryNames) {
        String systemPrompt = buildSystemPrompt(categoryNames);
        String userPrompt = buildUserPrompt(batch);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(OPENAI_URL)
                    .header("Authorization", "Bearer " + aiConfig.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", aiConfig.model(),
                            "temperature", 0.0,
                            "messages", List.of(
                                    Map.of("role", "system", "content", systemPrompt),
                                    Map.of("role", "user", "content", userPrompt)
                            )
                    ))
                    .retrieve()
                    .body(Map.class);

            return parseResponse(response, batch.size());
        } catch (Exception e) {
            log.error("OpenAI API call failed: {}", e.getMessage());
            return batch.stream().map(_ -> "").toList();
        }
    }

    private String buildSystemPrompt(List<String> categoryNames) {
        return """
                You are a financial transaction categorizer. Given bank transaction descriptions, \
                assign each one to exactly one of the following budget categories:
                
                %s
                
                Rules:
                - Reply with ONLY the category names, one per line, in the exact same order as the input transactions.
                - Use the EXACT category name from the list above.
                - If a transaction does not clearly fit any category, reply with "Miscellaneous" for that line.
                - Do NOT add numbering, explanations, or any other text — just the category name per line.
                """.formatted(String.join("\n", categoryNames));
    }

    private String buildUserPrompt(List<Transaction> batch) {
        var sb = new StringBuilder();
        for (int i = 0; i < batch.size(); i++) {
            Transaction tx = batch.get(i);
            sb.append("%d. ".formatted(i + 1));
            if (tx.details() != null) sb.append(tx.details());
            if (tx.counterpartyName() != null) sb.append(" | ").append(tx.counterpartyName());
            if (tx.remittanceInfo() != null) sb.append(" | ").append(tx.remittanceInfo());
            sb.append(" | Amount: ").append(tx.amount()).append(' ').append(tx.currency());
            sb.append('\n');
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private List<String> parseResponse(Map<String, Object> response, int expectedCount) {
        if (response == null) {
            return new ArrayList<>(java.util.Collections.nCopies(expectedCount, ""));
        }

        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                log.warn("OpenAI response has no choices");
                return new ArrayList<>(java.util.Collections.nCopies(expectedCount, ""));
            }

            Map<String, Object> message = (Map<String, Object>) choices.getFirst().get("message");
            String content = (String) message.get("content");

            String[] lines = content.strip().split("\\n");
            List<String> categories = new ArrayList<>(expectedCount);
            for (int i = 0; i < expectedCount; i++) {
                if (i < lines.length) {
                    // Strip any leading numbering (e.g. "1. Groceries" -> "Groceries")
                    String line = lines[i].strip().replaceFirst("^\\d+\\.\\s*", "");
                    categories.add(line);
                } else {
                    categories.add("");
                }
            }
            return categories;
        } catch (Exception e) {
            log.error("Failed to parse OpenAI response: {}", e.getMessage());
            return new ArrayList<>(java.util.Collections.nCopies(expectedCount, ""));
        }
    }
}
