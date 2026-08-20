package com.antshorttv.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class VolcengineAdapter extends AbstractCompatibleProviderAdapter {
    public VolcengineAdapter(AiSecretCodec aiSecretCodec, ObjectMapper objectMapper) {
        super(aiSecretCodec, objectMapper);
    }

    @Override
    public String providerCode() {
        return "火山";
    }
}
