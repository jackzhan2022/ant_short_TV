package com.antshorttv.video;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class VideoReferenceSubsetSelector {
    public Selection select(
        List<VideoTaskReferenceResolver.ResolvedReference> references,
        JsonNode constraints
    ) {
        List<VideoTaskReferenceResolver.ResolvedReference> ordered = references.stream()
            .sorted(Comparator
                .comparingInt((VideoTaskReferenceResolver.ResolvedReference value) ->
                    roleRank(value.reference().bindingRole()))
                .thenComparingInt(value -> value.reference().formalBinding() ? 0 : 1)
                .thenComparingInt(value -> value.reference().sortOrder())
                .thenComparingInt(value -> mediaRank(value.reference().mediaType())))
            .toList();
        List<VideoTaskReferenceResolver.ResolvedReference> kept = new ArrayList<>();
        List<OmittedReference> omitted = new ArrayList<>();
        Set<String> identities = new HashSet<>();
        int images = 0;
        int videos = 0;
        int audio = 0;
        BigDecimal videoDuration = BigDecimal.ZERO;
        BigDecimal audioDuration = BigDecimal.ZERO;
        for (var value : ordered) {
            String identity = identity(value);
            if (!identities.add(identity)) {
                omitted.add(omitted(value, "DUPLICATE_MEDIA"));
                continue;
            }
            JsonNode rules = constraints.path(value.reference().mediaType().toLowerCase());
            int maxCount = rules.path("maxCount").asInt(Integer.MAX_VALUE);
            switch (value.reference().mediaType()) {
                case "IMAGE" -> {
                    if (images >= maxCount) omitted.add(omitted(value, "MODEL_COUNT_LIMIT"));
                    else { images++; kept.add(value); }
                }
                case "VIDEO" -> {
                    BigDecimal duration = value.durationSeconds() == null
                        ? BigDecimal.ZERO : value.durationSeconds();
                    BigDecimal max = rules.path("maxTotalDurationSeconds").isNumber()
                        ? rules.path("maxTotalDurationSeconds").decimalValue() : null;
                    if (videos >= maxCount) omitted.add(omitted(value, "MODEL_COUNT_LIMIT"));
                    else if (max != null && videoDuration.add(duration).compareTo(max) > 0) {
                        omitted.add(omitted(value, "MODEL_DURATION_LIMIT"));
                    } else { videos++; videoDuration = videoDuration.add(duration); kept.add(value); }
                }
                case "AUDIO" -> {
                    BigDecimal duration = value.durationSeconds() == null
                        ? BigDecimal.ZERO : value.durationSeconds();
                    BigDecimal max = rules.path("maxTotalDurationSeconds").isNumber()
                        ? rules.path("maxTotalDurationSeconds").decimalValue() : null;
                    if (audio >= maxCount) omitted.add(omitted(value, "MODEL_COUNT_LIMIT"));
                    else if (max != null && audioDuration.add(duration).compareTo(max) > 0) {
                        omitted.add(omitted(value, "MODEL_DURATION_LIMIT"));
                    } else { audio++; audioDuration = audioDuration.add(duration); kept.add(value); }
                }
                default -> omitted.add(omitted(value, "UNSUPPORTED_MEDIA_TYPE"));
            }
        }
        return new Selection(List.copyOf(kept), List.copyOf(omitted));
    }

    private String identity(VideoTaskReferenceResolver.ResolvedReference value) {
        var reference = value.reference();
        return reference.mediaType() + ":" + reference.sourceType() + ":"
            + reference.sourceId() + ":" + reference.variantId();
    }

    private OmittedReference omitted(
        VideoTaskReferenceResolver.ResolvedReference value, String reason
    ) {
        var reference = value.reference();
        return new OmittedReference(reference.mediaType(), reference.sourceType(),
            reference.sourceId(), reference.variantId(), reference.assetType(), reference.assetId(),
            reference.displayName(), reference.bindingRole(), reference.sortOrder(), reason);
    }

    private int roleRank(String role) {
        return switch (role == null ? "" : role) {
            case "MAIN" -> 0;
            case "VISIBLE" -> 1;
            case "SUPPORTING" -> 2;
            case "TRANSITION" -> 3;
            default -> 4;
        };
    }

    private int mediaRank(String mediaType) {
        return switch (mediaType == null ? "" : mediaType) {
            case "IMAGE" -> 0;
            case "VIDEO" -> 1;
            case "AUDIO" -> 2;
            default -> 3;
        };
    }

    public record Selection(
        List<VideoTaskReferenceResolver.ResolvedReference> kept,
        List<OmittedReference> omitted
    ) {
    }

    public record OmittedReference(
        String mediaType,
        String sourceType,
        Long sourceId,
        Long variantId,
        String assetType,
        Long assetId,
        String displayName,
        String referenceRole,
        int sortOrder,
        String reason
    ) {
    }
}
