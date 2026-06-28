CREATE TABLE control_turn (
    id BINARY(16) NOT NULL,
    conversation_id BINARY(16) NOT NULL,
    source_message_id BINARY(16) NOT NULL,
    idempotency_key VARCHAR(140) NOT NULL,
    status VARCHAR(40) NOT NULL,
    job_id BINARY(16) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_control_turn_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversation (id),
    CONSTRAINT fk_control_turn_source_message
        FOREIGN KEY (source_message_id) REFERENCES message (id),
    CONSTRAINT fk_control_turn_job
        FOREIGN KEY (job_id) REFERENCES job (id),
    UNIQUE KEY uq_control_turn_source_message (source_message_id),
    UNIQUE KEY uq_control_turn_idempotency (idempotency_key),
    INDEX idx_control_turn_conversation_created (
        conversation_id,
        created_at
    )
);

INSERT INTO control_turn (
    id,
    conversation_id,
    source_message_id,
    idempotency_key,
    status,
    job_id,
    created_at,
    updated_at
)
SELECT
    decision_record.id,
    decision_record.conversation_id,
    decision_record.source_message_id,
    COALESCE(
        source_message.idempotency_key,
        CONCAT(
            'legacy-control-turn:',
            LOWER(HEX(decision_record.source_message_id))
        )
    ),
    'COMPLETED',
    decision_record.job_id,
    decision_record.created_at,
    decision_record.created_at
FROM control_decision decision_record
JOIN message source_message
  ON source_message.id = decision_record.source_message_id;

ALTER TABLE control_decision
    ADD COLUMN control_turn_id BINARY(16) NULL AFTER id;

UPDATE control_decision
SET control_turn_id = id
WHERE control_turn_id IS NULL;

ALTER TABLE control_decision
    MODIFY control_turn_id BINARY(16) NOT NULL,
    ADD CONSTRAINT fk_control_decision_turn
        FOREIGN KEY (control_turn_id) REFERENCES control_turn (id),
    ADD UNIQUE KEY uq_control_decision_turn (control_turn_id);

CREATE TABLE task_run_dispatch (
    id BINARY(16) NOT NULL,
    job_id BINARY(16) NOT NULL,
    task_id BINARY(16) NOT NULL,
    task_run_id BINARY(16) NOT NULL,
    status VARCHAR(30) NOT NULL,
    worker_id VARCHAR(120) NULL,
    available_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    claimed_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_task_run_dispatch_job
        FOREIGN KEY (job_id) REFERENCES job (id),
    CONSTRAINT fk_task_run_dispatch_task
        FOREIGN KEY (task_id) REFERENCES task (id),
    CONSTRAINT fk_task_run_dispatch_run
        FOREIGN KEY (task_run_id) REFERENCES task_run (id),
    UNIQUE KEY uq_task_run_dispatch_run (task_run_id),
    INDEX idx_task_run_dispatch_claim (
        status,
        available_at,
        created_at
    ),
    INDEX idx_task_run_dispatch_job_status (job_id, status)
);

CREATE TABLE skill_package (
    id VARCHAR(100) NOT NULL,
    version INT NOT NULL,
    name VARCHAR(180) NOT NULL,
    manifest_checksum CHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id, version),
    UNIQUE KEY uq_skill_package_checksum (id, manifest_checksum)
);

INSERT INTO skill_package (
    id,
    version,
    name,
    manifest_checksum,
    status,
    created_at
)
SELECT
    id,
    version,
    name,
    content_hash,
    status,
    created_at
FROM capability_source;

CREATE TABLE skill_resource (
    package_id VARCHAR(100) NOT NULL,
    package_version INT NOT NULL,
    resource_path VARCHAR(500) NOT NULL,
    resource_type VARCHAR(30) NOT NULL,
    content_hash CHAR(64) NOT NULL,
    content_text LONGTEXT NULL,
    executable_json JSON NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (
        package_id,
        package_version,
        resource_path
    ),
    CONSTRAINT fk_skill_resource_package
        FOREIGN KEY (package_id, package_version)
        REFERENCES skill_package (id, version),
    INDEX idx_skill_resource_type (
        package_id,
        package_version,
        resource_type
    )
);

CREATE TABLE subagent_profile (
    id VARCHAR(100) NOT NULL,
    agent_profile_id VARCHAR(80) NOT NULL,
    version INT NOT NULL,
    name VARCHAR(180) NOT NULL,
    role_prompt TEXT NOT NULL,
    model_policy_json JSON NOT NULL,
    skill_refs_json JSON NOT NULL,
    tool_allowlist_json JSON NOT NULL,
    authority_json JSON NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id, version),
    CONSTRAINT fk_subagent_profile_agent
        FOREIGN KEY (agent_profile_id) REFERENCES agent_profile (id),
    INDEX idx_subagent_profile_agent_status (
        agent_profile_id,
        status
    )
);

ALTER TABLE job
    ADD COLUMN subagent_profile_id VARCHAR(100) NULL
        AFTER template_version,
    ADD COLUMN subagent_profile_version INT NULL
        AFTER subagent_profile_id,
    ADD CONSTRAINT fk_job_subagent_profile
        FOREIGN KEY (subagent_profile_id, subagent_profile_version)
        REFERENCES subagent_profile (id, version),
    ADD INDEX idx_job_subagent_profile (
        subagent_profile_id,
        subagent_profile_version
    );
