# STATE OF THE ART — `DROP-RATE-AG` / RAN Advisor

> **What this document is.** A complete, evidence-based snapshot of the `VBAUTSITA/DROP-RATE-AG`
> repository across **all 8 branches**, assembled by reading the code rather than the docs.
> Every claim here is anchored to a file and, where it matters, a line.
>
> **The last section is the point.** [§11 — Research vs. Reality](#11-research-vs-reality--auditing-the-research-branch-against-the-code)
> takes the literature review that lives on the `claude/drop-rate-agent-research-j607ex` branch and
> checks, claim by claim, whether what the research says is *actually true of this system*. Some of it
> is. Some of it is aspirational. One claim is false as of today. The evidence is cited in each row.
>
> Generated: 2026-08-06. Branch tips as of `git fetch --all` on that date.

---

## Table of contents

1. [Executive summary](#1-executive-summary)
2. [Repository map and branch topology](#2-repository-map-and-branch-topology)
3. [The data — what the system actually knows](#3-the-data--what-the-system-actually-knows)
4. [Baseline architecture (`master`)](#4-baseline-architecture-master)
5. [Branch-by-branch: what each one adds](#5-branch-by-branch-what-each-one-adds)
6. [The consolidated state of the art (`gemini-pci-timerange`)](#6-the-consolidated-state-of-the-art-gemini-pci-timerange)
7. [Endpoint surface across branches](#7-endpoint-surface-across-branches)
8. [Configuration and operational prerequisites](#8-configuration-and-operational-prerequisites)
9. [Build, test and verification status](#9-build-test-and-verification-status)
10. [Known gaps and risks (code-level)](#10-known-gaps-and-risks-code-level)
11. [**Research vs. Reality — auditing the research branch against the code**](#11-research-vs-reality--auditing-the-research-branch-against-the-code)
12. [Recommended sequence from here](#12-recommended-sequence-from-here)

---

## 1. Executive summary

**What this project is.** A Spring Boot 3.4.5 / Java 17 application that puts an LLM agent in front of
real 5G NSA radio counters and lets an engineer ask, in natural language, which cells are dropping
calls and why. It is built on **LangChain4j** using the `@Tool` / `AiServices` pattern: the model never
touches the database, it calls typed Java methods that do.

**Where it stands.** Eight branches, all forked from the same `master` commit (`7eea634`), forming
two chains that were later merged:

- a **capability chain** — PCI planning module → multi-module orchestrator → planner port → time-range analysis
- a **provider chain** — a full migration off OpenRouter/GPT onto **native Google Gemini**

They converge on `claude/gemini-pci-timerange`, which is the most advanced state and the de-facto
integration branch. **Nothing has been merged back to `master`.** `master` is 8 commits of solid
single-agent foundation; the interesting work is all unmerged.

**The honest headline.** The system is a **well-engineered deterministic analytics layer with an LLM
conversational front-end**. It is *not* an ML anomaly detector, and it does not claim to be in code —
though some of the surrounding documentation reads as if it were. Its real strengths are the parts
the literature says actually matter: typed tools, guardrails, an audit trail, an eval harness, and a
correlator that produces a *reasoned* root-cause hypothesis rather than a flag. Its real weaknesses
are that the RAG knowledge base file is **absent from every branch** (so RAG cannot work as shipped),
the eval harness's most interesting scorer is a stub, and the flagship cross-module demo runs on
**seeded synthetic PCI data with deliberately planted conflicts**.

**Numbers at a glance:**

| Dimension | Value |
|---|---|
| Branches | 8 (`master` + 7 feature) |
| Commits on `master` | 8 |
| Largest branch delta vs `master` | `gemini-pci-timerange`: **+5,587 / −80** across 61 files |
| Java source files (tip branch) | ~55 |
| LLM-callable `@Tool` methods (tip branch) | **17** across 4 tool classes |
| REST endpoints (tip branch) | 12 |
| Real counter rows | 18,816 |
| Cells / gNodeBs | 29 / 8 |
| Data window | 2026-05-24 → 2026-06-20 (hourly) |
| Unit tests (tip branch) | 21 (13 orchestration + 8 time-range) |
| Unit tests (`master`) | 0 real (one empty context-load test) |

---

## 2. Repository map and branch topology

### 2.1 Branch graph

All seven feature branches share the merge-base `7eea634` (`master` HEAD). None is behind `master`.

```
master (7eea634) ── "Add eval harness 101 + industry comparison guide"
│
├── 8981875  claude/drop-rate-agent-research-j607ex
│   │        └─ +1 file: ran-anomaly-detection-research.md (382 lines, ~40 sources)
│   │
│   └── 944d…03bd44d  claude/gemini-native-migration
│                     └─ Gemini native provider, OpenRouter deleted, Oracle-portable columns
│
└── 10af35f  claude/ran-multi-module-orchestration
    │        └─ PCI module + DiagnosticModule SPI + orchestrator + correlator
    │
    └── 2ef7539  claude/multi-agent-integration-jcgmdl
        │        └─ + triple-agent architecture doc + 424 KB PPTX proposal
        │
        └── f1e97b7  claude/pci-planner-integration
            │        └─ + PciPlannerPort (local | REST adapters) + tracker-side reference code
            │
            └── 206f8b3  claude/gemini-pci-office        ← merge of BOTH chains
                │
                └── eae1528  claude/gemini-pci-timerange ← ★ MOST ADVANCED
                             └─ + time-dimension analysis (5 new tools, parser, 8 tests)
```

### 2.2 What each branch is *for*

| Branch | Tip | Ahead | Role |
|---|---|---|---|
| `master` | `7eea634` | — | Stable single-agent baseline. Everything below forks from here. |
| `drop-rate-agent-research-j607ex` | `8981875` | 1 | **Documentation only.** The literature review audited in §11. Zero code. |
| `ran-multi-module-orchestration` | `00524bb` | 2 | Introduces the `DiagnosticModule` SPI, the PCI module, the supervisor agent and the deterministic correlator. |
| `multi-agent-integration-jcgmdl` | `2ef7539` | 3 | Adds the stakeholder-facing PPTX proposal (`docs/presentacion/`) and its Node.js generator. |
| `pci-planner-integration` | `f1e97b7` | 4 | Replaces direct PCI access with a **hexagonal port** (`PciPlannerPort`) so the agent can talk to either the local mirror or the real JDK-8 planner over HTTP. |
| `gemini-native-migration` | `03bd44d` | 6 | Rips out OpenRouter/OpenAI entirely; native Gemini API only. Oracle-portable column names. |
| `gemini-pci-office` | `206f8b3` | 12 | The **integration point**: merges the provider chain into the capability chain. |
| `gemini-pci-timerange` | `eae1528` | 14 | ★ Adds the **time dimension** — period-aware tools, a natural-language range parser, daily trends, before/after comparison. |

### 2.3 Package layout (tip branch)

```
com.ranadvisor
├── RanAdvisorApplication          Spring Boot entry point
├── config/
│   ├── AiConfig                   TelecomAgent wiring
│   ├── ChatModelConfig            ★ Gemini native (3 beans @ temp 0.1 / 0.0 / 0.7)
│   └── RagConfig                  pgvector EmbeddingStore + EmbeddingModel
├── core/                          ★ the extensibility seam
│   ├── DiagnosticModule           SPI — implement + @Component = auto-registered
│   ├── ModuleFinding              typed per-module output (severity + machine tags)
│   └── RootCauseHypothesis        the correlator's verdict
├── agent/                         legacy telecom agent (cell_status / kpi_definitions / telecom_commands)
├── drops/                         ★ the drop-rate domain
│   ├── DropRateAgent              @SystemMessage interface
│   ├── DropAgentConfig            AiServices wiring
│   ├── DropAnalysisTool           10 @Tool methods
│   ├── DropRateModule             DiagnosticModule impl
│   ├── DropAgentController        /agent/drops + 2-layer guardrail
│   ├── NrCellDropsLoader          CSV → PostgreSQL bulk import
│   ├── entity/NrCellDrops         72-column counter row
│   ├── repository/                JPA + derived range queries
│   └── timerange/                 ★ TimeRange + TimeRangeParser (ES/EN NL parsing)
├── pci/                           ★ the PCI domain
│   ├── PciAnalysisTool            deterministic collision/confusion/mod-3 engine
│   ├── PciPlannerTools            @Tool surface over the port
│   ├── PciTrackWorkflow           identify → re-plan, ordered deterministically
│   ├── PciModule                  DiagnosticModule impl
│   ├── PciDataLoader              seeds 28 cells + 5 ANR edges (synthetic)
│   ├── planner/                   PciPlannerPort + Local/Rest adapters + value objects
│   ├── entity/ · repository/
├── orchestrator/                  ★ the supervisor layer
│   ├── RanSupervisorAgent         routing @SystemMessage
│   ├── OrchestratorTools          5 @Tool: fan-out, correlate, route
│   ├── CrossModuleCorrelator      6 deterministic rules, no LLM
│   └── SupervisorController       /agent/ran + /agent/ran/diagnose
├── guardrail/                     InputGuardrail (rules) + LlmGuardrail (semantic)
├── logging/                       AgentLog entity + repo + /logs endpoints
├── eval/                          Phase-0 eval harness (cases, runner, scorer, results)
├── knowledge/                     KnowledgeIngestionService (RAG ingestion)
└── service/ · controller/         ChatService (no tools) + /ai/chat
```

---

## 3. The data — what the system actually knows

This section matters more than the architecture, because every capability claim downstream is
bounded by it.

### 3.1 The drop counters (real)

| Property | Value |
|---|---|
| Source file | `src/main/resources/data/nsa_drops.csv` |
| Format | Tab-separated, 3 preamble lines + header, exported from an OSS tool |
| Export stamp | `Save Time :22/06/2026 16:15:50`, `User Name :gustavo.diaz` |
| Rows | **18,816** data records |
| Columns | **72** counters per row |
| Cells | **29** distinct |
| gNodeBs | **8** (`MBTS_AR1891_JM_CUADROS`, `AR3855_PARRA`, `AR3889_MELGAR`, `AR3909_AZANGARO`, `AR3936_RESIDENCIAL_LA_LOMADA`, `AR3993_PUENTE_QUINONES`, `AR4031_MORAN_URIBE`, …) |
| Window | **2026-05-24 → 2026-06-20**, hourly granularity |
| Loader | `NrCellDropsLoader` — runs on `ApplicationReadyEvent`, batches of 1,000, skips if table non-empty |

**Counter families** (`NrCellDrops` entity, 72 mapped columns):

- **MeNB-triggered SCG failures** — `menbScgfail` + 5 causes: `RAProblem`, `RlcMaxNumRetx`, `RecfgFail`, `SyncRecfgFail`, `T310Expiry`
- **SgNB abnormal releases** — `sgnbAbnrel` + causes: `NoReply`, `Radio` (→ `UeLost`, `UlSyncFail`, `SUL`), `RnlPreempt`, `Trans`
- **RA timing-advance histogram** — `raTaUeIdx0..15` (16 bins) — *loaded, never analysed*
- **RSRP distributions** — `measrptRsrpIdx0..9`, `ulPuschRsrpIdx0..11` — *loaded, never analysed*
- **SINR distributions** — `measrptSinrIdx0..4`, `ulPuschSinrIdx0..7` — *loaded, never analysed*
- **Denominator** — `sgnbRelTotal`

> **Finding D-1.** Roughly **50 of the 72 columns are ingested but never read by any code path.**
> The TA histogram (a direct proxy for cell radius / overshoot) and the RSRP/SINR distributions
> (direct proxies for coverage and interference) are the exact signals the literature says you need
> for spatio-temporal RCA — and they are already in the database, unused. This is the single largest
> piece of latent capability in the repo.

### 3.2 The drop-rate formula

Defined once and duplicated in two places — `DropAnalysisTool.buildSummary` and
`DropRateModule.analyze`:

```
abnormal  = menbScgfail + sgnbAbnrel
dropRate  = abnormal * 100.0 / sgnbRelTotal        (0.0 when denominator is 0)
severity  = dropRate > 15 ? CRITICAL : dropRate > 5 ? WARNING : OK
```

`DropAnalysisTool.java:246` carries the thresholds. Dominant cause = `argmax` over seven cause
counters, mapped to a human label.

**Verified ground truth** (recorded in `SUMMARY.md` and encoded in `db/eval_seed.sql`):
worst cell = `ARR40312C1_Moran_Uribe`, **30.13 %** drop rate, dominant cause **RA Problem**.

### 3.3 The PCI plan (synthetic)

`PciDataLoader` seeds **28 cells and 5 inter-site ANR edges** at startup. The Javadoc is explicit
that three of the conflicts are **deliberate**:

| Conflict | Cells | Purpose |
|---|---|---|
| `COLLISION` | `ARR40312C1_Moran_Uribe` (PCI 168) ⟷ `ARR39931C1_Puente_Quinones` (PCI 168) | "the crafted root cause of that cell's RACH-driven drops" |
| `CONFUSION` | `ARR39091C1_Azangaro` sees two neighbours both on PCI 110 | demonstrate confusion detection |
| `MOD3` | `ARR18911C1_Jm_Cuadros` (10) ⟷ `ARR38551C1_Parra` (40), PSS group 1 | demonstrate the softer conflict class |

ARFCN and azimuth are *derived from the cell name string* (`C3` ⇒ high band; sector digit ⇒
azimuth = (sector−1)·120°).

> **Finding D-2 (important for honesty).** The flagship demonstration — "the PCI collision explains
> the RACH drops on the worst cell" — is **true by construction, not by observation**. The drop data
> is real; the PCI collision that "explains" it was planted in `PciDataLoader` to line up with the
> real worst cell. The correlation logic is genuine and would work on real data, but the specific
> correlation shown is a fixture. Any demo or slide should say so.

### 3.4 The RAG knowledge base (absent)

`KnowledgeIngestionService.java:56` loads `knowledge/5G_NSA_CallDrop_KnowledgeBase.md` from the
classpath and splits it on `## CHUNK` headers.

```
$ git ls-tree -r --name-only <every branch> | grep knowledge
src/main/java/com/ranadvisor/knowledge/KnowledgeIngestionService.java     ← the code
                                                                          ← the file: nothing
```

> **Finding D-3.** **The knowledge base file does not exist on any of the 8 branches.** It is
> gitignored or was never committed. On a clean clone, ingestion throws, is caught, prints
> `[KnowledgeIngestion] Ingestion failed: …`, and the store stays empty. `getKnowledgeForCause` then
> returns `"No relevant knowledge found for: …"` for every query. **RAG is inert on a fresh
> checkout of every branch.**

---

## 4. Baseline architecture (`master`)

### 4.1 The two agents

**`TelecomAgent`** (legacy) — LangChain4j agent over `cell_status` / `kpi_definitions` /
`telecom_commands` with 4 tools (`getCellStatus`, `getDegradedCells`, `calculateKpi`,
`findCommands`). Wired in `AiConfig`. Reachable at `POST /agent/telecom`. Largely untouched since
the initial commit and not the focus of any branch.

**`DropRateAgent`** (the real product) — a `@SystemMessage` interface, no implementation class;
LangChain4j generates the proxy. The system prompt is the behavioural contract and encodes:

- respond in **Spanish**
- **"NEVER invent numbers. Only use data returned by your tools."**
- a tool-ordering rule: `getCellDropSummary` → **always** `getKnowledgeForCause`
- explain the dominant cause in plain language
- if drop rate is CRITICAL (>15 %), suggest checking the dominant cause and name the parameter
- refuse anything outside drop analysis

### 4.2 The tool surface on `master`

`DropAnalysisTool` — 4 `@Tool` methods, every one wrapped in a timing + persistence log:

| Tool | What it does |
|---|---|
| `getCellDropSummary(cellName)` | Full-history aggregate: total/abnormal releases, drop rate, dominant cause, MeNB + SgNB breakdown, severity |
| `listAllCells()` | Distinct cell names |
| `getWorstCells(topN)` | Ranks all cells by drop rate, capped at 10 |
| `getKnowledgeForCause(query)` | pgvector similarity search, `maxResults=2` |

### 4.3 Guardrails — two layers

**Layer 1 — `InputGuardrail`** (deterministic, free, always first):

- length bounds: 2 ≤ len ≤ 1000
- 11 prompt-injection phrases (`"ignore previous"`, `"system prompt"`, `"act as"`, …)
- topic allowlist: ~35 Spanish + English telecom keywords, **or** a cell-name regex
  `[A-Z]{2,}\d{3,}`
- anything matching none of those → `off_topic`, blocked

**Layer 2 — `LlmGuardrail`** (semantic, one cheap model call at temperature 0.0):

- a tight classifier prompt returning `{"safe":…, "on_topic":…, "reason":…}`
- system and user messages kept separate so user text cannot bleed into instructions
- JSON extracted defensively via first-`{`/last-`}`
- **fail-open**: any API or parse error returns `allowed=true` (`LlmGuardrail.java:92`)

Both blocks are persisted to `agent_logs` with reason codes (`GUARDRAIL_BLOCK`,
`LLM_GUARDRAIL_BLOCK`) and the user sees a fixed canned Spanish response.

### 4.4 Observability

`AgentLog` → `agent_logs` table (Hibernate `ddl-auto=update` creates it). Every tool call records
agent name, tool name, input, output truncated at 2,000 chars, and latency in ms. Log writes are
wrapped in try/catch so a logging failure never breaks a tool.

- `GET /logs/recent?n=50` — last N entries as plain text
- `GET /logs/export` — writes `agent_log_<timestamp>.txt` to the project root

### 4.5 The eval harness (Phase 0)

| Piece | Behaviour |
|---|---|
| `EvalCase` | `eval_cases` table: agent, question, expected, score_type, notes |
| `EvalResult` | `eval_results` table: response, passed, latency, run_label |
| `EvalRunnerService` | Iterates all cases, routes to `drops` or `telecom`, catches per-case exceptions so one failure never aborts a run |
| `EvalScorer` | `exact` \| `contains` \| `llm_judge` |
| `EvalController` | `POST /eval/run?label=…`, `GET /eval/results?label=…`, `GET /eval/cases` |
| Seed | `db/eval_seed.sql` — **20 cases** (10 drops, 10 telecom) |

Two limitations are **documented in the code itself**, which is to its credit:

- `EvalScorer.java:34-42` — `llm_judge` is **not implemented**; it logs a warning and
  `yield false`. It **fails closed** deliberately so an unimplemented strategy can never inflate a
  pass rate.
- `EvalRunnerService.java:40-52` — a `KNOWN LIMITATION` block stating that agents are invoked
  through their normal `chat(String)` interface, backed by a **single shared
  `MessageWindowChatMemory`**, so eval cases contaminate each other *and* any concurrent live
  traffic. Results are explicitly "not perfectly reproducible."

### 4.6 Documentation already on `master`

| File | Lines | Content |
|---|---|---|
| `static/reference.html` | 545 | Full agent reference guide + guardrail documentation |
| `static/comparison.md` | 436 | RAN Advisor vs 11 public AI-telecom GitHub repos |
| `static/best-101-evalharnessguide.md` | 180 | Eval harness 101 + industry comparison |
| `static/eval-guide.md` | 158 | How to run the harness |
| `static/index.html` / `chat.html` | 130 | Dev console (local only) |
| `SUMMARY.md` | — | Change log + pgvector SQL setup + known caveats |

---

## 5. Branch-by-branch: what each one adds

### 5.1 `claude/drop-rate-agent-research-j607ex` — the research branch

**+382 lines, 1 file, 0 code.** `src/main/resources/static/ran-anomaly-detection-research.md`.

A literature scan of AI/ML and LLM-agent anomaly detection in the telecom RAN, dated July 2026,
covering ~40 papers, surveys, patents and industry sources. Structure:

- §4 — **11 recurring issues**: data scarcity, label scarcity, class imbalance, concept drift, false
  positives/alarm fatigue, black-box opacity, spatio-temporal complexity, real-time/MLOps, flawed
  evaluation, LLM-specific risks, security/privacy
- §5 — **10 solution families**: classical ML, deep learning (LSTM/CNN/AE/VAE/GAN/Transformer),
  unsupervised/self-supervised, ensembles/transfer, **GNN+Transformer spatio-temporal RCA**,
  federated learning + digital twins, XAI/causal RCA, **LLM agents + RAG + guardrails**, xApp/rApp
  deployment, better benchmarks
- §6 — industry state (Deutsche Telekom "RAN Guardian" live; AWS Level-5 autonomy framing)
- §7 — **"Implications for RAN Advisor"**: a table claiming five repo capabilities are validated by
  the literature, plus five recommended next steps

**§7 is what §11 of this document audits.**

### 5.2 `claude/ran-multi-module-orchestration` — the extensibility spine

**+2,200 lines, 25 files.** The most architecturally significant branch.

**`core.DiagnosticModule`** — the SPI. Implement it, annotate `@Component`, and the orchestrator
picks it up automatically via `@Autowired List<DiagnosticModule>`. Contract is explicit:
`analyze()` must be deterministic and side-effect free, must never throw on an unknown cell
(return `ModuleFinding.none`), and must populate stable machine-readable tags.

**`core.ModuleFinding`** — the typed unit of output: `module`, `cellName`, `severity`
(`CRITICAL|WARNING|OK|UNKNOWN`), `headline`, `Set<String> tags`, `detail`.

> The design note is the important part: *"it never parses a module's free-text answer — it reads
> `severity` and the machine-readable `tags`."* Prose stays for humans; orchestration keys off tags.
> This is the discipline that makes the correlator auditable.

**`CrossModuleCorrelator`** — **six ordered rules, no LLM at all**:

| # | Condition | Verdict | Confidence |
|---|---|---|---|
| 1 | drops actionable ∧ `RACH_FAILURE` ∧ (`PCI_COLLISION` ∨ `PCI_CONFUSION`) | PCI conflict is the likely root cause | **HIGH** |
| 2 | drops actionable ∧ `RACH_FAILURE` ∧ `PCI_MOD3` | mod-3 may be degrading RACH | MEDIUM |
| 3 | drops actionable ∧ `COVERAGE_DEGRADATION` ∧ PCI confirmed clean | RF coverage, not identity | MEDIUM |
| 4 | drops actionable ∧ PCI confirmed clean | cause is inside the drop domain | LOW |
| 4b | drops actionable ∧ **PCI unchecked** | partial verdict — identity still open | LOW |
| 5 | PCI conflict ∧ drops fine | fix proactively | MEDIUM |
| 6 | nothing actionable | no cross-module issue | NONE |

> **The Rule 4b distinction is the single best piece of engineering judgment in the repo.** The code
> comment says it plainly: *"'Not checked' and 'checked, clean' are different facts and must not
> collapse into the same verdict."* A module whose backend is unreachable returns `UNKNOWN` with no
> tags — and without this rule, the *absence* of conflict tags would read as a clean plan, and the
> correlator would rule identity out on the strength of a check that never ran. There is a
> dedicated test for exactly this (`correlator_treatsAnUnreachablePciBackendAsUnknown_notAsCleanPci`).

**`PciAnalysisTool`** — deterministic PCI conflict detection over the mirror:

- neighbour set = explicit ANR relations (either direction) ∪ co-sited cells
- `COLLISION` — same PCI, same ARFCN, neighbours
- `MOD3` — `pci % 3` equal, same ARFCN, neighbours
- `CONFUSION` — two distinct neighbours sharing a PCI
- `searchSpace()` — returns the *constraint set* (banned exact PCIs, banned PSS groups) as a value
  so a caller can explain **why** a value was chosen, not just report the number
- `firstFreePci()` — two-pass: pass 1 clears collision + confusion + mod-3; pass 2 falls back to
  collision/confusion-only when all three PSS groups are occupied

**`RanSupervisorAgent` + `OrchestratorTools`** — agent-as-tool routing plus the deterministic
fan-out (`diagnoseCell`), with an explicit "do not call more than two tools" budget in the prompt.

### 5.3 `claude/multi-agent-integration-jcgmdl` — the proposal artefact

Adds `docs/presentacion/Agente-RAN-propuesta.pptx` (424 KB) and its 561-line Node.js generator
`build-presentacion.js`, plus `static/arquitectura-triple-agente.md` (338 lines) describing a
triple-agent architecture: **Drops · Coverage · PCI**.

> Note: the **Coverage** agent is described in the architecture doc and the deck, but **no
> `CoverageModule` exists in any branch.** It is a proposal, not an implementation.

### 5.4 `claude/pci-planner-integration` — hexagonal port

**+4,061 lines.** Refactors PCI access behind `PciPlannerPort`, with two backends selected by
`pci.planner.backend`:

- **`LocalPciPlannerAdapter`** (default, `matchIfMissing = true`) — answers from the seeded
  PostgreSQL mirror. Its Javadoc is unusually honest about what it does *not* do: no RSI (stays
  `null` rather than emitting a made-up number), no RF geometry ("That is a correct answer to
  'which PCIs are free', not to 'which PCI is best'"). Both limits appear in **every proposal's
  warnings list**.
- **`RestPciPlannerAdapter`** — HTTP to the real multi-technology planner.

The port separates two operations by **cost**, deliberately:

| Operation | Cost | Called |
|---|---|---|
| `audit(cell)` — IDENTIFY | ms | on **every** `diagnoseCell` fan-out |
| `propose(cell)` — RE-PLAN | seconds | only on explicit user request |

> **`PciPlannerPort` has no `apply()` method, and that is a security decision, not an omission.**
> The Javadoc: *"Any method annotated `@Tool` in the agent can be fired by the model on its own from
> a chat phrase. Applying a PCI stays where it is today: a person in front of a screen."* This is a
> textbook agent-safety boundary — the dangerous capability is architecturally unreachable rather
> than merely discouraged by a prompt.

`integration/tracker/` (125 + 115 + 77 lines) is **reference-only Java 8 code** for the other
application — explicitly not compiled here. The README explains why there are two apps: the real
planner evaluates `planner_{3g,4g,5g}.js` with **Nashorn**, removed from the JDK in 15, so it is
pinned to JDK 8 while the agent runs Java 17 on Spring Boot 3. They cannot share a JVM; the seam is
HTTP.

### 5.5 `claude/gemini-native-migration` — provider replacement

**6 commits.** Not a configuration change — a removal.

- `ddb12ae` **removes OpenRouter entirely.** `langchain4j-open-ai` is dropped from the classpath, so
  "the code cannot reach any other AI endpoint … whether by default, by typo, or by a missing
  property."
- `944d…` adds `ChatModelConfig` with **three `ChatModel` beans** at different temperatures:
  `@Primary` at 0.1 (agents), `guardrailChatModel` at 0.0, `freeChatModel` at 0.7.
- `48e21b5` drops `langchain4j-spring-boot-starter` because its `RagAutoConfig` breaks startup.
- `5161cff` gives the Gemini HTTP client an **explicit `ProxySelector`** — the Gemini module uses
  `java.net.http.HttpClient`, which does *not* honour `-Dhttps.proxyHost`, so behind a corporate
  proxy every call died with `TimeoutException: HTTP connect timed out`.
- `03bd44d` **preserves Gemini thinking across the tool loop.** This is the subtlest fix in the
  repo: Gemini 2.5+/3.x thinking models emit a `thought_signature` with each `functionCall` that
  must be echoed back on the next turn. The OpenAI-compatible schema has no field to carry it, so
  the compatibility endpoint fails with `400 INVALID_ARGUMENT: Function call is missing a
  thought_signature`. The fix requires **all three** of `includeThoughts(true)`,
  `returnThinking(true)`, `sendThinking(true)` — dropping any one loses the signature.

Also switches column mappings to **Oracle-portable** names (`TelecomCommand`, `EvalCase`,
`EvalResult`, `AgentLog`) — the target production DB is Oracle, not PostgreSQL.

### 5.6 `claude/gemini-pci-office` — the merge

Merges the provider chain into the capability chain. One follow-up commit (`206f8b3`) flattens the
PCI tool description into a plain concatenated string — a Gemini tool-schema compatibility fix.

### 5.7 `claude/gemini-pci-timerange` — the time dimension ★

The most advanced branch. Adds what was arguably the biggest functional hole: **every tool on
`master` averages the entire history**, so "did it get worse last week?" was unanswerable.

**`TimeRangeParser`** — natural-language period parsing in Spanish *and* English:

| Form | Example |
|---|---|
| full dataset | `todo`, `all`, `histórico` |
| single day | `2026-06-15` |
| explicit range | `2026-06-01..2026-06-15`, `… a …`, `… to …` |
| last N | `últimos 7 días`, `last 14 days`, `últimas 48 horas` |
| named relative | `última semana`, `last month`, `mes pasado` |
| day-relative | `ayer`, `yesterday`, `hoy`, `today` |
| calendar month | `junio`, `june`, `mayo 2026` |

> **The design decision that makes this correct:** *"Relative phrases are anchored to the newest
> sample in the database, not to the system clock."* The counters end 2026-06-20; resolving "última
> semana" against `now()` (2026-08-06) returns an **empty window**, and the agent would report "no
> data" for a cell that has plenty. Anchoring to `MAX(sample_time)` makes "last week" mean the last
> week actually measured.

Every result is **clamped to dataset bounds** and carries a `label` and a `clamped` flag, so the
agent can state the window it used. Three failure modes are distinguished and reported rather than
hidden: trimmed, entirely-outside, and uninterpretable.

**Five new period-aware tools** in `DropAnalysisTool`:

| Tool | Purpose |
|---|---|
| `getDataCoverage()` | Report the actual data window — "call this FIRST" |
| `getCellDropSummaryForPeriod(cell, period)` | Windowed summary |
| `getCellDailyTrend(cell, period)` | Day-by-day rate — answers **when** it changed |
| `compareCellPeriods(cell, A, B)` | Before/after with Δ pp, WORSE/BETTER/STABLE, and **whether the dominant cause changed** |
| `getWorstCellsForPeriod(topN, period)` | Windowed ranking |

Plus `suggestPciFixForCell(cell)` — the cross-domain hook letting a user already discussing drops
pursue the PCI track without switching agents.

The `DropRateAgent` system prompt grows by ~28 lines encoding: switch to period tools the moment a
date is mentioned; call `getDataCoverage` first; **always state the period actually used**; use
`getCellDailyTrend` for "when did it change"; the proposed PCI is a **simulation**, never claim to
have changed the network; a clean PCI audit is a **useful** result that rules identity out.

Repository additions: `findByCellNameAndSampleTimeBetweenOrderBySampleTimeAsc` (a derived query, so
it renders as JPQL `BETWEEN` and stays **portable across PostgreSQL and Oracle**),
`findEarliestSampleTime()`, `findLatestSampleTime()`.

---

## 6. The consolidated state of the art (`gemini-pci-timerange`)

### 6.1 Request flow

```
                     ┌──────────────────────────────────────────┐
   GET /agent/ran ──▶ │ InputGuardrail (rules)                   │
                     └──────────────┬───────────────────────────┘
                                    ▼
                        ┌───────────────────────┐
                        │  RanSupervisorAgent   │  Gemini, temp 0.1, memory 10
                        │  (routing prompt)     │  max 2 tools per question
                        └───────────┬───────────┘
              ┌─────────────────────┼─────────────────────┐
              ▼                     ▼                     ▼
     routeToDropSpecialist   diagnoseCell(cell)   routeToPciSpecialist
              │                     │                     │
              ▼                     ▼                     ▼
      ┌──────────────┐   ┌────────────────────┐   ┌──────────────────┐
      │ DropRateAgent│   │ fan-out over ALL   │   │ PciPlanningAgent │
      │  10 @Tools   │   │ DiagnosticModules  │   │  PciPlannerTools │
      └──────┬───────┘   │  (autowired List)  │   └────────┬─────────┘
             │           └─────────┬──────────┘            │
             │                     ▼                       │
             │        ┌─────────────────────────┐          │
             │        │ CrossModuleCorrelator   │          │
             │        │ 6 rules · NO LLM        │          │
             │        └─────────┬───────────────┘          │
             │                  ▼                          │
             │        RootCauseHypothesis                  │
             │        (headline, confidence,               │
             │         rationale, actions,                 │
             │         contributing findings)              │
             ▼                                             ▼
      ┌──────────────────────────────────────────────────────────┐
      │ nr_cell_drops (18,816 real rows) · pci_cell/pci_neighbor │
      │ (28 seeded) · telecom_knowledge (pgvector, EMPTY)        │
      │ agent_logs · eval_cases · eval_results                   │
      └──────────────────────────────────────────────────────────┘
```

### 6.2 Complete tool inventory (17 LLM-callable tools)

**`DropAnalysisTool` (10)** — `getCellDropSummary`, `listAllCells`, `getWorstCells`,
`getDataCoverage`, `getCellDropSummaryForPeriod`, `getCellDailyTrend`, `compareCellPeriods`,
`getWorstCellsForPeriod`, `suggestPciFixForCell`, `getKnowledgeForCause`

**`OrchestratorTools` (5)** — `listModules`, `diagnoseCell`, `tacklePciIssue`,
`routeToDropSpecialist`, `routeToPciSpecialist`

**`PciPlannerTools`** — `getCellPci`, `auditCellPci`, `auditNetworkPci`, `proposePciReplan`

**`TelecomTools` (4, legacy)** — `getCellStatus`, `getDegradedCells`, `calculateKpi`, `findCommands`

Every one of these is logged to `agent_logs` with input, truncated output, and latency.

### 6.3 The three-agent topology

| Agent | Model | Temp | Memory | Tools | Entry point |
|---|---|---|---|---|---|
| `RanSupervisorAgent` | Gemini (`@Primary`) | 0.1 | 10 msgs | `OrchestratorTools` | `/agent/ran` |
| `DropRateAgent` | Gemini | 0.1 | 10 msgs | `DropAnalysisTool` | `/agent/drops` |
| `PciPlanningAgent` | Gemini | 0.1 | 10 msgs | `PciPlannerTools` | `/agent/pci` |
| `TelecomAgent` (legacy) | Gemini | 0.1 | 10 msgs | `TelecomTools` | `/agent/telecom` |

---

## 7. Endpoint surface across branches

| Endpoint | Method | Branch | Purpose |
|---|---|---|---|
| `/ai/chat?message=` | GET | all | Plain LLM, no tools, no memory |
| `/agent/telecom` | POST | all | Legacy telecom agent |
| `/agent/drops?message=` | GET | all | Drop-rate agent (2-layer guardrail) |
| `/logs/recent?n=` | GET | all | Last N log entries as text |
| `/logs/export` | GET | all | Dump log to timestamped file |
| `/eval/run?label=` | POST | all | Run the eval suite |
| `/eval/results?label=` | GET | all | Per-case results |
| `/eval/cases` | GET | all | Inspect the question set |
| `/agent/ran?message=` | GET | orchestration+ | **Supervisor, LLM-orchestrated** |
| `/agent/ran/diagnose?cell=` | GET | orchestration+ | **Deterministic cross-module RCA, no LLM** |
| `/agent/pci?message=` | GET | orchestration+ | PCI specialist |
| `/` , `/chat.html`, `/reference.html` | GET | all | Dev console (static) |

> `/agent/ran/diagnose` is the most under-appreciated endpoint in the repo: full cross-module
> root-cause analysis with **zero LLM involvement** — cheap, reproducible, and directly usable as a
> dashboard feed or a deterministic eval target.

---

## 8. Configuration and operational prerequisites

### 8.1 Stack

Spring Boot 3.4.5 · Java 17 · LangChain4j 1.0.0-beta1 (BOM) · PostgreSQL + pgvector (Oracle-portable
on Gemini branches) · Hibernate `ddl-auto=update`.

### 8.2 Required properties

`src/main/resources/application.properties` is **gitignored** (it holds a live API key and a DB
password). Use `application.properties.example`.

**`master`:** `spring.datasource.*`, `openai.api-key` (an OpenRouter key).

**Gemini branches:** `gemini.api-key` and `gemini.model` are **required with no defaults** —
startup fails loudly rather than guessing. Optional: `gemini.max-retries` (default 2, because the
free tier 429s), `gemini.proxy-host` / `gemini.proxy-port`, `gemini.timeout-seconds` (60),
`gemini.thinking` (`preserve` | `off`), `pci.planner.backend` (`local` | `rest`).

### 8.3 Manual setup steps required before anything works

1. **pgvector SQL** (from `SUMMARY.md`) — `CREATE EXTENSION vector`, create `telecom_knowledge`
   with a `vector(1536)` column and an HNSW index.
2. **Eval seed** — `db/eval_cases.sql` then `db/eval_seed.sql` (20 cases).
3. **Knowledge base** — place `5G_NSA_CallDrop_KnowledgeBase.md` under
   `src/main/resources/knowledge/`. ⚠️ **This file is not in the repository** (§3.4).

### 8.4 Fail-soft behaviour

The app is designed to start even when RAG is misconfigured. `RagConfig` catches store-creation
failure and **returns `null`**; `DropAnalysisTool` and `KnowledgeIngestionService` inject it with
`@Autowired(required = false)` and degrade to a "not available" message. Good for demos — but it
means a broken RAG setup is **silent** unless someone reads stderr.

---

## 9. Build, test and verification status

### 9.1 Compilation and test execution — verified in this session

Both were actually run, not inferred:

| Branch | Command | Result |
|---|---|---|
| `master` | `mvnw compile` | ✅ exit 0 |
| `claude/gemini-pci-timerange` | `mvnw test -Dtest=OrchestrationLogicTest,TimeRangeParserTest` | ✅ exit 0 |

```
Test set: com.ranadvisor.orchestrator.OrchestrationLogicTest
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.524 s

Test set: com.ranadvisor.drops.timerange.TimeRangeParserTest
Tests run: 8,  Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.120 s
```

**21/21 passing.** The correlator, the PCI detection engine, the planner proposal path and the
time-range parser are all verified green on the tip branch. Note the orchestration suite runs with
Mockito's inline mock-maker, so the PCI-backend-unavailable case is exercised against a stubbed
port rather than a real outage — which is the correct way to test it.

### 9.2 Test inventory

| Branch | Tests | What they cover |
|---|---|---|
| `master` | **0 real** | One empty `@SpringBootTest` context-load stub in `com.example.demo` |
| orchestration branches | 13 | `OrchestrationLogicTest` |
| `gemini-pci-timerange` | **21** | + 8 `TimeRangeParserTest` |

**`OrchestrationLogicTest` (13)** covers exactly the right things — the seeded conflicts
(`worstCell_hasExactlyOneCollisionWithPuente`, `azangaro_hasConfusionOnPci110`,
`networkAudit_findsCollisionConfusionAndMod3`), the planner
(`suggestPci_forWorstCell_isCollisionFree`, `proposal_movesOffTheCollidingPci_andCarriesItsReasoning`,
**`proposal_isNeverAnAppliedChange`**), graceful degradation
(`unknownCell_yieldsNoFindingRatherThanAnError`), and the correlator
(**`correlator_treatsAnUnreachablePciBackendAsUnknown_notAsCleanPci`**,
`correlator_linksRachDropsToPciCollision_withHighConfidence`, `correlator_cleanPci_doesNotBlamePci`).

**`TimeRangeParserTest` (8)** covers anchoring-to-data-not-today, bilingual last-N, order-independent
range parsing, month clamping, out-of-range fallback, and unparseable-phrase fallback.

### 9.3 What is *not* tested anywhere

- `DropAnalysisTool` — the drop-rate formula, dominant-cause selection and severity thresholds have
  **no unit test on any branch**
- `InputGuardrail` / `LlmGuardrail` — no tests for injection phrases, the keyword allowlist, the
  cell-name regex, or the fail-open path
- `EvalScorer` / `EvalRunnerService`
- `TimeRangeParser` is tested; the five tools that *consume* it are not
- `NrCellDropsLoader` CSV parsing
- No integration test exercises a full agent turn

---

## 10. Known gaps and risks (code-level)

Ordered by consequence. Every item is a code observation, not a style preference.

| # | Finding | Evidence | Impact |
|---|---|---|---|
| **G-1** | **RAG knowledge base file absent from all branches** | `KnowledgeIngestionService.java:56` loads `knowledge/5G_NSA_CallDrop_KnowledgeBase.md`; `git ls-tree` finds it on no branch | The single most-cited capability of the project does not function on a clean clone. `getKnowledgeForCause` returns "no relevant knowledge" always. |
| **G-2** | **`llm_judge` scorer is a stub** | `EvalScorer.java:34-42` | 3 of the harness's scoring modes are really 2. Any seeded `llm_judge` case counts as a permanent failure. *Fails closed — the right choice.* |
| **G-3** | **Shared chat memory across eval and live traffic** | `DropAgentConfig.java:28`, `AiConfig.java:30`, documented at `EvalRunnerService.java:40-52` | Eval runs are not reproducible; cases contaminate each other. Also a multi-user correctness bug in production. |
| **G-4** | **PCI conflicts are planted fixtures** | `PciDataLoader` Javadoc: "deliberate", "crafted root cause" | The headline cross-module demo is true by construction. Correlation *logic* is real; the shown correlation is not an observation. |
| **G-5** | **LLM guardrail fails open** | `LlmGuardrail.java:86-92` | A Gemini outage silently disables Layer 2. Deliberate and documented, but the deterministic layer becomes the only defence with no alert. |
| **G-6** | **`getWorstCells` is N+1** | `DropAnalysisTool.java:64-68` — one `findDistinctCellNames` then a full history load **per cell** | 29 queries + full in-memory aggregation of 18k rows per call. `getWorstCellsForPeriod` has the same shape. Fine at 29 cells; will not survive a real network. |
| **G-7** | **~50 of 72 counters unused** | `NrCellDrops` maps TA/RSRP/SINR histograms; nothing reads them | The signals needed for coverage and overshoot analysis are already loaded and idle. |
| **G-8** | **Thresholds are hardcoded magic numbers** | `DropAnalysisTool.java:246`, duplicated in `DropRateModule` | 15 %/5 % are not configurable, not derived from the data distribution, and duplicated in two files that can drift. |
| **G-9** | **Drop-rate formula duplicated** | `DropAnalysisTool.buildSummary` and `DropRateModule.analyze` | Two copies of the definitional formula of the whole product. |
| **G-10** | **`master` has zero real tests** | `src/test/.../RanParameterCopilotV2ApplicationTests.java` is an empty context-load stub | The branch most likely to be treated as "stable" is the least verified. |
| **G-11** | **Nothing is merged to `master`** | All 7 branches are 1–14 commits ahead, 0 behind | The orchestrator, PCI module, Gemini migration and time analysis all live only on branches. Divergence risk grows daily. |
| **G-12** | **No `CoverageModule` despite being in the architecture doc and deck** | `arquitectura-triple-agente.md` describes Drops · Coverage · PCI; only two modules exist | The "triple agent" is currently a double agent. |
| **G-13** | **Static analysis data is a snapshot, not a feed** | CSV exported 2026-06-22, loaded once at startup, skipped if the table is non-empty | No refresh path. Nothing detects that the data has gone stale. |

---

## 11. Research vs. Reality — auditing the research branch against the code

> **This is the section the exercise was for.** The research branch (`claude/drop-rate-agent-research-j607ex`)
> makes claims about what the literature says *and* about how it maps onto this repository. Below,
> every claim is checked against the actual source.

### 11.0 Method

For each item I asked one question: **is this true of the code that exists in this repository, on
some branch, today?** Verdicts:

- ✅ **CONFIRMED** — the code does this; the file is cited.
- ⚠️ **PARTIAL** — a real mechanism exists but is narrower than the claim implies.
- ❌ **NOT PRESENT** — no code addresses this.
- 🔴 **CONTRADICTED** — the code shows the claim to be false as shipped.
- ➖ **N/A** — genuinely does not apply to this system's design.

### 11.1 The 11 issues (§4 of the research) — do they apply here?

| § | Issue | Applies? | Evidence in this codebase |
|---|---|---|---|
| 4.1 | Data scarcity / confidentiality | ⚠️ **PARTIAL — inverted** | The research frames this as *can't get data*. Here the opposite is true: 18,816 real rows, 72 counters, a named exporter (`gustavo.diaz`), hourly for 4 weeks. The scarcity is in **scope** (29 cells, 8 sites, one operator, one 4-week window), not in access. Confidentiality does bite: `application.properties` is gitignored for exactly this reason. |
| 4.2 | Label scarcity → unsupervised by necessity | ➖ **N/A as framed** | There is no learned model of any kind, so labels are moot. But the underlying problem reappears in a different costume: **`db/eval_seed.sql` is a hand-labelled ground-truth set of 20 cases**, and it is the only ground truth in the project. That is label scarcity, exactly — just at the eval layer instead of the training layer. |
| 4.3 | Class imbalance | ➖ **N/A** | No classifier exists. `dropRate > 15` is a threshold on a ratio, not a decision boundary learned from imbalanced samples. **Would become live the moment any statistical detector is added** — the very first thing to plan for. |
| 4.4 | Concept drift | ✅ **APPLIES — wholly unaddressed** | No drift detection, no baselining, no retraining anywhere. Worse, the design is drift-*blind* by construction: `NrCellDropsLoader.load()` **skips import entirely if the table is non-empty**, and `PciDataLoader` does the same. There is no refresh path and nothing that notices the data has aged. The 15 %/5 % thresholds are fixed constants (`DropAnalysisTool.java:246`) that no mechanism re-derives. |
| 4.5 | False positives / alarm fatigue | ⚠️ **PARTIAL — structurally avoided** | The system is **pull, not push**: it answers questions, it does not raise alarms. There is no alerting path, so there is no alarm fatigue *yet*. But precision is also never measured — the eval harness scores answer *text*, not detection quality, so if alerting were added tomorrow there'd be no baseline to tune against. |
| 4.6 | Black-box opacity → no trust, no RCA | ✅ **CONFIRMED — genuinely well handled** | This is the repo's strongest suit and it is not an accident. `CrossModuleCorrelator` **contains no LLM at all** — six ordered, readable rules. `RootCauseHypothesis` carries `rationale`, `recommendedActions` **and `contributing` findings** as an evidence trail. `LocalPciPlannerAdapter.propose()` builds a 5-step `trail` ("Step 2 — PCIs excluded …", "Step 3 — PSS groups excluded …") plus an explicit `warnings` list stating what the backend cannot do. Every tool call lands in `agent_logs` with input, output and latency. An engineer can reconstruct *why* the system said what it said. |
| 4.7 | Spatio-temporal complexity | ⚠️ **PARTIAL — both halves exist, separately** | **Spatial:** real, on the PCI side — `PciAnalysisTool.neighborsOf()` builds an undirected neighbour graph (ANR edges ∪ co-sited cells), and `searchSpace()` even reasons about **second-tier** neighbours to avoid creating confusion. That is genuine topology-aware analysis. **Temporal:** real, on the drops side — `getCellDailyTrend` and `compareCellPeriods` (timerange branch). **But they never meet.** No code correlates a *neighbour's* drop behaviour with this cell's, and the drop side has no spatial dimension at all: `DropRateModule.analyze(cellName)` looks only at one cell. |
| 4.8 | Real-time inference / MLOps | ➖ **N/A — different deployment model** | No xApp, no rApp, no RIC, no near-RT loop, no model to retrain. This is a batch analytics + chat application over a static export. The RIC latency budgets in the literature simply do not apply. Notably, the *cost* discipline the literature associates with this issue **does** appear, in a different form: `PciPlannerPort` splits `audit` (ms, called on every fan-out) from `propose` (seconds, user-initiated only) precisely so the cheap path stays cheap. |
| 4.9 | Flawed evaluation | ✅ **APPLIES — partially mitigated, honestly documented** | A harness exists (20 cases, pass rate, per-case latency, run labels). But 18 of 20 cases use `contains` — substring matching against a fragment. `"30"` passes if the answer contains the digits 3 and 0 anywhere; `"RA Problem"` passes on an echo of the phrase without any correct reasoning. This is exactly the "illusion of progress" the research cites Wu & Keogh for, reproduced in miniature. **The mitigating credit is real though:** `llm_judge` **fails closed** rather than silently passing (`EvalScorer.java:40-42`), and the shared-memory reproducibility flaw is written into the source as a `KNOWN LIMITATION` block. The project is honest about its own weak evaluation, which is rarer than a strong one. |
| 4.10 | LLM hallucination / grounding / unsafe tools | ✅ **CONFIRMED — the best-defended area** | Four independent layers: (1) **prompt** — `"NEVER invent numbers. Only use data returned by your tools."` in every agent; (2) **tools** — the model cannot reach the DB, only typed Java methods; (3) **guardrails** — deterministic rules then a semantic classifier; (4) **architecture** — `PciPlannerPort` has **no `apply()` method**, so no `@Tool` can mutate the network no matter what the model decides. Layer 4 is the one that matters most and the one most projects skip. ⚠️ The grounding leg is broken in practice: RAG has no corpus (§3.4). |
| 4.11 | Security / privacy | ⚠️ **PARTIAL** | Handled: credentials gitignored; no write path to the network; a proxy-aware HTTP client for corporate egress; prompt-injection filtering. Not handled: no authn/authz on **any** endpoint — `/agent/ran`, `/logs/export`, `/eval/run` are all open. `index.html` says "Developer console — local only", which is the entire access-control model. |

**Issue-level verdict: 4 of 11 confirmed as applying and addressed; 4 partial; 3 genuinely N/A. The
one unambiguous, wholly-unaddressed gap is §4.4 concept drift.**

### 11.2 The research's own §7 table — "what the literature validates about the current design"

The research branch asserts five repo capabilities are backed by literature. Checking each against
the source:

| Claimed capability | Verdict | What the code actually shows |
|---|---|---|
| **pgvector RAG over a call-drop knowledge base** | 🔴 **CONTRADICTED** | The plumbing is complete and correct: `RagConfig` builds a `PgVectorEmbeddingStore` (table `telecom_knowledge`, dim 1536, HNSW), `KnowledgeIngestionService` splits on `## CHUNK` and embeds, `getKnowledgeForCause` does a `maxResults=2` similarity search, and the agent prompt *mandates* calling it. **But the corpus file `knowledge/5G_NSA_CallDrop_KnowledgeBase.md` exists on no branch.** On a clean clone the store is empty and the tool returns "No relevant knowledge found" for every query. The research's #1 anti-hallucination claim is, as shipped, **a wired-up empty box**. Additionally `RagConfig` requires manual SQL before it works at all, and fails soft to `null` when it doesn't — so the failure is silent. |
| **Two-layer guardrails (rule + LLM)** | ✅ **CONFIRMED** | Both layers exist and both are wired into `DropAgentController` in the stated order. Layer 1: length bounds, 11 injection phrases, ~35-keyword allowlist, cell-name regex. Layer 2: temperature-0.0 classifier with separated system/user messages and defensive JSON extraction. Both persist blocks with reason codes. ⚠️ Two caveats the claim omits: Layer 2 **fails open** on any error, and `SupervisorController` uses **only Layer 1** — the `/agent/ran` front door has no semantic guardrail at all. |
| **Eval harness (question → ground-truth fragment, pass rate)** | ✅ **CONFIRMED** (and the claim is appropriately modest) | Entities, runner, scorer, controller, 20 seeded cases, run labels, per-case latency, per-case exception isolation. The phrase "ground-truth fragment" is honest — that *is* what it does, and §11.1/4.9 explains why fragment-matching is weak. `llm_judge` remains a stub. |
| **Tool-call logging (`agent_logs`)** | ✅ **CONFIRMED — arguably the most complete feature** | `AgentLog` + repository + two endpoints. **Every** `@Tool` in `DropAnalysisTool`, `OrchestratorTools` and `PciPlannerTools` is wrapped with start-time capture and a `log(...)` call recording agent, tool, input, output (truncated 2,000 chars) and latency. Guardrail blocks are logged too. Log failures are caught so they can never break a tool. This genuinely supports the auditability the XAI literature calls for. |
| **Drop-rate / worst-cell focus** | ✅ **CONFIRMED** | Retainability is the entire product. `getCellDropSummary`, `getWorstCells`, `DropRateModule`, and the whole `nr_cell_drops` schema are built around it, on real operator counters. |

**Scorecard: 4 confirmed, 1 contradicted.** The contradicted one is the capability the research
ranks *highest* ("the highest-leverage anti-hallucination move").

### 11.3 The research's five recommended next steps — current status

| # | Recommendation | Status | Detail |
|---|---|---|---|
| **1** | **Add root-cause reasoning, not just detection**; spatio-temporal (neighbour-cell) view | ✅ **DONE — and it went further than recommended** | This is the strongest research→code result in the repo. `CrossModuleCorrelator` produces a *ranked hypothesis with confidence, rationale, ordered actions and an evidence trail* — not a flag. `RootCauseHypothesis` is a first-class type. `DiagnosticModule` makes the pattern extensible. The neighbour dimension arrived too, via `PciAnalysisTool.neighborsOf()` and second-tier confusion reasoning. **Remaining gap:** neighbour topology lives only in the PCI domain; nothing correlates a neighbour's *drop* behaviour with this cell's. |
| **2** | **Guard against concept drift**; monitor KPI drift, set a refresh cadence | ❌ **NOT STARTED** | Zero drift code. Both loaders skip if data exists. Thresholds are constants. No baselining, no staleness check, no refresh path. `getDataCoverage` (timerange branch) at least *reports* the data window so an answer can be framed against it — the nearest thing to drift-awareness, and it is a reporting tool, not a detector. |
| **3** | **Handle class imbalance explicitly if statistical detection is added** | ➖ **NOT APPLICABLE YET** | Correctly not started — no statistical detection exists. The conditional has not fired. |
| **4** | **Strengthen eval beyond fragment-matching** | ⚠️ **STARTED, INCOMPLETE** | The `llm_judge` path is *designed for* (a `score_type` value, a switch branch, a documented Phase-1 TODO) but not implemented; it fails closed. 18 of 20 cases remain `contains`. `/agent/ran/diagnose` is a ready-made deterministic eval target — no LLM, fully reproducible — but **no eval case uses it.** That is the cheapest available upgrade in the whole repo. |
| **5** | **Digital twin / synthetic data path; federated option** | ⚠️ **ACCIDENTALLY PARTIAL** | Nobody built a digital twin, but `PciDataLoader` **is** a synthetic-data path: a deterministic seeded network with planted, documented conflicts used to exercise the correlator without touching a live plan. `LocalPciPlannerAdapter` is a safe sandbox standing in for the real planner, and its warnings state exactly where the simulation stops. Federated learning: no trace, and correctly so at this scale. |

### 11.4 Things the code does that the research **did not** anticipate

A fair audit runs in both directions. Four engineering decisions in this codebase address real
failure modes that the literature review does not cover:

1. **The "unchecked ≠ clean" distinction** (`CrossModuleCorrelator`, Rule 4b + `PciModule`'s
   `UNKNOWN` on backend failure). The literature discusses false positives and false negatives at
   length; it does not discuss **the epistemics of a check that never ran**. This codebase treats
   "PCI backend unreachable" as a distinct, reportable state rather than collapsing it into "no
   conflict found" — and has a dedicated test asserting it. That is a subtler correctness property
   than anything in §4 of the research.

2. **Capability removal as a safety boundary.** The research recommends "guardrails and tool specs."
   This repo goes further: `PciPlannerPort` **has no `apply()` method**, so the dangerous action is
   unreachable by construction rather than forbidden by a prompt. Likewise the Gemini branch deletes
   `langchain4j-open-ai` from the classpath so no other AI endpoint can be reached "by default, by
   typo, or by a missing property." Both are *architectural* controls, which no amount of prompt
   engineering equals.

3. **Anchoring relative time to the data, not the clock** (`TimeRangeParser`). The research's §4.4
   covers concept drift as a *model* problem. This is the same family of error at the *query* layer:
   asking a 2026-06 dataset about "last week" on 2026-08-06 returns nothing, and the agent would
   confidently report "no data" for a healthy cell. Anchoring to `MAX(sample_time)`, clamping to
   bounds, and surfacing a `clamped` flag is a class of correctness the literature does not name.

4. **Cost-tiered ports.** Splitting `audit` (ms, every fan-out) from `propose` (seconds,
   user-initiated) is exactly the latency/expense discipline §4.8 associates with RIC deployment —
   arrived at independently, for a non-RIC architecture.

### 11.5 Overall verdict

**On the research's self-assessment.** Its §8 conclusion — *"This repo is aimed at the right targets
… its RAG, guardrails, logging, and eval pieces map cleanly onto the field's consensus mitigations;
the biggest opportunities are RCA depth, drift handling, and stronger evaluation"* — holds up well,
with two corrections:

- **RCA depth is no longer the biggest opportunity.** It was the recommendation that got *acted on*,
  and thoroughly: a typed SPI, a deterministic correlator with confidence and evidence, and an
  extensible module registry. That item should be marked done.
- **RAG should not be in the "validated" column.** It is plumbing without a corpus. Until
  `5G_NSA_CallDrop_KnowledgeBase.md` is committed, listing pgvector RAG as a working
  anti-hallucination control **overstates the system**, and the agent prompt's mandatory
  `getKnowledgeForCause` call makes it worse: the model is instructed to consult a knowledge base
  that always answers "nothing found."

**What the research got right about this system.** The core thesis — *models are commoditized; data,
operations and trust are the moat* — is exactly what this codebase demonstrates. There is **no ML at
all** here, and it is still a useful diagnostic tool, because it invested in typed tools, an audit
trail, explicit reasoning, safety boundaries and honest failure reporting. §8's second takeaway,
*"detection is table stakes; root-cause analysis and explanations are what operators buy,"* is the
thesis the `CrossModuleCorrelator` was built on.

**What the research over-claims.** One row of one table (RAG), and by implication the "grounding"
leg of §4.10.

**The single highest-value action arising from this audit:** commit the knowledge-base corpus. It
converts the project's most-cited capability from decorative to functional, and it costs one file.

---

## 12. Recommended sequence from here

Ordered by value ÷ effort, each grounded in a finding above.

| # | Action | Addresses | Effort |
|---|---|---|---|
| 1 | **Commit `src/main/resources/knowledge/5G_NSA_CallDrop_KnowledgeBase.md`** | G-1, §11.2 row 1 | Trivial — file exists somewhere, just uncommitted |
| 2 | **Merge `gemini-pci-timerange` → `master`** (or make it the trunk) | G-11 | Low — no branch is behind master |
| 3 | **Add eval cases against `/agent/ran/diagnose`** — deterministic, no LLM, perfectly reproducible | G-2, §11.3 row 4 | Low — the endpoint already exists |
| 4 | **Give the eval runner its own memory-less agent instances** | G-3 | Low — already scoped as "Phase 1" in the source |
| 5 | **Unit-test the drop-rate formula, dominant-cause selection and severity thresholds** | §9.3 | Low — pure functions, no I/O |
| 6 | **Extract the drop-rate formula and thresholds into one configurable place** | G-8, G-9 | Low |
| 7 | **Implement `llm_judge`** with the guardrail model | G-2 | Medium |
| 8 | **Add a `CoverageModule`** using the already-loaded RSRP/SINR/TA histograms | G-7, G-12, §11.1/4.7 | Medium — data is already in the DB; completes the "triple agent" |
| 9 | **Add drift/staleness awareness** — data-age check, distribution baseline per cell, thresholds derived from the data | G-13, §11.3 row 2 | Medium — the only wholly-unaddressed research issue |
| 10 | **Correlate neighbour drop behaviour**, not just neighbour PCI | §11.1/4.7 | Medium — the neighbour graph already exists in `PciAnalysisTool` |
| 11 | **Fix the `getWorstCells` N+1** with a single aggregate query | G-6 | Medium |
| 12 | **Label the PCI demo as seeded** wherever it is presented | G-4 | Trivial — and it protects credibility |
| 13 | **Add authn to the endpoints** before anything leaves localhost | §11.1/4.11 | Medium |

---

*Compiled by reading every branch of `VBAUTSITA/DROP-RATE-AG` at the tips listed in §2.
Verdicts in §11 are code observations with file and line citations; where a claim could not be
confirmed from source, it is marked partial rather than assumed.*
