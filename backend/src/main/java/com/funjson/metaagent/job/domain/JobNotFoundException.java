package com.funjson.metaagent.job.domain;

import java.util.UUID;

/**
 * 表示请求的 Job 不存在。
 */
public class JobNotFoundException extends RuntimeException {

    /**
     * 创建 Job 不存在异常。
     *
     * @param jobId Job ID
     */
    public JobNotFoundException(UUID jobId) {
        super("Job not found: " + jobId);
    }
}
