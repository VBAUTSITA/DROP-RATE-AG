# RAN Advisor — Summary of Changes & Current State

## Repo
- Git initialized, pushed to `https://github.com/VBAUTSITA/DROP-RATE-AG.git` (branch `master`)
- `src/main/resources/application.properties` is gitignored (holds a live API key + DB password). Use `application.properties.example` as the template.

## What exists

### Telecom agent (untouched)
- `ChatService` — plain LLM call, no tools, no memory
- `TelecomAgent` / `TelecomTools` / `AiConfig` — LangChain4j agent with 4 tools (`getCellStatus`, `getDegradedCells`, `calculateKpi`, `findCommands`) over `cell_status` / `kpi_definitions` / `telecom_commands`
- Endpoints: `GET /ai/chat?message=...`, `POST /agent/telecom` (JSON body)

### Drop-rate agent
- `DropAnalysisTool` — 4 tools: `getCellDropSummary`, `listAllCells`, `getWorstCells`, `getKnowledgeForCause` (RAG)
- `DropRateAgent` / `DropAgentConfig` / `DropAgentController`
- Endpoint: `GET /agent/drops?message=...`
- Verified against live DB: worst cell = `ARR40312C1_Moran_Uribe`, 30.13% drop rate, dominant cause RA Problem

### Tool call logging (Task A — NEW)
- `AgentLog` entity → `agent_logs` table (auto-created by Hibernate on startup)
- `AgentLogRepository` — JPA repo
- `LogController` — two endpoints:
  - `GET /logs/recent?n=50` — last N entries as plain text
  - `GET /logs/export` — writes `agent_log_<timestamp>.txt` to project root
- All 4 tools in `DropAnalysisTool` log agent name, tool name, input, output (truncated at 2000 chars), and latency

### RAG — vector knowledge base (Task B — NEW, requires pgvector setup)
- `RagConfig` — Spring beans for `EmbeddingModel` (OpenAI ada-002 via OpenRouter) and `PgVectorEmbeddingStore`
- `KnowledgeIngestionService` — fires on `ApplicationReadyEvent`, skips if already ingested; splits `5G_NSA_CallDrop_KnowledgeBase.md` on `## CHUNK` headers and embeds each chunk
- Knowledge file must be placed at: `src/main/resources/knowledge/5G_NSA_CallDrop_KnowledgeBase.md`
- `DropRateAgent` system prompt updated: after `getCellDropSummary`, agent ALWAYS calls `getKnowledgeForCause`

## Endpoints
| Endpoint | Method | Backed by | Tools/Memory |
|---|---|---|---|
| `/ai/chat?message=...` | GET | `ChatService` | No |
| `/agent/telecom` | POST (JSON body) | `TelecomAgent` | Yes |
| `/agent/drops?message=...` | GET | `DropRateAgent` | Yes |
| `/logs/recent?n=50` | GET | `LogController` | — |
| `/logs/export` | GET | `LogController` | — |

## Task B SQL setup (run manually in pgAdmin/psql before enabling RAG)

```sql
-- 1. Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. Create the knowledge table (HNSW index works on empty tables)
CREATE TABLE IF NOT EXISTS telecom_knowledge (
    id          BIGSERIAL PRIMARY KEY,
    content     TEXT        NOT NULL,
    source      VARCHAR(255),
    doc_type    VARCHAR(50),
    chunk_id    VARCHAR(100),
    embedding   vector(1536)
);

CREATE INDEX IF NOT EXISTS telecom_knowledge_embedding_idx
    ON telecom_knowledge USING hnsw (embedding vector_cosine_ops);
```

## Known caveats
- Chat memory on both agents is a single shared `MessageWindowChatMemory` (not per-session).
- If OpenRouter does not proxy `text-embedding-ada-002`, try `text-embedding-3-small` in `RagConfig.embeddingModel()`, or point directly at `https://api.openai.com/v1`.
- If `PgVectorEmbeddingStore` bean creation fails at startup, the app still starts — RAG tools return "not available" messages. Fix: run the SQL setup above, then restart.
- `/agent/telecom` (POST) has no UI; test via curl/Postman.
