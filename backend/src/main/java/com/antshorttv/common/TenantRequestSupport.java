package com.antshorttv.common;

import jakarta.servlet.http.HttpServletRequest;

public final class TenantRequestSupport {
    private TenantRequestSupport() {
    }

    public static Long tenantId(HttpServletRequest request) {
        String header = request.getHeader("X-Tenant-Id");
        if (header == null || header.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少团队上下文。");
        }
        try {
            return Long.valueOf(header);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前创作团队标识不正确。");
        }
    }
}
