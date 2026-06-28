package com.funjson.metaagent.provider.domain;

/**
 * 表示 DeepSeek Adapter 产生的稳定调用错误。
 */
public class DeepSeekCallException extends RuntimeException {

    private final String code;

    /**
     * 创建 DeepSeek 调用异常。
     *
     * @param code 稳定错误码
     * @param message 安全错误消息
     */
    public DeepSeekCallException(String code, String message) {
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
