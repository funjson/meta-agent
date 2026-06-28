CREATE TABLE recovery_attempt (
    id BINARY(16) NOT NULL,
    task_run_id BINARY(16) NOT NULL,
    checkpoint_id BINARY(16) NULL,
    interruption_type VARCHAR(50) NOT NULL,
    disposition VARCHAR(50) NOT NULL,
    decision_code VARCHAR(100) NOT NULL,
    status VARCHAR(40) NOT NULL,
    context_json JSON NOT NULL,
    requested_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_recovery_attempt_task_run
        FOREIGN KEY (task_run_id) REFERENCES task_run (id),
    CONSTRAINT fk_recovery_attempt_checkpoint
        FOREIGN KEY (checkpoint_id) REFERENCES checkpoint (id),
    INDEX idx_recovery_attempt_task_time (
        task_run_id,
        requested_at
    )
);
