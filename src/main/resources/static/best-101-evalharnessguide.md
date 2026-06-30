# Eval Harness 101 — Best Practices & How Ours Compares

> What an LLM eval harness is, what the industry-standard ones do, and an honest scorecard
> of the RAN Advisor Phase 0 harness (`com.ranadvisor.eval`) against those standards.
>
> Companion to [eval-guide.md](eval-guide.md), which explains how to *run* ours.
> This file explains how *good* ours is.

---

## Part 1 — What an eval harness actually is (the 101)

An **eval harness** is the test suite for non-deterministic software. Traditional code is tested
with assertions (`assertEquals(4, add(2,2))`) because the output is fixed. An LLM agent returns
different wording every time, may call tools in different orders, and can be subtly wrong while
sounding confident. You cannot assert exact equality, so you need a different machine:

```
  ┌─────────────┐     ┌──────────────┐     ┌───────────┐     ┌──────────────┐
  │  Dataset    │ ──▶ │  Run target  │ ──▶ │  Score    │ ──▶ │  Aggregate   │
  │ (Q + truth) │     │  (the agent) │     │ (grade)   │     │ (pass rate)  │
  └─────────────┘     └──────────────┘     └───────────┘     └──────────────┘
```

Four parts, always:

1. **Dataset** — questions paired with ground truth ("golden" answers).
2. **Task runner** — feeds each question to the system under test and captures the output (and,
   for agents, the *trajectory*: which tools were called).
3. **Scorer / evaluator** — grades each output. This is where harnesses differ the most.
4. **Aggregator + store** — turns per-case grades into a tracked metric over time, so you can
   tell whether a prompt/model/tool change made things better or worse.

The discipline this enables is **eval-driven development**: write the eval before the feature,
watch it fail, build until it passes, and never let it regress. It is unit-testing's contract,
applied to behavior instead of return values.

---

## Part 2 — The industry framework landscape

These are the harnesses real teams use. Grouped by what they're for.

| Framework | Origin | Language | Niche | Scoring approach |
|-----------|--------|----------|-------|------------------|
| **OpenAI Evals** | OpenAI | Python/YAML | General LLM correctness | Exact match + model-graded (LLM-as-judge) |
| **lm-evaluation-harness** | EleutherAI | Python | Academic benchmarks/leaderboards | Accuracy, exact match, perplexity, log-likelihood |
| **HELM** | Stanford CRFM | Python | Holistic multi-metric benchmarking | Accuracy, robustness, calibration, bias, toxicity |
| **LangSmith** | LangChain | Python/JS (SaaS) | Production agent eval + tracing | Custom + LLM-as-judge + **trajectory eval** |
| **promptfoo** | OSS | JS/YAML/CLI | CI-friendly prompt/assertion testing | contains, equals, regex, similarity, llm-rubric |
| **DeepEval** | Confident AI | Python (pytest-style) | Unit-test-style LLM testing | G-Eval, faithfulness, relevancy, hallucination |
| **Ragas** | OSS | Python | **RAG-specific** quality | Faithfulness, answer relevancy, context precision/recall |
| **TruLens** | TruEra | Python | Feedback functions / RAG triad | Context relevance, groundedness, answer relevance |
| **Braintrust** | Braintrust | SaaS | Experiment tracking + scorers | Custom scorers, LLM-as-judge, diffing |
| **Arize Phoenix** | Arize | Python | Observability + offline eval | LLM evals over traces |
| **MLflow LLM Evaluate** | Databricks | Python | MLOps-integrated eval | Built-in + custom metrics |
| **RAN Advisor eval** | *this project* | **Java/Spring** | In-app agent regression | exact, contains, llm_judge (stub) |

**Key takeaway:** the entire mainstream tooling ecosystem is **Python or SaaS**. There is no
dominant Java-native eval harness. Ours is a from-scratch, dependency-free, in-process Java
harness — which is unusual, and the right call for a Spring Boot app that wants evals to live
*inside* the deployable rather than in a separate Python sidecar.

---

## Part 3 — The scoring-method taxonomy (and where we sit)

Scoring is the axis that separates a toy harness from a serious one. The full spectrum,
cheapest/most-brittle first:

| # | Method | What it does | Cost | We have it? |
|---|--------|--------------|------|-------------|
| 1 | **Exact match** | String equality | Free | ✅ `exact` |
| 2 | **Substring / contains** | Fragment appears in output | Free | ✅ `contains` |
| 3 | **Regex** | Pattern match | Free | ❌ |
| 4 | **Numeric / tolerance** | `abs(actual-expected) < ε` for numbers | Free | ❌ |
| 5 | **NLP overlap** (BLEU/ROUGE) | Token overlap with reference | Cheap | ❌ |
| 6 | **Semantic similarity** | Embedding cosine ≥ threshold | 1 embed call | ❌ |
| 7 | **LLM-as-judge / G-Eval** | A model grades the answer | 1 LLM call | ⚠️ stubbed |
| 8 | **RAG metrics** (faithfulness, context recall) | Is the answer grounded in retrieved context? | Several LLM calls | ❌ |
| 9 | **Trajectory / tool-call eval** | Did the agent call the *right tools in the right order*? | Free–LLM | ❌ |

We implement **2 of 9** (plus a stub for #7). That is the floor of a credible harness — and for a
fact-based telecom agent it covers more ground than the count suggests (see Part 5) — but it
leaves the highest-value agentic method, **#9 trajectory eval**, completely unaddressed.

---

## Part 4 — Validation scorecard: RAN Advisor vs. the standards

Graded against the practices the frameworks above treat as table stakes.

| Best practice | Standard expectation | Our harness | Verdict |
|---------------|---------------------|-------------|---------|
| **Golden dataset** | Versioned Q + ground-truth set | 20 seed cases in `eval_cases` | ✅ Meets |
| **Multiple scorers** | At least exact + semantic + judge | exact + contains; judge stubbed | ⚠️ Partial |
| **Result persistence** | Store every run for comparison | `eval_results` + `run_label` | ✅ Meets (LangSmith-style "experiments") |
| **Run-over-run tracking** | Compare metric across versions | `run_label` enables it; no diff view | ⚠️ Partial |
| **Latency capture** | Track speed per case | `latency_ms` per result | ✅ Meets (many homegrown skip this) |
| **Cost/token capture** | Track tokens & $ per run | Not captured | ❌ Gap |
| **Error isolation** | One failure can't abort the run | per-case try/catch → `ERROR:` result | ✅ Meets (robust) |
| **Reproducibility / isolation** | Stateless, isolated runs | Shared `MessageWindowChatMemory` | ❌ Gap (documented) |
| **Statistical rigor** | Multi-run variance, pass@k | Single deterministic-ish run | ❌ Gap |
| **Trajectory / tool eval** | Verify tool selection & order | Only final-text checked | ❌ Gap (biggest) |
| **RAG-specific metrics** | Faithfulness/groundedness for RAG | None, despite having pgvector RAG | ❌ Gap |
| **CI/CD integration** | Fail the build on regression | Manual `curl` trigger only | ❌ Gap |
| **Case categorization** | Tag by capability/difficulty | Only `agent` column | ⚠️ Partial |
| **Eval-first workflow** | Documented discipline | Documented in eval-guide.md | ✅ Meets |

**Score: roughly 5 met / 4 partial / 5 gaps.** This is a legitimate **Phase 0 / v0** harness:
it has the four-part skeleton, persistence, and latency — the things people most often forget —
but it grades only final text with free substring matching, which is where the real risk lives.

---

## Part 5 — Where we're genuinely fine (don't over-fix)

- **`contains` is not as weak as it looks here.** Our agents answer from concrete facts — cell
  names (`ARR40312C1_Moran_Uribe`), percentages, cause labels (`RA Problem`). Substring presence
  of a *specific* fragment catches the majority of real regressions at zero cost. promptfoo and
  LangSmith both ship `contains`/`icontains` assertions for exactly this reason.
- **DB-backed results with run labels** is conceptually the same model LangSmith and Braintrust
  charge for: named experiments you compare over time. We get it with two tables.
- **Per-case error isolation** is a maturity signal many first-draft harnesses miss — a single
  thrown exception logging `ERROR:` instead of killing the run is correct design.
- **In-process Java** means no Python sidecar, no data export, no second deployment. For a
  single Spring Boot artifact this is the pragmatic, low-ops choice.

---

## Part 6 — Prioritized gap-closing roadmap

Ordered by value-per-effort for *this* project.

1. **Trajectory / tool-call eval (highest value).** The whole point of these agents is calling
   the right `@Tool`. Right now a case "passes" if the final text contains a fragment even if the
   agent fabricated it without calling `getCellDropSummary`. Capture which tools fired (the
   `agent_logs` table already records tool calls — join eval runs to it by timestamp) and assert
   the expected tool was invoked. This is the single biggest correctness upgrade.
2. **Implement `llm_judge` (G-Eval style).** Already stubbed and failing closed. Add a second
   call to a stronger model that reads (question, expected, actual) and returns PASS/FAIL +
   reason. Unlocks the free-text reasoning cases `contains` can't grade.
3. **RAG metrics for the knowledge tool.** Since `getKnowledgeForCause` does pgvector retrieval,
   add a Ragas-style **faithfulness** check: is the explanation grounded in the retrieved chunk,
   or hallucinated? High value because RAG hallucination is invisible to `contains`.
4. **Fix run isolation.** Give the runner its own memory-less agent instances so cases can't bias
   each other. Required before any statistical claim about pass rate.
5. **Semantic-similarity scorer.** Embedding cosine ≥ threshold for paraphrase-tolerant matching
   — you already have an `EmbeddingModel` bean from the RAG work, so this is nearly free to add.
6. **CI integration.** A small test that calls `runAll` and asserts `passRate >= baseline`, wired
   into the build so regressions fail PRs.
7. **Token/cost capture + case tags** (difficulty, capability) for richer slicing.

---

## Verdict

The RAN Advisor eval harness is a **correct, honest v0**: it has the canonical four-part
structure, persists results like the commercial platforms, tracks latency, and isolates failures
— and it's a rare *Java-native, in-process* harness in a field that is otherwise all Python/SaaS.

Its limitation is equally clear: it grades **only final-text substrings** and ignores the
**agent trajectory** (tool selection), which is the thing most worth testing in a tool-using
agent, and it has no RAG-faithfulness check despite shipping RAG. Closing gaps #1–#3 above would
move it from "credible smoke test" to "trustworthy agentic eval." For Phase 0 — establishing a
measurable baseline before building features — it is fit for purpose.

---

### Sources / further reading

- OpenAI Evals — https://github.com/openai/evals
- EleutherAI lm-evaluation-harness — https://github.com/EleutherAI/lm-evaluation-harness
- LangSmith evaluation docs — https://docs.smith.langchain.com
- promptfoo assertions — https://www.promptfoo.dev
- DeepEval — https://github.com/confident-ai/deepeval
- Ragas (RAG metrics) — https://github.com/explodinggradients/ragas
- TruLens (RAG triad) — https://www.trulens.org
- G-Eval paper — "G-Eval: NLG Evaluation using GPT-4 with Better Human Alignment" (Liu et al., 2023)
- Stanford HELM — https://crfm.stanford.edu/helm
