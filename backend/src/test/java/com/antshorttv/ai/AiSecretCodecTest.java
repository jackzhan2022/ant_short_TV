package com.antshorttv.ai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void rejectsCipherThatCannotBeDecrypted() {
        AiSecretCodec codec = new AiSecretCodec("test-secret");

        assertThatThrownBy(() -> codec.requireDecrypted("not-a-valid-cipher"))
            .isInstanceOf(AiGatewayException.class)
            .extracting(exception -> ((AiGatewayException) exception).getErrorCode())
            .isEqualTo(ErrorCode.AI_AUTH_FAILED);
    }
}
