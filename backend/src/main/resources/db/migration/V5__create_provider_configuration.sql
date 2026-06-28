CREATE TABLE provider_config (
    id VARCHAR(50) NOT NULL,
    provider_type VARCHAR(50) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    model_name VARCHAR(120) NOT NULL,
    secret_source VARCHAR(30) NOT NULL,
    enabled BOOLEAN NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id)
);

INSERT INTO provider_config (
    id, provider_type, display_name, base_url, model_name, secret_source, enabled
) VALUES (
    'deepseek', 'DEEPSEEK', 'DeepSeek', 'https://api.deepseek.com',
    'deepseek-v4-flash', 'ENVIRONMENT_OR_NONE', TRUE
);

CREATE TABLE model_call (
    id BINARY(16) NOT NULL,
    task_run_id BINARY(16) NULL,
    loop_node_id BINARY(16) NULL,
    provider VARCHAR(50) NOT NULL,
    model VARCHAR(120) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    prompt_tokens INT NULL,
    completion_tokens INT NULL,
    latency_ms BIGINT NOT NULL,
    error_code VARCHAR(80) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_model_call_task_run FOREIGN KEY (task_run_id) REFERENCES task_run (id),
    CONSTRAINT fk_model_call_loop_node FOREIGN KEY (loop_node_id) REFERENCES loop_node (id),
    INDEX idx_model_call_task_run_created_at (task_run_id, created_at)
);

