package com.ranadvisor.config;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Builds the HTTP client every Gemini call goes through, in one place.
 *
 * <p>This exists because the proxy was configured on the chat model and forgotten on the
 * embedding model, and the second one then failed with
 * {@code HttpConnectTimeoutException: HTTP connect timed out} on every startup and every
 * RAG lookup. Both clients need identical network configuration, so neither should own it.
 *
 * <p>The reason it is needed at all: the Gemini modules talk over
 * {@code java.net.http.HttpClient} (langchain4j-http-client-jdk), and a client built with
 * {@code HttpClient.newBuilder()} does not pick up {@code -Dhttps.proxyHost}. Behind a
 * corporate proxy the selector has to be handed over explicitly.
 */
@Component
public class GeminiHttpClientFactory {

    /** Empty means "use the JVM's default proxy selector". */
    @Value("${gemini.proxy-host:}")
    private String proxyHost;

    @Value("${gemini.proxy-port:80}")
    private Integer proxyPort;

    @Value("${gemini.timeout-seconds:60}")
    private Integer timeoutSeconds;

    public HttpClientBuilder create() {
        ProxySelector selector = proxyHost.isBlank()
                ? ProxySelector.getDefault()
                : ProxySelector.of(new InetSocketAddress(proxyHost, proxyPort));

        HttpClient.Builder jdkBuilder = HttpClient.newBuilder().proxy(selector);

        return new JdkHttpClientBuilder()
                .httpClientBuilder(jdkBuilder)
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .readTimeout(Duration.ofSeconds(timeoutSeconds));
    }

    public String describe() {
        return proxyHost.isBlank()
                ? "JVM default selector (-Dhttps.proxyHost if set)"
                : proxyHost + ":" + proxyPort;
    }
}
