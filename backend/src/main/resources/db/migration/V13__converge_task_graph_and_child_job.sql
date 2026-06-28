CREATE TABLE task_graph_template (
    id BINARY(16) NOT NULL,
    agent_profile_id VARCHAR(80) NOT NULL,
    template_key VARCHAR(100) NOT NULL,
    version INT NOT NULL,
    name VARCHAR(180) NOT NULL,
    intent_tags_json JSON NOT NULL,
    graph_json JSON NOT NULL,
    checksum CHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id, version),
    CONSTRAINT fk_task_graph_template_profile
        FOREIGN KEY (agent_profile_id) REFERENCES agent_profile (id),
    UNIQUE KEY uq_task_graph_template_profile_key_version (
        agent_profile_id,
        template_key,
        version
    ),
    UNIQUE KEY uq_task_graph_template_checksum (
        agent_profile_id,
        checksum
    ),
    INDEX idx_task_graph_template_match (
        agent_profile_id,
        status,
        template_key
    )
);

ALTER TABLE job
    ADD COLUMN parent_job_id BINARY(16) NULL AFTER id,
    ADD COLUMN root_job_id BINARY(16) NULL AFTER parent_job_id,
    ADD COLUMN recursion_depth INT NOT NULL DEFAULT 0 AFTER root_job_id,
    ADD COLUMN template_id BINARY(16) NULL AFTER recursion_depth,
    ADD COLUMN template_version INT NULL AFTER template_id,
    ADD COLUMN effective_policy_snapshot JSON NULL AFTER policy_json,
    ADD COLUMN effective_policy_hash CHAR(64) NULL AFTER effective_policy_snapshot;

UPDATE job
SET root_job_id = id,
    effective_policy_snapshot = policy_json,
    effective_policy_hash = LOWER(
        SHA2(CAST(CAST(policy_json AS JSON) AS CHAR), 256)
    )
WHERE root_job_id IS NULL;

ALTER TABLE job
    MODIFY root_job_id BINARY(16) NOT NULL,
    MODIFY effective_policy_snapshot JSON NOT NULL,
    MODIFY effective_policy_hash CHAR(64) NOT NULL,
    ADD CONSTRAINT fk_job_parent
        FOREIGN KEY (parent_job_id) REFERENCES job (id),
    ADD CONSTRAINT fk_job_root
        FOREIGN KEY (root_job_id) REFERENCES job (id),
    ADD CONSTRAINT fk_job_task_graph_template
        FOREIGN KEY (template_id, template_version)
        REFERENCES task_graph_template (id, version),
    ADD INDEX idx_job_root_parent (root_job_id, parent_job_id),
    ADD INDEX idx_job_parent_status (parent_job_id, status);

CREATE TABLE job_derivation (
    id BINARY(16) NOT NULL,
    parent_job_id BINARY(16) NOT NULL,
    child_job_id BINARY(16) NULL,
    origin_task_run_id BINARY(16) NOT NULL,
    origin_loop_node_id BINARY(16) NOT NULL,
    idempotency_key VARCHAR(180) NOT NULL,
    source_skill_id VARCHAR(100) NULL,
    source_skill_version INT NULL,
    status VARCHAR(40) NOT NULL,
    request_json JSON NOT NULL,
    outcome_json JSON NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_job_derivation_parent
        FOREIGN KEY (parent_job_id) REFERENCES job (id),
    CONSTRAINT fk_job_derivation_child
        FOREIGN KEY (child_job_id) REFERENCES job (id),
    CONSTRAINT fk_job_derivation_task_run
        FOREIGN KEY (origin_task_run_id) REFERENCES task_run (id),
    CONSTRAINT fk_job_derivation_loop_node
        FOREIGN KEY (origin_loop_node_id) REFERENCES loop_node (id),
    UNIQUE KEY uq_job_derivation_idempotency (idempotency_key),
    UNIQUE KEY uq_job_derivation_child (child_job_id),
    INDEX idx_job_derivation_parent_status (parent_job_id, status)
);

CREATE TABLE authorization_request (
    id BINARY(16) NOT NULL,
    job_id BINARY(16) NOT NULL,
    task_run_id BINARY(16) NULL,
    loop_node_id BINARY(16) NULL,
    request_type VARCHAR(50) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    source_id VARCHAR(180) NOT NULL,
    requested_delta_json JSON NOT NULL,
    status VARCHAR(30) NOT NULL,
    decision_json JSON NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    decided_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_authorization_request_job
        FOREIGN KEY (job_id) REFERENCES job (id),
    CONSTRAINT fk_authorization_request_task_run
        FOREIGN KEY (task_run_id) REFERENCES task_run (id),
    CONSTRAINT fk_authorization_request_loop_node
        FOREIGN KEY (loop_node_id) REFERENCES loop_node (id),
    INDEX idx_authorization_request_status_created (
        status,
        created_at
    )
);

ALTER TABLE loop_node
    ADD COLUMN active_child_job_id BINARY(16) NULL AFTER parent_node_id,
    ADD CONSTRAINT fk_loop_node_active_child_job
        FOREIGN KEY (active_child_job_id) REFERENCES job (id);

ALTER TABLE capability_source
    ADD COLUMN raw_content LONGTEXT NULL AFTER scope_type,
    ADD COLUMN compiled_manifest_json JSON NULL AFTER raw_content,
    ADD COLUMN compiler_prompt_id VARCHAR(120) NULL AFTER compiled_manifest_json,
    ADD COLUMN compiler_prompt_version INT NULL AFTER compiler_prompt_id,
    ADD COLUMN content_hash CHAR(64) NULL AFTER compiler_prompt_version,
    ADD COLUMN compiled_at DATETIME(6) NULL AFTER content_hash;

UPDATE capability_source
SET raw_content = CAST(descriptor_json AS CHAR),
    compiled_manifest_json = descriptor_json,
    compiler_prompt_id = 'legacy-json-capability-import',
    compiler_prompt_version = 1,
    content_hash = checksum,
    compiled_at = created_at
WHERE raw_content IS NULL;

ALTER TABLE capability_source
    MODIFY raw_content LONGTEXT NOT NULL,
    MODIFY compiled_manifest_json JSON NOT NULL,
    MODIFY compiler_prompt_id VARCHAR(120) NOT NULL,
    MODIFY compiler_prompt_version INT NOT NULL,
    MODIFY content_hash CHAR(64) NOT NULL,
    MODIFY compiled_at DATETIME(6) NOT NULL;

ALTER TABLE workflow_instance
    DROP FOREIGN KEY fk_workflow_instance_current_stage;

ALTER TABLE workflow_run
    DROP FOREIGN KEY fk_workflow_run_current_instance;

DROP TABLE workflow_stage_run;
DROP TABLE workflow_instance;
DROP TABLE workflow_run;
DROP TABLE workflow_definition;
