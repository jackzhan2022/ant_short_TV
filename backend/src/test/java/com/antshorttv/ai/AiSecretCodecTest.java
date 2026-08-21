package com.antshorttv.ai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import org.junit.jupiter.api.Test;

class AiSecretCodecTest {

    @Test
    void rejectsBlankSecretKey() {
        assertThatThrownBy(() -> new AiSecretCodec(" "))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("ai.secret-key must be configured.");
    }

    @Test
    void rejectsInvalidCipherTextWhenDecrypting() {
        AiSecretCodec codec = new AiSecretCodec("test-ai-secret-key");

        assertThatThrownBy(() -> codec.decrypt("not-a-valid-cipher"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("AI 服务密钥解密失败");
    }

    @Test
    void rejectsCipherThatCannotBeDecrypted() {
        AiSecretCodec codec = new AiSecretCodec("test-secret");

        assertThatThrownBy(() -> codec.requireDecrypted("not-a-valid-cipher"))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.AI_AUTH_FAILED);
    }
}
