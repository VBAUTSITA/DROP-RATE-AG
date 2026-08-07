package com.ranadvisor.knowledge;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class KnowledgeIngestionService {

    private final JdbcTemplate jdbc;

    @Autowired(required = false)
    private EmbeddingStore<TextSegment> embeddingStore;

    @Autowired(required = false)
    private EmbeddingModel embeddingModel;

    public KnowledgeIngestionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ingest() {
        if (embeddingStore == null || embeddingModel == null) {
            System.out.println("[KnowledgeIngestion] RAG not configured. Skipping ingestion.");
            return;
        }

        // The "already ingested?" guard only applies to the persistent pgvector store.
        // An in-memory store starts empty on every boot, so querying telecom_knowledge there
        // would fail against a table that does not exist and abort an ingestion that is in
        // fact required.
        if (embeddingStore instanceof InMemoryEmbeddingStore) {
            System.out.println("[KnowledgeIngestion] In-memory store — ingesting on every startup.");
        } else {
            try {
                Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM telecom_knowledge", Integer.class);
                if (count != null && count > 0) {
                    System.out.println("[KnowledgeIngestion] Already ingested " + count + " chunks. Skipping.");
                    return;
                }
            } catch (Exception e) {
                System.err.println("[KnowledgeIngestion] Cannot query telecom_knowledge: " + e.getMessage());
                System.err.println("[KnowledgeIngestion] Create the table first (see SUMMARY.md Task B SQL setup).");
                return;
            }
        }

        System.out.println("[KnowledgeIngestion] Starting ingestion of knowledge base...");

        try {
            String content = new String(
                new ClassPathResource("knowledge/5G_NSA_CallDrop_KnowledgeBase.md")
                    .getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);

            // Split on chunk headers; the file uses "## CHUNK N — ..." delimiters
            String[] rawChunks = content.split("(?=## CHUNK)");

            int stored = 0;
            for (String chunk : rawChunks) {
                String trimmed = chunk.trim();
                if (trimmed.isEmpty()) continue;

                TextSegment segment = TextSegment.from(trimmed);
                Embedding embedding;
                try {
                    embedding = embeddingModel.embed(segment).content();
                } catch (Exception e) {
                    // Abort on the first failure instead of retrying 22 more times. Embedding
                    // errors are almost always the same for every chunk — a blocked endpoint,
                    // a bad key, no quota — so continuing only buries the cause under identical
                    // stack traces and delays startup by the timeout times the chunk count.
                    System.err.println("[KnowledgeIngestion] Embedding failed on chunk " + (stored + 1)
                            + ": " + e.getMessage());
                    System.err.println("[KnowledgeIngestion] Aborting ingestion. RAG will report the "
                            + "knowledge base as unavailable; the rest of the app is unaffected.");
                    System.err.println("[KnowledgeIngestion] If this is a connect timeout, the embedding "
                            + "model is not reaching Google — check gemini.proxy-host.");
                    return;
                }
                embeddingStore.add(embedding, segment);
                stored++;
                System.out.println("[KnowledgeIngestion] Embedded chunk " + stored + ": "
                    + trimmed.substring(0, Math.min(80, trimmed.length())) + "...");
            }

            System.out.println("[KnowledgeIngestion] Complete. Stored " + stored + " chunks.");

        } catch (Exception e) {
            System.err.println("[KnowledgeIngestion] Ingestion failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
