package com.antshorttv.workflowagent.skill;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.antshorttv.project.ProjectEntity;
import com.antshorttv.project.ProjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AssetExtractionSkillServiceTest {
    @TempDir Path root;
    FileSkillRepository files;
    ProjectMapper projects = mock(ProjectMapper.class);
    AssetExtractionSkillService service;

    @BeforeEach void setup() throws Exception {
        files = new FileSkillRepository(root, 1048576, new SkillDocumentParser());
        for (String code : List.of(AssetExtractionSkillService.DEFAULT, AssetExtractionSkillService.LIVE_ACTION, "short-drama-analysis-foundation"))
            files.create(code, Files.readString(Path.of("skills", code, "SKILL.md")));
        service = new AssetExtractionSkillService(files, projects);
    }

    @Test void dynamicallyListsStrategiesAndSelectsExactlyOneWithFoundation() {
        files.create("custom-production", "---\nname: 新方案\ndescription: 完整美术\npurpose: asset-extraction\nprompt-contract: complete\n---\n规则\n");
        assertThat(service.list()).extracting(AssetExtractionSkillService.Option::code)
            .containsExactlyInAnyOrder(AssetExtractionSkillService.DEFAULT, AssetExtractionSkillService.LIVE_ACTION, "custom-production");
        var project = new ProjectEntity();
        project.assetExtractionSkillCode = "custom-production";
        when(projects.selectByTenantIdAndId(7L, 8L)).thenReturn(project);
        assertThat(service.select(AssetExtractionSkillService.AGENT, 7L, 8L,
            List.of("short-drama-analysis-foundation", AssetExtractionSkillService.DEFAULT, AssetExtractionSkillService.LIVE_ACTION)))
            .containsExactly("short-drama-analysis-foundation", "custom-production");
        assertThatThrownBy(() -> service.projectCode(99L, 8L)).hasMessageContaining("项目不存在");
        assertThatThrownBy(() -> service.validate("short-drama-analysis-foundation")).hasMessageContaining("有效");
        assertThatThrownBy(() -> service.validate("absent")).hasMessageContaining("不存在");
    }

    @Test void selectedContentChangesFingerprintAndLegacyRemainsDefault() {
        var project = new ProjectEntity();
        when(projects.selectByTenantIdAndId(7L, 8L)).thenReturn(project);
        assertThat(service.projectCode(7L, 8L)).isEqualTo(AssetExtractionSkillService.DEFAULT);
        project.assetExtractionSkillCode = AssetExtractionSkillService.LIVE_ACTION;
        String before = service.fingerprint(7L, 8L);
        var current = files.get(AssetExtractionSkillService.LIVE_ACTION);
        files.update(current.code(), current.content() + "\n新的角色辨识规则\n", current.revision());
        assertThat(service.fingerprint(7L, 8L)).isNotEqualTo(before);
    }
}
