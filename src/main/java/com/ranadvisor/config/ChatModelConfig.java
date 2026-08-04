package com.ranadvisor.config;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;

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
 * <p><b>Proxy.</b> The Gemini module talks over {@code java.net.http.HttpClient}
 * (langchain4j-http-client-jdk), not OkHttp. A client built through
 * {@code HttpClient.newBuilder()} does not pick up {@code -Dhttps.proxyHost} on
 * its own, so behind a corporate proxy every call fails with
 * {@code TimeoutException: HTTP connect timed out}. The selector is therefore set
 * explicitly here: from {@code gemini.proxy-host} / {@code gemini.proxy-port} when
 * present, otherwise from {@link ProxySelector#getDefault()}, which does read the
 * {@code -Dhttps.proxyHost} JVM arguments.
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

    /** Empty means "use the JVM's default proxy selector". */
    @Value("${gemini.proxy-host:}")
    private String proxyHost;

    @Value("${gemini.proxy-port:80}")
    private Integer proxyPort;

    @Value("${gemini.timeout-seconds:60}")
    private Integer timeoutSeconds;

    /** Used by both agents. Low temperature = more methodical tool use. */
    @Bean
    @Primary
    public ChatModel chatModel() {
        System.out.println("[ChatModelConfig] Provider=Google Gemini (native API), model=" + modelName);
        System.out.println("[ChatModelConfig] Proxy=" + (proxyHost.isBlank()
                ? "JVM default selector (-Dhttps.proxyHost if set)"
                : proxyHost + ":" + proxyPort));
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
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .httpClientBuilder(proxyAwareHttpClientBuilder())
                .build();
    }

    private HttpClientBuilder proxyAwareHttpClientBuilder() {
        ProxySelector selector = proxyHost.isBlank()
                ? ProxySelector.getDefault()
                : ProxySelector.of(new InetSocketAddress(proxyHost, proxyPort));

        HttpClient.Builder jdkBuilder = HttpClient.newBuilder().proxy(selector);

        return new JdkHttpClientBuilder()
                .httpClientBuilder(jdkBuilder)
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .readTimeout(Duration.ofSeconds(timeoutSeconds));
    }
}
