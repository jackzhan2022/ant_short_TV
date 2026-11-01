package com.antshorttv.workflowagent.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.antshorttv.common.BusinessException;
import com.antshorttv.project.ProjectEntity;
import com.antshorttv.project.ProjectMapper;
import com.antshorttv.project.StoryboardSkillVersion;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectStoryboardSkillSelectorTest {
    private final ProjectMapper projects = mock(ProjectMapper.class);
    private final ProjectStoryboardSkillSelector selector = new ProjectStoryboardSkillSelector(projects);

    @Test
    void selectsExactlyOneVersionAndDefaultsLegacyProjectsToV1() {
        ProjectEntity project = new ProjectEntity();
        when(projects.selectByTenantIdAndId(7L, 12L)).thenReturn(project);
        assertThat(selector.select("short-drama-storyboard", 7L, 12L, List.of(StoryboardSkillVersion.V1.skillCode(), "custom")))
            .containsExactly(StoryboardSkillVersion.V1.skillCode(), "custom");
        project.storyboardSkillVersion = "V2";
        assertThat(selector.select("short-drama-storyboard", 7L, 12L,
            List.of(StoryboardSkillVersion.V1.skillCode(), "short-drama-storyboard-planning", "custom")))
            .containsExactly(StoryboardSkillVersion.V2.skillCode(), "custom");
    }

    @Test
    void refusesUnknownVersionsAndProjectsOutsideTheTenant() {
        assertThatThrownBy(() -> StoryboardSkillVersion.resolve("other-skill"))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> selector.select("short-drama-storyboard", 8L, 12L, List.of()))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> selector.select("short-drama-storyboard", null, 12L, List.of()))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void leavesOtherAgentsAndProjectlessConfigurationAlone() {
        List<String> configured = List.of("summary-skill");
        assertThat(selector.select("short-drama-episode-summary", 7L, 12L, configured)).isEqualTo(configured);
        assertThat(selector.select("short-drama-storyboard", 7L, null, configured)).isEqualTo(configured);
        verifyNoInteractions(projects);
    }
}
