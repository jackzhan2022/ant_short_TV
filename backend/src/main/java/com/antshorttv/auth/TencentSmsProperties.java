package com.antshorttv.auth;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "sms.tencent")
public record TencentSmsProperties(
    @NotBlank String secretId,
    @NotBlank String secretKey,
    @NotBlank String sdkAppId,
    @NotBlank String signName,
    @NotBlank String registerTemplateId,
    @NotBlank String region
) {
}
