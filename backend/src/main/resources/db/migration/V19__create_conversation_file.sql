CREATE TABLE conversation_file (
    id BINARY(16) NOT NULL,
    conversation_id BINARY(16) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    storage_path VARCHAR(1000) NOT NULL,
    content_type VARCHAR(200) NOT NULL,
    size_bytes BIGINT NOT NULL,
    checksum_sha256 CHAR(64) NOT NULL,
    status VARCHAR(40) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_conversation_file_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversation (id),
    INDEX idx_conversation_file_conversation_created
        (conversation_id, created_at),
    INDEX idx_conversation_file_name
        (conversation_id, file_name)
);
