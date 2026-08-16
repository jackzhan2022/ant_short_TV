package com.antshorttv.auth;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class VerificationCodeService {

    private static final String DEVELOPMENT_CODE = "123456";

    public void verify(String mobile, String verificationCode) {
        if (!DEVELOPMENT_CODE.equals(verificationCode)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "验证码错误。");
        }
    }
}
