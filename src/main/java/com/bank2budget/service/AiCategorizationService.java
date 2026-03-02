package com.bank2budget.service;

import com.bank2budget.model.Transaction;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.DisabledChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Fallback categorization using a LangChain4j {@link ChatModel}.
 * <p>
 * The model implementation is injected via Spring and configured in {@code AiConfig}.
 * By default it uses OpenAI, but any OpenAI-compatible provider (Ollama, Mistral, etc.)
 * can be used by setting {@code app.categorization.ai.base-url} in application.yml.
 * <p>
 * If no API key is set, {@link DisabledChatModel} is injected and this service is a no-op.
 */
@Service
public class AiCategorizationService {

    private static final Logger log = LoggerFactory.getLogger(AiCategorizationService.class);

    /** Maximum transactions to send in a single AI request (to stay within token limits). */
    private static final int MAX_BATCH_SIZE = 50;

    private final ChatModel chatModel;

    public AiCategorizationService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public boolean isConfigured() {
        return !(chatModel instanceof DisabledChatModel);
    }

    /**
     * Categorizes transactions using the configured AI model. Returns a list of category strings
     * in the same order as input. Unknown/unclassifiable transactions get an empty string.
     *
     * @param transactions  list of uncategorized transactions
     * @param categoryNames all known category names (for the AI prompt)
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
        try {
            String response = chatModel.chat(
                    SystemMessage.from(buildSystemPrompt(categoryNames)),
                    UserMessage.from(buildUserPrompt(batch))
            ).aiMessage().text();

            return parseResponse(response, batch.size());
        } catch (Exception e) {
            log.error("AI categorization request failed: {}", e.getMessage());
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

    private List<String> parseResponse(String response, int expectedCount) {
        if (response == null || response.isBlank()) {
            return new ArrayList<>(java.util.Collections.nCopies(expectedCount, ""));
        }

        try {
            String[] lines = response.strip().split("\\n");
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
            log.error("Failed to parse AI response: {}", e.getMessage());
            return new ArrayList<>(java.util.Collections.nCopies(expectedCount, ""));
        }
    }
}
