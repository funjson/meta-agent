CREATE TABLE web_search_run (
    id BINARY(16) NOT NULL,
    tool_invocation_id BINARY(16) NOT NULL,
    job_id BINARY(16) NULL,
    task_id BINARY(16) NULL,
    task_run_id BINARY(16) NULL,
    loop_run_id BINARY(16) NULL,
    loop_node_id BINARY(16) NULL,
    query_text VARCHAR(500) NOT NULL,
    recency_days INT NULL,
    domains_json JSON NOT NULL,
    locale VARCHAR(40) NULL,
    result_count INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_web_search_invocation (tool_invocation_id),
    INDEX idx_web_search_loop_node (
        loop_node_id,
        created_at
    ),
    INDEX idx_web_search_task_run (
        task_run_id,
        created_at
    ),
    CONSTRAINT fk_web_search_tool_invocation
        FOREIGN KEY (tool_invocation_id) REFERENCES tool_invocation(id),
    CONSTRAINT fk_web_search_job
        FOREIGN KEY (job_id) REFERENCES job(id),
    CONSTRAINT fk_web_search_task
        FOREIGN KEY (task_id) REFERENCES task(id),
    CONSTRAINT fk_web_search_task_run
        FOREIGN KEY (task_run_id) REFERENCES task_run(id),
    CONSTRAINT fk_web_search_loop_run
        FOREIGN KEY (loop_run_id) REFERENCES loop_run(id),
    CONSTRAINT fk_web_search_loop_node
        FOREIGN KEY (loop_node_id) REFERENCES loop_node(id)
);

CREATE TABLE web_search_candidate (
    id BINARY(16) NOT NULL,
    search_run_id BINARY(16) NOT NULL,
    tool_invocation_id BINARY(16) NOT NULL,
    job_id BINARY(16) NULL,
    task_id BINARY(16) NULL,
    task_run_id BINARY(16) NULL,
    loop_run_id BINARY(16) NULL,
    loop_node_id BINARY(16) NULL,
    rank_no INT NOT NULL,
    title VARCHAR(500) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    snippet TEXT NULL,
    provider VARCHAR(80) NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    published_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_web_candidate_run_rank (
        search_run_id,
        rank_no
    ),
    INDEX idx_web_candidate_loop_node (
        loop_node_id,
        rank_no
    ),
    CONSTRAINT fk_web_candidate_search_run
        FOREIGN KEY (search_run_id) REFERENCES web_search_run(id),
    CONSTRAINT fk_web_candidate_tool_invocation
        FOREIGN KEY (tool_invocation_id) REFERENCES tool_invocation(id),
    CONSTRAINT fk_web_candidate_job
        FOREIGN KEY (job_id) REFERENCES job(id),
    CONSTRAINT fk_web_candidate_task
        FOREIGN KEY (task_id) REFERENCES task(id),
    CONSTRAINT fk_web_candidate_task_run
        FOREIGN KEY (task_run_id) REFERENCES task_run(id),
    CONSTRAINT fk_web_candidate_loop_run
        FOREIGN KEY (loop_run_id) REFERENCES loop_run(id),
    CONSTRAINT fk_web_candidate_loop_node
        FOREIGN KEY (loop_node_id) REFERENCES loop_node(id)
);

CREATE TABLE web_source_document (
    id BINARY(16) NOT NULL,
    tool_invocation_id BINARY(16) NOT NULL,
    job_id BINARY(16) NULL,
    task_id BINARY(16) NULL,
    task_run_id BINARY(16) NULL,
    loop_run_id BINARY(16) NULL,
    loop_node_id BINARY(16) NULL,
    url VARCHAR(2048) NOT NULL,
    title VARCHAR(500) NOT NULL,
    description TEXT NULL,
    source_type VARCHAR(40) NOT NULL,
    content_type VARCHAR(160) NULL,
    content_hash CHAR(64) NOT NULL,
    text_excerpt MEDIUMTEXT NULL,
    fetched_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_web_source_invocation_hash (
        tool_invocation_id,
        content_hash
    ),
    INDEX idx_web_source_loop_node (
        loop_node_id,
        created_at
    ),
    INDEX idx_web_source_task_run (
        task_run_id,
        created_at
    ),
    INDEX idx_web_source_hash (
        content_hash
    ),
    CONSTRAINT fk_web_source_tool_invocation
        FOREIGN KEY (tool_invocation_id) REFERENCES tool_invocation(id),
    CONSTRAINT fk_web_source_job
        FOREIGN KEY (job_id) REFERENCES job(id),
    CONSTRAINT fk_web_source_task
        FOREIGN KEY (task_id) REFERENCES task(id),
    CONSTRAINT fk_web_source_task_run
        FOREIGN KEY (task_run_id) REFERENCES task_run(id),
    CONSTRAINT fk_web_source_loop_run
        FOREIGN KEY (loop_run_id) REFERENCES loop_run(id),
    CONSTRAINT fk_web_source_loop_node
        FOREIGN KEY (loop_node_id) REFERENCES loop_node(id)
);

CREATE TABLE web_evidence_item (
    id BINARY(16) NOT NULL,
    source_document_id BINARY(16) NOT NULL,
    tool_invocation_id BINARY(16) NOT NULL,
    job_id BINARY(16) NULL,
    task_id BINARY(16) NULL,
    task_run_id BINARY(16) NULL,
    loop_run_id BINARY(16) NULL,
    loop_node_id BINARY(16) NULL,
    rank_no INT NOT NULL,
    source_url VARCHAR(2048) NOT NULL,
    title VARCHAR(500) NOT NULL,
    excerpt TEXT NOT NULL,
    relevance_score DECIMAL(5,4) NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_web_evidence_source_rank (
        source_document_id,
        rank_no
    ),
    INDEX idx_web_evidence_tool_invocation (
        tool_invocation_id,
        rank_no
    ),
    INDEX idx_web_evidence_loop_node (
        loop_node_id,
        created_at
    ),
    INDEX idx_web_evidence_task_run (
        task_run_id,
        created_at
    ),
    CONSTRAINT fk_web_evidence_source
        FOREIGN KEY (source_document_id) REFERENCES web_source_document(id),
    CONSTRAINT fk_web_evidence_tool_invocation
        FOREIGN KEY (tool_invocation_id) REFERENCES tool_invocation(id),
    CONSTRAINT fk_web_evidence_job
        FOREIGN KEY (job_id) REFERENCES job(id),
    CONSTRAINT fk_web_evidence_task
        FOREIGN KEY (task_id) REFERENCES task(id),
    CONSTRAINT fk_web_evidence_task_run
        FOREIGN KEY (task_run_id) REFERENCES task_run(id),
    CONSTRAINT fk_web_evidence_loop_run
        FOREIGN KEY (loop_run_id) REFERENCES loop_run(id),
    CONSTRAINT fk_web_evidence_loop_node
        FOREIGN KEY (loop_node_id) REFERENCES loop_node(id)
);
