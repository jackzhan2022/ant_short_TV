package com.antshorttv.workflowagent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.review.ReviewAgentExecutionPlanFactory;
import com.antshorttv.review.ReviewDimension;
import com.antshorttv.workflowagent.run.WorkflowAgentExecutionPlan;
import com.antshorttv.workflowagent.skill.FileSkillRepository;
import com.antshorttv.workflowagent.skill.SkillDocumentParser;
import com.antshorttv.workflowagent.skill.SkillReferenceLookup;
import com.antshorttv.workflowagent.skill.WorkflowSkillService;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

class ScriptReviewAgentBootstrapTest {
    private final WorkflowAgentRepository agents = mock(WorkflowAgentRepository.class);
    private final WorkflowAgentService service = mock(WorkflowAgentService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ScriptReviewAgentBootstrap bootstrap = new ScriptReviewAgentBootstrap(agents, service, jdbc);

    @Test
    void bootstrapsReadOnlyDefinitionWithoutFeatureFlagAndNarrowsMarkdownPhases() {
        when(agents.get(ScriptReviewAgentBootstrap.AGENT_CODE))
            .thenThrow(new BusinessException(ErrorCode.WORKFLOW_AGENT_NOT_FOUND, "missing"));
        when(jdbc.queryForList(anyString(), eq(Long.class))).thenReturn(List.of(9L));

        bootstrap.run(new DefaultApplicationArguments(new String[0]));

        ArgumentCaptor<WorkflowAgentCommand> created = ArgumentCaptor.forClass(WorkflowAgentCommand.class);
        verify(agents).create(created.capture(), isNull());
        WorkflowAgentCommand definition = created.getValue();
        verify(service).validate(definition, true);
        assertThat(definition.status()).isEqualTo("ENABLED");
        assertThat(definition.maxSteps()).isEqualTo(20);
        assertThat(definition.skillCodes()).hasSize(16).contains(
            "script-review-foundation", "script-review-execution-framework",
            "script-review-cross-episode-synthesis");
        assertThat(definition.toolCodes()).containsExactly("read_review_context", "read_review_content");

        when(service.loadForRun("script-review")).thenReturn(record(definition));
        WorkflowSkillService skills = new WorkflowSkillService(
            new FileSkillRepository(Path.of("skills"), 1024 * 1024, new SkillDocumentParser()),
            mock(SkillReferenceLookup.class));
        ReviewAgentExecutionPlanFactory plans = new ReviewAgentExecutionPlanFactory(service, skills);

        WorkflowAgentExecutionPlan quick = plans.freeze(List.of("台词合理性"), "MARKDOWN_QUICK");
        assertThat(quick.skillSnapshots()).extracting(skill -> skill.code()).containsExactly(
            "script-review-foundation", "script-review-execution-framework", ReviewDimension.DIALOGUE.skillCode());
        assertThat(quick.agent().toolCodes()).containsExactly("read_review_context", "read_review_content");
        WorkflowAgentExecutionPlan child = plans.freeze(List.of("台词合理性"), "MARKDOWN_DEEP_CHILD");
        assertThat(child.agent().toolCodes()).containsExactly("read_review_context", "read_review_content");
        WorkflowAgentExecutionPlan aggregation = plans.freeze(List.of("台词合理性"), "MARKDOWN_DEEP_AGGREGATION");
        assertThat(aggregation.skillSnapshots()).extracting(skill -> skill.code())
            .endsWith("script-review-cross-episode-synthesis");
        assertThat(aggregation.agent().toolCodes()).isEmpty();
    }

    @Test
    void existingAdministratorDefinitionIsNeverOverwritten() {
        WorkflowAgentCommand defaults = bootstrap.definition(9L);
        WorkflowAgentRecord existing = record(new WorkflowAgentCommand(
            defaults.code(), "管理员审核配置", defaults.description(), "管理员提示词",
            99L, defaults.temperature(), 32000, 40, "DISABLED", defaults.skillCodes(), defaults.toolCodes()));
        when(agents.get("script-review")).thenReturn(existing);

        bootstrap.run(new DefaultApplicationArguments(new String[0]));

        verify(agents).get("script-review");
        verifyNoMoreInteractions(agents);
        verifyNoInteractions(service, jdbc);
    }

    private WorkflowAgentRecord record(WorkflowAgentCommand command) {
        return new WorkflowAgentRecord(1L, command.code(), command.name(), command.description(),
            command.systemPrompt(), command.modelId(), command.temperature(), command.maxTokens(),
            command.maxSteps(), command.status(), 1L, 9L, 9L, LocalDateTime.now(), LocalDateTime.now(),
            command.skillCodes(), command.toolCodes());
    }
}
