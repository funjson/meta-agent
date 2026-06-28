CREATE TABLE platform_metadata (
    metadata_key VARCHAR(100) NOT NULL,
    metadata_value VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (metadata_key)
);

INSERT INTO platform_metadata (metadata_key, metadata_value)
VALUES ('schema_version', 'SPI-001-S0');

