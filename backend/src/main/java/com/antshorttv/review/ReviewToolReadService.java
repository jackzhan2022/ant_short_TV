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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ReviewToolReadService {
    private final ReviewTaskMapper tasks;
    private final ReviewScriptVersionMapper versions;
    private final ReviewContentService contentService;
    private final ReviewFanoutRepository fanout;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ReviewToolReadService(
        ReviewTaskMapper tasks,
        ReviewScriptVersionMapper versions,
        ReviewContentService contentService,
        ReviewFanoutRepository fanout,
        JdbcTemplate jdbc,
        ObjectMapper json
    ) {
        this.tasks = tasks;
        this.versions = versions;
        this.contentService = contentService;
        this.fanout = fanout;
        this.jdbc = jdbc;
        this.json = json;
    }

    public JsonNode readContext(ToolExecutionContext context) {
        State state = state(context);
        ObjectNode result = json.createObjectNode();
        result.put("mode", state.task.getReviewMode());
        result.put("phase", state.scope.phase());
        result.put("round", state.task.getRoundNo());
        result.set("dimensions", json.valueToTree(state.dimensions));
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

    public JsonNode readHistory(ToolExecutionContext context, JsonNode arguments) {
        State state = state(context);
        int page = Math.max(1, arguments.path("page").asInt(1));
        int pageSize = Math.min(100, Math.max(1, arguments.path("pageSize").asInt(50)));
        int offset = (page - 1) * pageSize;
        List<Map<String, Object>> rows = jdbc.queryForList("""
            select issue.id, issue.issue_no, issue.dimension, issue.severity, issue.title,
                   issue.status, issue.manually_resolved, issue.related_issue_no,
                   issue.position_json, issue.excerpt, issue.problem, issue.suggestion
              from review_issue issue
             where issue.tenant_id = ? and issue.project_id = ? and issue.round_no < ?
               and issue.dimension in (%s)
             order by issue.round_no desc, issue.id asc limit ? offset ?
            """.formatted(placeholders(state.dimensions.size())), historyArgs(
                context.tenantId(), state.scope.reviewProjectId(), state.task.getRoundNo(),
                state.dimensions, pageSize + 1, offset));
        boolean hasMore = rows.size() > pageSize;
        if (hasMore) rows = rows.subList(0, pageSize);
        ArrayNode issues = json.createArrayNode();
        for (Map<String, Object> row : rows) {
            ObjectNode issue = issues.addObject();
            row.forEach((key, value) -> issue.set(camel(key), json.valueToTree(value)));
            Long issueId = issueId(row);
            issue.set("hits", json.valueToTree(jdbc.queryForList("""
                select episode_no, scene_no, line_no, anchor_label, excerpt
                  from review_issue_hit where issue_id = ? order by hit_no limit 50
                """, issueId)));
            issue.set("events", json.valueToTree(jdbc.queryForList("""
                select event_type, previous_status, new_status, created_at
                  from review_issue_event where issue_id = ? order by created_at limit 50
                """, issueId)));
        }
        ObjectNode result = json.createObjectNode();
        result.set("issues", issues);
        result.put("hasMore", hasMore);
        return result;
    }

    Long issueId(Map<String, Object> row) {
        Object value = row.entrySet().stream()
            .filter(entry -> "id".equalsIgnoreCase(entry.getKey()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElseThrow(() -> invalid("审核历史缺少问题标识。"));
        if (!(value instanceof Number number)) throw invalid("审核历史问题标识无效。");
        return number.longValue();
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
        Map<String, Object> scopeMap = map(task.getReviewScopeJson());
        ReviewContentService.FrozenReview frozen = contentService.freeze(
            version.getContent(), task.getReviewScopeType(), scopeMap, dimensions);
        requireHash(task.getVersionHash(), frozen.versionHash(), "版本");
        requireHash(task.getScopeHash(), frozen.scopeHash(), "范围");
        requireHash(task.getDimensionsHash(), frozen.dimensionsHash(), "维度");
        return new State(task, version, scope, dimensions, scopeMap, frozen);
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

    private String placeholders(int count) {
        if (count <= 0) throw invalid("审核维度不能为空。");
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private Object[] historyArgs(Long tenantId, Long projectId, Integer round, List<String> dimensions, int limit, int offset) {
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        args.add(tenantId); args.add(projectId); args.add(round); args.addAll(dimensions); args.add(limit); args.add(offset);
        return args.toArray();
    }

    private String camel(String value) {
        String lower = value.toLowerCase();
        StringBuilder result = new StringBuilder();
        boolean upper = false;
        for (char c : lower.toCharArray()) {
            if (c == '_') upper = true;
            else { result.append(upper ? Character.toUpperCase(c) : c); upper = false; }
        }
        return result.toString();
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

    record State(
        ReviewTaskEntity task, ReviewScriptVersionEntity version, ReviewToolScope scope,
        List<String> dimensions, Map<String, Object> scopeMap, ReviewContentService.FrozenReview frozen
    ) {}
}
