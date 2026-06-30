# Eval Harness Guide (Phase 0)

> Infrastructure to measure agent quality **before** adding new features.
> Package: `com.ranadvisor.eval`.

---

## 1. What is an eval harness, and why does it exist?

An **eval harness** is a unit-test suite for AI behavior. A normal unit test asserts that
`add(2, 2)` returns exactly `4` — the function is deterministic, so the test is a hard
equality check. An LLM agent is **not** deterministic: ask it "which cell is worst?" twice and
you may get two differently-worded answers, both correct. You cannot assert exact string
equality. Instead the eval harness stores a set of **questions** paired with **ground-truth
answer fragments**, runs every question through the real agent, and checks whether each
response contains (or matches) the expected fragment. The output is a **pass rate** — a single
number you can track over time. When you change a system prompt, swap a model, or add a tool,
you re-run the harness and see whether quality went up or down, instead of guessing.

---

## 2. The three scoring strategies

Each case has a `score_type` that decides how its response is graded.

| Strategy | How it grades | When to use | Tradeoff |
|----------|--------------|-------------|----------|
| **`exact`** | `response.trim().equalsIgnoreCase(expected.trim())` | Only when the agent should return one precise, short string (a status code, a single number with no prose around it). | Brittle. Any extra word fails it. Rarely usable for conversational agents. |
| **`contains`** | `response.toLowerCase().contains(expected.toLowerCase())` | The default for almost every case here. Checks that a key fact (a cell name, a percentage, a cause label) appears *somewhere* in the answer. | Can produce false passes — if `expected="30"` and the agent says "there were 300 drops" it passes even if the drop rate was wrong. Keep expected fragments specific. |
| **`llm_judge`** | **Not yet implemented (Phase 1).** Currently logs a warning and returns `false`. | When the correct answer is free-form and can't be reduced to a substring (e.g. "explain why this cell is degrading" — graded on reasoning quality, not keywords). | Costs a second LLM call per case and introduces the judge's own variance. Most powerful but slowest and least reproducible. |

**Rule of thumb:** start every case as `contains` with the most specific fragment you can.
Reach for `llm_judge` only when no substring can capture correctness.

---

## 3. How to run the harness

### a. Create the tables and seed the cases

Run the two SQL files in order, in pgAdmin or psql:

```bash
psql -U postgres -d victorb -f src/main/resources/db/eval_cases.sql
psql -U postgres -d victorb -f src/main/resources/db/eval_seed.sql
```

> **Note:** this app runs with `spring.jpa.hibernate.ddl-auto=update`, so Hibernate will
> actually auto-create `eval_cases` and `eval_results` from the JPA entities the first time
> you start the app. If you rely on that, you only need to run **`eval_seed.sql`** to load the
> 20 cases. The `eval_cases.sql` DDL is kept for documentation and for `ddl-auto=none`
> deployments. Either path produces the same schema (the SQL uses `BIGSERIAL`/`BIGINT` to match
> the entities' `Long` + `IDENTITY` mapping).

### b. Start the app

```bash
mvn spring-boot:run
```

### c. Trigger a run

```bash
curl -X POST "http://localhost:8080/eval/run?label=baseline"
```

Response (the `EvalRunSummary`):

```json
{
  "runLabel": "baseline",
  "total": 20,
  "passed": 14,
  "failed": 6,
  "passRate": 0.7,
  "failedCaseIds": [3, 7, 11, 13, 18, 20]
}
```

### d. See per-case results

```bash
curl "http://localhost:8080/eval/results?label=baseline"
```

Each row includes the full `agentResponse`, `passed`, `latencyMs`, and the `evalCaseId` so you
can trace it back to the question.

### e. Inspect the question set

```bash
curl "http://localhost:8080/eval/cases"
```

---

## 4. How to read the results

- **`passRate`** is `passed / total`, from `0.0` to `1.0`. This is your headline metric.
  Treat the first run as your **baseline** and compare every future change against it.
- **A failing case is a lead, not a verdict.** Pull the row from `/eval/results`, read the
  `agentResponse`, and ask *why*:
  - Did the agent fail to call the right tool? (→ system-prompt / tool-description problem)
  - Did it call the tool but phrase the answer without the expected fragment? (→ the case's
    `expected` may be too strict, or the prompt needs a formatting rule)
  - Did it throw? (`agentResponse` starts with `ERROR:` → infra/model/DB problem, not quality)
- **To add a new case**, insert a row into `eval_cases` (`agent`, `question`, `expected`,
  `score_type`, `notes`) and re-run. No code change needed — the runner loads all cases each run.

---

## 5. The LLM-judge gap

`score_type = 'llm_judge'` is **declared but not implemented**. Right now `EvalScorer` logs a
warning and returns `false` for these cases (fail-closed, so an unimplemented path can never
silently inflate the pass rate).

When implemented (Phase 1), it will: send the question, the expected answer, and the agent's
actual free-text response to a **stronger grading model**, with a prompt like *"Does this
response correctly answer the question given the expected answer? Reply PASS or FAIL with a
one-line reason."* — then parse that verdict.

Until then, **`contains` is a reasonable approximation**: for a telecom agent whose answers are
built from concrete facts (cell names, percentages, cause labels), checking that the key fact
appears in the response catches the large majority of real regressions at zero extra cost.
`llm_judge` only earns its cost for cases where correctness is about *reasoning quality* rather
than presence of a fact.

---

## 6. How to evolve the harness — eval-first discipline

The point of Phase 0 is to make quality measurable *before* you build. The workflow for any new
feature:

1. **Write the cases first.** Before adding a new tool or capability, add eval cases describing
   what a correct answer looks like. They will fail (the feature doesn't exist yet) — that's
   expected. This is red/green TDD applied to agents.
2. **Build the feature.**
3. **Re-run the harness.** The new cases should now pass, and — critically — the *old* cases
   should not regress. If `passRate` drops on cases unrelated to your change, you introduced a
   regression.
4. **Keep cases forever.** Every bug you find in production should become a new eval case so it
   can never silently come back.

---

## Known limitations

- **Shared chat memory.** The runner calls the agents through their normal `chat(String)`
  interface, which is backed by a single shared `MessageWindowChatMemory` (see `AiConfig` /
  `DropAgentConfig`). Eval calls therefore share conversational memory with each other and with
  any live `/agent/*` traffic running at the same time. Earlier cases can bias later ones and
  results aren't perfectly reproducible. **Run the baseline against an otherwise-idle instance.**
  A Phase 1 fix would give the runner its own memory-less agent instances.
- **`contains` false-positives.** A loose `expected` fragment (e.g. a bare number) can pass on a
  wrong answer. Keep fragments specific.
- **No auth.** The `/eval/*` endpoints are open. Fine for local dev; do not expose publicly.
