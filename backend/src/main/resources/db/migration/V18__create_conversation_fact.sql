CREATE TABLE conversation_fact (
    id BINARY(16) PRIMARY KEY,
    conversation_id BINARY(16) NOT NULL,
    source_message_id BINARY(16) NULL,
    source_type VARCHAR(64) NOT NULL,
    scope VARCHAR(64) NOT NULL DEFAULT 'CONVERSATION',
    fact_key VARCHAR(128) NOT NULL,
    fact_value TEXT NOT NULL,
    confidence DECIMAL(5,4) NOT NULL DEFAULT 0.8000,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_conversation_fact_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversation(id),
    CONSTRAINT fk_conversation_fact_source_message
        FOREIGN KEY (source_message_id) REFERENCES message(id),
    UNIQUE KEY uq_conversation_fact_scope_key (
        conversation_id,
        scope,
        fact_key
    ),
    INDEX idx_conversation_fact_conversation_status (
        conversation_id,
        status,
        updated_at
    )
);
