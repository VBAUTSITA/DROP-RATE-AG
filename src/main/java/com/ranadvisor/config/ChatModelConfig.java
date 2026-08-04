package com.ranadvisor.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Single place where the LLM provider is chosen, so the same codebase runs
 * against OpenRouter (home setup) and Google Gemini (office setup) with only
 * application.properties changing.
 *
 * <p>Switch with {@code llm.provider}:
 * <ul>
 *   <li>{@code openrouter} — OpenAI-compatible endpoint (default).</li>
 *   <li>{@code gemini} — Google AI native endpoint. Required when using Gemini
 *       "thinking" models (2.5+, 3.x) together with tools: those models emit a
 *       {@code thought_signature} that must be echoed back on the next turn, and
 *       the OpenAI-compatible schema has no field to carry it. Going through the
 *       compatibility endpoint fails the tool loop with
 *       {@code 400 INVALID_ARGUMENT: Function call is missing a thought_signature}.</li>
 * </ul>
 *
 * <p>Three beans are exposed because the callers want different temperatures:
 * the agents are methodical (0.1), the guardrail classifier is deterministic
 * (0.0), and free-form chat is creative (0.7).
 */
@Configuration
public class ChatModelConfig {

    @Value("${llm.provider:openrouter}")
    private String provider;

    @Value("${llm.api-key:${openai.api-key}}")
    private String apiKey;

    @Value("${llm.model:openai/gpt-4o-mini}")
    private String modelName;

    @Value("${llm.base-url:https://openrouter.ai/api/v1}")
    private String baseUrl;

    @Value("${llm.max-retries:2}")
    private Integer maxRetries;

    /** Used by both agents. Low temperature = more methodical tool use. */
    @Bean
    @Primary
    public ChatModel chatModel() {
        System.out.println("[ChatModelConfig] provider=" + provider + ", model=" + modelName);
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
        if ("gemini".equalsIgnoreCase(provider)) {
            return GoogleAiGeminiChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelName)
                    .temperature(temperature)
                    .maxRetries(maxRetries)
                    .build();
        }

        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .maxRetries(maxRetries)
                .build();
    }
}
