package com.funjson.metaagent.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import com.funjson.metaagent.conversation.application.UserFacingResponseRenderer;
import org.junit.jupiter.api.Test;

/**
 * Verifies user-facing rendering boundaries for Conversation messages.
 */
class UserFacingResponseRendererTest {

    @Test
    void failureRenderingDoesNotExposeRawExceptionMessage() {
        UserFacingResponseRenderer renderer = new UserFacingResponseRenderer();

        String message = renderer.renderFailure(
                "任务执行",
                new RuntimeException(
                        "HTTP connect timed out at java.net.SocketTimeout"));

        assertThat(message)
                .contains("任务执行时遇到了一个内部执行问题")
                .doesNotContain("HTTP connect timed out")
                .doesNotContain("java.net");
    }

    @Test
    void renderFiltersInternalToolNamesFromModelOutput() {
        UserFacingResponseRenderer renderer = new UserFacingResponseRenderer();

        String message = renderer.render("""
                抱歉，我无法完成这个请求。
                根据系统规则，我不能调用 web.fetch 读取这个页面。
                你提供的链接是搜索结果页，不是具体文章页面。
                """);

        assertThat(message)
                .contains("搜索结果页")
                .doesNotContain("web.fetch")
                .doesNotContain("系统规则");
    }
}
