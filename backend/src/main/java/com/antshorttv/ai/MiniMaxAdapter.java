package com.antshorttv.ai;

import org.springframework.stereotype.Component;

@Component
public class MiniMaxAdapter extends AbstractCompatibleProviderAdapter {
    public MiniMaxAdapter(AiSecretCodec aiSecretCodec) {
        super(aiSecretCodec);
    }

    @Override
    public String providerCode() {
        return "MiniMax";
    }
}
