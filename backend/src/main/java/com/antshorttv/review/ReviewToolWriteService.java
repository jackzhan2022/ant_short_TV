package com.antshorttv.review;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.workflowagent.tool.ReviewToolScope;
import com.antshorttv.workflowagent.tool.ToolExecutionContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewToolWriteService {
    private static final Set<String> SEVERITIES = Set.of("HIGH", "MEDIUM", "LOW", "P0", "P1", "P2");
    private final ReviewToolReadService reads;
    private final ReviewFanoutRepository fanout;
    private final ReviewTaskMapper tasks;
    private final ReviewIssueMapper issues;
    private final ReviewIssueHitMapper hits;
    private final ReviewIssueEventMapper events;
    private final ReviewSemanticAuditRepository semanticAudits;
    private final ReviewWorkflowFeatureFlags featureFlags;
    private final ObjectMapper json;

    public ReviewToolWriteService(ReviewToolReadService reads, ReviewFanoutRepository fanout,
        ReviewTaskMapper tasks, ReviewIssueMapper issues, ReviewIssueHitMapper hits,
        ReviewIssueEventMapper events, ReviewSemanticAuditRepository semanticAudits, ObjectMapper json,
        ReviewWorkflowFeatureFlags featureFlags) {
        this.reads = reads;
        this.fanout = fanout;
        this.tasks = tasks;
        this.issues = issues;
        this.hits = hits;
        this.events = events;
        this.semanticAudits = semanticAudits;
        this.json = json;
        this.featureFlags = featureFlags;
    }

    @Transactional
    public JsonNode saveUnitResult(ToolExecutionContext context, JsonNode arguments) {
        ReviewToolReadService.State state = reads.state(context);
        ReviewToolScope scope = state.scope();
        if ("CANCELED".equals(state.task().getStatus())) throw invalid("审核任务已取消，不能保存正式结果。");
        requirePhase(scope, "DEEP_CHILD");
        if (scope.snapshotId() == null || scope.unitId() == null || context.agentRunId() == null) {
            throw invalid("缺少审核单元的可信运行作用域。");
        }
        verifyHashes(state, arguments);
        requireCoverage(arguments.path("coverage"));
        ReviewFanoutUnitEntity unit = requireUnit(scope.snapshotId(), scope.unitId());
        String content = unitContent(state, unit);
        requireFullyRead(context, unit.getContentFingerprint(), content.length());
        if (!unit.getContentFingerprint().equals(arguments.path("contentFingerprint").asText())
            || !unit.getContentFingerprint().equals(ReviewContentService.hash(content))) {
            throw invalid("审核单元内容已变化。");
        }
        boolean dimensional = unit.getDimension() != null && !unit.getDimension().isBlank();
        Set<String> unitAnchors = state.frozen().segments().stream()
            .filter(segment -> dimensional || (segment.endOffset() > unit.getStartOffset()
                && segment.startOffset() < unit.getEndOffset()))
            .map(ReviewContentService.Segment::anchor)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        ArrayNode candidates = normalizedUnitCandidates(arguments.path("candidates"), state, content, unitAnchors);
        semanticAudits.appendCandidates(new ReviewSemanticAuditRepository.CandidateBatch(
            state.task().getTenantId(), state.task().getProjectId(), state.task().getId(),
            scope.snapshotId(), scope.unitId(), context.agentRunId(), unit.getContentFingerprint()
        ), candidates);
        String coverage = stringify(arguments.path("coverage"));
        String serializedCandidates = stringify(candidates);
        fanout.replaceCandidate(new ReviewFanoutRepository.CandidateDraft(scope.snapshotId(), scope.unitId(),
            context.agentRunId(), scope.attemptNo(), state.frozen().versionHash(), state.frozen().scopeHash(),
            state.frozen().dimensionsHash(), unit.getContentFingerprint(), coverage, serializedCandidates,
            ReviewContentService.hash(coverage + "\n" + serializedCandidates)));
        fanout.transitionUnit(scope.unitId(), ReviewUnitStatus.RUNNING, ReviewUnitStatus.SUCCEEDED);
        ObjectNode result = json.createObjectNode();
        result.put("saved", true);
        result.put("unitSaved", scope.unitId());
        return result;
    }

    public JsonNode readUnitResults(ToolExecutionContext context, JsonNode arguments) {
        ReviewToolReadService.State state = reads.state(context);
        ReviewToolScope scope = state.scope();
        requirePhase(scope, "DEEP_AGGREGATION");
        if (scope.snapshotId() == null) throw invalid("缺少聚合快照。");
        int page = Math.max(1, arguments.path("page").asInt(1));
        int size = Math.min(100, Math.max(1, arguments.path("pageSize").asInt(50)));
        List<ReviewFanoutUnitEntity> units = fanout.orderedUnits(scope.snapshotId());
        if (units.isEmpty() || units.stream().anyMatch(unit -> !"SUCCEEDED".equals(unit.getStatus())
            || !Boolean.TRUE.equals(unit.getCandidateSaved()))) {
            throw invalid("REVIEW_UNITS_INCOMPLETE：仍有审核单元未完成或未保存候选结果。");
        }
        List<ReviewUnitResultEntity> candidates = new ArrayList<>();
        for (ReviewFanoutUnitEntity unit : units) {
            ReviewUnitResultEntity candidate;
            try { candidate = fanout.currentCandidate(scope.snapshotId(), unit.getId()); }
            catch (Exception exception) { throw invalid("REVIEW_UNITS_INCOMPLETE：审核单元候选结果缺失。"); }
            if (!state.frozen().versionHash().equals(candidate.getVersionHash())
                || !state.frozen().scopeHash().equals(candidate.getScopeHash())
                || !state.frozen().dimensionsHash().equals(candidate.getDimensionsHash())
                || !unit.getContentFingerprint().equals(candidate.getContentFingerprint())) {
                throw invalid("审核候选快照已变化，请重新执行深度审核。");
            }
            candidates.add(candidate);
        }
        int from = Math.min((page - 1) * size, candidates.size());
        int to = Math.min(from + size, candidates.size());
        Map<Long, List<ReviewSemanticAuditRepository.CandidateRecord>> auditsByUnit = new LinkedHashMap<>();
        Map<Long, ReviewSemanticAuditRepository.CandidateRecord> auditsById = new LinkedHashMap<>();
        for (ReviewSemanticAuditRepository.CandidateRecord audit : semanticAudits.candidates(scope.snapshotId())) {
            auditsByUnit.computeIfAbsent(audit.unitId(), ignored -> new ArrayList<>()).add(audit);
            auditsById.put(audit.id(), audit);
        }
        Map<Long, ReviewSemanticAuditRepository.DecisionRecord> decisionsByCandidate = new LinkedHashMap<>();
        for (ReviewSemanticAuditRepository.DecisionRecord decision : semanticAudits.decisions(scope.snapshotId())) {
            decisionsByCandidate.put(decision.candidateId(), decision);
        }
        ArrayNode output = json.createArrayNode();
        for (int index = from; index < to; index++) {
            ObjectNode item = output.addObject();
            item.put("unitId", units.get(index).getId());
            item.put("unitKey", units.get(index).getUnitKey());
            item.set("coverage", parse(candidates.get(index).getCoverageJson()));
            ArrayNode unitCandidates = (ArrayNode) parse(candidates.get(index).getCandidatesJson());
            List<ReviewSemanticAuditRepository.CandidateRecord> unitAudits =
                auditsByUnit.getOrDefault(units.get(index).getId(), List.of());
            for (int candidateIndex = 0; candidateIndex < unitCandidates.size()
                && candidateIndex < unitAudits.size(); candidateIndex++) {
                if (!(unitCandidates.get(candidateIndex) instanceof ObjectNode candidate)) continue;
                ReviewSemanticAuditRepository.CandidateRecord audit = unitAudits.get(candidateIndex);
                candidate.put("candidateId", audit.id());
                candidate.put("candidateKey", audit.candidateKey());
                ReviewSemanticAuditRepository.DecisionRecord decision = decisionsByCandidate.get(audit.id());
                if (decision != null) candidate.set("semanticDecision", semanticDecisionJson(decision));
            }
            item.set("candidates", unitCandidates);
        }
        ObjectNode result = json.createObjectNode();
        result.set("units", output);
        ArrayNode humanReviewFindings = result.putArray("humanReviewFindings");
        for (ReviewSemanticAuditRepository.DecisionRecord decision : decisionsByCandidate.values()) {
            if (!"NEEDS_HUMAN_REVIEW".equals(decision.decision())) continue;
            ReviewSemanticAuditRepository.CandidateRecord audit = auditsById.get(decision.candidateId());
            if (audit == null) continue;
            ObjectNode finding = humanReviewFindings.addObject();
            finding.put("candidateId", audit.id());
            finding.put("unitId", audit.unitId());
            finding.put("dimension", audit.dimension());
            finding.put("confidence", decision.confidence());
            finding.put("rationale", decision.rationale());
            if (decision.severityDecision() != null) {
                finding.put("severityDecision", decision.severityDecision());
            }
            finding.set("evidenceRefs", parse(decision.evidenceRefsJson()));
            finding.set("candidate", parse(audit.rawPayloadJson()));
        }
        result.put("hasMore", to < candidates.size());
        return result;
    }

    public JsonNode readCandidates(ToolExecutionContext context, JsonNode arguments) {
        ReviewToolReadService.State state = reads.state(context);
        ReviewToolScope scope = state.scope();
        requirePhase(scope, "DEEP_SEMANTIC");
        if (scope.snapshotId() == null) throw invalid("缺少语义质检快照。");
        int page = Math.max(1, arguments.path("page").asInt(1));
        int size = Math.min(100, Math.max(1, arguments.path("pageSize").asInt(50)));
        List<ReviewSemanticAuditRepository.CandidateRecord> all = semanticAudits.candidates(scope.snapshotId());
        int from = Math.min((page - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        Map<Long, ReviewSemanticAuditRepository.DecisionRecord> existingDecisions =
            semanticAudits.decisions(scope.snapshotId()).stream().collect(java.util.stream.Collectors.toMap(
                ReviewSemanticAuditRepository.DecisionRecord::candidateId, decision -> decision));
        ArrayNode output = json.createArrayNode();
        for (int index = from; index < to; index++) {
            ReviewSemanticAuditRepository.CandidateRecord candidate = all.get(index);
            ObjectNode item = output.addObject();
            item.put("candidateId", candidate.id());
            item.put("unitId", candidate.unitId());
            item.put("discoveryRunId", candidate.discoveryRunId());
            item.put("candidateKey", candidate.candidateKey());
            item.put("dimension", candidate.dimension());
            item.put("candidateNo", candidate.candidateNo());
            item.put("status", candidate.status());
            item.put("sourceFingerprint", candidate.sourceFingerprint());
            item.set("candidate", parse(candidate.rawPayloadJson()));
            if (candidate.validationErrorsJson() != null) {
                item.set("validationErrors", parse(candidate.validationErrorsJson()));
            }
            ReviewSemanticAuditRepository.DecisionRecord existing = existingDecisions.get(candidate.id());
            if (existing != null) item.set("semanticDecision", semanticDecisionJson(existing));
        }
        ObjectNode result = json.createObjectNode();
        result.set("candidates", output);
        result.put("hasMore", to < all.size());
        return result;
    }

    @Transactional
    public JsonNode saveSemanticDecisions(ToolExecutionContext context, JsonNode arguments) {
        ReviewToolReadService.State state = reads.state(context);
        ReviewToolScope scope = state.scope();
        requirePhase(scope, "DEEP_SEMANTIC");
        if (scope.snapshotId() == null || context.agentRunId() == null) {
            throw invalid("缺少语义质检的可信运行作用域。");
        }
        verifyHashes(state, arguments);
        JsonNode decisions = arguments.path("decisions");
        if (!decisions.isArray() || decisions.size() > 100) throw invalid("语义质检裁决列表格式或数量无效。");
        boolean finalBatch = !arguments.has("finalBatch") || arguments.path("finalBatch").asBoolean(false);
        List<ReviewSemanticAuditRepository.CandidateRecord> candidates = semanticAudits.candidates(scope.snapshotId());
        Map<Long, ReviewSemanticAuditRepository.CandidateRecord> byId = candidates.stream()
            .collect(java.util.stream.Collectors.toMap(ReviewSemanticAuditRepository.CandidateRecord::id,
                candidate -> candidate));
        boolean anomalyRequired = featureFlags.anomalyGate() && candidates.isEmpty() && finalBatch;
        JsonNode anomalyReview = arguments.path("anomalyReview");
        if (anomalyRequired) requireAnomalyReview(anomalyReview);
        Set<Long> submitted = new LinkedHashSet<>();
        List<ReviewSemanticAuditRepository.DecisionDraft> validated = new ArrayList<>();
        for (JsonNode decision : decisions) {
            long candidateId = decision.path("candidateId").asLong(0);
            if (!byId.containsKey(candidateId)) throw invalid("语义质检候选不属于当前冻结快照。");
            if (!submitted.add(candidateId)) throw invalid("同一候选不能重复提交语义裁决。");
            JsonNode evidenceRefs = decision.path("evidenceRefs");
            if (!evidenceRefs.isArray() || evidenceRefs.isEmpty()) throw invalid("语义质检必须提供证据引用。");
            validated.add(new ReviewSemanticAuditRepository.DecisionDraft(
                state.task().getTenantId(), state.task().getProjectId(), state.task().getId(),
                scope.snapshotId(), candidateId, context.agentRunId(), decision.path("decision").asText(),
                decimal(decision.path("confidence")), requireText(decision, "rationale", "语义质检理由"),
                optionalText(decision, "severityDecision"), evidenceRefs,
                optionalText(decision, "duplicateClusterKey")
            ));
        }
        validated.forEach(semanticAudits::validateDecision);
        validated.forEach(semanticAudits::saveDecisionIdempotent);
        int totalDecisions = semanticAudits.decisionCount(scope.snapshotId());
        if (finalBatch) {
            if (totalDecisions != candidates.size()) {
                throw invalid("最终批次提交后，每个冻结候选都必须获得一次终态语义裁决。");
            }
            ObjectNode coverage = json.createObjectNode();
            coverage.put("candidateCount", candidates.size());
            coverage.put("anomalyRequired", anomalyRequired);
            if (anomalyReview.isObject()) coverage.set("anomalyReview", anomalyReview.deepCopy());
            semanticAudits.recordQualityCoverage(scope.snapshotId(), context.agentRunId(), coverage);
        }
        ObjectNode result = json.createObjectNode();
        result.put("saved", true);
        result.put("decisionCount", totalDecisions);
        result.put("complete", finalBatch);
        return result;
    }

    @Transactional
    public JsonNode saveResult(ToolExecutionContext context, JsonNode arguments) {
        ReviewToolReadService.State state = reads.state(context);
        if ("CANCELED".equals(state.task().getStatus())) throw invalid("审核任务已取消，不能保存正式结果。");
        ReviewToolScope scope = state.scope();
        if (!Set.of("QUICK", "DEEP_AGGREGATION").contains(scope.phase())) {
            throw invalid("当前审核阶段不能保存正式结果。");
        }
        if (context.agentRunId() == null) throw invalid("缺少可信 Agent 运行记录。");
        if (context.runState().successfulToolCodes().contains("save_review_result")
            || "COMPLETED".equals(state.task().getStatus())) throw invalid("当前运行已经保存过正式审核结果。");
        verifyHashes(state, arguments);
        requireCoverage(arguments.path("coverage"));
        if ("DEEP_AGGREGATION".equals(scope.phase())) {
            readUnitResults(context, json.createObjectNode().put("page", 1).put("pageSize", 100));
            if (featureFlags.semanticReview()) {
                requireSemanticApproval(scope.snapshotId(), arguments.path("issues"));
            }
        }
        int score = arguments.path("score").asInt(-1);
        if (score < 0 || score > 100) throw invalid("审核评分必须在 0 到 100 之间。");
        requireText(arguments, "conclusion", "审核结论");
        validateIssues(arguments.path("issues"), state, state.frozen().content(), null, 500);
        if ("QUICK".equals(scope.phase())) {
            requireFullyRead(context, ReviewContentService.hash(state.frozen().content()), state.frozen().content().length());
        }

        clearCurrentTaskSnapshot(state.task().getId());
        int index = 1;
        for (JsonNode draft : arguments.path("issues")) {
            ReviewIssueEntity issue = persistIssue(state, draft, index++, "new", null);
            persistHits(state.task(), issue, draft.path("hits"));
            persistEvent(state.task(), issue, null, issue.getStatus(), draft);
        }
        ObjectNode formal = arguments.deepCopy();
        formal.put("overallScore", score);
        formal.put("overallConclusion", arguments.path("conclusion").asText());
        formal.set("selectedDimensions", json.valueToTree(state.dimensions()));
        ReviewTaskEntity task = state.task();
        task.setResultJson(stringify(formal));
        task.setStatus("COMPLETED");
        task.setCurrentStage("COMPLETED");
        task.setOverallProgress(100);
        task.setCurrentAction("审核完成");
        task.setErrorCode(null);
        task.setErrorMessage(null);
        task.setWorkflowAgentRunId(context.agentRunId());
        task.setWorkflowPhase(scope.phase());
        task.setWorkflowAttemptNo(scope.attemptNo());
        task.setStale(false);
        task.setCompletedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        tasks.updateById(task);
        ObjectNode result = json.createObjectNode();
        result.put("saved", true);
        result.put("formalSaved", task.getId());
        return result;
    }

    private void clearCurrentTaskSnapshot(Long taskId) {
        events.delete(new LambdaQueryWrapper<ReviewIssueEventEntity>()
            .eq(ReviewIssueEventEntity::getTaskId, taskId));
        hits.delete(new LambdaQueryWrapper<ReviewIssueHitEntity>()
            .eq(ReviewIssueHitEntity::getTaskId, taskId));
        issues.delete(new LambdaQueryWrapper<ReviewIssueEntity>()
            .eq(ReviewIssueEntity::getTaskId, taskId));
    }

    private void verifyHashes(ReviewToolReadService.State state, JsonNode arguments) {
        requireHash(arguments, "versionHash", state.frozen().versionHash(), "版本");
        requireHash(arguments, "scopeHash", state.frozen().scopeHash(), "范围");
        requireHash(arguments, "dimensionsHash", state.frozen().dimensionsHash(), "维度");
    }

    private void requireHash(JsonNode arguments, String field, String expected, String label) {
        if (!expected.equals(arguments.path(field).asText())) throw invalid(label + "内容已变化。");
    }

    private void requireCoverage(JsonNode coverage) {
        if (!coverage.isObject() || !coverage.path("complete").asBoolean(false)) {
            throw invalid("审核覆盖范围不完整，不能保存结果。");
        }
    }

    private void validateIssues(JsonNode drafts, ReviewToolReadService.State state,
        String visibleContent, Set<String> unitAnchors, int maximum) {
        if (!drafts.isArray() || drafts.size() > maximum) throw invalid("审核问题列表格式或数量无效。");
        Set<String> identities = new LinkedHashSet<>();
        for (JsonNode draft : drafts) {
            String dimension = requireText(draft, "dimension", "审核维度");
            if (!reads.activeDimensions(state).contains(dimension)) {
                throw invalid("审核结果包含当前运行未选择的维度：" + dimension);
            }
            String severity = requireText(draft, "severity", "严重级别").toUpperCase();
            if (!SEVERITIES.contains(severity)) throw invalid("审核严重级别无效：" + severity);
            String title = requireText(draft, "title", "问题标题");
            requireText(draft, "problem", "问题说明");
            requireText(draft, "suggestion", "修改建议");
            JsonNode evidence = draft.path("evidence");
            JsonNode draftHits = draft.path("hits");
            if (!evidence.isArray() || evidence.isEmpty() || !draftHits.isArray() || draftHits.isEmpty()) {
                throw invalid("每个审核问题必须包含可验证的证据和命中点。");
            }
            for (JsonNode item : evidence) requireExcerpt(visibleContent, item.asText());
            Set<String> anchors = new LinkedHashSet<>();
            for (JsonNode hit : draftHits) {
                String anchor = requireText(hit, "anchor", "命中锚点");
                String excerpt = requireText(hit, "excerpt", "命中证据");
                if (unitAnchors != null && !unitAnchors.contains(anchor)) {
                    throw invalid("命中锚点超出审核单元范围。");
                }
                requireExcerpt(visibleContent, excerpt);
                if (unitAnchors == null) validateFormalLocation(state, hit, anchor, excerpt);
                anchors.add(anchor + "|" + normalize(excerpt));
            }
            String identity = normalize(dimension) + "|" + normalize(title) + "|" + String.join(",", anchors);
            if (!identities.add(identity)) throw invalid("审核结果包含重复问题。");
        }
    }

    private ArrayNode normalizedUnitCandidates(JsonNode drafts, ReviewToolReadService.State state,
        String content, Set<String> unitAnchors) {
        if (!drafts.isArray() || drafts.size() > 100) throw invalid("审核问题列表格式或数量无效。");
        ArrayNode accepted = json.createArrayNode();
        for (JsonNode draft : drafts) {
            if (!draft.isObject()) continue;
            ObjectNode candidate = ((ObjectNode) draft).deepCopy();
            JsonNode hits = candidate.path("hits");
            if (hits.isArray()) {
                ArrayNode evidence = json.createArrayNode();
                boolean extracted = false;
                for (JsonNode hit : hits) {
                    if (!hit.isObject() || !hit.hasNonNull("startOffset") || !hit.hasNonNull("endOffset")) continue;
                    int start = hit.path("startOffset").asInt(-1);
                    int end = hit.path("endOffset").asInt(-1);
                    if (start < 0 || end <= start || end > content.length()) continue;
                    String excerpt = content.substring(start, end);
                    ((ObjectNode) hit).put("excerpt", excerpt);
                    evidence.add(excerpt);
                    extracted = true;
                }
                if (extracted) candidate.set("evidence", evidence);
            }
            validateIssues(json.createArrayNode().add(candidate), state, content, unitAnchors, 1);
            accepted.add(candidate);
        }
        return accepted;
    }

    private void requireExcerpt(String content, String excerpt) {
        if (excerpt == null || excerpt.isBlank() || !content.contains(excerpt)) {
            throw invalid("审核证据无法在当前范围正文中验证。");
        }
    }

    private void validateFormalLocation(ReviewToolReadService.State state, JsonNode hit,
        String anchor, String excerpt) {
        ReviewContentService.Segment segment = state.frozen().segments().stream()
            .filter(candidate -> candidate.anchor().equals(anchor)).findFirst()
            .orElseThrow(() -> invalid("命中锚点不属于当前冻结审核范围。"));
        if (!segment.content().contains(excerpt)) throw invalid("命中证据不属于所声明的审核位置。");
        if (hit.hasNonNull("episode") && (segment.episodeNo() == null
            || hit.path("episode").asInt() != segment.episodeNo())) throw invalid("命中集号超出审核位置。");
        if (hit.hasNonNull("scene") && !hit.path("scene").asText().equals(segment.sceneKey())) {
            throw invalid("命中场号超出审核位置。");
        }
    }

    private void requireFullyRead(ToolExecutionContext context, String fingerprint, int length) {
        String readFingerprint = context.runState().get("review.content.fingerprint", String.class);
        Integer coveredUntil = context.runState().get("review.content.coveredUntil", Integer.class);
        if (!fingerprint.equals(readFingerprint) || coveredUntil == null || coveredUntil < length) {
            throw invalid("审核正文尚未完整读取，不能声明完整覆盖。");
        }
    }

    private ReviewFanoutUnitEntity requireUnit(long snapshotId, long unitId) {
        return fanout.orderedUnits(snapshotId).stream().filter(unit -> unit.getId() == unitId).findFirst()
            .orElseThrow(() -> invalid("审核单元不存在。"));
    }

    private String unitContent(ReviewToolReadService.State state, ReviewFanoutUnitEntity unit) {
        if (unit.getDimension() != null && !unit.getDimension().isBlank()) {
            return state.frozen().content();
        }
        String source = state.version().getContent() == null ? "" : state.version().getContent();
        if (unit.getStartOffset() < 0 || unit.getEndOffset() > source.length()
            || unit.getStartOffset() >= unit.getEndOffset()) throw invalid("审核单元偏移已失效。");
        return source.substring(unit.getStartOffset(), unit.getEndOffset());
    }

    private ReviewIssueEntity persistIssue(ReviewToolReadService.State state, JsonNode draft,
        int index, String status, String related) {
        ReviewIssueEntity issue = baseIssue(state, index);
        issue.setDimension(draft.path("dimension").asText());
        issue.setSeverity(draft.path("severity").asText().toUpperCase());
        issue.setTitle(draft.path("title").asText());
        issue.setPositionJson(stringify(json.valueToTree(position(draft.path("hits")))));
        issue.setExcerpt(draft.path("hits").path(0).path("excerpt").asText());
        issue.setProblem(draft.path("problem").asText());
        issue.setEvidenceJson(stringify(draft.path("evidence")));
        issue.setSuggestion(draft.path("suggestion").asText());
        issue.setStatus(status);
        issue.setRelatedIssueNo(related);
        issues.insert(issue);
        return issue;
    }

    private ReviewIssueEntity baseIssue(ReviewToolReadService.State state, int index) {
        ReviewIssueEntity issue = new ReviewIssueEntity();
        ReviewTaskEntity task = state.task();
        issue.setTenantId(task.getTenantId()); issue.setProjectId(task.getProjectId()); issue.setTaskId(task.getId());
        issue.setScriptVersionId(state.version().getId()); issue.setRoundNo(task.getRoundNo());
        issue.setIssueNo("R%d-%02d".formatted(task.getRoundNo(), index)); issue.setManuallyResolved(false);
        issue.setCreatedAt(LocalDateTime.now()); issue.setUpdatedAt(LocalDateTime.now());
        return issue;
    }

    private void persistHits(ReviewTaskEntity task, ReviewIssueEntity issue, JsonNode draftHits) {
        int index = 1;
        for (JsonNode draft : draftHits) {
            ReviewIssueHitEntity hit = new ReviewIssueHitEntity();
            hit.setTenantId(task.getTenantId()); hit.setProjectId(task.getProjectId()); hit.setTaskId(task.getId());
            hit.setIssueId(issue.getId()); hit.setHitNo(index++); hit.setAnchorLabel(draft.path("anchor").asText());
            hit.setExcerpt(draft.path("excerpt").asText()); hit.setEpisodeNo(optionalInt(draft, "episode"));
            hit.setSceneNo(optionalText(draft, "scene")); hit.setShotNo(optionalInt(draft, "shot"));
            hit.setLineNo(optionalInt(draft, "line")); hit.setEntityName(optionalText(draft, "entity"));
            hit.setReplacementText(optionalText(draft, "replacementText")); hit.setSelected(true);
            hit.setCreatedAt(LocalDateTime.now()); hit.setUpdatedAt(LocalDateTime.now()); hits.insert(hit);
        }
    }

    private void persistEvent(ReviewTaskEntity task, ReviewIssueEntity issue,
        String previous, String next, Object payload) {
        ReviewIssueEventEntity event = new ReviewIssueEventEntity();
        event.setTenantId(task.getTenantId()); event.setProjectId(task.getProjectId()); event.setTaskId(task.getId());
        event.setIssueId(issue.getId()); event.setEventType("ROUND_RESULT"); event.setPreviousStatus(previous);
        event.setNewStatus(next); event.setPayloadJson(stringify(json.valueToTree(payload)));
        event.setCreatedBy(task.getCreatedBy()); event.setCreatedAt(LocalDateTime.now()); events.insert(event);
    }

    private Map<String, Object> position(JsonNode draftHits) {
        if (!draftHits.isArray() || draftHits.isEmpty()) return Map.of();
        JsonNode hit = draftHits.path(0);
        Map<String, Object> result = new LinkedHashMap<>();
        for (String field : List.of("episode", "scene", "shot", "line", "anchor")) {
            if (hit.hasNonNull(field) && !hit.path(field).asText().isBlank()) {
                result.put(field, hit.path(field).isNumber() ? hit.path(field).numberValue() : hit.path(field).asText());
            }
        }
        return result;
    }

    private Map<String, Object> map(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try { return json.readValue(value, new TypeReference<>() {}); }
        catch (Exception exception) { return Map.of(); }
    }

    private JsonNode parse(String value) {
        try { return json.readTree(value); }
        catch (Exception exception) { throw invalid("审核候选结果损坏。"); }
    }

    private String stringify(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw invalid("审核结果无法序列化。"); }
    }

    private String requireText(JsonNode node, String field, String label) {
        String value = node.path(field).asText("").trim();
        if (value.isEmpty()) throw invalid(label + "不能为空。");
        return value;
    }

    private String optionalText(JsonNode node, String field) {
        return node.hasNonNull(field) && !node.path(field).asText().isBlank() ? node.path(field).asText() : null;
    }

    private Integer optionalInt(JsonNode node, String field) {
        return node.hasNonNull(field) && node.path(field).canConvertToInt() ? node.path(field).asInt() : null;
    }

    private ObjectNode semanticDecisionJson(ReviewSemanticAuditRepository.DecisionRecord decision) {
        ObjectNode output = json.createObjectNode();
        output.put("decision", decision.decision());
        output.put("confidence", decision.confidence());
        output.put("rationale", decision.rationale());
        if (decision.severityDecision() != null) output.put("severityDecision", decision.severityDecision());
        output.set("evidenceRefs", parse(decision.evidenceRefsJson()));
        if (decision.duplicateClusterKey() != null) {
            output.put("duplicateClusterKey", decision.duplicateClusterKey());
        }
        return output;
    }

    private void requireSemanticApproval(Long snapshotId, JsonNode drafts) {
        if (snapshotId == null) throw invalid("缺少语义质检快照。");
        List<ReviewSemanticAuditRepository.CandidateRecord> candidates = semanticAudits.candidates(snapshotId);
        List<ReviewSemanticAuditRepository.DecisionRecord> decisions = semanticAudits.decisions(snapshotId);
        if (decisions.size() != candidates.size()) throw invalid("语义质检尚未覆盖全部候选。");
        JsonNode qualityCoverage = semanticAudits.qualityCoverage(snapshotId);
        boolean anomalyRequired = featureFlags.anomalyGate() && (candidates.isEmpty()
            || qualityCoverage != null && qualityCoverage.path("anomalyRequired").asBoolean(false));
        if (anomalyRequired && (qualityCoverage == null
            || !qualityCoverage.path("anomalyReview").path("passed").asBoolean(false))) {
            throw invalid("零问题异常复核未通过，不能完成正式报告。");
        }
        Map<Long, String> statuses = decisions.stream().collect(java.util.stream.Collectors.toMap(
            ReviewSemanticAuditRepository.DecisionRecord::candidateId,
            ReviewSemanticAuditRepository.DecisionRecord::decision));
        Set<Long> confirmed = statuses.entrySet().stream()
            .filter(entry -> "CONFIRMED".equals(entry.getValue()))
            .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
        Set<Long> used = new LinkedHashSet<>();
        for (JsonNode draft : drafts) {
            JsonNode sourceIds = draft.path("sourceCandidateIds");
            if (!sourceIds.isArray() || sourceIds.isEmpty()) {
                throw invalid("深度审核正式问题必须声明已确认的来源候选。");
            }
            for (JsonNode sourceId : sourceIds) {
                long id = sourceId.asLong(0);
                if (!"CONFIRMED".equals(statuses.get(id))) {
                    throw invalid("正式报告包含语义质检未确认的候选。");
                }
                used.add(id);
            }
        }
        if (!used.containsAll(confirmed)) throw invalid("正式报告遗漏了已确认候选。");
    }

    private void requireAnomalyReview(JsonNode review) {
        if (!review.isObject() || !review.has("passed") || !review.path("passed").isBoolean()) {
            throw invalid("零问题异常复核必须给出明确结论。");
        }
        requireText(review, "rationale", "零问题异常复核理由");
        JsonNode evidenceRefs = review.path("evidenceRefs");
        if (!evidenceRefs.isArray() || evidenceRefs.isEmpty()) {
            throw invalid("零问题异常复核必须提供覆盖或当前剧本证据引用。");
        }
    }

    private BigDecimal decimal(JsonNode node) {
        if (!node.isNumber()) throw invalid("语义质检置信度格式无效。");
        return node.decimalValue();
    }

    private void requirePhase(ReviewToolScope scope, String phase) {
        if (scope == null || !phase.equals(scope.phase())) throw invalid("当前审核阶段不能调用该工具。");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

}
