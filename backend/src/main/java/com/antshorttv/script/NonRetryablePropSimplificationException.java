package com.antshorttv.script;

import com.antshorttv.common.BusinessException;

final class NonRetryablePropSimplificationException extends BusinessException {
    NonRetryablePropSimplificationException(BusinessException cause) {
        super(cause.getErrorCode(),cause.getMessage());initCause(cause);
    }
}
