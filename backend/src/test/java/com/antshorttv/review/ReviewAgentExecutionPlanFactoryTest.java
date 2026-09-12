package com.antshorttv.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.antshorttv.common.BusinessException;
import com.antshorttv.workflowagent.agent.WorkflowAgentRecord;
import com.antshorttv.workflowagent.agent.WorkflowAgentService;
import com.antshorttv.workflowagent.skill.WorkflowSkillService;
import com.antshorttv.workflowagent.skill.WorkflowSkillView;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReviewAgentExecutionPlanFactoryTest {
    @Test
    void freezesOnlyMarkdownPhasesWithTrustedReadsAndNoWriteTools() {
        WorkflowAgentService agents = mock(WorkflowAgentService.class);
        WorkflowSkillService skills = mock(WorkflowSkillService.class);
        List<String> skillCodes = ReviewDimension.skillCodes(List.of(ReviewDimension.DIALOGUE), true);
        WorkflowAgentRecord agent = new WorkflowAgentRecord(1L, "script-review", "审核", "", "", 9L,
            BigDecimal.ZERO, 4096, 8, "ENABLED", 3L, 1L, 1L, LocalDateTime.now(), LocalDateTime.now(),
            skillCodes, List.of("read_review_context", "read_review_content"));
        when(agents.loadForRun("script-review")).thenReturn(agent);
        for (String code : skillCodes) {
            when(skills.detail(code)).thenReturn(new WorkflowSkillView(code, code, "", "instructions", "1", List.of()));
        }
        ReviewAgentExecutionPlanFactory plans = new ReviewAgentExecutionPlanFactory(agents, skills);

        for (String phase : List.of("MARKDOWN_QUICK", "MARKDOWN_DEEP_CHILD")) {
            assertThat(plans.freeze(List.of("台词合理性"), phase).agent().toolCodes())
                .containsExactly("read_review_context", "read_review_content");
        }
        assertThat(plans.freeze(List.of("台词合理性"), "MARKDOWN_DEEP_AGGREGATION").agent().toolCodes()).isEmpty();
        for (String phase : List.of("QUICK", "DEEP_CHILD", "DEEP_SEMANTIC", "DEEP_AGGREGATION")) {
            assertThatThrownBy(() -> plans.freeze(List.of("台词合理性"), phase))
                .isInstanceOf(BusinessException.class).hasMessageContaining("未知剧本审核阶段");
        }
    }
}
