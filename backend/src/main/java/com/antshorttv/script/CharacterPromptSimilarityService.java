package com.antshorttv.script;

import com.antshorttv.ai.AiBusinessScene;
import com.antshorttv.ai.AiInvocationRequest;
import com.antshorttv.ai.AiInvocationService;
import com.antshorttv.ai.AiTextRequest;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.execution.AiExecutionClaimLostException;
import com.antshorttv.execution.AiExecutionContext;
import com.antshorttv.execution.AiExecutionResponse;
import com.antshorttv.execution.AiExecutionResponseMapper;
import com.antshorttv.execution.AiExecutionService;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CharacterPromptSimilarityService {
    static final String OPERATION = "CHARACTER_PROMPT_SIMILARITY";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ProjectPermissionGuard permissions;
    private final ScriptAiOperationService operations;
    private final AssetNormalizationGuard normalization;
    private final AiExecutionService executions;
    private final AiExecutionResponseMapper responses;
    private final AiInvocationService invocation;
    private final TransactionTemplate transactions;

    public CharacterPromptSimilarityService(JdbcTemplate jdbc, ObjectMapper json, ProjectPermissionGuard permissions,
        ScriptAiOperationService operations, AssetNormalizationGuard normalization, AiExecutionService executions,
        AiExecutionResponseMapper responses, AiInvocationService invocation, PlatformTransactionManager transactions) {
        this.jdbc = jdbc; this.json = json; this.permissions = permissions; this.operations = operations;
        this.normalization = normalization; this.executions = executions; this.responses = responses;
        this.invocation = invocation; this.transactions = new TransactionTemplate(transactions);
    }

    public AiExecutionResponse submit(long tenant, long project, String key) {
        var user = permissions.require(tenant, project, "ELEMENT:AI_EXTRACT");
        return transactions.execute(tx -> {
            var script = script(tenant, project, true);
            normalization.requireReady(tenant, project);
            JsonNode input = capture(tenant, project, number(script, "id"));
            long usable = java.util.stream.StreamSupport.stream(input.spliterator(), false)
                .filter(character -> !character.path("prompt").asText("").isBlank()).count();
            check(usable >= 2 || CharacterVariantSimilarityAnalysis.hasComparableVariants(input),
                "请先填写至少两个角色的主体提示词，或为同一角色添加至少两个有描述的形态。");
            check(input.size() <= 200 && input.toString().getBytes(StandardCharsets.UTF_8).length <= 600_000,
                "角色形态或提示词内容超过单次检查上限（200个角色，内容总计600KB）。");
            String fingerprint = fingerprint(input);
            var active = jdbc.queryForList("""
                select c.execution_id from character_prompt_similarity_check c
                join ai_execution_task e on e.id=c.execution_id and e.tenant_id=c.tenant_id
                where c.tenant_id=? and c.project_id=? and c.script_id=? and c.script_version_id=? and c.created_by=?
                  and c.input_fingerprint=? and e.status in ('PENDING','RUNNING') order by c.id desc limit 1
                """, Long.class, tenant, project, script.get("id"), script.get("current_version_id"), user.userId(), fingerprint);
            if (!active.isEmpty()) return responses.toResponse(executions.requireTask(active.get(0)));
            var execution = operations.submit(user, project, AiBusinessScene.PROMPT_GENERATE, OPERATION,
                number(script, "id"), number(script, "current_version_id"), Map.of("inputFingerprint", fingerprint), key,
                "character-prompt-similarity");
            var existing = jdbc.queryForList("select id from character_prompt_similarity_check where tenant_id=? and project_id=? and execution_id=?",
                Long.class, tenant, project, execution.id());
            if (existing.isEmpty()) {
                new SimpleJdbcInsert(jdbc).withTableName("character_prompt_similarity_check").usingGeneratedKeyColumns("id")
                    .execute(Map.of("tenant_id", tenant, "project_id", project, "script_id", script.get("id"),
                        "script_version_id", script.get("current_version_id"), "execution_id", execution.id(),
                        "input_fingerprint", fingerprint, "input_json", input.toString(),
                        "created_by", user.userId(), "created_at", LocalDateTime.now()));
            }
            return execution;
        });
    }

    public ObjectNode status(long tenant, long project) {
        permissions.require(tenant, project, "ELEMENT:VIEW");
        var scripts = jdbc.queryForList("select id,current_version_id from script where tenant_id=? and project_id=? and deleted_at is null order by id desc limit 1", tenant, project);
        ObjectNode result = json.createObjectNode().put("status", "IDLE");
        if (scripts.isEmpty()) return result;
        var script = scripts.get(0);
        var rows = jdbc.queryForList("""
            select c.*,e.status,e.error_message from character_prompt_similarity_check c
            join ai_execution_task e on e.id=c.execution_id and e.tenant_id=c.tenant_id
            where c.tenant_id=? and c.project_id=? and c.script_id=? order by c.id desc limit 1
            """, tenant, project, script.get("id"));
        if (rows.isEmpty()) return result;
        var row = rows.get(0);
        JsonNode input = read(row.get("input_json"));
        JsonNode current = capture(tenant, project, number(script, "id"));
        boolean sameVersion = Objects.equals(script.get("current_version_id"), row.get("script_version_id"));
        result.put("id", number(row, "id")).put("executionId", number(row, "execution_id"))
            .put("status", Objects.toString(row.get("status"))).put("createdAt", Objects.toString(row.get("created_at")))
            .put("stale", !sameVersion || !fingerprint(current).equals(row.get("input_fingerprint")));
        result.set("characters", input);
        if (row.get("result_json") != null) {
            JsonNode analysis = read(row.get("result_json"));
            CharacterVariantResolutionService.decorate(jdbc, tenant, project, number(row, "id"), input, current, analysis, sameVersion);
            result.set("analysis", analysis);
        }
        if (row.get("error_message") != null) result.put("errorMessage", Objects.toString(row.get("error_message")));
        return result;
    }

    ScriptAiOperationExecutionResult analyze(ScriptAiOperationEntity operation, AiExecutionContext context) {
        var rows = jdbc.queryForList("select * from character_prompt_similarity_check where tenant_id=? and project_id=? and execution_id=?",
            operation.tenantId, operation.projectId, context.task().id);
        check(rows.size() == 1, "角色形态相似度检查快照不存在。");
        var snapshot = rows.get(0);
        long id = number(snapshot, "id");
        if (snapshot.get("result_json") != null) return new ScriptAiOperationExecutionResult(OPERATION, id, List.of());
        JsonNode input = read(snapshot.get("input_json"));
        var task = context.task();
        var call = invocation.invokeText(AiInvocationRequest.text().tenantId(task.tenantId).userId(task.userId)
            .projectId(task.projectId).taskId(operation.id).modelId(task.requestedModelId)
            .businessSceneCode("prompt_generate").traceId(task.traceId).executionId(task.id)
            .attemptId(context.claim().attemptId()).executionVersion(task.executionVersion).phase(context.claim().phase())
            .idempotencyKey("character-similarity:%d:%d:%d".formatted(task.id, task.executionVersion, context.claim().attemptId()))
            .textRequest(new AiTextRequest(CharacterPromptSimilarityAnalysis.INSTRUCTIONS, input.toString(), 0.1,
                CharacterPromptSimilarityAnalysis.OUTPUT_TOKENS, null, true, CharacterPromptSimilarityAnalysis.schema(json), 180, 0)).build());
        if (call.response() == null || call.response().truncated()) throw new CharacterPromptSimilarityAnalysis.InvalidResult();
        JsonNode analysis;
        try { analysis = json.readTree(call.content()); }
        catch (Exception exception) { throw new CharacterPromptSimilarityAnalysis.InvalidResult(); }
        CharacterPromptSimilarityAnalysis.validate(json, input, analysis);
        CharacterVariantSimilarityAnalysis.addEquivalentOutfits(json, input, (ObjectNode) analysis);
        for (JsonNode revision : analysis.path("revisions")) {
            String original = CharacterPromptSimilarityAnalysis.character(input, revision.path("characterId").asLong())
                .path("prompt").asText();
            ((ObjectNode) revision).put("originalFace", CharacterPromptSimilarityAnalysis.face(original))
                .put("prompt", CharacterPromptSimilarityAnalysis.replaceFace(original, revision.path("face").asText().trim()));
        }
        ArrayNode issues = (ArrayNode) analysis.path("insufficient");
        for (JsonNode character : input) {
            if (!character.path("prompt").asText("").isBlank()) continue;
            boolean reported = java.util.stream.StreamSupport.stream(issues.spliterator(), false)
                .anyMatch(issue -> issue.path("characterId").asLong() == character.path("id").asLong());
            if (!reported) issues.addObject().put("characterId", character.path("id").asLong())
                .put("reason", "未填写主体提示词，未参与相似度比较。");
        }
        ArrayNode sorted = json.createArrayNode();
        java.util.stream.StreamSupport.stream(analysis.path("pairs").spliterator(), false)
            .sorted(Comparator.comparingInt((JsonNode p) -> p.path("score").asInt()).reversed()).forEach(sorted::add);
        ((ObjectNode) analysis).set("pairs", sorted);
        JsonNode saved = analysis;
        transactions.executeWithoutResult(tx -> {
            var current = executions.requireTaskForUpdate(task.id);
            if (!"RUNNING".equals(current.status) || !Objects.equals(current.claimToken, context.claim().claimToken())
                || !Objects.equals(current.executionVersion, context.claim().executionVersion())
                || current.claimExpiresAt == null || !current.claimExpiresAt.isAfter(LocalDateTime.now()))
                throw new AiExecutionClaimLostException(task.id);
            jdbc.update("update character_prompt_similarity_check set result_json=?,completed_at=? where id=? and tenant_id=? and execution_id=?",
                saved.toString(), LocalDateTime.now(), id, task.tenantId, task.id);
        });
        return new ScriptAiOperationExecutionResult(OPERATION, id, List.of(call));
    }

    public ObjectNode apply(long tenant, long project, long checkId, long characterId, String prompt) {
        permissions.require(tenant, project, "ELEMENT:EDIT");
        check(prompt != null && !prompt.isBlank() && prompt.length() <= 8000, "修改后的提示词不能为空或超过8000字。");
        return transactions.execute(tx -> {
            var script = script(tenant, project, true);
            normalization.requireReady(tenant, project);
            var checks = jdbc.queryForList("select * from character_prompt_similarity_check where id=? and tenant_id=? and project_id=? and script_id=?",
                checkId, tenant, project, script.get("id"));
            check(checks.size() == 1, "检查记录不存在或不属于当前项目。");
            var snapshot = checks.get(0);
            check(Objects.equals(script.get("current_version_id"), snapshot.get("script_version_id")), "剧本版本已变化，请重新检查。");
            JsonNode result = read(snapshot.get("result_json"));
            boolean proposed = java.util.stream.StreamSupport.stream(result.path("revisions").spliterator(), false)
                .anyMatch(revision -> revision.path("characterId").asLong() == characterId);
            check(proposed, "该检查记录没有这个角色的修改稿。");
            String original = CharacterPromptSimilarityAnalysis.character(read(snapshot.get("input_json")), characterId)
                .path("prompt").asText();
            var current = jdbc.queryForList("select prompt from character_asset where id=? and tenant_id=? and project_id=? and script_id=? and deleted_at is null and merge_target_id is null for update",
                characterId, tenant, project, script.get("id"));
            check(current.size() == 1, "角色已删除或合并，请重新检查。");
            String value = Objects.toString(current.get(0).get("prompt"), "");
            check(value.equals(original) || value.equals(prompt), "这个角色的提示词已被修改，请重新检查后再应用，避免覆盖现有编辑。");
            if (!value.equals(prompt)) jdbc.update("update character_asset set prompt=?,source='USER',updated_at=? where id=? and tenant_id=? and project_id=?",
                prompt, LocalDateTime.now(), characterId, tenant, project);
            return json.createObjectNode().put("characterId", characterId).put("prompt", prompt);
        });
    }

    public JsonNode capture(long tenant, long project, long script) {
        ArrayNode input = json.createArrayNode();
        for (var row : jdbc.queryForList("select id,name,prompt from character_asset where tenant_id=? and project_id=? and script_id=? and deleted_at is null and merge_target_id is null order by id", tenant, project, script)) {
            input.addObject().put("id", number(row, "id")).put("name", Objects.toString(row.get("name"), ""))
                .put("prompt", Objects.toString(row.get("prompt"), ""));
        }
        CharacterVariantSimilarityAnalysis.capture(jdbc, json, tenant, project, input);
        return input;
    }

    private Map<String, Object> script(long tenant, long project, boolean lock) {
        var rows = jdbc.queryForList("select id,current_version_id from script where tenant_id=? and project_id=? and deleted_at is null order by id desc limit 1" + (lock ? " for update" : ""), tenant, project);
        check(!rows.isEmpty() && rows.get(0).get("current_version_id") != null, "请先创建剧本并提取角色。");
        return rows.get(0);
    }

    private JsonNode read(Object value) {
        try { return json.readTree(Objects.toString(value, "null")); }
        catch (Exception exception) { throw new IllegalStateException("角色检查记录损坏。", exception); }
    }
    static String fingerprint(JsonNode value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private static long number(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    private static void check(boolean valid, String message) {
        if (!valid) throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}
