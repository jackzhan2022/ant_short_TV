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
                + "?sign=4802332b5a3a367afdc58a515b971eb3bf5f506897c9d71ace681ebbab6f6674&t=654ab680"
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

    @Test
    void derivesTimestampFromConfiguredCdnLifetime() {
        CdnTypeDSigner signer = new CdnTypeDSigner("https://antvcdn.aixmax.cn", "test-key", 3600);
        Instant expiresAt = Instant.ofEpochSecond(1_700_000_000L);

        String url = signer.sign("materials/11/22/video.mp4", expiresAt);

        String timestamp = url.substring(url.lastIndexOf("&t=") + 3);
        assertThat(Long.parseLong(timestamp, 16) + 3600).isEqualTo(expiresAt.getEpochSecond());
        assertThat(signer.sign("materials/11/22/video.mp4", expiresAt)).isEqualTo(url);
    }
}
