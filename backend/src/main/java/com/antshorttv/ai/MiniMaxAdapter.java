package com.antshorttv.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class MiniMaxAdapter extends AbstractCompatibleProviderAdapter {
    public MiniMaxAdapter(AiSecretCodec aiSecretCodec, ObjectMapper objectMapper) {
        super(aiSecretCodec, objectMapper);
    }

    @Override
    public String providerCode() {
        return "MiniMax";
    }
}
