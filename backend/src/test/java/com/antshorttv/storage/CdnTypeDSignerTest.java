package com.antshorttv.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class CdnTypeDSignerTest {

    @Test
    void signsNormalizedObjectPathWithTypeDContract() {
        CdnTypeDSigner signer = new CdnTypeDSigner("https://antvcdn.aixmax.cn", "test-key");

        String url = signer.sign("materials/11/22/video.mp4", Instant.ofEpochSecond(1_700_000_000L));

        assertThat(url).isEqualTo(
            "https://antvcdn.aixmax.cn/materials/11/22/video.mp4"
                + "?sign=22d16097bf5f5928deaa90af014189a3&t=6553f100"
        );
    }

    @Test
    void rejectsAbsoluteAndTraversalPaths() {
        CdnTypeDSigner signer = new CdnTypeDSigner("https://antvcdn.aixmax.cn", "test-key");

        assertThatThrownBy(() -> signer.sign("https://other.example/video.mp4", Instant.now()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> signer.sign("materials/../secret", Instant.now()))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
