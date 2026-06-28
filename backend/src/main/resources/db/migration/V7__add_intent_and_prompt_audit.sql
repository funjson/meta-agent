ALTER TABLE control_decision
    ADD COLUMN confidence DECIMAL(5,4) NOT NULL DEFAULT 0.5000,
    ADD COLUMN classifier VARCHAR(80) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN requires_clarification BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN compound_task BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN risk_level VARCHAR(20) NOT NULL DEFAULT 'MEDIUM';

ALTER TABLE model_call
    ADD COLUMN prompt_id VARCHAR(120) NULL,
    ADD COLUMN prompt_version VARCHAR(40) NULL,
    ADD COLUMN prompt_hash CHAR(64) NULL,
    ADD INDEX idx_model_call_prompt (prompt_id, prompt_version);
