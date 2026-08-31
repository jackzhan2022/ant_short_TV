package com.antshorttv.script;

import com.antshorttv.common.ErrorCode;
import java.util.Set;

public record GlobalUnderstandingProgress(int percent, String action) {
    private static final Set<ErrorCode> VALIDATION_ERRORS = Set.of(
        ErrorCode.VALIDATION_ERROR,
        ErrorCode.AI_RESPONSE_INVALID,
        ErrorCode.WORKFLOW_AGENT_TOOL_INVALID,
        ErrorCode.REQUIRED_TOOL_NOT_CALLED
    );

    public static GlobalUnderstandingProgress waiting() {
        return new GlobalUnderstandingProgress(0, "等待开始剧情全局理解");
    }

    public static GlobalUnderstandingProgress reading() {
        return new GlobalUnderstandingProgress(20, "Agent 正在读取当前剧本");
    }

    public static GlobalUnderstandingProgress analyzing() {
        return new GlobalUnderstandingProgress(40, "Agent 已读取当前剧本，正在分析剧情");
    }

    public static GlobalUnderstandingProgress saving() {
        return new GlobalUnderstandingProgress(80, "Agent 正在校验并保存全局理解");
    }

    public static GlobalUnderstandingProgress committed() {
        return new GlobalUnderstandingProgress(100, "剧情全局理解已保存");
    }

    public static GlobalUnderstandingProgress failed(ErrorCode errorCode) {
        if (errorCode == ErrorCode.SCRIPT_CONTENT_CHANGED) {
            return new GlobalUnderstandingProgress(40, "剧本已变化，请基于当前稿重试");
        }
        if (VALIDATION_ERRORS.contains(errorCode)) {
            return new GlobalUnderstandingProgress(80, "全局理解校验失败，可重试");
        }
        return new GlobalUnderstandingProgress(20, "剧情全局理解失败，可重试");
    }

    public static GlobalUnderstandingProgress retrying() {
        return new GlobalUnderstandingProgress(0, "等待重试剧情全局理解");
    }
}
