CREATE TABLE job (
    id BINARY(16) NOT NULL,
    original_request TEXT NOT NULL,
    goal_summary VARCHAR(500) NOT NULL,
    status VARCHAR(40) NOT NULL,
    policy_json JSON NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_job_status_updated_at (status, updated_at)
);

CREATE TABLE task (
    id BINARY(16) NOT NULL,
    job_id BINARY(16) NOT NULL,
    title VARCHAR(300) NOT NULL,
    goal TEXT NOT NULL,
    task_type VARCHAR(100) NOT NULL,
    status VARCHAR(40) NOT NULL,
    execution_mode VARCHAR(40) NOT NULL,
    contract_json JSON NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_task_job FOREIGN KEY (job_id) REFERENCES job (id),
    INDEX idx_task_job_status (job_id, status)
);

CREATE TABLE runtime_event (
    id BINARY(16) NOT NULL,
    job_id BINARY(16) NULL,
    task_id BINARY(16) NULL,
    task_run_id BINARY(16) NULL,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id BINARY(16) NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    payload_json JSON NOT NULL,
    trace_id VARCHAR(64) NULL,
    sequence_no BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_runtime_event_aggregate_sequence (aggregate_id, sequence_no),
    INDEX idx_runtime_event_task_run_created_at (task_run_id, created_at),
    INDEX idx_runtime_event_job_created_at (job_id, created_at)
);

CREATE TABLE outbox_event (
    id BINARY(16) NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    available_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_outbox_status_available_at (status, available_at)
);

CREATE TABLE command_deduplication (
    idempotency_key VARCHAR(100) NOT NULL,
    command_type VARCHAR(100) NOT NULL,
    resource_id BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (idempotency_key),
    INDEX idx_command_dedup_created_at (created_at)
);

