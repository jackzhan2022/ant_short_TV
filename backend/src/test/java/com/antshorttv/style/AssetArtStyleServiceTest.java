package com.antshorttv.style;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.antshorttv.project.ProjectEntity;
import com.antshorttv.project.ProjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AssetArtStyleServiceTest {
    private final ProjectMapper projects = mock(ProjectMapper.class);
    private final AssetArtStyleService service = new AssetArtStyleService(projects, new ObjectMapper());

    @Test void legacyAndNewV1ProjectsDoNotReceiveArtRules() {
        ProjectEntity project = project();
        assertThat(service.rules(service.snapshot(7L, 8L), "ALL")).isEmpty();
        project.visualStyleVersion = "V1";
        assertThat(service.rules(service.snapshot(7L, 8L), "CHARACTER")).isEmpty();
        assertThat(service.rules(null, "ALL")).isEmpty();
    }

    @Test void v2LoadsOnlySelectedAssetRulesAndNeverStoryboardRules() {
        ProjectEntity project = project();
        service.configure(project, "V2", "realpeople_modern_city", "ignored");
        String snapshot = service.snapshot(7L, 8L);
        assertThat(project.visualStyle).isEqualTo("真人都市电影");
        assertThat(service.rules(snapshot, "CHARACTER")).contains("自然皮肤", "角色美术").doesNotContain("场景美术", "道具美术");
        assertThat(service.rules(snapshot, "SCENE")).contains("场景美术", "四宫格").doesNotContain("角色美术");
        assertThat(service.rules(snapshot, "STORYBOARD")).isEmpty();
        assertThat(service.rules(snapshot, "ALL")).contains("角色美术", "场景美术", "道具美术");
    }

    @Test void snapshotsKeepRulesAfterProjectSwitchAndV1CanRestoreOldStyle() {
        ProjectEntity project = project();
        service.configure(project, "V2", "3D_clay_stopmotion", null);
        String snapshot = service.snapshot(7L, 8L);
        service.configure(project, "V1", null, "写实风格-写实");
        assertThat(service.rules(snapshot, "PROP")).contains("黏土");
        assertThat(service.rules(service.snapshot(7L, 8L), "PROP")).isEmpty();
        assertThat(project.visualStyle).isEqualTo("写实风格-写实");
        assertThat(project.visualStyleCode).isEmpty();
    }

    @Test void validatesCatalogAndRejectsUnknownVersionOrStyle() {
        assertThat(service.catalog()).hasSize(11).extracting(AssetArtStyleService.Style::code).doesNotHaveDuplicates();
        ProjectEntity project = project();
        assertThatThrownBy(() -> service.configure(project, "V3", null, null)).hasMessageContaining("画风版本");
        assertThatThrownBy(() -> service.configure(project, "V2", "missing", null)).hasMessageContaining("画风");
    }

    @Test void splitsMixedV2BackfillButPreservesV1AndStoryboardRequests() {
        assertThat(AssetArtStyleService.backfillScopes("ALL", "rules")).containsExactly("ASSETS", "STORYBOARD");
        assertThat(AssetArtStyleService.backfillScopes("ALL", "")).containsExactly("ALL");
        assertThat(AssetArtStyleService.backfillScopes("STORYBOARD", "")).containsExactly("STORYBOARD");
    }

    @Test void ordinaryProjectEditPreservesV2SelectionAndTenantBoundary() {
        ProjectEntity project = project();
        service.configure(project, "V2", "2D_flat_design", null);
        service.configure(project, null, null, "stale name");
        assertThat(project.visualStyleCode).isEqualTo("2D_flat_design");
        assertThat(project.visualStyle).isEqualTo("二维扁平设计");
        assertThatThrownBy(() -> service.snapshot(99L, 8L)).hasMessageContaining("项目");
    }

    @Test void switchingFromV2ToV1RequiresAnExplicitLegacyStyle() {
        ProjectEntity project = project();
        service.configure(project, "V2", "2D_flat_design", null);
        assertThatThrownBy(() -> service.configure(project, "V1", null, ""))
            .hasMessageContaining("画风 1.0");
        assertThat(project.visualStyleVersion).isEqualTo("V2");
    }

    private ProjectEntity project() {
        ProjectEntity project = new ProjectEntity();
        project.id = 8L; project.tenantId = 7L;
        project.visualStyle = "写实风格-写实";
        when(projects.selectById(8L)).thenReturn(project);
        return project;
    }
}
