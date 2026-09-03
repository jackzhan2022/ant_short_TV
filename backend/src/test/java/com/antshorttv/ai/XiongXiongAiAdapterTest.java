package com.antshorttv.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class XiongXiongAiAdapterTest {
    @Test
    void exposesTheDedicatedOpenAiCompatibleProviderCode() {
        XiongXiongAiAdapter adapter = new XiongXiongAiAdapter(new AiSecretCodec("test-secret"), new ObjectMapper());

        assertThat(adapter.providerCode()).isEqualTo("XiongXiongAI");
    }
}
