package com.antshorttv.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class GeminiAdapter extends AbstractCompatibleProviderAdapter {
    public GeminiAdapter(AiSecretCodec aiSecretCodec, ObjectMapper objectMapper) {
        super(aiSecretCodec, objectMapper);
    }

    @Override
    public String providerCode() {
        return "Gemini";
    }
}
