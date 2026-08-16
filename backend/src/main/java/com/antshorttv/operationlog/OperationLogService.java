package com.antshorttv.operationlog;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;

    public OperationLogService(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    public void record(
        Long userId,
        Long tenantId,
        String operation,
        Long targetId,
        OperationResult result,
        HttpServletRequest request
    ) {
        OperationLogEntity log = new OperationLogEntity();
        log.setUserId(userId);
        log.setTenantId(tenantId);
        log.setOperation(operation);
        log.setTargetId(targetId);
        log.setResult(result.name());
        log.setIp(resolveIp(request));
        log.setUserAgent(request == null ? null : request.getHeader("User-Agent"));
        log.setCreatedAt(LocalDateTime.now());
        operationLogMapper.insert(log);
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
