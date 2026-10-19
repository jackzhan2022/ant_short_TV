package com.antshorttv.workflowagent.tool;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

class AssetDetailFallbackTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ObjectMapper json = new ObjectMapper();
    private final ProjectPermissionGuard permissions = mock(ProjectPermissionGuard.class);
    private final AssetCatalogService service = new AssetCatalogService(jdbc, json, permissions);

    private ToolExecutionContext context() {
        var c = new ToolExecutionContext(1L, 2L, 3L, 4L, 5L, 6L, null, 7L,
            Set.of("SCRIPT:VIEW"), null, new WorkflowToolRunState());
        c.runState().put("assetScope", "PROP");
        return c;
    }
    private ObjectNode args(int count) {
        var args = json.createObjectNode();
        var keys = args.putArray("assetKeys");
        for (int i = 1; i <= count; i++) keys.add("p_" + i);
        return args;
    }
    private Map<String, Object> asset(long id, String description) {
        return Map.of("id", id, "name", "道具" + id, "normalized_name", "道具" + id,
            "content_json", json.createObjectNode().put("description", description).put("evidence", "原文证据").toString(),
            "prompt", "已有资产提示词");
    }
    private Map<String, Object> variant(long id, String prompt) {
        return Map.of("id", id, "name", "完整形态" + id, "is_primary", true, "prompt", prompt,
            "content_json", json.createObjectNode().put("description", "金属外壳、边缘有划痕")
                .put("evidence", "原文形态证据").put("prompt", prompt).toString());
    }
    private void data(String description, String prompt) {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.contains("from prop_asset")) return List.of(asset(((Number) call.getArguments()[4]).longValue(), description));
            return List.of(variant(101, prompt));
        });
    }
    private void bounded(ObjectNode result) {
        assertThat(result.toString().getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(AssetCatalogService.BYTE_LIMIT);
    }
    @Test void defaultsToFullDetailsAndPreservesExistingContent() {
        data("完整外观描述", "完整旧提示词");
        var result = service.details(context(), args(1));
        assertThat(result.path("detailLevel").asText()).isEqualTo("FULL");
        assertThat(result.path("degraded").asBoolean()).isFalse();
        var item = result.path("items").get(0);
        assertThat(item.path("content").path("evidence").asText()).isEqualTo("原文证据");
        var look = item.path("variants").get(0);
        assertThat(look.path("content").path("prompt").asText()).isEqualTo("完整旧提示词");
        assertThat(look.path("content").path("evidence").asText()).isEqualTo("原文形态证据");
        bounded(result);
    }
    @Test void oversizedAssetFallsBackWithoutRetryingTheDataRead() {
        data("外观".repeat(20_000), "旧提示词");
        var result = service.details(context(), args(1));
        assertThat(result.path("detailLevel").asText()).isEqualTo("SUMMARY");
        assertThat(result.path("degraded").asBoolean()).isTrue();
        assertThat(result.path("degradationReason").asText()).isEqualTo("RESPONSE_SIZE_LIMIT");
        var item = result.path("items").get(0);
        assertThat(item.path("assetKey").asText()).isEqualTo("p_1");
        assertThat(item.path("contentTruncated").asBoolean()).isTrue();
        assertThat(item.path("content").path("description").asText()).isNotBlank().hasSizeLessThanOrEqualTo(120);
        assertThat(item.path("content").has("evidence")).isFalse();
        verify(jdbc, times(1)).queryForList(contains("from prop_asset"), eq(1L), eq(3L), eq(5L), eq(1L));
        bounded(result);
    }
    @Test void oversizedVariantOmitsLongPromptButKeepsIdentityAndVisualSummary() {
        data("资产外观", "提示词".repeat(12_000));
        var result = service.details(context(), args(1));
        var look = result.path("items").get(0).path("variants").get(0);
        assertThat(result.path("detailLevel").asText()).isEqualTo("SUMMARY");
        assertThat(look.path("variantKey").asText()).isEqualTo("v_101");
        assertThat(look.path("name").asText()).isEqualTo("完整形态101");
        assertThat(look.path("hasPrompt").asBoolean()).isTrue();
        assertThat(look.path("primary").asBoolean()).isTrue();
        assertThat(look.path("content").path("description").asText()).isEqualTo("金属外壳、边缘有划痕");
        assertThat(look.path("content").has("prompt")).isFalse();
        assertThat(look.path("content").has("evidence")).isFalse();
        bounded(result);
    }
    @Test void aggregateOverflowKeepsAllTenRequestedAssets() {
        data("资产外观".repeat(150), "旧提示词".repeat(200));
        var result = service.details(context(), args(10));
        assertThat(result.path("detailLevel").asText()).isEqualTo("SUMMARY");
        assertThat(result.path("items").size()).isEqualTo(10);
        for (int i = 0; i < 10; i++) {
            var item = result.path("items").get(i);
            assertThat(item.path("assetKey").asText()).isEqualTo("p_" + (i + 1));
            assertThat(item.path("variants").size()).isEqualTo(1);
        }
        bounded(result);
    }
    @Test void summariesPaginateWithoutLosingVariantsWhenTenAssetsExhaustTheBudget() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.contains("from prop_asset")) return List.of(asset(((Number) call.getArguments()[4]).longValue(), "资产外观"));
            long after = ((Number) call.getArguments()[5]).longValue();
            return IntStream.rangeClosed(1, 51).filter(i -> i > after).limit(21)
                .mapToObj(i -> {
                    var row = new java.util.HashMap<>(variant(i, "长提示词".repeat(1000)));
                    row.put("name", "形".repeat(100));
                    row.put("content_json", json.createObjectNode().put("description", "描述".repeat(120))
                        .put("prompt", "长提示词".repeat(1000)).toString());
                    return row;
                }).toList();
        });
        var c = context();
        var first = service.details(c, args(10));
        assertThat(first.path("detailLevel").asText()).isEqualTo("SUMMARY");
        assertThat(first.path("items").size()).isEqualTo(10);
        bounded(first);
        Set<String> seen = new HashSet<>();
        var item = first.path("items").get(0);
        int pages = 0;
        while (true) {
            assertThat(++pages).isLessThan(60);
            assertThat(item.path("variants").isEmpty()).isFalse();
            item.path("variants").forEach(v -> assertThat(seen.add(v.path("variantKey").asText())).isTrue());
            if (!item.path("hasMore").asBoolean()) break;
            var request = args(1).put("cursor", item.path("nextCursor").asText());
            var page = service.details(c, request); bounded(page); item = page.path("items").get(0);
        }
        assertThat(seen).hasSize(51);
    }
    @Test void nonSizeFailuresNeverBecomeSummaries() {
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "denied")).when(permissions).require(1L, 3L, "SCRIPT:VIEW");
        assertThatThrownBy(() -> service.details(context(), args(1))).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verifyNoInteractions(jdbc);
    }
    @Test void missingAssetsAndDatabaseFailuresAreNotHiddenAfterAnOversizedAsset() {
        data("长描述".repeat(20_000), "旧提示词");
        when(jdbc.queryForList(contains("from prop_asset"), eq(1L), eq(3L), eq(5L), eq(2L))).thenReturn(List.of());
        assertThatThrownBy(() -> service.details(context(), args(2))).isInstanceOf(BusinessException.class).hasMessageContaining("不属于");
        when(jdbc.queryForList(contains("from prop_asset"), eq(1L), eq(3L), eq(5L), eq(2L)))
            .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        assertThatThrownBy(() -> service.details(context(), args(2))).isInstanceOf(DataAccessResourceFailureException.class);
    }
    @Test void corruptMetadataAndForeignCursorsAreNotHiddenByFallback() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
            "id", 1L, "name", "道具", "normalized_name", "道具", "content_json", "invalid json")));
        assertThatThrownBy(() -> service.details(context(), args(1))).isInstanceOf(BusinessException.class).hasMessageContaining("元数据损坏");
        data("长描述".repeat(20_000), "旧提示词");
        assertThatThrownBy(() -> service.details(context(), args(1).put("cursor", "foreign")))
            .isInstanceOf(BusinessException.class).hasMessageContaining("游标");
    }
}
