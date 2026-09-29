package com.antshorttv.workflowagent.tool;

import com.antshorttv.workflowagent.tool.EpisodeSourceSegmenter.EpisodeSourceSegment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Completes repairable storyboard payloads after the single focused model correction. */
public final class StoryboardFallbackCompleter {
    private final ObjectMapper json;

    public StoryboardFallbackCompleter(ObjectMapper json) {
        this.json = json;
    }

    public ObjectNode complete(
        ToolExecutionContext context,
        JsonNode submitted,
        WorkflowToolValidationException failure
    ) {
        ObjectNode result = submitted instanceof ObjectNode object
            ? object.deepCopy() : json.createObjectNode();
        result.put("schemaVersion", 3);
        String fingerprint = context.runState().get("currentEpisodeFingerprint", String.class);
        if (fingerprint != null) result.put("episodeFingerprint", fingerprint);

        List<EpisodeSourceSegment> required = trustedRequiredSegments(context);
        ArrayNode boards = result.path("storyboards") instanceof ArrayNode values
            ? values : result.putArray("storyboards");
        if (boards.isEmpty() && !required.isEmpty()) boards.addObject();
        while (!required.isEmpty() && boards.size() > required.size()) {
            boards.remove(boards.size() - 1);
        }
        for (int index = 0; index < boards.size(); index++) {
            ObjectNode board;
            if (boards.get(index) instanceof ObjectNode object) {
                board = object;
            } else {
                board = json.createObjectNode();
                boards.set(index, board);
            }
            completeBoard(board, index, boards.size(), required);
        }
        result.put("_serverFallback", true);
        result.put("_fallbackItemCount", boards.size());
        result.put("_correctedItemCount", 1);
        Object code = failure == null ? null : failure.details().get("validationCode");
        if (code != null) result.put("_fallbackValidationCode", String.valueOf(code));
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<EpisodeSourceSegment> trustedRequiredSegments(ToolExecutionContext context) {
        List<?> segments = context.runState().get("currentEpisodeSourceSegments", List.class);
        if (segments == null) return List.of();
        List<EpisodeSourceSegment> required = new ArrayList<>();
        for (Object value : segments) {
            if (value instanceof EpisodeSourceSegment segment && segment.requiredCoverage()) {
                required.add(segment);
            }
        }
        return List.copyOf(required);
    }

    private void completeBoard(
        ObjectNode board,
        int boardIndex,
        int boardCount,
        List<EpisodeSourceSegment> required
    ) {
        board.put("storyboardNo", boardIndex + 1);
        int start = required.isEmpty() ? 0 : boardIndex * required.size() / boardCount;
        int end = required.isEmpty() ? 0
            : Math.max(start, ((boardIndex + 1) * required.size() / boardCount) - 1);
        if (!required.isEmpty()) board.put("sourceTo", required.get(end).id());
        ensureMaterialGroups(board, "usedAssetKeys");
        ensureMaterialGroups(board, "unmatchedMaterials");

        ArrayNode shots = board.path("shots") instanceof ArrayNode values
            ? values : board.putArray("shots");
        while (shots.size() > 10) shots.remove(shots.size() - 1);
        while (shots.size() < 3) shots.addObject();
        BigDecimal duration = BigDecimal.valueOf(12).divide(
            BigDecimal.valueOf(shots.size()), 2, RoundingMode.HALF_UP)
            .max(new BigDecimal("1.5")).min(new BigDecimal("4"));
        for (int shotIndex = 0; shotIndex < shots.size(); shotIndex++) {
            ObjectNode shot;
            if (shots.get(shotIndex) instanceof ObjectNode object) {
                shot = object;
            } else {
                shot = json.createObjectNode();
                shots.set(shotIndex, shot);
            }
            EpisodeSourceSegment source = required.isEmpty() ? null
                : required.get(Math.min(end, start + shotIndex * Math.max(1, end - start + 1)
                    / shots.size()));
            shot.put("shotNo", shotIndex + 1);
            shot.put("durationSeconds", duration);
            shot.remove("sourceAnchor");
            if (!shot.path("positioning").isTextual() || shot.path("positioning").asText().isBlank()) {
                shot.put("positioning", source == null ? "可信来源画面" : "来源片段 " + source.id());
            }
            if (!shot.path("action").isTextual() || shot.path("action").asText().isBlank()) {
                shot.put("action", source == null ? "保持当前画面动作"
                    : fallbackAction(source));
            }
        }
    }

    private String fallbackAction(EpisodeSourceSegment source) {
        return switch (source.type()) {
            case DIALOGUE, NARRATION, INNER_OS -> "按可信原文完成当前声音段落";
            default -> source.text();
        };
    }

    private void ensureMaterialGroups(ObjectNode board, String field) {
        ObjectNode groups = board.path(field) instanceof ObjectNode object
            ? object : board.putObject(field);
        for (String type : List.of("characters", "scenes", "props")) {
            if (!groups.path(type).isArray()) groups.putArray(type);
        }
    }
}
