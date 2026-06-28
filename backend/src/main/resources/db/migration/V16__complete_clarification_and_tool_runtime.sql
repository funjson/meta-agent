ALTER TABLE clarification_request
    ADD COLUMN answer_message_id BINARY(16) NULL AFTER answer,
    ADD COLUMN resolution_json JSON NULL AFTER answer_message_id,
    ADD COLUMN resolved_at DATETIME(6) NULL AFTER resolution_json,
    ADD CONSTRAINT fk_clarification_answer_message
        FOREIGN KEY (answer_message_id) REFERENCES message(id);

CREATE INDEX idx_clarification_conversation_status
    ON clarification_request (conversation_id, status, created_at);

CREATE TABLE tool_invocation (
    id BINARY(16) NOT NULL,
    job_id BINARY(16) NULL,
    task_id BINARY(16) NULL,
    task_run_id BINARY(16) NULL,
    loop_run_id BINARY(16) NULL,
    loop_node_id BINARY(16) NULL,
    tool_id VARCHAR(160) NOT NULL,
    tool_type VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(180) NOT NULL,
    arguments_json JSON NOT NULL,
    status VARCHAR(40) NOT NULL,
    result_json JSON NULL,
    error_message TEXT NULL,
    clarification_request_id BINARY(16) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    started_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_tool_invocation_idempotency (idempotency_key),
    INDEX idx_tool_invocation_loop_node (
        loop_node_id,
        created_at
    ),
    INDEX idx_tool_invocation_task_run_status (
        task_run_id,
        status,
        created_at
    ),
    CONSTRAINT fk_tool_invocation_job
        FOREIGN KEY (job_id) REFERENCES job(id),
    CONSTRAINT fk_tool_invocation_task
        FOREIGN KEY (task_id) REFERENCES task(id),
    CONSTRAINT fk_tool_invocation_task_run
        FOREIGN KEY (task_run_id) REFERENCES task_run(id),
    CONSTRAINT fk_tool_invocation_loop_run
        FOREIGN KEY (loop_run_id) REFERENCES loop_run(id),
    CONSTRAINT fk_tool_invocation_loop_node
        FOREIGN KEY (loop_node_id) REFERENCES loop_node(id),
    CONSTRAINT fk_tool_invocation_clarification
        FOREIGN KEY (clarification_request_id)
        REFERENCES clarification_request(id)
);
