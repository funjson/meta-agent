CREATE TABLE agent_profile (
    id VARCHAR(80) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(500) NOT NULL,
    default_provider_id VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    config_json JSON NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id)
);

INSERT INTO agent_profile (
    id, name, description, default_provider_id, status, config_json
) VALUES (
    'general-agent',
    'General Agent',
    '通用任务 Agent，负责从对话初始化任务并交给 Loop Kernel 执行。',
    'auto',
    'ACTIVE',
    JSON_OBJECT(
        'intentPolicy', 'CONTROL_BASELINE_V1',
        'completionPolicy', JSON_OBJECT('requireEvidence', TRUE)
    )
);

CREATE TABLE conversation (
    id BINARY(16) NOT NULL,
    agent_profile_id VARCHAR(80) NOT NULL,
    title VARCHAR(300) NOT NULL,
    status VARCHAR(30) NOT NULL,
    default_provider_id VARCHAR(50) NOT NULL,
    active_job_id BINARY(16) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_conversation_agent_profile
        FOREIGN KEY (agent_profile_id) REFERENCES agent_profile (id),
    INDEX idx_conversation_updated_at (updated_at)
);

CREATE TABLE message (
    id BINARY(16) NOT NULL,
    conversation_id BINARY(16) NOT NULL,
    role VARCHAR(30) NOT NULL,
    message_type VARCHAR(40) NOT NULL,
    content TEXT NOT NULL,
    idempotency_key VARCHAR(140) NULL,
    job_id BINARY(16) NULL,
    task_run_id BINARY(16) NULL,
    metadata_json JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_message_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversation (id),
    UNIQUE KEY uq_message_idempotency_key (idempotency_key),
    INDEX idx_message_conversation_created_at (conversation_id, created_at)
);

CREATE TABLE control_decision (
    id BINARY(16) NOT NULL,
    conversation_id BINARY(16) NOT NULL,
    source_message_id BINARY(16) NOT NULL,
    job_id BINARY(16) NULL,
    intent_type VARCHAR(60) NOT NULL,
    goal_summary VARCHAR(500) NOT NULL,
    decision_summary VARCHAR(1000) NOT NULL,
    constraints_json JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_control_decision_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversation (id),
    CONSTRAINT fk_control_decision_source_message
        FOREIGN KEY (source_message_id) REFERENCES message (id),
    INDEX idx_control_decision_conversation_created_at (conversation_id, created_at)
);

ALTER TABLE job
    ADD COLUMN agent_profile_id VARCHAR(80) NULL,
    ADD COLUMN conversation_id BINARY(16) NULL,
    ADD COLUMN source_message_id BINARY(16) NULL,
    ADD CONSTRAINT fk_job_agent_profile
        FOREIGN KEY (agent_profile_id) REFERENCES agent_profile (id),
    ADD CONSTRAINT fk_job_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversation (id),
    ADD INDEX idx_job_conversation_created_at (conversation_id, created_at);

ALTER TABLE control_decision
    ADD CONSTRAINT fk_control_decision_job
        FOREIGN KEY (job_id) REFERENCES job (id);

ALTER TABLE message
    ADD CONSTRAINT fk_message_job
        FOREIGN KEY (job_id) REFERENCES job (id),
    ADD CONSTRAINT fk_message_task_run
        FOREIGN KEY (task_run_id) REFERENCES task_run (id);

ALTER TABLE conversation
    ADD CONSTRAINT fk_conversation_active_job
        FOREIGN KEY (active_job_id) REFERENCES job (id);
