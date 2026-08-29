package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AssetVisualVariantServiceTest {
    @Autowired private AssetVisualVariantService service;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void insertAssets() {
        jdbc.update("""
            insert into character_asset
              (id, tenant_id, project_id, name, role_type, status, merge_target_id, created_by, created_at, updated_at)
            values (9910, 9901, 9902, '林夏', 'LEAD', 'CONFIRMED', null, 9999, now(), now())
            """);
    }

    @Test
    void createsUpdatesDeletesAndReplacesPrimaryVariant() {
        var first = service.create(9901L, 9902L, "CHARACTER", 9910L, 9999L,
            new AssetVisualVariantService.VariantCommand(
                "日常造型", "白衬衫", "日常定妆", "MANUAL", "NOT_STARTED", null, null, false));
        var second = service.create(9901L, 9902L, "CHARACTER", 9910L, 9999L,
            new AssetVisualVariantService.VariantCommand(
                "晚宴造型", "黑礼服", "晚宴定妆", "MANUAL", "NOT_STARTED", null, null, true));

        assertThat(first.primary()).isTrue();
        assertThat(service.list(9901L, 9902L, "CHARACTER", 9910L))
            .filteredOn(AssetVisualVariantService.VariantResponse::primary)
            .extracting(AssetVisualVariantService.VariantResponse::id)
            .containsExactly(second.id());

        var updated = service.update(9901L, 9902L, second.id(),
            new AssetVisualVariantService.VariantCommand(
                "晚宴礼服", "红礼服", "晚宴定妆", null, null, null, null, null));
        assertThat(updated.name()).isEqualTo("晚宴礼服");

        service.delete(9901L, 9902L, second.id());
        assertThat(service.list(9901L, 9902L, "CHARACTER", 9910L))
            .singleElement().satisfies(remaining -> {
                assertThat(remaining.id()).isEqualTo(first.id());
                assertThat(remaining.primary()).isTrue();
            });
    }

    @Test
    void recordsGenerationSuccessAndFailureIndependentlyFromAssetStatus() {
        var variant = service.create(9901L, 9902L, "CHARACTER", 9910L, 9999L,
            new AssetVisualVariantService.VariantCommand(
                "战损造型", null, "战损定妆", "GENERATED", "NOT_STARTED", null, null, true));
        assertThat(service.generationStarted(9901L, 9902L, variant.id(), 9920L).generationStatus())
            .isEqualTo("GENERATING");
        assertThat(service.generationFailed(9901L, 9902L, variant.id(), "PROVIDER_ERROR", "失败")
            .errorCode()).isEqualTo("PROVIDER_ERROR");
        var completed = service.generationSucceeded(
            9901L, 9902L, variant.id(), 9930L, "https://cdn.example.com/variant.png");

        assertThat(completed.usable()).isTrue();
        assertThat(service.primaryVisual(9901L, 9902L, "CHARACTER", 9910L).variantId())
            .isEqualTo(variant.id());
    }

    @Test
    void rejectsInvalidPolymorphicOwnership() {
        assertThatThrownBy(() -> service.create(9901L, 9902L, "SCENE", 9910L, 9999L,
            new AssetVisualVariantService.VariantCommand(
                "错误形象", null, null, "MANUAL", "NOT_STARTED", null, null, true)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("逻辑资产不存在");
    }

    @Test
    void selectingUnusablePrimaryKeepsLastUsableLegacyImageUntilReplacementCompletes() {
        var usable = service.create(9901L, 9902L, "CHARACTER", 9910L, 9999L,
            new AssetVisualVariantService.VariantCommand(
                "旧形象", null, null, "GENERATED", "COMPLETED", 9931L,
                "https://cdn.example.com/old.png", true));
        var pending = service.create(9901L, 9902L, "CHARACTER", 9910L, 9999L,
            new AssetVisualVariantService.VariantCommand(
                "新形象", null, null, "GENERATED", "NOT_STARTED", null, null, false));

        service.selectPrimary(9901L, 9902L, pending.id());

        var legacyBeforeCompletion = jdbc.queryForMap(
            "select main_image_result_id, main_image_url from character_asset where id = 9910");
        assertThat(legacyBeforeCompletion.get("main_image_result_id")).isEqualTo(9931L);
        assertThat(legacyBeforeCompletion.get("main_image_url")).isEqualTo("https://cdn.example.com/old.png");

        service.delete(9901L, 9902L, usable.id());
        var legacyAfterDeletingFallback = jdbc.queryForMap(
            "select main_image_result_id, main_image_url from character_asset where id = 9910");
        assertThat(legacyAfterDeletingFallback.get("main_image_result_id")).isNull();
        assertThat(legacyAfterDeletingFallback.get("main_image_url")).isNull();

        service.generationSucceeded(9901L, 9902L, pending.id(), 9932L, "https://cdn.example.com/new.png");
        var legacyAfterCompletion = jdbc.queryForMap(
            "select main_image_result_id, main_image_url from character_asset where id = 9910");
        assertThat(legacyAfterCompletion.get("main_image_result_id")).isEqualTo(9932L);
        assertThat(legacyAfterCompletion.get("main_image_url")).isEqualTo("https://cdn.example.com/new.png");
        assertThat(usable.id()).isNotEqualTo(pending.id());
    }
}
