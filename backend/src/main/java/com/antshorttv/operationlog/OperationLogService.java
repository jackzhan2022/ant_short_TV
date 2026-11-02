package com.antshorttv.operationlog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.LinkedHashMap;
import com.antshorttv.security.CurrentPrincipal;
import org.springframework.stereotype.Service;

@Service
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;
    private final CurrentPrincipal principal;

    public OperationLogService(OperationLogMapper operationLogMapper, ObjectMapper objectMapper, CurrentPrincipal principal) {
        this.operationLogMapper = operationLogMapper;
        this.objectMapper = objectMapper;
        this.principal = principal;
    }

    public void record(
        Long userId,
        Long tenantId,
        String operation,
        Long targetId,
        OperationResult result,
        HttpServletRequest request
    ) {
        record(userId, tenantId, operation, targetId, result, request, null);
    }

    public void record(
        Long userId,
        Long tenantId,
        String operation,
        Long targetId,
        OperationResult result,
        HttpServletRequest request,
        Map<String, ?> detail
    ) {
        OperationLogEntity log = new OperationLogEntity();
        log.setUserId(userId);
        log.setTenantId(tenantId);
        log.setOperation(operation);
        log.setTargetId(targetId);
        log.setResult(result.name());
        log.setIp(resolveIp(request));
        log.setUserAgent(request == null ? null : request.getHeader("User-Agent"));
        Map<String, Object> attributedDetail = new LinkedHashMap<>();
        if (detail != null) attributedDetail.putAll(detail);
        principal.get().filter(user -> user.impersonation() != null).ifPresent(user -> {
            attributedDetail.put("administratorId", user.impersonation().administratorId());
            attributedDetail.put("targetUserId", user.userId());
            attributedDetail.put("sessionId", user.sessionId());
        });
        log.setDetailJson(serializeDetail(attributedDetail));
        log.setCreatedAt(LocalDateTime.now());
        operationLogMapper.insert(log);
    }

    private String serializeDetail(Map<String, ?> detail) {
        if (detail == null || detail.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Operation detail cannot be serialized.", exception);
        }
    }

    private String resolveIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
