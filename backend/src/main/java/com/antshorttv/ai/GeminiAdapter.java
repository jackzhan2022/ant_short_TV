package com.antshorttv.ai;

import org.springframework.stereotype.Component;

@Component
public class GeminiAdapter extends AbstractCompatibleProviderAdapter {
    public GeminiAdapter(AiSecretCodec aiSecretCodec) {
        super(aiSecretCodec);
    }

    @Override
    public String providerCode() {
        return "Gemini";
    }
}
