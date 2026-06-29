package com.funjson.metaagent.file.api;

import java.util.UUID;

/**
 * 文件正文读取结果。
 *
 * @param id 文件 ID
 * @param fileName 文件名
 * @param contentType 内容类型
 * @param content 正文
 * @param truncated 是否被截断
 */
public record FileContentView(
        UUID id,
        String fileName,
        String contentType,
        String content,
        boolean truncated) {
}
