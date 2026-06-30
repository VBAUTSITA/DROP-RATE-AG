-- Run this once in pgAdmin or psql before starting the app.
-- psql -U postgres -d victorb -f src/main/resources/db/eval_cases.sql
--
-- NOTE: this project runs with spring.jpa.hibernate.ddl-auto=update, so Hibernate will
-- also auto-create these tables from the EvalCase / EvalResult entities on startup. These
-- DDL statements are kept (a) as documentation of the intended schema and (b) so the harness
-- still works on an instance configured with ddl-auto=none. Column types here (BIGSERIAL,
-- BIGINT) deliberately match the JPA Long + GenerationType.IDENTITY mapping so the two paths
-- agree. If you let Hibernate create the tables, you can skip this file and run only
-- eval_seed.sql.

CREATE TABLE IF NOT EXISTS eval_cases (
    id          BIGSERIAL PRIMARY KEY,
    agent       VARCHAR(20)  NOT NULL,  -- 'drops' or 'telecom'
    question    TEXT         NOT NULL,
    expected    TEXT         NOT NULL,  -- the ground-truth answer fragment
    score_type  VARCHAR(20)  NOT NULL,  -- 'exact', 'contains', or 'llm_judge'
    notes       TEXT,                   -- why this case exists
    created_at  TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS eval_results (
    id             BIGSERIAL PRIMARY KEY,
    eval_case_id   BIGINT,              -- FK reference to eval_cases.id
    agent_response TEXT,                -- full raw response from the agent
    passed         BOOLEAN,
    score_type     VARCHAR(20),         -- copied from the case at run time
    latency_ms     BIGINT,
    run_label      VARCHAR(255),        -- free label for this run, e.g. 'baseline-2026-06-29'
    created_at     TIMESTAMP DEFAULT NOW(),
    CONSTRAINT fk_eval_results_case
        FOREIGN KEY (eval_case_id) REFERENCES eval_cases (id)
);

CREATE INDEX IF NOT EXISTS idx_eval_results_run_label ON eval_results (run_label);
CREATE INDEX IF NOT EXISTS idx_eval_cases_agent       ON eval_cases (agent);
