package com.antshorttv.ai;

import org.springframework.stereotype.Component;

@Component
public class VolcengineAdapter extends AbstractCompatibleProviderAdapter {
    public VolcengineAdapter(AiSecretCodec aiSecretCodec) {
        super(aiSecretCodec);
    }

    @Override
    public String providerCode() {
        return "火山";
    }
}
