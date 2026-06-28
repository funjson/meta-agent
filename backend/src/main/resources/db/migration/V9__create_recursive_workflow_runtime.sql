ALTER TABLE loop_run
    ADD COLUMN scoped_context_json JSON NULL AFTER policy_json,
    ADD COLUMN recursion_depth INT NOT NULL DEFAULT 0 AFTER scoped_context_json;

UPDATE loop_run
SET scoped_context_json = JSON_OBJECT()
WHERE scoped_context_json IS NULL;

ALTER TABLE loop_run
    MODIFY scoped_context_json JSON NOT NULL;

CREATE TABLE workflow_definition (
    id VARCHAR(100) NOT NULL,
    version INT NOT NULL,
    name VARCHAR(180) NOT NULL,
    source_capability_id VARCHAR(120) NULL,
    definition_json JSON NOT NULL,
    checksum CHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id, version),
    UNIQUE KEY uq_workflow_definition_checksum (checksum)
);

CREATE TABLE workflow_run (
    id BINARY(16) NOT NULL,
    task_run_id BINARY(16) NOT NULL,
    definition_id VARCHAR(100) NOT NULL,
    definition_version INT NOT NULL,
    origin_loop_run_id BINARY(16) NULL,
    origin_loop_node_id BINARY(16) NULL,
    parent_execution_type VARCHAR(40) NOT NULL,
    parent_execution_id BINARY(16) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    recursion_depth INT NOT NULL,
    status VARCHAR(40) NOT NULL,
    current_instance_id BINARY(16) NULL,
    policy_json JSON NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    started_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_workflow_run_task_run
        FOREIGN KEY (task_run_id) REFERENCES task_run (id),
    CONSTRAINT fk_workflow_run_definition
        FOREIGN KEY (definition_id, definition_version)
        REFERENCES workflow_definition (id, version),
    CONSTRAINT fk_workflow_run_origin_loop_run
        FOREIGN KEY (origin_loop_run_id) REFERENCES loop_run (id),
    CONSTRAINT fk_workflow_run_origin_loop_node
        FOREIGN KEY (origin_loop_node_id) REFERENCES loop_node (id),
    INDEX idx_workflow_run_task_status (task_run_id, status),
    INDEX idx_workflow_run_origin (origin_loop_node_id, recursion_depth),
    UNIQUE KEY uq_workflow_run_idempotency (idempotency_key)
);

CREATE TABLE workflow_instance (
    id BINARY(16) NOT NULL,
    workflow_run_id BINARY(16) NOT NULL,
    definition_version INT NOT NULL,
    status VARCHAR(40) NOT NULL,
    current_stage_run_id BINARY(16) NULL,
    state_json JSON NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    started_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_workflow_instance_run
        FOREIGN KEY (workflow_run_id) REFERENCES workflow_run (id),
    INDEX idx_workflow_instance_run_status (workflow_run_id, status)
);

CREATE TABLE workflow_stage_run (
    id BINARY(16) NOT NULL,
    workflow_instance_id BINARY(16) NOT NULL,
    stage_key VARCHAR(100) NOT NULL,
    sequence_no INT NOT NULL,
    status VARCHAR(40) NOT NULL,
    goal TEXT NOT NULL,
    loop_run_id BINARY(16) NULL,
    input_json JSON NOT NULL,
    output_json JSON NULL,
    version BIGINT NOT NULL DEFAULT 0,
    started_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_workflow_stage_instance
        FOREIGN KEY (workflow_instance_id) REFERENCES workflow_instance (id),
    CONSTRAINT fk_workflow_stage_loop_run
        FOREIGN KEY (loop_run_id) REFERENCES loop_run (id),
    UNIQUE KEY uq_workflow_stage_sequence (
        workflow_instance_id,
        sequence_no
    ),
    UNIQUE KEY uq_workflow_stage_key (
        workflow_instance_id,
        stage_key
    ),
    INDEX idx_workflow_stage_status (
        workflow_instance_id,
        status
    )
);

ALTER TABLE workflow_run
    ADD CONSTRAINT fk_workflow_run_current_instance
        FOREIGN KEY (current_instance_id) REFERENCES workflow_instance (id);

ALTER TABLE workflow_instance
    ADD CONSTRAINT fk_workflow_instance_current_stage
        FOREIGN KEY (current_stage_run_id) REFERENCES workflow_stage_run (id);
