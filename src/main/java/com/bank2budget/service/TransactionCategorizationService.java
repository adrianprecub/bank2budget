package com.bank2budget.service;

import com.bank2budget.config.CategorizationProperties;
import com.bank2budget.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Categorizes transactions using a two-pass approach:
 * <ol>
 *   <li><b>Keyword matching</b> — fast, deterministic, configurable via {@code app.categorization.rules}</li>
 *   <li><b>AI fallback</b> — sends uncategorized transactions to OpenAI for classification</li>
 * </ol>
 * <p>
 * Keyword matching is case-insensitive and searches across the transaction's
 * {@code details}, {@code counterpartyName}, and {@code remittanceInfo} fields.
 */
@Service
public class TransactionCategorizationService {

    private static final Logger log = LoggerFactory.getLogger(TransactionCategorizationService.class);

    /** Pre-compiled rules: each entry is (lowercase keyword → category name). Ordered by config order. */
    private final List<KeywordRule> rules;
    private final AiCategorizationService aiService;
    private final CategorizationProperties properties;

    public TransactionCategorizationService(CategorizationProperties properties,
                                            AiCategorizationService aiService) {
        this.properties = properties;
        this.aiService = aiService;
        this.rules = compileRules(properties.rules());
        log.info("Loaded {} keyword rules across {} categories",
                rules.size(), properties.rules().size());
    }

    /**
     * Categorizes a list of transactions. Returns a list of category strings
     * in the same order as the input. Uncategorized transactions get an empty string.
     */
    public List<String> categorize(List<Transaction> transactions) {
        if (transactions.isEmpty()) return List.of();

        List<String> categories = new ArrayList<>(transactions.size());
        List<Integer> uncategorizedIndices = new ArrayList<>();

        // Pass 1: keyword matching
        for (int i = 0; i < transactions.size(); i++) {
            String category = matchByKeyword(transactions.get(i));
            categories.add(category);
            if (category.isEmpty()) {
                uncategorizedIndices.add(i);
            }
        }

        int keywordMatched = transactions.size() - uncategorizedIndices.size();
        log.info("Keyword matching: {}/{} transactions categorized", keywordMatched, transactions.size());

        // Pass 2: AI fallback for unmatched
        if (!uncategorizedIndices.isEmpty() && aiService.isConfigured()) {
            log.info("Sending {} uncategorized transactions to AI", uncategorizedIndices.size());
            try {
                List<Transaction> uncategorized = uncategorizedIndices.stream()
                        .map(transactions::get)
                        .toList();
                List<String> aiCategories = aiService.categorize(uncategorized, allCategoryNames());
                for (int i = 0; i < uncategorizedIndices.size(); i++) {
                    String aiCategory = i < aiCategories.size() ? aiCategories.get(i) : "";
                    categories.set(uncategorizedIndices.get(i), aiCategory);
                }
                long aiMatched = aiCategories.stream().filter(c -> !c.isEmpty()).count();
                log.info("AI categorized {}/{} remaining transactions", aiMatched, uncategorizedIndices.size());
            } catch (Exception e) {
                log.warn("AI categorization failed, leaving transactions uncategorized: {}", e.getMessage());
            }
        } else if (!uncategorizedIndices.isEmpty()) {
            log.info("{} transactions left uncategorized (AI not configured)", uncategorizedIndices.size());
        }

        return categories;
    }

    /**
     * Attempts to match a transaction against keyword rules.
     * Returns the category name or empty string if no match.
     */
    String matchByKeyword(Transaction tx) {
        String searchText = buildSearchText(tx);
        for (KeywordRule rule : rules) {
            if (searchText.contains(rule.keyword)) {
                return rule.category;
            }
        }
        return "";
    }

    /**
     * Builds a single lowercase search string from all relevant transaction fields.
     */
    private String buildSearchText(Transaction tx) {
        var sb = new StringBuilder();
        if (tx.details() != null) sb.append(tx.details().toLowerCase()).append(' ');
        if (tx.counterpartyName() != null) sb.append(tx.counterpartyName().toLowerCase()).append(' ');
        if (tx.remittanceInfo() != null) sb.append(tx.remittanceInfo().toLowerCase()).append(' ');
        return sb.toString();
    }

    /**
     * Flattens config rules into an ordered list of (keyword, category) pairs.
     * Keywords are lowercased and trimmed at compile time for fast matching.
     */
    private static List<KeywordRule> compileRules(Map<String, List<String>> configRules) {
        List<KeywordRule> compiled = new ArrayList<>();
        for (var entry : configRules.entrySet()) {
            String category = entry.getKey();
            for (String keyword : entry.getValue()) {
                if (keyword != null && !keyword.isBlank()) {
                    compiled.add(new KeywordRule(keyword.toLowerCase().trim(), category));
                }
            }
        }
        return List.copyOf(compiled);
    }

    private List<String> allCategoryNames() {
        return List.copyOf(properties.rules().keySet());
    }

    private record KeywordRule(String keyword, String category) {}
}
