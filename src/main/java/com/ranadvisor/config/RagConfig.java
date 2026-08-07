package com.ranadvisor.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfig {

    @Bean
    public EmbeddingModel embeddingModel(
            @Value("${gemini.api-key}") String apiKey,
            @Value("${gemini.embedding-model:text-embedding-004}") String embeddingModelName) {
        return GoogleAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(embeddingModelName)
                .build();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(
            @Value("${spring.datasource.url}") String datasourceUrl,
            @Value("${spring.datasource.username}") String dbUser,
            @Value("${spring.datasource.password}") String dbPassword,
            @Value("${rag.embedding-dimension:768}") int dimension) {

        // pgvector is PostgreSQL-only. On Oracle it cannot be used at all — but returning
        // null here turned off RAG entirely, and RAG is the layer that keeps the agent's
        // expert advice tied to the knowledge base instead of to the model's own recall.
        // With it off, every tuning recommendation was ungrounded improvisation that read
        // exactly like a sourced answer. The knowledge base is 23 chunks, so holding the
        // vectors in memory costs almost nothing and restores the grounding.
        if (!datasourceUrl.startsWith("jdbc:postgresql://")) {
            System.out.println("[RagConfig] Datasource is not PostgreSQL — pgvector unavailable.");
            System.out.println("[RagConfig] Falling back to InMemoryEmbeddingStore: RAG stays ON, "
                    + "re-embedded at every startup (a few seconds, ~23 chunks).");
            return new InMemoryEmbeddingStore<>();
        }

        try {
            // Parse jdbc:postgresql://host:port/dbname
            String pgUrl = datasourceUrl.replace("jdbc:postgresql://", "");
            String host = pgUrl.split(":")[0];
            int port = Integer.parseInt(pgUrl.split(":")[1].split("/")[0]);
            String database = pgUrl.split("/")[1].split("\\?")[0];

            PgVectorEmbeddingStore store = PgVectorEmbeddingStore.builder()
                    .host(host)
                    .port(port)
                    .database(database)
                    .user(dbUser)
                    .password(dbPassword)
                    .table("telecom_knowledge")
                    // Google text-embedding-004 emits 768 dimensions, not the 1536 of
                    // OpenAI ada-002. A telecom_knowledge table created for the old model
                    // must be recreated, or set rag.embedding-dimension to match it.
                    .dimension(dimension)
                    .createTable(false) // table created manually via SQL; do not let LangChain4j alter it
                    .build();

            System.out.println("[RagConfig] PgVectorEmbeddingStore ready (table=telecom_knowledge).");
            return store;

        } catch (Exception e) {
            // Fail soft: app still starts, RAG tools return a "not available" message.
            // Cause: pgvector extension not installed, or telecom_knowledge table not created yet.
            System.err.println("[RagConfig] WARNING — PgVectorEmbeddingStore could not be created: " + e.getMessage());
            System.err.println("[RagConfig] Run the pgvector SQL setup (see SUMMARY.md Task B) then restart.");
            return null;
        }
    }
}
