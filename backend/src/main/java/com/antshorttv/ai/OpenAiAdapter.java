package com.antshorttv.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class OpenAiAdapter extends AbstractCompatibleProviderAdapter {
    public OpenAiAdapter(AiSecretCodec aiSecretCodec, ObjectMapper objectMapper) {
        super(aiSecretCodec, objectMapper);
    }

    @Override
    public String providerCode() {
        return "OpenAI";
    }
}
