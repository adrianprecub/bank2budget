package com.bank2budget.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.DisabledChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds a LangChain4j {@link ChatModel} bean from {@code app.categorization.ai.*} properties.
 * <p>
 * Uses OpenAI's {@code ChatModel} implementation by default, but the {@code base-url} property
 * can redirect to any OpenAI-compatible endpoint (Ollama, Mistral, LM Studio, etc.).
 * <p>
 * If no API key is configured, a {@link DisabledChatModel} is registered so the application
 * starts cleanly and skips AI categorization entirely.
 */
@Configuration
@EnableConfigurationProperties(CategorizationProperties.class)
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Bean
    public ChatModel chatModel(CategorizationProperties properties) {
        CategorizationProperties.Ai ai = properties.ai();

        if (!ai.isConfigured()) {
            log.info("No AI API key configured — AI categorization disabled");
            return new DisabledChatModel();
        }

        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .apiKey(ai.apiKey())
                .modelName(ai.model())
                .temperature(0.0);

        if (ai.baseUrl() != null && !ai.baseUrl().isBlank()) {
            log.info("AI categorization using custom base URL: {}", ai.baseUrl());
            builder.baseUrl(ai.baseUrl());
        }

        log.info("AI categorization enabled with model: {}", ai.model());
        return builder.build();
    }
}
