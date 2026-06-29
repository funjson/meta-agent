package com.funjson.metaagent.file.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.funjson.metaagent.file.api.ConversationFileView;
import com.funjson.metaagent.file.api.FileContentView;
import com.funjson.metaagent.file.api.FileSearchMatchView;
import com.funjson.metaagent.file.application.port.out.ConversationFileStore;
import com.funjson.metaagent.file.domain.ConversationFile;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 管理 Conversation 文件上传、读取、搜索和受控写入。
 */
@Service
public class FileAttachmentService {

    private static final long MAX_FILE_BYTES = 2_000_000L;
    private static final int DEFAULT_READ_CHARS = 30_000;
    private static final int MAX_READ_CHARS = 80_000;
    private static final int MAX_SEARCH_MATCHES = 20;

    private final ConversationFileStore store;
    private final Path root;

    /**
     * 创建文件附件服务。
     *
     * @param store 文件元数据 Store
     * @param artifactRoot artifact 根目录
     */
    public FileAttachmentService(
            ConversationFileStore store,
            @Value("${meta-agent.artifact.root:./.data/artifacts}")
            String artifactRoot) {
        this.store = store;
        this.root = Path.of(artifactRoot)
                .resolve("conversation-files")
                .toAbsolutePath()
                .normalize();
    }

    /**
     * 上传用户文件到受控 Conversation 文件空间。
     *
     * @param conversationId Conversation ID
     * @param file multipart 文件
     * @return 文件视图
     */
    public ConversationFileView upload(
            UUID conversationId,
            MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeStateException(
                    "FILE_EMPTY",
                    "Uploaded file is empty");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new RuntimeStateException(
                    "FILE_TOO_LARGE",
                    "File is larger than " + MAX_FILE_BYTES + " bytes");
        }
        try {
            return create(
                    conversationId,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getBytes());
        } catch (IOException exception) {
            throw new RuntimeStateException(
                    "FILE_UPLOAD_FAILED",
                    "Unable to read uploaded file: "
                            + exception.getMessage());
        }
    }

    /**
     * 写入模型或 Tool 生成的受控文本文件。
     *
     * @param conversationId Conversation ID
     * @param fileName 文件名
     * @param content 文本内容
     * @return 文件视图
     */
    public ConversationFileView writeText(
            UUID conversationId,
            String fileName,
            String content) {
        return create(
                conversationId,
                fileName,
                "text/plain; charset=utf-8",
                (content == null ? "" : content)
                        .getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 查询 Conversation 文件列表。
     *
     * @param conversationId Conversation ID
     * @return 文件视图
     */
    public List<ConversationFileView> list(UUID conversationId) {
        return store.findByConversation(conversationId)
                .stream()
                .map(this::view)
                .toList();
    }

    /**
     * 读取文件正文。
     *
     * @param conversationId Conversation ID
     * @param fileId 文件 ID
     * @param maxChars 最大字符数
     * @return 文件正文
     */
    public FileContentView read(
            UUID conversationId,
            UUID fileId,
            int maxChars) {
        ConversationFile file = store.findById(conversationId, fileId)
                .orElseThrow(() -> new RuntimeStateException(
                        "FILE_NOT_FOUND",
                        "File not found: " + fileId));
        return read(file, maxChars);
    }

    /**
     * 按文件名读取最近上传的文件。
     *
     * @param conversationId Conversation ID
     * @param fileName 文件名
     * @param maxChars 最大字符数
     * @return 文件正文
     */
    public FileContentView readByName(
            UUID conversationId,
            String fileName,
            int maxChars) {
        ConversationFile file = store.findLatestByName(
                        conversationId,
                        fileName)
                .orElseThrow(() -> new RuntimeStateException(
                        "FILE_NOT_FOUND",
                        "File not found: " + fileName));
        return read(file, maxChars);
    }

    /**
     * 在 Conversation 文件中做简单全文搜索。
     *
     * @param conversationId Conversation ID
     * @param query 查询词
     * @param maxMatches 最大结果数
     * @return 命中片段
     */
    public List<FileSearchMatchView> search(
            UUID conversationId,
            String query,
            int maxMatches) {
        String normalized = query == null
                ? ""
                : query.trim().toLowerCase(Locale.ROOT);
        int limit = Math.max(
                1,
                Math.min(maxMatches, MAX_SEARCH_MATCHES));
        return store.findByConversation(conversationId).stream()
                .filter(this::isTextLike)
                .map(file -> match(file, normalized))
                .flatMap(java.util.Optional::stream)
                .limit(limit)
                .toList();
    }

    /**
     * 渲染适合放入模型上下文的文件清单。
     *
     * @param conversationId Conversation ID
     * @return 文件清单
     */
    public String promptSummary(UUID conversationId) {
        List<ConversationFileView> files = list(conversationId);
        if (files.isEmpty()) {
            return "当前 Conversation 没有上传文件。";
        }
        return files.stream()
                .map(file -> "- id=%s name=%s type=%s size=%d"
                        .formatted(
                                file.id(),
                                file.fileName(),
                                file.contentType(),
                                file.sizeBytes()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("当前 Conversation 没有上传文件。");
    }

    /**
     * 创建文件并写入元数据。
     */
    private ConversationFileView create(
            UUID conversationId,
            String requestedName,
            String contentType,
            byte[] bytes) {
        if (bytes.length > MAX_FILE_BYTES) {
            throw new RuntimeStateException(
                    "FILE_TOO_LARGE",
                    "File is larger than " + MAX_FILE_BYTES + " bytes");
        }
        UUID fileId = UUID.randomUUID();
        String safeName = safeName(requestedName);
        String storagePath = conversationId + "/" + fileId + "-" + safeName;
        Path target = resolveStoragePath(storagePath);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException exception) {
            throw new RuntimeStateException(
                    "FILE_WRITE_FAILED",
                    "Unable to write file: " + exception.getMessage());
        }
        ConversationFile file = new ConversationFile(
                fileId,
                conversationId,
                safeName,
                storagePath,
                contentType,
                bytes.length,
                sha256(bytes),
                "ACTIVE",
                Instant.now());
        store.insert(file);
        return view(file);
    }

    /**
     * 读取文本文件。
     */
    private FileContentView read(
            ConversationFile file,
            int maxChars) {
        if (!isTextLike(file)) {
            throw new RuntimeStateException(
                    "FILE_NOT_TEXT",
                    "Only text-like files can be read in v0.1: "
                            + file.fileName());
        }
        try {
            String content = Files.readString(
                    resolveStoragePath(file.storagePath()),
                    StandardCharsets.UTF_8);
            int limit = Math.max(
                    1,
                    Math.min(maxChars <= 0 ? DEFAULT_READ_CHARS : maxChars,
                            MAX_READ_CHARS));
            boolean truncated = content.length() > limit;
            return new FileContentView(
                    file.id(),
                    file.fileName(),
                    file.contentType(),
                    truncated ? content.substring(0, limit) : content,
                    truncated);
        } catch (IOException exception) {
            throw new RuntimeStateException(
                    "FILE_READ_FAILED",
                    "Unable to read file: " + exception.getMessage());
        }
    }

    /**
     * 搜索单个文本文件。
     */
    private java.util.Optional<FileSearchMatchView> match(
            ConversationFile file,
            String query) {
        FileContentView content = read(file, MAX_READ_CHARS);
        if (query.isBlank()) {
            return java.util.Optional.of(new FileSearchMatchView(
                    file.id(),
                    file.fileName(),
                    preview(content.content(), 160)));
        }
        String haystack = content.content().toLowerCase(Locale.ROOT);
        int index = haystack.indexOf(query);
        if (index < 0) {
            return java.util.Optional.empty();
        }
        int start = Math.max(0, index - 80);
        int end = Math.min(content.content().length(),
                index + query.length() + 120);
        return java.util.Optional.of(new FileSearchMatchView(
                file.id(),
                file.fileName(),
                content.content().substring(start, end)));
    }

    /**
     * 判断是否可按 UTF-8 文本读取。
     */
    private boolean isTextLike(ConversationFile file) {
        String name = file.fileName().toLowerCase(Locale.ROOT);
        String type = file.contentType().toLowerCase(Locale.ROOT);
        return type.startsWith("text/")
                || type.contains("json")
                || type.contains("xml")
                || name.matches(".*\\.(md|txt|json|csv|tsv|xml|yaml|yml|java|js|ts|tsx|jsx|py|sql|html|css|properties)$");
    }

    /**
     * 生成安全文件名。
     */
    private String safeName(String requestedName) {
        String raw = requestedName == null || requestedName.isBlank()
                ? "untitled.txt"
                : requestedName.replace('\\', '/');
        String fileName = Path.of(raw).getFileName().toString();
        String safe = fileName.replaceAll("[^\\p{L}\\p{N}._-]+", "_");
        return safe.isBlank() ? "untitled.txt" : safe;
    }

    /**
     * 把相对存储路径解析到受控根目录并防止路径逃逸。
     */
    private Path resolveStoragePath(String storagePath) {
        Path resolved = root.resolve(storagePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new RuntimeStateException(
                    "FILE_PATH_ESCAPE",
                    "File path escapes managed storage");
        }
        return resolved;
    }

    /**
     * 转换 API 视图。
     */
    private ConversationFileView view(ConversationFile file) {
        return new ConversationFileView(
                file.id(),
                file.conversationId(),
                file.fileName(),
                file.contentType(),
                file.sizeBytes(),
                file.checksumSha256(),
                file.status(),
                file.createdAt());
    }

    /**
     * 计算内容 SHA-256。
     */
    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception);
        }
    }

    /**
     * 截断预览文本。
     */
    private String preview(String content, int limit) {
        return content.length() <= limit
                ? content
                : content.substring(0, limit);
    }
}
