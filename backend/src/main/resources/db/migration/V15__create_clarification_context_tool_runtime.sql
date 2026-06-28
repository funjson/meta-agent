CREATE TABLE clarification_request (
    id BINARY(16) PRIMARY KEY,
    conversation_id BINARY(16) NOT NULL,
    job_id BINARY(16) NULL,
    task_id BINARY(16) NULL,
    task_run_id BINARY(16) NULL,
    loop_node_id BINARY(16) NULL,
    source_type VARCHAR(64) NOT NULL,
    reason_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    question TEXT NOT NULL,
    answer TEXT NULL,
    blocking_summary TEXT NOT NULL,
    max_rounds INT NOT NULL,
    current_round INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_clarification_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversation(id),
    CONSTRAINT fk_clarification_job
        FOREIGN KEY (job_id) REFERENCES job(id),
    CONSTRAINT fk_clarification_task
        FOREIGN KEY (task_id) REFERENCES task(id),
    CONSTRAINT fk_clarification_task_run
        FOREIGN KEY (task_run_id) REFERENCES task_run(id),
    CONSTRAINT fk_clarification_loop_node
        FOREIGN KEY (loop_node_id) REFERENCES loop_node(id)
);

CREATE INDEX idx_clarification_job_status
    ON clarification_request (job_id, status, created_at);

CREATE INDEX idx_clarification_source_status
    ON clarification_request (
        job_id,
        task_id,
        task_run_id,
        loop_node_id,
        status
    );

ALTER TABLE loop_node
    MODIFY action_type VARCHAR(64) NOT NULL DEFAULT 'MODEL_CALL';
