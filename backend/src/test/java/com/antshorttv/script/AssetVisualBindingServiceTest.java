package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.common.BusinessException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AssetVisualBindingServiceTest {
    @Autowired private AssetVisualBindingService bindingService;
    @Autowired private AssetVisualVariantService variantService;
    @Autowired private EpisodeAwareVisualResolver resolver;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void prepare() {
        jdbc.update("""
            insert into character_asset
              (id, tenant_id, project_id, name, role_type, status, merge_target_id, main_image_url,
               created_by, created_at, updated_at)
            values (10010, 10001, 10002, '林夏', 'LEAD', 'CONFIRMED', null,
                    'https://cdn.example.com/legacy.png', 10099, now(), now())
            """);
        jdbc.update("""
            insert into script
              (id, tenant_id, project_id, title, source_type, content, status, current_version_id,
               created_by, created_at, updated_at)
            values (10020, 10001, 10002, '剧本', 'MANUAL_EDIT', '正文', 'DRAFT', 10021,
                    10099, now(), now())
            """);
        jdbc.update("""
            insert into script_episode
              (id, tenant_id, project_id, script_id, script_version_id, stable_key, episode_no,
               title, content, content_fingerprint, reconciliation_status, status, created_at, updated_at)
            values
              (10031, 10001, 10002, 10020, 10021, 'ep-1', 1, '第1集', 'A', 'a', 'MATCHED', 'ACTIVE', now(), now()),
              (10032, 10001, 10002, 10020, 10021, 'ep-2', 2, '第2集', 'B', 'b', 'MATCHED', 'ACTIVE', now(), now())
            """);
    }

    @Test
    void batchBindsAlternativesAndAtomicallyReplacesPreferredVariant() {
        var primary = variant("日常", "https://cdn.example.com/daily.png", true);
        var costume = variant("礼服", "https://cdn.example.com/dress.png", false);

        bindingService.bind(10001L, 10002L, primary.id(), 10099L,
            new AssetVisualBindingService.BindingCommand(List.of(10031L, 10032L), false));
        bindingService.bind(10001L, 10002L, costume.id(), 10099L,
            new AssetVisualBindingService.BindingCommand(List.of(10031L), true));
        bindingService.bind(10001L, 10002L, primary.id(), 10099L,
            new AssetVisualBindingService.BindingCommand(List.of(10031L), true));

        assertThat(bindingService.list(10001L, 10002L, "CHARACTER", 10010L))
            .filteredOn(AssetVisualBindingService.BindingResponse::preferred)
            .extracting(AssetVisualBindingService.BindingResponse::variantId)
            .containsExactly(primary.id());
    }

    @Test
    void resolvesPreferredThenPrimaryThenLegacyWithoutUsingAnotherEpisodesVariant() {
        assertThat(resolver.resolve(10001L, 10002L, "CHARACTER", 10010L, 10032L).source())
            .isEqualTo("LEGACY_FALLBACK");
        var primary = variant("日常", "https://cdn.example.com/daily.png", true);
        var costume = variant("礼服", "https://cdn.example.com/dress.png", false);
        bindingService.bind(10001L, 10002L, costume.id(), 10099L,
            new AssetVisualBindingService.BindingCommand(List.of(10031L), true));

        assertThat(resolver.resolve(10001L, 10002L, "CHARACTER", 10010L, 10031L).source())
            .isEqualTo("EPISODE_PREFERRED");
        assertThat(resolver.resolve(10001L, 10002L, "CHARACTER", 10010L, 10032L).variantId())
            .isEqualTo(primary.id());

        variantService.delete(10001L, 10002L, primary.id());
        assertThat(resolver.resolve(10001L, 10002L, "CHARACTER", 10010L, 10032L).source())
            .isEqualTo("PRIMARY_VARIANT");

        variantService.delete(10001L, 10002L, costume.id());
        assertThat(resolver.resolve(10001L, 10002L, "CHARACTER", 10010L, 10032L).source())
            .isEqualTo("UNRESOLVED");
        assertThat(resolver.legacyFallbackCount()).isPositive();
    }

    @Test
    void putReplacesTheCompleteSelectionAndAllowsClearingAllBindings() {
        var costume = variant("礼服", "https://cdn.example.com/dress.png", false);
        bindingService.bind(10001L, 10002L, costume.id(), 10099L,
            new AssetVisualBindingService.BindingCommand(List.of(10031L, 10032L), true));

        bindingService.bind(10001L, 10002L, costume.id(), 10099L,
            new AssetVisualBindingService.BindingCommand(List.of(10032L), true));
        assertThat(bindingService.list(10001L, 10002L, "CHARACTER", 10010L))
            .extracting(AssetVisualBindingService.BindingResponse::episodeId)
            .containsExactly(10032L);

        bindingService.bind(10001L, 10002L, costume.id(), 10099L,
            new AssetVisualBindingService.BindingCommand(List.of(), true));
        assertThat(bindingService.list(10001L, 10002L, "CHARACTER", 10010L)).isEmpty();
    }

    @Test
    void rejectsCrossProjectAndRetiredEpisodes() {
        var variant = variant("日常", "https://cdn.example.com/daily.png", true);
        jdbc.update("update script_episode set status = 'RETIRED', retired_at = now() where id = 10031");
        assertThatThrownBy(() -> bindingService.bind(10001L, 10002L, variant.id(), 10099L,
            new AssetVisualBindingService.BindingCommand(List.of(10031L), true)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("已退役");
        assertThatThrownBy(() -> bindingService.bind(10001L, 99902L, variant.id(), 10099L,
            new AssetVisualBindingService.BindingCommand(List.of(10032L), true)))
            .isInstanceOf(BusinessException.class);
    }

    private AssetVisualVariantService.VariantResponse variant(String name, String url, boolean primary) {
        return variantService.create(10001L, 10002L, "CHARACTER", 10010L, 10099L,
            new AssetVisualVariantService.VariantCommand(
                name, name, name, "MANUAL", "COMPLETED", null, url, primary));
    }
}
