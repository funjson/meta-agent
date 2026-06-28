CREATE TABLE task_run (
    id BINARY(16) NOT NULL,
    task_id BINARY(16) NOT NULL,
    run_type VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    attempt_no INT NOT NULL,
    latest_checkpoint_id BINARY(16) NULL,
    result_summary TEXT NULL,
    lease_owner VARCHAR(120) NULL,
    lease_until DATETIME(6) NULL,
    heartbeat_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    started_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_task_run_task FOREIGN KEY (task_id) REFERENCES task (id),
    UNIQUE KEY uq_task_run_attempt (task_id, attempt_no),
    INDEX idx_task_run_status_lease (status, lease_until)
);

CREATE TABLE loop_run (
    id BINARY(16) NOT NULL,
    task_run_id BINARY(16) NOT NULL,
    parent_type VARCHAR(40) NOT NULL,
    parent_id BINARY(16) NULL,
    status VARCHAR(40) NOT NULL,
    root_node_id BINARY(16) NULL,
    policy_json JSON NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    started_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_loop_run_task_run FOREIGN KEY (task_run_id) REFERENCES task_run (id),
    INDEX idx_loop_run_task_run (task_run_id)
);

CREATE TABLE loop_node (
    id BINARY(16) NOT NULL,
    loop_run_id BINARY(16) NOT NULL,
    parent_node_id BINARY(16) NULL,
    node_type VARCHAR(40) NOT NULL,
    action_type VARCHAR(80) NOT NULL,
    goal TEXT NOT NULL,
    status VARCHAR(40) NOT NULL,
    side_effect_class VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    input_json JSON NOT NULL,
    decision_json JSON NULL,
    observation_json JSON NULL,
    output_json JSON NULL,
    version BIGINT NOT NULL DEFAULT 0,
    started_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_loop_node_loop_run FOREIGN KEY (loop_run_id) REFERENCES loop_run (id),
    CONSTRAINT fk_loop_node_parent FOREIGN KEY (parent_node_id) REFERENCES loop_node (id),
    UNIQUE KEY uq_loop_node_idempotency (idempotency_key),
    INDEX idx_loop_node_run_status (loop_run_id, status)
);

CREATE TABLE checkpoint (
    id BINARY(16) NOT NULL,
    task_run_id BINARY(16) NOT NULL,
    sequence_no BIGINT NOT NULL,
    checkpoint_type VARCHAR(50) NOT NULL,
    state_json JSON NOT NULL,
    event_offset BIGINT NOT NULL,
    checksum CHAR(64) NOT NULL,
    restorable BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_checkpoint_task_run FOREIGN KEY (task_run_id) REFERENCES task_run (id),
    UNIQUE KEY uq_checkpoint_task_run_sequence (task_run_id, sequence_no)
);

CREATE TABLE evidence (
    id BINARY(16) NOT NULL,
    task_id BINARY(16) NOT NULL,
    task_run_id BINARY(16) NOT NULL,
    evidence_type VARCHAR(80) NOT NULL,
    subject_ref VARCHAR(200) NOT NULL,
    result VARCHAR(30) NOT NULL,
    details_json JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_evidence_task FOREIGN KEY (task_id) REFERENCES task (id),
    CONSTRAINT fk_evidence_task_run FOREIGN KEY (task_run_id) REFERENCES task_run (id),
    INDEX idx_evidence_task_type (task_id, evidence_type)
);

ALTER TABLE task
    ADD COLUMN active_task_run_id BINARY(16) NULL,
    ADD CONSTRAINT fk_task_active_run FOREIGN KEY (active_task_run_id) REFERENCES task_run (id);

ALTER TABLE task_run
    ADD CONSTRAINT fk_task_run_latest_checkpoint
        FOREIGN KEY (latest_checkpoint_id) REFERENCES checkpoint (id);

ALTER TABLE loop_run
    ADD CONSTRAINT fk_loop_run_root_node
        FOREIGN KEY (root_node_id) REFERENCES loop_node (id);

