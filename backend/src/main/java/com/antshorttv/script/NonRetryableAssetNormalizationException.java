package com.antshorttv.script;

import com.antshorttv.common.BusinessException;

/** A rejected complete decision must not trigger another automatic model request. */
final class NonRetryableAssetNormalizationException extends BusinessException {
    NonRetryableAssetNormalizationException(BusinessException cause) {
        super(cause.getErrorCode(),cause.getMessage());
        initCause(cause);
    }
}
