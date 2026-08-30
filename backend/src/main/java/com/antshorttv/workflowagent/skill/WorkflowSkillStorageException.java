package com.antshorttv.workflowagent.skill;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;

final class WorkflowSkillStorageException extends BusinessException {
    WorkflowSkillStorageException(String message, Throwable cause) {
        super(ErrorCode.WORKFLOW_SKILL_STORAGE_ERROR, message);
        initCause(cause);
    }
}
