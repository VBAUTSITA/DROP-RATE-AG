package com.ranadvisor.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Google Gemini is the only LLM provider on this branch.
 *
 * <p>This is deliberate and is not a configuration choice: the code cannot reach
 * any other AI endpoint, because {@code langchain4j-open-ai} is not on the
 * classpath at all. Nothing here can send traffic to a public AI host other than
 * Google's, whether by default, by typo, or by a missing property.
 *
 * <p>The native Gemini API (rather than Google's OpenAI-compatible endpoint) is
 * required for tool calling: Gemini "thinking" models (2.5+, 3.x) emit a
 * {@code thought_signature} with each {@code functionCall} that must be echoed
 * back on the next turn, and the OpenAI-compatible schema has no field to carry
 * it. Through the compatibility endpoint the tool loop fails with
 * {@code 400 INVALID_ARGUMENT: Function call is missing a thought_signature}.
 *
 * <p>Three beans are exposed because callers want different temperatures: the
 * agents are methodical (0.1), the guardrail classifier is deterministic (0.0),
 * and free-form chat is creative (0.7).
 */
@Configuration
public class ChatModelConfig {

    // No defaults: startup must fail loudly if these are missing rather than
    // guess. Both are required in application.properties.
    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.model}")
    private String modelName;

    /** Free-tier quota is small; each retry consumes it (429 RESOURCE_EXHAUSTED). */
    @Value("${gemini.max-retries:2}")
    private Integer maxRetries;

    /** Used by both agents. Low temperature = more methodical tool use. */
    @Bean
    @Primary
    public ChatModel chatModel() {
        System.out.println("[ChatModelConfig] Provider=Google Gemini (native API), model=" + modelName);
        return build(0.1);
    }

    /** Deterministic output for consistent guardrail classification. */
    @Bean(name = "guardrailChatModel")
    public ChatModel guardrailChatModel() {
        return build(0.0);
    }

    /** Free-form chat endpoint, no tools. */
    @Bean(name = "freeChatModel")
    public ChatModel freeChatModel() {
        return build(0.7);
    }

    private ChatModel build(double temperature) {
        return GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxRetries(maxRetries)
                .build();
    }
}
