package com.antshorttv.ai;

import org.springframework.stereotype.Component;

@Component
public class OpenAiAdapter extends AbstractCompatibleProviderAdapter {
    public OpenAiAdapter(AiSecretCodec aiSecretCodec) {
        super(aiSecretCodec);
    }

    @Override
    public String providerCode() {
        return "OpenAI";
    }
}
