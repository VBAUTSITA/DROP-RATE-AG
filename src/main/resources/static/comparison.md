# RAN Advisor — GitHub Landscape Comparison

> How this project compares to every public repo doing AI-assisted telecom / RAN / network optimization.
> Research conducted June 2026 across GitHub. Repos ordered from most similar to least similar stack.

---

## TL;DR Verdict

**No public repo combines all of:** Java/Spring Boot + LangChain4j @Tool agents + live RAN KPI database + two-layer input guardrails + tool-call logging + pgvector RAG.

The closest thing in the same language/framework is a pet clinic demo. Every telecom-specific repo uses Python. Our stack is genuinely unusual — and more production-complete than most of them.

---

## Comparison Table

| Repo | Language | LLM Framework | Telecom Domain | @Tool / Function Calling | Guardrails | Logging | RAG | Live DB |
|------|----------|---------------|---------------|--------------------------|------------|---------|-----|---------|
| **RAN Advisor** (ours) | Java | LangChain4j 1.0 | ✅ 5G NSA drops | ✅ @Tool methods | ✅ 2-layer (rule + LLM) | ✅ agent_logs table | ✅ pgvector | ✅ PostgreSQL |
| spring-petclinic-langchain4j | Java | LangChain4j | ❌ Pet clinic | ✅ @Tool methods | ❌ None | ❌ None | ❌ None | ✅ H2/MySQL |
| langchain4j-agent-rag-orcl | Java | LangChain4j | ❌ Generic | ✅ @Tool methods | ⚠️ Score threshold only | ❌ None | ✅ Oracle DB | ✅ Oracle |
| bubbleran/telco-network-configuration | Python | LangGraph | ✅ RAN params | ✅ Tool calls | ❌ None | ❌ None | ❌ None | ⚠️ SQLite |
| GoogleCloudPlatform/telco-autonomous-networks-data-demo | Python | Google ADK | ✅ Network KPIs | ✅ ADK tool calls | ❌ None | ❌ None | ❌ None | ✅ BigQuery |
| netop-team/Telco-RAG | Python/TS | OpenAI API | ✅ 3GPP docs | ❌ RAG only | ❌ None | ❌ None | ✅ Vector DB | ❌ None |
| open-experiments/Telco-AIX | Python | Mixed/Jupyter | ✅ Telecom | ⚠️ Rule-based | ❌ None | ❌ None | ❌ None | ❌ None |
| N00Bception/AI-Powered-5G-OpenRAN-Optimizer | Python | None (ML only) | ✅ 5G OpenRAN | ❌ No LLM agents | ❌ None | ❌ None | ❌ None | ❌ None |
| automateyournetwork/netclaw | Python | Anthropic API | ⚠️ Networking | ✅ MCP (72 servers) | ❌ None | ❌ None | ❌ None | ❌ None |
| benayat/rag-with-spring-ai | Java | Spring AI | ❌ Generic | ❌ RAG only | ❌ None | ❌ None | ✅ pgvector | ❌ None |
| filipw/AgentGuard | .NET | Any IChatClient | ❌ Generic | N/A | ✅ 3-tier pipeline | ❌ None | ❌ None | ❌ None |
| bubbleran/bat (ADK toolkit) | Python | Google ADK | ✅ Telecom | ✅ ADK tool calls | ❌ None | ❌ None | ❌ None | ❌ None |

---

## Repo Profiles

---

### 1. spring-petclinic/spring-petclinic-langchain4j
**URL:** https://github.com/spring-petclinic/spring-petclinic-langchain4j  
**Similarity score: 8/10 (stack) — 3/10 (domain)**

The single closest match to our technology stack. Spring Boot + LangChain4j + `@Tool`-annotated Java methods + `AiServices.builder()` wiring — exactly the same pattern we use.

**How they wire AI to tools:**
```java
// Their pattern — identical to ours
@Component
public class AssistantTools {
    @Tool("Get information about a vet by name")
    public Vet getVetByName(String name) { ... }
}
// Wired via AiServices.builder(Assistant.class).tools(assistantTools).build()
```

**What they access:** H2 in-memory database of vets, pets, and appointments. No live network data.

**What's missing vs ours:**
- No telecom domain whatsoever
- No guardrails (input or LLM)
- No tool-call logging
- No RAG / vector store
- No per-session memory provider (same shared-memory caveat we have)

**What they do better:**
- Official Spring project — well maintained, tested
- Demonstrates streaming responses (we don't)
- Has a proper frontend integrated

---

### 2. juarezjuniorgithub/langchain4j-agent-rag-orcl
**URL:** https://github.com/juarezjuniorgithub/langchain4j-agent-rag-orcl  
**Similarity score: 6/10**

Java + LangChain4j + RAG backed by Oracle Database. Demonstrates the same `@Tool` pattern and adds retrieval-augmented generation over a real enterprise database. Confirmed via code inspection.

**How they wire AI to tools:**
- `@Tool`-annotated Java methods (LangChain4j `AiServices`)
- `searchKnowledgeBase()` tool that queries Oracle via JDBC
- Oracle AI Vector Search as the embedding store (equivalent to our pgvector)

**What they access:** Oracle DB with embedded vector search. Business documents, not telecom KPIs.

**Guardrails:** Minimal — only a retrieval similarity score threshold of 0.5. No prompt-injection detection, no LLM classifier, no topic relevance check.

**What's missing vs ours:**
- No telecom domain
- No input guardrails beyond a score cutoff
- No tool-call logging to a dedicated table
- No two-agent architecture (drop agent + telecom agent)

**What they do better:**
- Oracle AI Vector Search is more enterprise-grade than pgvector for very large datasets
- Uses connection pooling properly

---

### 3. bubbleran/telco-network-configuration
**URL:** https://github.com/bubbleran/telco-network-configuration  
**Similarity score: 7/10 (domain) — 2/10 (stack)**

The most telecom-similar repo found. Multi-agent RAN parameter optimization using LangGraph with NVIDIA NIM (Llama 3.1 70B). Accesses actual RAN configuration parameters and real-time BubbleRAN network telemetry.

**Tech stack:** Python 100%, LangGraph orchestration, NVIDIA NIM as LLM backend, SQLite database (not PostgreSQL).

**How they wire AI to tools:**
- LangGraph nodes (not `@Tool` annotations)
- Each agent node is a Python function that reads/writes RAN parameters
- Multi-agent graph: optimizer agent → validator agent → executor agent

**What they access:**
- SQLite DB with `historical_data.csv` (SNR, MCS, LDPC decoder iterations)
- Live BubbleRAN network telemetry via API
- Configurable RAN parameters: `p0_nominal`, `att_rx`, `att_tx`, `ul_carrierbandwidth`

**What's missing vs ours:**
- No guardrails of any kind
- No logging infrastructure
- No RAG / knowledge base
- SQLite instead of PostgreSQL (not production-grade for this use case)
- Python only — no Spring Boot, no LangChain4j

**What they do better:**
- Multi-agent orchestration with LangGraph (we have two independent agents, not a graph)
- Actually writes back to real network equipment (we are read-only)
- Uses a much more powerful model (Llama 3.1 70B vs gpt-4o-mini)
- Real-time telemetry integration

---

### 4. GoogleCloudPlatform/telco-autonomous-networks-data-demo
**URL:** https://github.com/GoogleCloudPlatform/telco-autonomous-networks-data-demo  
**Similarity score: 6/10 (domain) — 1/10 (stack)**

Google Cloud's reference implementation of a telecom autonomous network agent. Most enterprise-grade of all the repos found. Uses Google's Agent Development Kit (ADK) v1.21 with Vertex AI as the LLM backend.

**Tech stack:** Python, Google ADK, Vertex AI (Gemini), BigQuery (not PostgreSQL).

**How they wire AI to tools:**
- Google ADK tool calls (equivalent concept to `@Tool`, different API)
- Tools read from BigQuery materialized-view KPI tables and incident tables

**What they access:**
- BigQuery: materialized-view performance KPI table + incident/anomaly metadata table
- Network topology data
- Historical performance trends

**What's missing vs ours:**
- No guardrails
- No logging to a queryable table
- No RAG / knowledge base for root-cause explanations
- Requires GCP (not self-hostable locally)
- Python only

**What they do better:**
- Production-grade infrastructure (BigQuery scales to billions of rows)
- Incident + anomaly metadata table (we only have KPI counters)
- Backed by Google — actively maintained with enterprise support

---

### 5. netop-team/Telco-RAG
**URL:** https://github.com/netop-team/Telco-RAG  
**Similarity score: 4/10**

RAG system specifically for 3GPP telecommunications standards documents. Python/TypeScript, OpenAI API directly, Next.js frontend + FastAPI backend.

**Tech stack:** Python (backend), TypeScript/Next.js (frontend), OpenAI API (no LangChain), vector database for 3GPP doc chunks.

**How they wire AI to tools:**
- Pure RAG — no function/tool calling
- Retrieves relevant 3GPP spec paragraphs, stuffs them into the prompt
- No agent orchestration

**What they access:**
- 3GPP standards documents only (no live network data)
- No SQL/PostgreSQL integration

**What's missing vs ours:**
- No live network KPI data
- No `@Tool` / function calling — just retrieval-augmented prompts
- No guardrails
- No logging
- No agent memory

**What they do better:**
- Actually useful for standards compliance questions (we can't answer "what does 3GPP TS 38.331 say about...")
- Full-stack UI (Next.js) — more polished than our plain HTML
- Domain expertise encoded in the document corpus

---

### 6. open-experiments/Telco-AIX
**URL:** https://github.com/open-experiments/Telco-AIX  
**Similarity score: 4/10**

Research-grade Python/Jupyter project exploring AI approaches to telecom — mixes traditional ML, rule-based agents, and some LLM experiments. No consistent architecture.

**Tech stack:** Python 57%, Jupyter Notebooks 33%, no Java/Spring.

**How they wire AI to tools:**
- The first `agentic/` implementation uses rule-based logic (explicitly avoids embedding an LLM because "too heavy to launch")
- Later notebooks experiment with LLM calls but not in an agentic tool-calling pattern

**What they access:** Varies by notebook — synthetic network data, no live PostgreSQL.

**What's missing vs ours:**
- No consistent architecture — research notebooks, not a deployable service
- No guardrails
- No logging
- No @Tool / function calling in the agentic sense

**What they do better:**
- Broader scope (ML + RL + LLM experiments in one place)
- Good reference for traditional ML baselines (if you want to compare LLM agents to gradient boosting, it's there)

---

### 7. N00Bception/AI-Powered-5G-OpenRAN-Optimizer
**URL:** https://github.com/N00Bception/AI-Powered-5G-OpenRAN-Optimizer  
**Similarity score: 3/10 (domain) — 0/10 (stack)**

Python, 100%. Uses traditional supervised/unsupervised ML and reinforcement learning — **no LLM agents at all**. Relevant because it targets 5G OpenRAN specifically, but the AI approach is fundamentally different.

**Tech stack:** Python, scikit-learn / PyTorch / RL libraries. No LangChain, no LLM.

**What they access:** Synthetic or static 5G OpenRAN datasets. No live DB.

**What's missing vs ours:**
- No LLM, no agents, no tool calling
- No natural language interface
- No guardrails, logging, or RAG

**What they do better:**
- The ML models can optimize parameters autonomously (we explain; they act)
- Reproducible experiments with Jupyter notebooks
- No API cost per inference (model runs locally)

---

### 8. automateyournetwork/netclaw
**URL:** https://github.com/automateyournetwork/netclaw  
**Similarity score: 5/10 (tool-wiring concept) — 2/10 (domain)**

The most ambitious tool-wiring approach found: Python + Anthropic Claude + Model Context Protocol (MCP), chaining the LLM to **72 MCP servers** via stdio/HTTP transports. Targets enterprise routing, SD-WAN, and security infrastructure — not RAN.

**Tech stack:** Python, Anthropic Claude API, MCP (not LangChain4j or Spring).

**How they wire AI to tools:**
- MCP (Model Context Protocol) — structured JSON-RPC tool definitions
- 72 MCP servers covering BGP, OSPF, firewall rules, SD-WAN policies, etc.
- Claude chooses which MCP server to call, the servers execute, results return to Claude

**What they access:** Live enterprise network devices via MCP servers. No 5G/RAN, no cell KPI data.

**What's missing vs ours:**
- No telecom RAN / 5G domain
- No guardrails
- No structured logging
- No RAG / knowledge base
- Python only

**What they do better:**
- Scale of tool integration (72 tools vs our 4-7)
- MCP is more composable and language-agnostic than `@Tool` annotations
- Targets configuration management (read + write), not just analysis

---

### 9. filipw/AgentGuard
**URL:** https://github.com/filipw/AgentGuard  
**Similarity score: 9/10 (guardrail pattern) — 0/10 (domain)**

Not a telecom project, but the most relevant reference for how we built our guardrail module. .NET library implementing a **three-tier guardrail pipeline** that closely matches our two-layer design.

**Tech stack:** .NET / C#, any `IChatClient` (model-agnostic).

**Their guardrail pipeline:**
1. Regex/rule-based deterministic check (~0ms) — equivalent to our `InputGuardrail`
2. ONNX ML classifiers (~8ms inference, no API call) — a layer we don't have
3. LLM-as-judge with configurable rules — equivalent to our `LlmGuardrail`

**Configurable execution order** — you can swap the order or skip tiers.

**What they do better than our guardrail:**
- Tier 2 (ONNX ML classifier) gives semantic classification without an API call — faster and cheaper than our LLM tier
- Composable pipeline — execution order is configurable at runtime
- Model-agnostic (works with any `IChatClient`, not tied to OpenRouter)

**What we do that they don't:**
- Persist every block to a queryable log table (`agent_logs`)
- Distinguish block reasons by layer (`GUARDRAIL_BLOCK` vs `LLM_GUARDRAIL_BLOCK`)
- Domain-specific telecom keyword list

---

### 10. benayat/rag-with-spring-ai
**URL:** https://github.com/benayat/rag-with-spring-ai  
**Similarity score: 4/10 (stack) — 0/10 (domain)**

Java Spring Boot + Spring AI + pgvector RAG. No telecom domain, no agent tool calling — pure document QA.

**Tech stack:** Java, Spring Boot, Spring AI (not LangChain4j), pgvector.

**How they wire AI to tools:** They don't — pure RAG, no `@Tool` / function calling.

**What they do better:**
- Spring AI is the official Spring-ecosystem LLM library (better long-term support than LangChain4j in Spring contexts)
- pgvector integration is well-documented

**What's missing vs ours:**
- No tool-calling agents
- No guardrails
- No logging
- No telecom domain

---

### 11. bubbleran/bat (BubbleRAN Agentic Telco Toolkit)
**URL:** https://github.com/bubbleran/bat  
**Similarity score: 5/10 (domain) — 1/10 (stack)**

Python-based Agent Development Kit explicitly scoped to telecom AI agents. Google ADK under the hood. Limited public implementation details in the README.

**Tech stack:** Python, Google ADK v1.21, Vertex AI.

**What's public:** Scaffolding for telecom-specific ADK agents, tool definitions for network operations.

**What's missing vs ours:** Full implementation not fully public. No guardrails documented. No logging. Java/Spring not involved.

---

## Feature-by-Feature Breakdown

### Tool wiring (how AI calls your code)

| Approach | Used by | Pros | Cons |
|----------|---------|------|------|
| LangChain4j `@Tool` annotation | **RAN Advisor**, spring-petclinic-langchain4j, langchain4j-agent-rag-orcl | Declarative, compile-safe, Java-native | LangChain4j Java community smaller than Python |
| LangGraph nodes (Python) | bubbleran/telco-network-configuration | Multi-agent graph orchestration | No compile-time safety, Python only |
| Google ADK tool calls | bubbleran/bat, GCP telco demo | GCP-native, managed infra | Vendor lock-in (Vertex AI) |
| MCP (Model Context Protocol) | automateyournetwork/netclaw | Language-agnostic, 72 tools composable | More complex setup, no type safety |
| Raw OpenAI API | netop-team/Telco-RAG | Simple, no framework dependency | Manual everything, no orchestration |
| Spring AI | benayat/rag-with-spring-ai | Official Spring project | Less mature than LangChain4j for agents |

### Guardrails

| Project | Layer 1 (fast) | Layer 2 (semantic) | Persisted log |
|---------|---------------|-------------------|---------------|
| **RAN Advisor** | ✅ Keyword + injection rules | ✅ LLM classifier (gpt-4o-mini, T=0.0) | ✅ agent_logs table |
| AgentGuard | ✅ Regex rules | ✅ ONNX ML + LLM-as-judge | ❌ None |
| langchain4j-agent-rag-orcl | ⚠️ Score threshold | ❌ None | ❌ None |
| All others | ❌ None | ❌ None | ❌ None |

**RAN Advisor is the only telecom repo with any guardrail at all.** AgentGuard has a more sophisticated middleware architecture but is a .NET library, not a telecom application.

### Data sources

| Project | DB type | Data type | Read-only? |
|---------|---------|-----------|------------|
| **RAN Advisor** | PostgreSQL | 5G NSA cell drop counters (18K rows) + LTE KPIs | ✅ Read-only analysis |
| bubbleran/telco-network-configuration | SQLite | RAN params + real-time telemetry | ❌ Writes back to RAN |
| GCP telco demo | BigQuery | Network KPIs + incident table | ✅ Read-only |
| spring-petclinic-langchain4j | H2/MySQL | Pet clinic records | ❌ Writes |
| langchain4j-agent-rag-orcl | Oracle | Business documents | ✅ Read-only |
| netop-team/Telco-RAG | Vector DB | 3GPP spec documents | ✅ Read-only |
| automateyournetwork/netclaw | Live devices via MCP | BGP/OSPF/firewall rules | ❌ Can write |

### RAG (Retrieval-Augmented Generation)

| Project | Vector store | Embedding model | Chunks |
|---------|-------------|----------------|--------|
| **RAN Advisor** | pgvector | text-embedding-ada-002 | 23 domain knowledge chunks |
| langchain4j-agent-rag-orcl | Oracle AI Vector | Oracle embedding | Business docs |
| netop-team/Telco-RAG | Custom vector DB | OpenAI | 3GPP spec paragraphs |
| benayat/rag-with-spring-ai | pgvector | Spring AI default | Generic docs |
| All others | ❌ None | ❌ None | ❌ None |

### Logging / observability

| Project | Where | What | Queryable? |
|---------|-------|------|-----------|
| **RAN Advisor** | PostgreSQL `agent_logs` | Tool name, input, output, latency, guardrail blocks | ✅ SQL + `/logs/recent` |
| All others | ❌ Console only or nothing | — | ❌ |

**No other repo in this comparison has structured tool-call logging to a database.** Most log to stdout at best.

---

## Where RAN Advisor Stands

### What we do that almost no one else does (combined)

1. **Java + LangChain4j + real telecom data** — the only Java-based telecom AI agent found
2. **Two-layer input guardrail** — only AgentGuard has comparable guardrail architecture, and it's .NET
3. **Persistent tool-call logging** — unique among all repos reviewed
4. **Combined: agent tools + RAG in the same app** — only this project does both for telecom

### Where production-grade repos are ahead

| What | Who's ahead | Gap |
|------|-------------|-----|
| **Write-back to real RAN equipment** | bubbleran/telco-network-configuration | We only read and explain; they actually tune parameters |
| **Multi-agent graph orchestration** | LangGraph-based repos | Our two agents are independent; LangGraph lets agents hand off to each other |
| **Scale of tool coverage** | netclaw (72 MCP tools) | We have 4-7 tools; they have 72 composable tools |
| **Infra scale** | GCP telco demo (BigQuery) | BigQuery handles billions of rows; our PostgreSQL is fine for 18K rows |
| **Guardrail middleware pattern** | AgentGuard | Their ONNX tier adds semantic classification without API cost |
| **Standards knowledge** | Telco-RAG | Their RAG corpus is 3GPP specs; ours is a hand-written knowledge base |
| **Model power** | bubbleran (Llama 3.1 70B) | We use gpt-4o-mini; 70B models produce richer analysis |

### What a v2 should add (based on this comparison)

1. **Write-back tools** — at minimum a simulation mode that shows what command would be run
2. **LangGraph4j orchestration** — wire the telecom agent and drop agent into a graph so they can hand off to each other
3. **ONNX/ML tier in guardrail** — add a third tier between keyword check and LLM classifier for better cost/accuracy tradeoff (AgentGuard pattern)
4. **Per-session chat memory** — replace shared `MessageWindowChatMemory` with `chatMemoryProvider(sessionId → ...)` 
5. **3GPP spec RAG** — add a second knowledge base with the actual 3GPP 5G NR specs so the agent can cite standards

---

## Sources

| Repo | URL | Stars (approx, June 2026) |
|------|-----|--------------------------|
| spring-petclinic-langchain4j | https://github.com/spring-petclinic/spring-petclinic-langchain4j | ~400 |
| langchain4j-agent-rag-orcl | https://github.com/juarezjuniorgithub/langchain4j-agent-rag-orcl | ~80 |
| bubbleran/telco-network-configuration | https://github.com/bubbleran/telco-network-configuration | ~60 |
| GoogleCloudPlatform/telco-autonomous-networks-data-demo | https://github.com/GoogleCloudPlatform/telco-autonomous-networks-data-demo | ~120 |
| netop-team/Telco-RAG | https://github.com/netop-team/Telco-RAG | ~300 |
| open-experiments/Telco-AIX | https://github.com/open-experiments/Telco-AIX | ~50 |
| N00Bception/AI-Powered-5G-OpenRAN-Optimizer | https://github.com/N00Bception/AI-Powered-5G-OpenRAN-Optimizer | ~200 |
| automateyournetwork/netclaw | https://github.com/automateyournetwork/netclaw | ~150 |
| benayat/rag-with-spring-ai | https://github.com/benayat/rag-with-spring-ai | ~40 |
| filipw/AgentGuard | https://github.com/filipw/AgentGuard | ~90 |
| bubbleran/bat | https://github.com/bubbleran/bat | ~30 |
