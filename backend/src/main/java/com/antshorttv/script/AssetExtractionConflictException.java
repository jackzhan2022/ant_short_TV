package com.antshorttv.script;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;

public class AssetExtractionConflictException extends BusinessException {
    private final long executionId;
    public AssetExtractionConflictException(long executionId) {
        super(ErrorCode.VALIDATION_ERROR,"当前剧本已有不同范围或策略的资产提取任务，请先查看该任务进度。");
        this.executionId=executionId;
    }
    public long executionId() {return executionId;}
}
