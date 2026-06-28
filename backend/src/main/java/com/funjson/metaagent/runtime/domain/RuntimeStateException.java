package com.funjson.metaagent.runtime.domain;

/**
 * 表示跨层运行时状态、版本或资源约束不满足。
 */
public class RuntimeStateException extends RuntimeException {

    private final String code;

    /**
     * 创建运行时状态异常。
     *
     * @param code 稳定错误码
     * @param message 安全错误消息
     */
    public RuntimeStateException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 返回稳定错误码。
     *
     * @return 错误码
     */
    public String code() {
        return code;
    }
}
