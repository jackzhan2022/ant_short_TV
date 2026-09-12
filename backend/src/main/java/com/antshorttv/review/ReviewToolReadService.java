package com.antshorttv.review;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.workflowagent.tool.ReviewToolScope;
import com.antshorttv.workflowagent.tool.ToolExecutionContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ReviewToolReadService {
    private final ReviewTaskMapper tasks;
    private final ReviewScriptVersionMapper versions;
    private final ReviewContentService contentService;
    private final ReviewFanoutRepository fanout;
    private final ObjectMapper json;

    public ReviewToolReadService(
        ReviewTaskMapper tasks,
        ReviewScriptVersionMapper versions,
        ReviewContentService contentService,
        ReviewFanoutRepository fanout,
        ObjectMapper json
    ) {
        this.tasks = tasks;
        this.versions = versions;
        this.contentService = contentService;
        this.fanout = fanout;
        this.json = json;
    }

    public JsonNode readContext(ToolExecutionContext context) {
        State state = state(context);
        ObjectNode result = json.createObjectNode();
        result.put("mode", state.task.getReviewMode());
        result.put("phase", state.scope.phase());
        result.put("round", state.task.getRoundNo());
        result.set("dimensions", json.valueToTree(activeDimensions(state)));
        result.set("scope", json.valueToTree(state.scopeMap));
        result.put("versionHash", state.frozen.versionHash());
        result.put("scopeHash", state.frozen.scopeHash());
        result.put("dimensionsHash", state.frozen.dimensionsHash());
        result.put("snapshotKey", state.frozen.snapshotKey());
        result.put("lineCount", state.frozen.lineCount());
        result.put("segmentCount", state.frozen.segments().size());
        if (state.scope.snapshotId() != null) result.put("snapshotId", state.scope.snapshotId());
        if (state.scope.unitId() != null) result.put("unitId", state.scope.unitId());
        ObjectNode coverage = result.putObject("coverage");
        coverage.put("completeRequired", true);
        coverage.put("unitBound", state.scope.unitId() != null);
        coverage.put("segmentCount", state.scope.unitId() == null ? state.frozen.segments().size() : 1);
        return result;
    }

    public JsonNode readContent(ToolExecutionContext context, JsonNode arguments) {
        State state = state(context);
        int offset = Math.max(0, arguments.path("offset").asInt(0));
        int limit = Math.min(50000, Math.max(1, arguments.path("limit").asInt(50000)));
        String visible = state.frozen.content();
        String fingerprint = ReviewContentService.hash(visible);
        String unitKey = null;
        List<String> unitAnchors = null;
        if (state.scope.unitId() != null) {
            ReviewFanoutUnitEntity unit = fanout.orderedUnits(state.scope.snapshotId()).stream()
                .filter(candidate -> candidate.getId().equals(state.scope.unitId())).findFirst()
                .orElseThrow(() -> invalid("审核单元不存在。"));
            String source = state.version.getContent() == null ? "" : state.version.getContent();
            if (unit.getStartOffset() < 0 || unit.getEndOffset() > source.length()
                || unit.getStartOffset() >= unit.getEndOffset()) throw invalid("审核单元偏移已失效。");
            visible = source.substring(unit.getStartOffset(), unit.getEndOffset());
            fingerprint = ReviewContentService.hash(visible);
            if (!fingerprint.equals(unit.getContentFingerprint())) throw invalid("审核单元内容已变化。");
            unitKey = unit.getUnitKey();
            unitAnchors = state.frozen.segments().stream()
                .filter(segment -> segment.endOffset() > unit.getStartOffset()
                    && segment.startOffset() < unit.getEndOffset())
                .map(ReviewContentService.Segment::anchor)
                .distinct()
                .toList();
        }
        int start = Math.min(offset, visible.length());
        int end = Math.min(visible.length(), start + limit);
        ObjectNode result = json.createObjectNode();
        ArrayNode segments = result.putArray("segments");
        ObjectNode segment = segments.addObject();
        segment.put("content", visible.substring(start, end));
        segment.put("startOffset", start);
        segment.put("endOffset", end);
        segment.put("contentFingerprint", fingerprint);
        if (unitKey == null) {
            segment.set("anchors", json.valueToTree(state.frozen.segments().stream()
                .map(ReviewContentService.Segment::anchor).limit(1000).toList()));
        } else {
            segment.put("unitKey", unitKey);
            segment.set("anchors", json.valueToTree(unitAnchors));
        }
        result.put("hasMore", end < visible.length());
        String trackedFingerprint = context.runState().get("review.content.fingerprint", String.class);
        Integer coveredUntil = context.runState().get("review.content.coveredUntil", Integer.class);
        if (!fingerprint.equals(trackedFingerprint)) coveredUntil = 0;
        if (start <= (coveredUntil == null ? 0 : coveredUntil)) {
            context.runState().put("review.content.fingerprint", fingerprint);
            context.runState().put("review.content.coveredUntil", Math.max(end, coveredUntil == null ? 0 : coveredUntil));
        }
        return result;
    }

    State state(ToolExecutionContext context) {
        ReviewToolScope scope = context.reviewScope();
        if (scope == null || context.taskId() == null) throw invalid("缺少可信审核作用域。");
        ReviewTaskEntity task = tasks.selectById(context.taskId());
        ReviewScriptVersionEntity version = versions.selectById(scope.versionId());
        if (task == null || version == null || !context.tenantId().equals(task.getTenantId())
            || !scope.reviewProjectId().equals(task.getProjectId())
            || !scope.versionId().equals(task.getScriptVersionId())
            || !task.getProjectId().equals(version.getProjectId())) throw invalid("审核任务或版本不匹配。");
        List<String> dimensions = list(task.getSelectedDimensionsJson());
        ReviewDimension.parseAll(dimensions);
        if (!scope.selectedDimensions().isEmpty()
            && !dimensions.containsAll(scope.selectedDimensions())) {
            throw invalid("审核运行包含未选择的维度。");
        }
        Map<String, Object> scopeMap = map(task.getReviewScopeJson());
        ReviewContentService.FrozenReview frozen = contentService.freeze(
            version.getContent(), task.getReviewScopeType(), scopeMap, dimensions);
        requireHash(task.getVersionHash(), frozen.versionHash(), "版本");
        requireHash(task.getScopeHash(), frozen.scopeHash(), "范围");
        requireHash(task.getDimensionsHash(), frozen.dimensionsHash(), "维度");
        return new State(task, version, scope, dimensions, scopeMap, frozen);
    }

    List<String> activeDimensions(State state) {
        return state.scope().selectedDimensions().isEmpty()
            ? state.dimensions()
            : state.scope().selectedDimensions();
    }

    private void requireHash(String stored, String actual, String label) {
        if (stored != null && !stored.equals(actual)) throw invalid(label + "内容已变化。");
    }

    private List<String> list(String value) {
        try { return json.readValue(value, new TypeReference<>() {}); }
        catch (Exception exception) { throw invalid("审核维度配置无效。"); }
    }

    private Map<String, Object> map(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try { return json.readValue(value, new TypeReference<>() {}); }
        catch (Exception exception) { throw invalid("审核范围配置无效。"); }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

    record State(
        ReviewTaskEntity task, ReviewScriptVersionEntity version, ReviewToolScope scope,
        List<String> dimensions, Map<String, Object> scopeMap, ReviewContentService.FrozenReview frozen
    ) {}
}
