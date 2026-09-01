package com.antshorttv.operationlog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;

    public OperationLogService(OperationLogMapper operationLogMapper, ObjectMapper objectMapper) {
        this.operationLogMapper = operationLogMapper;
        this.objectMapper = objectMapper;
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
        log.setDetailJson(serializeDetail(detail));
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
