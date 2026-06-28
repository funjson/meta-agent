ALTER TABLE loop_node
    ADD COLUMN scoped_context_json JSON NULL AFTER input_json;

UPDATE loop_node
SET scoped_context_json = JSON_OBJECT()
WHERE scoped_context_json IS NULL;

ALTER TABLE loop_node
    MODIFY scoped_context_json JSON NOT NULL;

CREATE TABLE capability_source (
    id VARCHAR(100) NOT NULL,
    version INT NOT NULL,
    name VARCHAR(180) NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    adapter_id VARCHAR(100) NOT NULL,
    capability_type VARCHAR(40) NOT NULL,
    scope_type VARCHAR(40) NOT NULL,
    descriptor_json JSON NOT NULL,
    checksum CHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id, version),
    UNIQUE KEY uq_capability_source_checksum (id, checksum)
);

ALTER TABLE workflow_stage_run
    ADD COLUMN capability_id VARCHAR(100) NULL AFTER goal,
    ADD COLUMN capability_version INT NULL AFTER capability_id,
    ADD CONSTRAINT fk_workflow_stage_capability
        FOREIGN KEY (capability_id, capability_version)
        REFERENCES capability_source (id, version);

CREATE TABLE capability_load (
    id BINARY(16) NOT NULL,
    task_run_id BINARY(16) NOT NULL,
    loop_run_id BINARY(16) NOT NULL,
    loop_node_id BINARY(16) NOT NULL,
    source_id VARCHAR(100) NOT NULL,
    source_version INT NOT NULL,
    adapter_id VARCHAR(100) NOT NULL,
    capability_type VARCHAR(40) NOT NULL,
    scope_root_type VARCHAR(40) NOT NULL,
    scope_root_id BINARY(16) NOT NULL,
    inherited_from_load_id BINARY(16) NULL,
    descriptor_json JSON NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    applied_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_capability_load_task_run
        FOREIGN KEY (task_run_id) REFERENCES task_run (id),
    CONSTRAINT fk_capability_load_loop_run
        FOREIGN KEY (loop_run_id) REFERENCES loop_run (id),
    CONSTRAINT fk_capability_load_loop_node
        FOREIGN KEY (loop_node_id) REFERENCES loop_node (id),
    CONSTRAINT fk_capability_load_source
        FOREIGN KEY (source_id, source_version)
        REFERENCES capability_source (id, version),
    CONSTRAINT fk_capability_load_parent
        FOREIGN KEY (inherited_from_load_id) REFERENCES capability_load (id),
    UNIQUE KEY uq_capability_load_node_source (
        loop_node_id,
        source_id,
        source_version
    ),
    INDEX idx_capability_load_scope (
        scope_root_type,
        scope_root_id,
        status
    )
);

INSERT INTO capability_source (
    id, version, name, source_type, adapter_id,
    capability_type, scope_type, descriptor_json, checksum, status
) VALUES (
    'general-runtime-policy',
    1,
    'General Runtime Policy',
    'CONFIG',
    'json-capability-v1',
    'POLICY',
    'LOOP_NODE_SUBTREE',
    JSON_OBJECT(
        'instructions',
        JSON_ARRAY(
            '优先给出与当前目标直接相关、可验证的结果。',
            '不得暴露密钥、隐藏思维链或内部敏感配置。'
        ),
        'policy',
        JSON_OBJECT(
            'requireEvidence',
            TRUE
        )
    ),
    LOWER(SHA2(
        CAST(JSON_OBJECT(
            'instructions',
            JSON_ARRAY(
                '优先给出与当前目标直接相关、可验证的结果。',
                '不得暴露密钥、隐藏思维链或内部敏感配置。'
            ),
            'policy',
            JSON_OBJECT(
                'requireEvidence',
                TRUE
            )
        ) AS CHAR),
        256
    )),
    'ACTIVE'
);

UPDATE agent_profile
SET config_json = JSON_SET(
        config_json,
        '$.execution',
        JSON_OBJECT(
            'rootCapability',
            JSON_OBJECT(
                'id',
                'general-runtime-policy',
                'version',
                1
            )
        )
    ),
    version = version + 1
WHERE id = 'general-agent';
