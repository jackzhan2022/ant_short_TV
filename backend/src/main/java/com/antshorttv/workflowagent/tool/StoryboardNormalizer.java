package com.antshorttv.workflowagent.tool;

import com.antshorttv.workflowagent.tool.EpisodeSourceSegmenter.EpisodeSourceSegment;
import com.antshorttv.workflowagent.tool.EpisodeSourceSegmenter.SourceSegmentType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Canonicalizes bookkeeping fields that must not consume a model correction round. */
final class StoryboardNormalizer {
    private final ObjectMapper json;

    StoryboardNormalizer(ObjectMapper json) {
        this.json = json;
    }

    Result normalize(JsonNode submitted, List<EpisodeSourceSegment> segments) {
        ArrayNode boards = submitted.deepCopy();
        Map<String, IndexedSegment> byId = new LinkedHashMap<>();
        List<IndexedSegment> required = new ArrayList<>();
        for (int index = 0; index < segments.size(); index++) {
            IndexedSegment segment = new IndexedSegment(index, segments.get(index));
            byId.put(segment.value.id(), segment);
            if (segment.value.requiredCoverage()) required.add(segment);
        }
        if (required.isEmpty()) {
            throw failure("SOURCE_SEGMENTS_UNAVAILABLE", null, null,
                "当前剧集没有可用于分镜的正文片段。");
        }

        int normalized = 0;
        int requiredCursor = 0;
        int derivedSounds = 0;
        for (int boardIndex = 0; boardIndex < boards.size(); boardIndex++) {
            ObjectNode board = (ObjectNode) boards.get(boardIndex);
            int boardNo = boardIndex + 1;
            normalized += putIfChanged(board, "storyboardNo", boardNo);

            IndexedSegment start = requiredCursor < required.size() ? required.get(requiredCursor) : null;
            if (start == null) {
                throw failure("SOURCE_SEGMENT_REVERSED", boardNo, null,
                    "分镜数量超过了可覆盖的正文范围。");
            }
            IndexedSegment submittedEnd = require(byId, text(board, "sourceTo"), boardNo);
            if (submittedEnd.ordinal < start.ordinal) {
                throw failure("SOURCE_SEGMENT_REVERSED", boardNo, submittedEnd.value.id(),
                    "分镜来源片段范围顺序颠倒。");
            }
            IndexedSegment end = submittedEnd;
            if (boardIndex == boards.size() - 1) {
                IndexedSegment lastRequired = required.get(required.size() - 1);
                if (end.ordinal < lastRequired.ordinal) end = lastRequired;
            }
            normalized += putIfChanged(board, "sourceFrom", start.value.id());
            normalized += putIfChanged(board, "sourceTo", end.value.id());

            int nextRequired = requiredCursor;
            while (nextRequired < required.size() && required.get(nextRequired).ordinal <= end.ordinal) {
                nextRequired++;
            }
            if (nextRequired == requiredCursor) {
                throw failure("SOURCE_SEGMENT_GAP", boardNo, start.value.id(),
                    "分镜来源范围没有覆盖正文片段。");
            }
            requiredCursor = nextRequired;

            ArrayNode shots = (ArrayNode) board.path("shots");
            List<Integer> anchors = anchors(shots, byId, boardNo, start.ordinal, end.ordinal);
            List<List<String>> soundIds = new ArrayList<>();
            for (int shotIndex = 0; shotIndex < shots.size(); shotIndex++) {
                ObjectNode shot = (ObjectNode) shots.get(shotIndex);
                normalized += putIfChanged(shot, "shotNo", shotIndex + 1);
                normalized += putIfChanged(shot, "sourceAnchor", segments.get(anchors.get(shotIndex)).id());
                shot.remove(List.of("dialogue", "narration", "innerOs"));
                soundIds.add(new ArrayList<>());
            }
            for (int ordinal = start.ordinal; ordinal <= end.ordinal; ordinal++) {
                EpisodeSourceSegment sound = segments.get(ordinal);
                if (!isSound(sound.type())) continue;
                int owner = shots.size() - 1;
                for (int shotIndex = 0; shotIndex < anchors.size(); shotIndex++) {
                    if (anchors.get(shotIndex) >= ordinal) {
                        owner = shotIndex;
                        break;
                    }
                }
                soundIds.get(owner).add(sound.id());
                derivedSounds++;
            }
            for (int shotIndex = 0; shotIndex < shots.size(); shotIndex++) {
                normalized += replaceArray((ObjectNode) shots.get(shotIndex), "soundSegmentIds",
                    soundIds.get(shotIndex));
            }
        }
        if (requiredCursor < required.size()) {
            throw failure("SOURCE_SEGMENT_GAP", boards.size() + 1,
                required.get(requiredCursor).value.id(), "分镜未完整覆盖当前剧集正文。");
        }
        return new Result(boards, normalized, derivedSounds);
    }

    private List<Integer> anchors(
        ArrayNode shots,
        Map<String, IndexedSegment> byId,
        int boardNo,
        int from,
        int to
    ) {
        List<Integer> values = new ArrayList<>();
        int previousExplicit = from;
        for (JsonNode shot : shots) {
            String id = text(shot, "sourceAnchor");
            if (id.isBlank()) {
                values.add(null);
                continue;
            }
            IndexedSegment anchor = require(byId, id, boardNo);
            if (anchor.ordinal < from || anchor.ordinal > to || anchor.ordinal < previousExplicit) {
                throw failure("SOURCE_ANCHOR_REVERSED", boardNo, id,
                    "镜头来源锚点必须位于当前分镜范围内并按顺序非递减。");
            }
            previousExplicit = anchor.ordinal;
            values.add(anchor.ordinal);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (JsonNode shot : shots) total = total.add(shot.path("durationSeconds").decimalValue());
        BigDecimal elapsed = BigDecimal.ZERO;
        int previous = from;
        for (int index = 0; index < values.size(); index++) {
            Integer explicit = values.get(index);
            if (explicit == null) {
                int nextExplicit = to;
                for (int next = index + 1; next < values.size(); next++) {
                    if (values.get(next) != null) {
                        nextExplicit = values.get(next);
                        break;
                    }
                }
                int span = to - from;
                int weighted = total.signum() == 0 ? from : from + BigDecimal.valueOf(span)
                    .multiply(elapsed).divide(total, 0, RoundingMode.HALF_UP).intValue();
                explicit = Math.max(previous, Math.min(weighted, nextExplicit));
                values.set(index, explicit);
            }
            previous = explicit;
            elapsed = elapsed.add(shots.get(index).path("durationSeconds").decimalValue());
        }
        return values;
    }

    private IndexedSegment require(Map<String, IndexedSegment> byId, String id, int boardNo) {
        IndexedSegment segment = byId.get(id);
        if (segment == null) {
            throw failure("SOURCE_SEGMENT_UNKNOWN", boardNo, id,
                "分镜引用了未知的来源片段：" + id);
        }
        return segment;
    }

    private int putIfChanged(ObjectNode node, String field, int value) {
        if (node.path(field).isInt() && node.path(field).asInt() == value) return 0;
        node.put(field, value);
        return 1;
    }

    private int putIfChanged(ObjectNode node, String field, String value) {
        if (node.path(field).isTextual() && value.equals(node.path(field).asText())) return 0;
        node.put(field, value);
        return 1;
    }

    private int replaceArray(ObjectNode node, String field, List<String> expected) {
        JsonNode current = node.path(field);
        ArrayNode replacement = json.createArrayNode();
        expected.forEach(replacement::add);
        if (current.equals(replacement)) return 0;
        node.set(field, replacement);
        return 1;
    }

    private String text(JsonNode node, String field) {
        return node.path(field).isTextual() ? node.path(field).asText().trim() : "";
    }

    private boolean isSound(SourceSegmentType type) {
        return type == SourceSegmentType.DIALOGUE
            || type == SourceSegmentType.NARRATION
            || type == SourceSegmentType.INNER_OS;
    }

    private WorkflowToolValidationException failure(
        String code, Integer storyboardNo, String actualSegmentId, String message
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("validationCode", code);
        if (storyboardNo != null) details.put("storyboardNo", storyboardNo);
        if (actualSegmentId != null) details.put("actualSegmentId", actualSegmentId);
        return new WorkflowToolValidationException(message, details);
    }

    record Result(ArrayNode storyboards, int normalizedFieldCount, int derivedSoundCount) {}
    private record IndexedSegment(int ordinal, EpisodeSourceSegment value) {}
}
