package com.antshorttv.video;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;

/** The original Ark result URL, whose 24-hour lifetime must survive transfer retries. */
record ArkVideoSourceLink(String url, Instant expiresAt) {
    static ArkVideoSourceLink capture(String url, JsonNode metadata, JsonNode previous, Instant observedAt) {
        if (url == null || url.isBlank()) return null;
        long completedAt = metadata.path("updatedAt").asLong(0);
        Instant generatedAt = completedAt > 0
            ? Instant.ofEpochSecond(Math.min(completedAt, observedAt.getEpochSecond())) : observedAt;
        Instant expiresAt = generatedAt.plus(Duration.ofHours(24));
        ArkVideoSourceLink stored = read(previous);
        if (stored != null && stored.expiresAt().isBefore(expiresAt)) expiresAt = stored.expiresAt();
        return new ArkVideoSourceLink(url, expiresAt);
    }

    static ArkVideoSourceLink read(JsonNode metadata) {
        String url = metadata.path("sourceVideoUrl").asText("");
        String expiresAt = metadata.path("sourceVideoExpiresAt").asText("");
        if (url.isBlank() || expiresAt.isBlank()) return null;
        return new ArkVideoSourceLink(url, Instant.parse(expiresAt));
    }

    void writeTo(ObjectNode metadata) {
        metadata.put("sourceVideoUrl", url);
        metadata.put("sourceVideoExpiresAt", expiresAt.toString());
    }
}
