package com.funjson.metaagent.job.api;

import java.util.List;

/**
 * Job 分页结果。
 *
 * @param items 当前页数据
 * @param page 页码
 * @param size 页大小
 * @param total 总数
 */
public record JobPage(
        List<JobView> items,
        int page,
        int size,
        long total) {
}
