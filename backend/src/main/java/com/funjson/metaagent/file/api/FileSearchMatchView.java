package com.funjson.metaagent.file.api;

import java.util.UUID;

/**
 * 文件搜索命中视图。
 *
 * @param fileId 文件 ID
 * @param fileName 文件名
 * @param snippet 命中片段
 */
public record FileSearchMatchView(
        UUID fileId,
        String fileName,
        String snippet) {
}
