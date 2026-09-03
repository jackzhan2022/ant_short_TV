package com.antshorttv.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** OpenAI-compatible adapter dedicated to the XiongXiongAI gateway. */
@Component
public class XiongXiongAiAdapter extends AbstractCompatibleProviderAdapter {
    public XiongXiongAiAdapter(AiSecretCodec aiSecretCodec, ObjectMapper objectMapper) {
        super(aiSecretCodec, objectMapper);
    }

    @Override
    public String providerCode() {
        return "XiongXiongAI";
    }
}
