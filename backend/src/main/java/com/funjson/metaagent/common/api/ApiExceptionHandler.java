package com.funjson.metaagent.common.api;

import java.time.Instant;
import java.util.Map;

import com.funjson.metaagent.job.domain.JobNotFoundException;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将领域和校验异常转换为稳定的 HTTP 错误合同。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * 处理 Job 不存在。
     *
     * @param exception 领域异常
     * @return 404 错误
     */
    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(JobNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(
                "JOB_NOT_FOUND",
                exception.getMessage(),
                Instant.now(),
                Map.of()));
    }

    /**
     * 处理请求参数校验失败。
     *
     * @param exception 校验异常
     * @return 400 错误
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, Object> details = Map.of(
                "fieldErrors",
                exception.getBindingResult().getFieldErrors().stream()
                        .map(error -> Map.of(
                                "field", error.getField(),
                                "message", error.getDefaultMessage()))
                        .toList());
        return ResponseEntity.badRequest().body(new ApiError(
                "VALIDATION_FAILED",
                "Request validation failed",
                Instant.now(),
                details));
    }

    /**
     * 处理运行时状态错误。
     *
     * @param exception 状态异常
     * @return 与错误码匹配的 HTTP 错误
     */
    @ExceptionHandler(RuntimeStateException.class)
    public ResponseEntity<ApiError> handleRuntimeState(RuntimeStateException exception) {
        HttpStatus status = switch (exception.code()) {
            case "JOB_NOT_FOUND", "TASK_NOT_FOUND", "TASK_RUN_NOT_FOUND",
                    "CONVERSATION_NOT_FOUND", "AGENT_PROFILE_NOT_FOUND" ->
                    HttpStatus.NOT_FOUND;
            case "VERSION_CONFLICT", "INVALID_STATE_TRANSITION" -> HttpStatus.CONFLICT;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ResponseEntity.status(status).body(new ApiError(
                exception.code(),
                exception.getMessage(),
                Instant.now(),
                Map.of()));
    }
}
