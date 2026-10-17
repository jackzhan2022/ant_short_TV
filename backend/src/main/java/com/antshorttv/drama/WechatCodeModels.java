package com.antshorttv.drama;

import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;

public final class WechatCodeModels {
    private WechatCodeModels() {}

    public record CommitRequest(@NotNull @PositiveOrZero Long templateId,
                                @NotBlank @Size(max = 64) String version,
                                @NotBlank @Size(max = 1024) String description) {}
    public record Template(long templateId, String version, String description,
                           String sourceAppid, String sourceName) {}
    public record CommitResult(String status) {}
    public record QrCode(String contentType, byte[] bytes) {}

    public static class CodeException extends RuntimeException {
        private final HttpStatus status;
        private final String code;
        CodeException(HttpStatus status, String code, String message) {
            super(message); this.status = status; this.code = code;
        }
        public HttpStatus status() { return status; }
        public String code() { return code; }
    }
    static CodeException upstream() {
        return new CodeException(HttpStatus.BAD_GATEWAY, "WECHAT_CODE_UPSTREAM_ERROR", "微信代码管理服务响应异常。");
    }
    static CodeException unknown() {
        return new CodeException(HttpStatus.BAD_GATEWAY, "WECHAT_COMMIT_UNKNOWN",
            "代码上传结果未知（UNKNOWN），请人工核实体验版，勿自动重试。");
    }
    static CodeException rejected(int code) {
        String message = switch (code) {
            case 40001, 40014, 42001 -> "微信调用凭据无效或已过期。";
            case 48001, 61007 -> "微信接口未授权。";
            case 85014, 85064 -> "微信模板不存在。";
            case 9402202 -> "微信正在处理上一次上传。";
            default -> "微信拒绝本次代码管理操作。";
        };
        return new CodeException(HttpStatus.BAD_GATEWAY, "WECHAT_CODE_REJECTED", message);
    }
}
