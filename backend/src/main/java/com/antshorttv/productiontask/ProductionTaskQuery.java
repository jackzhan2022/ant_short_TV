package com.antshorttv.productiontask;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

public record ProductionTaskQuery(String scope, String type, String statusGroup, Long projectId,
    Long creatorId, @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
    @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo, Integer page, Integer pageSize) {
    public String effectiveScope() {
        if (scope == null || scope.equals("mine")) return "mine";
        if (scope.equals("team")) return "team";
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "无效任务范围。");
    }
    public int effectivePage() { return page == null ? 1 : Math.max(1, page); }
    public int effectiveSize() { return pageSize == null ? 20 : Math.max(1, Math.min(100, pageSize)); }
}
