package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AssetSummaryImagePreviewTest {
    private JdbcTemplate jdbc;
    private ScriptWorkflowService service;

    @BeforeEach
    void setUp() throws Exception {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:cover_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("create table script (id bigint, tenant_id bigint, project_id bigint, deleted_at timestamp, updated_at timestamp)");
        String common = "id bigint, tenant_id bigint, project_id bigint, script_id bigint, name varchar(50), prompt varchar(100), status varchar(30), merge_target_id bigint, main_image_url varchar(100), main_image_result_id bigint, deleted_at timestamp";
        jdbc.execute("create table character_asset (" + common + ", role_type varchar(30), gender varchar(30), age_range varchar(30), identity varchar(50), personality varchar(50), appearance varchar(50))");
        jdbc.execute("create table scene_asset (" + common + ", scene_type varchar(30), time_atmosphere varchar(30), description varchar(50), visual_style varchar(30))");
        jdbc.execute("create table prop_asset (" + common + ", prop_type varchar(30), appearance varchar(50), plot_function varchar(50))");
        jdbc.execute("create table asset_visual_variant (id bigint, tenant_id bigint, project_id bigint, asset_type varchar(30), asset_id bigint, is_primary boolean, generation_status varchar(30), generation_task_id bigint, current_image_result_id bigint, deleted_at timestamp)");
        jdbc.execute("create table ai_image_result (id bigint, tenant_id bigint, project_id bigint, target_type varchar(30), target_id bigint, task_id bigint, is_selected boolean, status varchar(30))");
        for (String table : java.util.List.of("character_asset", "scene_asset", "prop_asset")) {
            jdbc.update("insert into " + table + " (id, tenant_id, project_id, name, status) values (1, 10, 33, 'Asset', 'CONFIRMED')");
        }
        var dependencies = new HashMap<Class<?>, Object>();
        dependencies.put(JdbcTemplate.class, jdbc);
        var constructor = ScriptWorkflowService.class.getConstructors()[0];
        Object[] arguments = Arrays.stream(constructor.getParameterTypes())
            .map(type -> dependencies.computeIfAbsent(type, key -> mock(key, RETURNS_DEEP_STUBS))).toArray();
        service = (ScriptWorkflowService) constructor.newInstance(arguments);
        when(((TenantContextResolver) dependencies.get(TenantContextResolver.class)).requireActiveMember(10L))
            .thenReturn(new TenantContext(1L, 10L, 1L, "OWNER"));
    }

    private void variant(long id, String type, boolean primary, String status, long taskId) {
        jdbc.update("insert into asset_visual_variant (id, tenant_id, project_id, asset_type, asset_id, is_primary, generation_status, generation_task_id) values (?, 10, 33, ?, 1, ?, ?, ?)", id, type, primary, status, taskId);
    }

    private void image(long id, long variantId, long taskId, boolean selected, String status) {
        jdbc.update("insert into ai_image_result values (?, 10, 33, 'VISUAL_VARIANT', ?, ?, ?, ?)", id, variantId, taskId, selected, status);
    }

    private JsonNode summary() {
        return new ObjectMapper().valueToTree(service.assetSettingsSummary(10L, 33L));
    }

    @Test
    void exposesGeneratingStatusWithoutExpandingVisuals() {
        variant(11, "CHARACTER", true, "GENERATING", 80);
        assertThat(summary().path("characters").get(0).path("imagePreview").path("generationStatus").asText())
            .isEqualTo("GENERATING");
    }

    @Test
    void showsAlreadyGeneratedUnselectedImagesForEveryAssetType() {
        variant(11, "CHARACTER", true, "COMPLETED", 80);
        variant(12, "SCENE", true, "COMPLETED", 81);
        variant(13, "PROP", true, "COMPLETED", 82);
        image(101, 11, 80, false, "ACTIVE");
        image(102, 12, 81, false, "ACTIVE");
        image(103, 13, 82, false, "ACTIVE");
        JsonNode result = summary();
        for (String key : java.util.List.of("characters", "scenes", "props")) {
            assertThat(result.path(key).get(0).path("imagePreview").path("pendingImageThumbnailUrl").asText())
                .startsWith("/api/projects/33/ai-image-results/10").endsWith("/thumbnail");
        }
    }

    @Test
    void ignoresInactiveForeignAndOldTaskResultsAndSuppressesConfirmedTaskSiblings() {
        variant(11, "CHARACTER", true, "COMPLETED", 80);
        image(100, 11, 79, false, "ACTIVE");
        image(101, 11, 80, false, "PROCESSING");
        image(102, 11, 80, false, "DELETED");
        image(103, 11, 80, false, "ACTIVE");
        jdbc.update("update ai_image_result set tenant_id = 99 where id = 103");
        assertThat(summary().path("characters").get(0).path("imagePreview").path("pendingImageThumbnailUrl").isNull()).isTrue();
        image(104, 11, 80, false, "ACTIVE");
        assertThat(summary().path("characters").get(0).path("imagePreview").path("pendingImageThumbnailUrl").asText())
            .isEqualTo("/api/projects/33/ai-image-results/104/thumbnail");
        image(105, 11, 80, true, "ACTIVE");
        assertThat(summary().path("characters").get(0).path("imagePreview").path("pendingImageThumbnailUrl").isNull()).isTrue();
    }

    @Test
    void retainsTheExistingPendingImageWhileRegeneratingOrAfterFailure() {
        variant(11, "CHARACTER", true, "GENERATING", 81);
        jdbc.update("update asset_visual_variant set current_image_result_id = 101 where id = 11");
        image(101, 11, 80, false, "ACTIVE");
        assertThat(summary().path("characters").get(0).path("imagePreview").path("pendingImageThumbnailUrl").asText())
            .isEqualTo("/api/projects/33/ai-image-results/101/thumbnail");
        jdbc.update("update asset_visual_variant set generation_status = 'FAILED' where id = 11");
        assertThat(summary().path("characters").get(0).path("imagePreview").path("pendingImageThumbnailUrl").asText())
            .isEqualTo("/api/projects/33/ai-image-results/101/thumbnail");
    }

    @Test
    void prefersPrimaryPendingImageAndKeepsProgressForOtherVariants() {
        variant(11, "CHARACTER", true, "COMPLETED", 80);
        variant(12, "CHARACTER", false, "GENERATING", 81);
        image(101, 11, 80, false, "ACTIVE");
        image(102, 12, 81, false, "ACTIVE");
        JsonNode preview = summary().path("characters").get(0).path("imagePreview");
        assertThat(preview.path("generationStatus").asText()).isEqualTo("GENERATING");
        assertThat(preview.path("pendingImageThumbnailUrl").asText())
            .isEqualTo("/api/projects/33/ai-image-results/101/thumbnail");
        jdbc.update("update asset_visual_variant set deleted_at = current_timestamp where id = 11");
        assertThat(summary().path("characters").get(0).path("imagePreview").path("pendingImageThumbnailUrl").isNull()).isTrue();
    }
}
