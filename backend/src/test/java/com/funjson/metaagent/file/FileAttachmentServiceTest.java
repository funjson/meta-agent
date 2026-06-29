package com.funjson.metaagent.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.funjson.metaagent.file.application.FileAttachmentService;
import com.funjson.metaagent.file.application.port.out.ConversationFileStore;
import com.funjson.metaagent.file.domain.ConversationFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 验证 Conversation 文件上传、读取和搜索能力。
 */
class FileAttachmentServiceTest {

    @TempDir
    private Path tempDir;

    @Test
    void uploadsReadsAndSearchesTextFile() {
        InMemoryConversationFileStore store =
                new InMemoryConversationFileStore();
        FileAttachmentService service = new FileAttachmentService(
                store,
                tempDir.toString());
        UUID conversationId = UUID.randomUUID();

        var uploaded = service.upload(
                conversationId,
                new MockMultipartFile(
                        "file",
                        "hello.md",
                        "text/markdown",
                        "你好，文件里的关键词是 Meta Agent。"
                                .getBytes(StandardCharsets.UTF_8)));
        var content = service.read(
                conversationId,
                uploaded.id(),
                1000);
        var matches = service.search(
                conversationId,
                "Meta Agent",
                5);

        assertThat(uploaded.fileName()).isEqualTo("hello.md");
        assertThat(content.content()).contains("Meta Agent");
        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().fileName()).isEqualTo("hello.md");
    }

    /**
     * 测试用内存 Store。
     */
    private static class InMemoryConversationFileStore
            implements ConversationFileStore {

        private final List<ConversationFile> files = new ArrayList<>();

        @Override
        public void insert(ConversationFile file) {
            files.add(file);
        }

        @Override
        public List<ConversationFile> findByConversation(
                UUID conversationId) {
            return files.stream()
                    .filter(file -> file.conversationId()
                            .equals(conversationId))
                    .sorted(Comparator.comparing(
                            ConversationFile::createdAt).reversed())
                    .toList();
        }

        @Override
        public Optional<ConversationFile> findById(
                UUID conversationId,
                UUID fileId) {
            return files.stream()
                    .filter(file -> file.conversationId()
                            .equals(conversationId))
                    .filter(file -> file.id().equals(fileId))
                    .findFirst();
        }

        @Override
        public Optional<ConversationFile> findLatestByName(
                UUID conversationId,
                String fileName) {
            return findByConversation(conversationId).stream()
                    .filter(file -> file.fileName().equals(fileName))
                    .findFirst();
        }
    }
}
