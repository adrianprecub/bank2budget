package com.bank2budget.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * Configuration for transaction categorization.
 * <p>
 * Keyword rules are defined in application.yml under {@code app.categorization.rules}.
 * Each key is a category name, and the value is a list of keywords to match (case-insensitive)
 * against transaction details, counterparty name, and remittance info.
 * <p>
 * The AI fallback uses LangChain4j's {@code ChatModel} abstraction. By default it connects
 * to OpenAI, but any OpenAI-compatible endpoint (Ollama, Mistral, etc.) can be used by
 * setting {@code app.categorization.ai.base-url}.
 * <p>
 * Example:
 * <pre>
 * app:
 *   categorization:
 *     rules:
 *       "Groceries":
 *         - "albert heijn"
 *     ai:
 *       api-key: ${OPENAI_API_KEY:}
 *       model: gpt-4o-mini
 *       # Optional: point to any OpenAI-compatible endpoint
 *       # base-url: http://localhost:11434/v1  # Ollama example
 * </pre>
 */
@ConfigurationProperties(prefix = "app.categorization")
public record CategorizationProperties(
        Map<String, List<String>> rules,
        Ai ai
) {
    public CategorizationProperties {
        if (rules == null) rules = Map.of();
        if (ai == null) ai = new Ai(null, null, null);
    }

    public record Ai(String apiKey, String model, String baseUrl) {
        public Ai {
            if (model == null || model.isBlank()) model = "gpt-4o-mini";
        }

        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
