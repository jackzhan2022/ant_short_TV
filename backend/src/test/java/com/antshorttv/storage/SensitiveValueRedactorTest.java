package com.antshorttv.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SensitiveValueRedactorTest {
    @Test
    void redactsSignedUrlsCredentialsAndAuthorizationHeaders() {
        String value = "cdn=https://antvcdn.aixmax.cn/a.webp?t=123&sign=secret-sign "
            + "model=https://cos.example/a.mp4?q-signature=model-secret&q-ak=secret-id "
            + "TmpSecretKey=temporary-secret Token=session-token "
            + "Authorization: q-sign-algorithm=sha1&q-signature=authorization-secret";

        String redacted = SensitiveValueRedactor.redact(value);

        assertThat(redacted).doesNotContain(
            "secret-sign", "model-secret", "secret-id", "temporary-secret",
            "session-token", "authorization-secret"
        );
        assertThat(redacted).contains("[REDACTED]");
    }
}
