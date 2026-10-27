package com.antshorttv.video;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ArkVideoSourceLinkTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Instant now = Instant.parse("2026-10-27T04:00:00Z");

    @Test
    void expiresTwentyFourHoursAfterProviderCompletionRatherThanPolling() {
        var completed = now.minus(Duration.ofHours(5));
        var metadata = mapper.createObjectNode().put("updatedAt", completed.getEpochSecond());
        var link = ArkVideoSourceLink.capture("https://ark.example/result.mp4", metadata, mapper.createObjectNode(), now);

        assertThat(link.url()).isEqualTo("https://ark.example/result.mp4");
        assertThat(link.expiresAt()).isEqualTo(completed.plus(Duration.ofHours(24)));
    }

    @Test
    void missingCompletionTimeUsesFirstObservationAndRetriesNeverExtendExpiry() {
        var metadata = mapper.createObjectNode();
        var first = ArkVideoSourceLink.capture("https://ark.example/result.mp4", metadata, mapper.createObjectNode(), now);
        var previous = mapper.createObjectNode();
        first.writeTo(previous);
        var retried = ArkVideoSourceLink.capture("https://ark.example/result.mp4?new-signature", metadata, previous, now.plusSeconds(3600));

        assertThat(first.expiresAt()).isEqualTo(now.plus(Duration.ofHours(24)));
        assertThat(retried.expiresAt()).isEqualTo(first.expiresAt());
        assertThat(ArkVideoSourceLink.read(previous)).isEqualTo(first);
    }

    @Test
    void correctedProviderCompletionCanShortenButNeverExtendStoredExpiry() {
        var previous = mapper.createObjectNode();
        ArkVideoSourceLink.capture("https://ark.example/result.mp4", mapper.createObjectNode(), previous, now).writeTo(previous);
        var earlier = mapper.createObjectNode().put("updatedAt", now.minusSeconds(3600).getEpochSecond());
        var later = mapper.createObjectNode().put("updatedAt", now.plusSeconds(3600).getEpochSecond());

        assertThat(ArkVideoSourceLink.capture("https://ark.example/result.mp4", earlier, previous, now.plusSeconds(7200)).expiresAt())
            .isEqualTo(now.plus(Duration.ofHours(23)));
        assertThat(ArkVideoSourceLink.capture("https://ark.example/result.mp4", later, previous, now.plusSeconds(7200)).expiresAt())
            .isEqualTo(now.plus(Duration.ofHours(24)));
    }

    @Test
    void futureProviderTimestampCannotMakeLifetimeExceedTwentyFourHours() {
        var metadata = mapper.createObjectNode().put("updatedAt", now.plusSeconds(3600).getEpochSecond());
        assertThat(ArkVideoSourceLink.capture("https://ark.example/result.mp4", metadata, mapper.createObjectNode(), now).expiresAt())
            .isEqualTo(now.plus(Duration.ofHours(24)));
    }

    @Test
    void absentSourceRemainsAbsentForLegacyAndMockResults() {
        assertThat(ArkVideoSourceLink.capture(null, mapper.createObjectNode(), mapper.createObjectNode(), now)).isNull();
        assertThat(ArkVideoSourceLink.capture(" ", mapper.createObjectNode(), mapper.createObjectNode(), now)).isNull();
        assertThat(ArkVideoSourceLink.read(mapper.createObjectNode())).isNull();
    }
}
