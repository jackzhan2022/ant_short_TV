package com.antshorttv.accounting;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;

public class ModelBillingMissingException extends BusinessException {
    public ModelBillingMissingException(String message) {
        super(ErrorCode.AI_MODEL_BILLING_MISSING, message);
    }
}
