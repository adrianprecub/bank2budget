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
 * Example:
 * <pre>
 * app:
 *   categorization:
 *     rules:
 *       "Health Insurance":
 *         - "zilveren kruis"
 *         - "zorgverzeker"
 *       "Groceries":
 *         - "albert heijn"
 *         - " ah "
 * </pre>
 */
@ConfigurationProperties(prefix = "app.categorization")
public record CategorizationProperties(
        Map<String, List<String>> rules,
        Ai ai
) {
    public CategorizationProperties {
        if (rules == null) rules = Map.of();
        if (ai == null) ai = new Ai(null, null);
    }

    public record Ai(String apiKey, String model) {
        public Ai {
            if (model == null || model.isBlank()) model = "gpt-4o-mini";
        }

        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
