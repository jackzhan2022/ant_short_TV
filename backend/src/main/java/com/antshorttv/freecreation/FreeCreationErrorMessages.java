package com.antshorttv.freecreation;

/** Only recognized provider errors become user-facing explanations; raw responses stay in call logs. */
final class FreeCreationErrorMessages {
    static final String UNAVAILABLE = "生成服务暂时不可用，请稍后重试。";

    private FreeCreationErrorMessages() {}

    static String recognized(String providerMessage) {
        if (providerMessage != null
            && providerMessage.contains("InputImageSensitiveContentDetected.PrivacyInformation")) {
            return "参考图片未通过模型服务商审核，可能涉及真人形象，请更换符合该模型要求的参考图片后重试。";
        }
        return null;
    }

    static String forProviderFailure(String providerMessage) {
        String message = recognized(providerMessage);
        return message == null ? UNAVAILABLE : message;
    }
}
