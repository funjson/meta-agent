package com.funjson.metaagent.provider.domain;

/**
 * 表示通用 Provider Adapter 调用错误。
 */
public class ProviderCallException extends RuntimeException {

    private final String code;

    /**
     * 创建 Provider 调用异常。
     *
     * @param code 稳定错误码
     * @param message 错误消息
     */
    public ProviderCallException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * @return 稳定错误码
     */
    public String code() {
        return code;
    }
}
