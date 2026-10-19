package com.antshorttv.workflowagent.tool;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AssetBatchLookupTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ObjectMapper json = new ObjectMapper();
    private final ProjectPermissionGuard permissions = mock(ProjectPermissionGuard.class);
    private final AssetCatalogService service = new AssetCatalogService(jdbc, json, permissions);
    private ToolExecutionContext context() {
        return new ToolExecutionContext(1L, 2L, 3L, 4L, 5L, 6L, null, 7L,
            Set.of("SCRIPT:VIEW"), null, new WorkflowToolRunState());
    }
    private Map<String, Object> row(long id, String name, String aliases) {
        return Map.of("id", id, "name", name, "normalized_name", name,
            "content_json", "{\"aliases\":" + aliases + "}", "prompt", "已有提示词");
    }
    private ObjectNode batch(String... names) {
        var args = json.createObjectNode();
        var queries = args.putArray("queries");
        for (String name : names) queries.addObject().put("assetType", "PROP").put("query", name);
        return args;
    }
    @Test void matchesEightQueriesInOneCallAndLoadsEachTypeOnlyOnce() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(call ->
            call.<String>getArgument(0).contains("from prop_asset")
                ? List.of(row(9, "胡百德的手机", "[]")) : List.of());
        var args = batch("周调解员", "林记录员", "百味楼会议室", "胡百德的手机", "对账单", "记事本", "协议", "文件夹");
        ((ObjectNode) args.path("queries").get(0)).put("assetType", "CHARACTER");
        ((ObjectNode) args.path("queries").get(1)).put("assetType", "CHARACTER");
        ((ObjectNode) args.path("queries").get(2)).put("assetType", "SCENE");
        var results = service.search(context(), args).path("results");
        assertThat(results.size()).isEqualTo(8);
        assertThat(results.get(3).path("items").get(0).path("assetKey").asText()).isEqualTo("p_9");
        for (int i = 0; i < 8; i++) {
            assertThat(results.get(i).path("query").asText()).isEqualTo(args.path("queries").get(i).path("query").asText());
            assertThat(results.get(i).path("total").asInt()).isEqualTo(i == 3 ? 1 : 0);
            assertThat(results.get(i).path("hasMore").asBoolean()).isFalse();
        }
        verify(jdbc, times(3)).queryForList(anyString(), eq(1L), eq(3L), eq(5L));
    }
    @Test void preservesAliasesAmbiguityAndExactMatching() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
            row(1, "甲的手机", "[\"手机\"]"), row(2, "乙的手机", "[\"手机\"]")));
        var results = service.search(context(), batch("手机", "甲的手机", "甲手机")).path("results");
        assertThat(results.get(0).path("total").asInt()).isEqualTo(2);
        assertThat(results.get(0).path("items").size()).isEqualTo(2);
        assertThat(results.get(1).path("items").get(0).path("assetKey").asText()).isEqualTo("p_1");
        assertThat(results.get(2).path("total").asInt()).isZero();
        verify(jdbc, times(1)).queryForList(anyString(), eq(1L), eq(3L), eq(5L));
    }
    @Test void validatesTheWholeBatchScopeBeforeReadingAnyIdentity() {
        var c = context(); c.runState().put("assetScope", "PROP");
        var args = batch("手机", "角色");
        ((ObjectNode) args.path("queries").get(1)).put("assetType", "CHARACTER");
        assertThatThrownBy(() -> service.search(c, args)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(jdbc);
    }
    @Test void cannotBypassProjectPermissionWithBatchLookup() {
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "denied"))
            .when(permissions).require(1L, 3L, "SCRIPT:VIEW");
        assertThatThrownBy(() -> service.search(context(), batch("手机")))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verifyNoInteractions(jdbc);
    }
    @Test void rejectsEmptyBlankOversizedAndMixedBatchInputs() {
        assertThatThrownBy(() -> service.search(context(), batch())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.search(context(), batch(" "))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.search(context(), batch("字".repeat(201)))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.search(context(), batch("手机").put("assetType", "PROP"))).isInstanceOf(BusinessException.class);
        var tooMany = batch();
        for (int i = 0; i < 51; i++) tooMany.withArray("queries").addObject().put("assetType", "PROP").put("query", "手机");
        assertThatThrownBy(() -> service.search(context(), tooMany)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(jdbc);
    }
    @Test void batchResultsKeepPaginationInsteadOfHidingAmbiguousMatches() {
        var rows = java.util.stream.LongStream.rangeClosed(1, 51)
            .mapToObj(id -> row(id, "手机" + id, "[\"手机\"]")).toList();
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(rows);
        var c = context();
        var result = service.search(c, batch("手机")).path("results").get(0);
        assertThat(result.path("total").asInt()).isEqualTo(51);
        assertThat(result.path("items").size()).isEqualTo(50);
        assertThat(result.path("hasMore").asBoolean()).isTrue();
        var next = service.search(c, json.createObjectNode().put("assetType", "PROP").put("query", "手机")
            .put("cursor", result.path("nextCursor").asText()));
        assertThat(next.path("items").get(0).path("assetKey").asText()).isEqualTo("p_51");
    }
    @Test void refusesOversizedCombinedResponsesWithoutReturningFalseMisses() {
        var aliases = "[\"" + "别名".repeat(1500) + "\"]";
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row(1, "手机", aliases)));
        assertThatThrownBy(() -> service.search(context(), batch("手机", "手机", "手机", "手机")))
            .isInstanceOf(BusinessException.class).hasMessageContaining("批量");
    }
    @Test void exposesBatchSchemaWhileKeepingSingleQueryCompatible() {
        var definition = new ScreenplayToolConfiguration().searchScriptAssetsTool(service, json);
        var validator = new WorkflowToolSchemaValidator();
        assertThat(definition.inputSchema().path("properties").path("queries").path("maxItems").asInt()).isEqualTo(50);
        validator.validate(definition.inputSchema(), batch("手机"));
        validator.validate(definition.inputSchema(), json.createObjectNode().put("assetType", "PROP").put("query", "手机"));
        assertThatThrownBy(() -> validator.validate(definition.inputSchema(), batch("手机").put("tenantId", 99)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
