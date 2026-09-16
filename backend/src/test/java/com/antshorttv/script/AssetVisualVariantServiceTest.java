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
        jdbc.update("""
            insert into scene_asset
              (id, tenant_id, project_id, name, scene_type, prompt, status, created_by, created_at, updated_at)
            values (9911, 9901, 9902, '地下停车场', 'INDOOR', '昏暗地下停车场，冷色电影光影', 'CONFIRMED', 9999, now(), now())
            """);
        jdbc.update("""
            insert into prop_asset
              (id, tenant_id, project_id, name, prop_type, prompt, status, created_by, created_at, updated_at)
            values (9912, 9901, 9902, '旧怀表', 'KEY_PROP', '黄铜旧怀表，表面有划痕', 'CONFIRMED', 9999, now(), now())
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

    @Test
    void primaryVariantsExposeCanonicalPromptsAndIgnoreStoredVariantPrompts() {
        jdbc.update("update character_asset set prompt = '角色主体提示词' where id = 9910");
        var character = service.create(9901L, 9902L, "CHARACTER", 9910L, 9999L,
            new AssetVisualVariantService.VariantCommand(
                "默认形态", null, null, "MANUAL", "NOT_STARTED", null, null, true));
        var scene = service.create(9901L, 9902L, "SCENE", 9911L, 9999L,
            new AssetVisualVariantService.VariantCommand(
                "默认场景", null, null, "MANUAL", "NOT_STARTED", null, null, true));
        var prop = service.create(9901L, 9902L, "PROP", 9912L, 9999L,
            new AssetVisualVariantService.VariantCommand(
                "默认形态", null, null, "MANUAL", "NOT_STARTED", null, null, true));
        jdbc.update("update asset_visual_variant set prompt = '旧角色变体提示词' where id = ?", character.id());
        jdbc.update("update asset_visual_variant set prompt = '旧场景变体提示词' where id = ?", scene.id());
        jdbc.update("update asset_visual_variant set prompt = '旧道具变体提示词' where id = ?", prop.id());

        assertThat(service.list(9901L, 9902L, "CHARACTER", 9910L)).singleElement()
            .extracting(AssetVisualVariantService.VariantResponse::prompt).isEqualTo("角色主体提示词");
        assertThat(service.list(9901L, 9902L, "SCENE", 9911L)).singleElement()
            .extracting(AssetVisualVariantService.VariantResponse::prompt).isEqualTo("昏暗地下停车场，冷色电影光影");
        assertThat(service.list(9901L, 9902L, "PROP", 9912L)).singleElement()
            .extracting(AssetVisualVariantService.VariantResponse::prompt).isEqualTo("黄铜旧怀表，表面有划痕");

        assertThat(jdbc.queryForObject("select prompt from asset_visual_variant where id = ?", String.class, character.id()))
            .isEqualTo("旧角色变体提示词");
        assertThat(jdbc.queryForObject("select prompt from asset_visual_variant where id = ?", String.class, scene.id()))
            .isEqualTo("旧场景变体提示词");
        assertThat(jdbc.queryForObject("select prompt from asset_visual_variant where id = ?", String.class, prop.id()))
            .isEqualTo("旧道具变体提示词");
    }

    @Test
    void usesCanonicalAndDeltaPromptsVerbatimWithOnlyTheCanonicalCharacterImage() {
        jdbc.update("update character_asset set prompt = 'canonical character markdown' where id = 9910");
        var primary = service.create(9901L, 9902L, "CHARACTER", 9910L, 9999L,
            new AssetVisualVariantService.VariantCommand(
                "日常造型", null, null, "GENERATED", "COMPLETED", 9930L,
                "https://cdn.example.com/canonical.png", true));
        jdbc.update("update asset_visual_variant set prompt = 'must not be used' where id = ?", primary.id());
        var delta = service.create(9901L, 9902L, "CHARACTER", 9910L, 9999L,
            new AssetVisualVariantService.VariantCommand(
                "雨天白裙", "湿裙", "性别:女；衣着描述:白色连衣裙，裙摆湿透，赤脚", "MANUAL", "NOT_STARTED", null, null, false));

        assertThat(service.prepareGeneration(9901L, 9902L, primary.id()))
            .isEqualTo(new AssetVisualVariantService.GenerationInput("canonical character markdown", java.util.List.of()));
        assertThat(service.prepareGeneration(9901L, 9902L, delta.id()))
            .isEqualTo(new AssetVisualVariantService.GenerationInput(
                "性别:女；衣着描述:白色连衣裙，裙摆湿透，赤脚",
                java.util.List.of("https://cdn.example.com/canonical.png")));
    }

    @Test
    void doesNotDeriveAnEmptyNonPrimaryVariantPromptDuringReadOrGeneration() {
        jdbc.update("update character_asset set prompt = '林夏角色定妆照' where id = 9910");
        service.create(9901L, 9902L, "CHARACTER", 9910L, 9999L,
            new AssetVisualVariantService.VariantCommand(
                "主体", null, null, "GENERATED", "COMPLETED", 9930L,
                "https://cdn.example.com/canonical.png", true));
        var variant = service.create(9901L, 9902L, "CHARACTER", 9910L, 9999L,
            new AssetVisualVariantService.VariantCommand(
                "雨天造型", "湿发白裙", null, "MANUAL", "NOT_STARTED", null, null, false));

        assertThat(service.list(9901L, 9902L, "CHARACTER", 9910L))
            .filteredOn(item -> item.id().equals(variant.id())).singleElement()
            .extracting(AssetVisualVariantService.VariantResponse::prompt).isNull();
        assertThatThrownBy(() -> service.prepareGeneration(9901L, 9902L, variant.id()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("视觉形象提示词");
        assertThat(jdbc.queryForObject("select prompt from asset_visual_variant where id = ?", String.class, variant.id()))
            .isNull();
    }

    @Test
    void routesPrimaryPromptEditsToCanonicalAssetAndNonPrimaryEditsToVariant() {
        jdbc.update("update character_asset set prompt = '原主体提示词' where id = 9910");
        var primary = service.create(9901L, 9902L, "CHARACTER", 9910L, 9999L,
            new AssetVisualVariantService.VariantCommand(
                "默认形态", null, null, "AI", "NOT_STARTED", null, null, true));
        jdbc.update("update asset_visual_variant set prompt = '历史主形象提示词' where id = ?", primary.id());
        var look = service.create(9901L, 9902L, "CHARACTER", 9910L, 9999L,
            new AssetVisualVariantService.VariantCommand(
                "雨天造型", "湿发白裙", "原变装提示词", "AI", "NOT_STARTED", null, null, false));

        var updatedPrimary = service.update(9901L, 9902L, primary.id(),
            new AssetVisualVariantService.VariantCommand(
                "默认形态", null, "新主体提示词", null, null, null, null, null));
        var updatedLook = service.update(9901L, 9902L, look.id(),
            new AssetVisualVariantService.VariantCommand(
                "雨天造型", "湿发白裙", "新变装提示词", null, null, null, null, null));

        assertThat(updatedPrimary.prompt()).isEqualTo("新主体提示词");
        assertThat(updatedLook.prompt()).isEqualTo("新变装提示词");
        assertThat(jdbc.queryForObject("select prompt from character_asset where id = 9910", String.class))
            .isEqualTo("新主体提示词");
        assertThat(jdbc.queryForObject("select prompt from asset_visual_variant where id = ?", String.class, primary.id()))
            .isEqualTo("历史主形象提示词");
        assertThat(jdbc.queryForObject("select prompt from asset_visual_variant where id = ?", String.class, look.id()))
            .isEqualTo("新变装提示词");
    }

    @Test
    void keepsSceneVariantGenerationTextOnlyWithoutAPrimaryImage() {
        service.create(9901L, 9902L, "SCENE", 9911L, 9999L,
            new AssetVisualVariantService.VariantCommand(
                "默认版本", null, null, "MANUAL", "NOT_STARTED", null, null, true));
        var variant = service.create(9901L, 9902L, "SCENE", 9911L, 9999L,
            new AssetVisualVariantService.VariantCommand(
                "雨夜版本", "地面积水反光", "雨夜地下停车场，地面积水反光", "MANUAL", "NOT_STARTED", null, null, false));

        assertThat(service.prepareGeneration(9901L, 9902L, variant.id()))
            .isEqualTo(new AssetVisualVariantService.GenerationInput(
                "雨夜地下停车场，地面积水反光", java.util.List.of()));
    }
}
