ALTER TABLE task
    ADD COLUMN task_key VARCHAR(50) NOT NULL DEFAULT 'task-1'
        AFTER job_id,
    ADD COLUMN sequence_no INT NOT NULL DEFAULT 1
        AFTER task_key,
    ADD UNIQUE KEY uq_task_job_key (job_id, task_key),
    ADD UNIQUE KEY uq_task_job_sequence (job_id, sequence_no);

CREATE TABLE task_dependency (
    task_id BINARY(16) NOT NULL,
    depends_on_task_id BINARY(16) NOT NULL,
    dependency_type VARCHAR(30) NOT NULL DEFAULT 'HARD',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (task_id, depends_on_task_id),
    CONSTRAINT fk_task_dependency_task
        FOREIGN KEY (task_id) REFERENCES task (id),
    CONSTRAINT fk_task_dependency_parent
        FOREIGN KEY (depends_on_task_id) REFERENCES task (id),
    INDEX idx_task_dependency_parent (depends_on_task_id)
);

ALTER TABLE control_decision
    ADD COLUMN task_graph_json JSON NULL
        AFTER constraints_json;
