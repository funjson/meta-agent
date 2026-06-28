ALTER TABLE loop_node
    ADD COLUMN current_phase VARCHAR(40) NULL AFTER status,
    ADD COLUMN depth INT NOT NULL DEFAULT 0 AFTER current_phase,
    ADD COLUMN iteration_no INT NOT NULL DEFAULT 1 AFTER depth,
    ADD INDEX idx_loop_node_parent (loop_run_id, parent_node_id),
    ADD INDEX idx_loop_node_phase_status (loop_run_id, current_phase, status);

UPDATE loop_node
SET current_phase = CASE
    WHEN status = 'COMPLETED' THEN 'EVALUATION'
    WHEN status = 'FAILED' THEN 'EVALUATION'
    ELSE 'ACTION_EXECUTION'
END
WHERE current_phase IS NULL;

ALTER TABLE checkpoint
    ADD COLUMN loop_run_id BINARY(16) NULL AFTER task_run_id,
    ADD COLUMN loop_node_id BINARY(16) NULL AFTER loop_run_id,
    ADD CONSTRAINT fk_checkpoint_loop_run
        FOREIGN KEY (loop_run_id) REFERENCES loop_run (id),
    ADD CONSTRAINT fk_checkpoint_loop_node
        FOREIGN KEY (loop_node_id) REFERENCES loop_node (id),
    ADD INDEX idx_checkpoint_loop_node (loop_node_id, sequence_no);

ALTER TABLE evidence
    ADD COLUMN loop_run_id BINARY(16) NULL AFTER task_run_id,
    ADD COLUMN loop_node_id BINARY(16) NULL AFTER loop_run_id,
    ADD CONSTRAINT fk_evidence_loop_run
        FOREIGN KEY (loop_run_id) REFERENCES loop_run (id),
    ADD CONSTRAINT fk_evidence_loop_node
        FOREIGN KEY (loop_node_id) REFERENCES loop_node (id),
    ADD INDEX idx_evidence_loop_node (loop_node_id, created_at);

CREATE TABLE loop_node_phase (
    id BINARY(16) NOT NULL,
    loop_node_id BINARY(16) NOT NULL,
    phase_type VARCHAR(40) NOT NULL,
    sequence_no INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    input_json JSON NOT NULL,
    output_json JSON NULL,
    version BIGINT NOT NULL DEFAULT 0,
    started_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_loop_node_phase_node
        FOREIGN KEY (loop_node_id) REFERENCES loop_node (id),
    UNIQUE KEY uq_loop_node_phase_sequence (loop_node_id, sequence_no),
    INDEX idx_loop_node_phase_type (loop_node_id, phase_type)
);
