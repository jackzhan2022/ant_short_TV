package com.antshorttv.workflowagent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.authsession.AuthenticatedUser;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.security.CurrentPrincipal;
import com.antshorttv.workflowagent.WorkflowAgentProperties;
import com.antshorttv.workflowagent.skill.WorkflowSkillService;
import com.antshorttv.workflowagent.tool.WorkflowToolRegistry;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowAgentServiceTest {
    @Mock
    private WorkflowAgentRepository repository;
    @Mock
    private WorkflowAgentModelLookup models;
    @Mock
    private WorkflowSkillService skills;
    @Mock
    private WorkflowToolRegistry tools;
    @Mock
    private WorkflowAgentBusinessReferenceLookup references;
    @Mock
    private CurrentPrincipal principal;

    private WorkflowAgentService service;

    @BeforeEach
    void setUp() {
        WorkflowAgentProperties properties = new WorkflowAgentProperties();
        properties.setMaxSteps(20);
        service = new WorkflowAgentService(repository, models, skills, tools, references, principal, properties);
        lenient().when(principal.require()).thenReturn(new AuthenticatedUser(
            17L, "13800000000", "session", LocalDateTime.now().plusHours(1)));
    }

    @Test
    void rejectsMissingDisabledOrIncompatibleModelsBeforePersistence() {
        WorkflowAgentCommand command = command(4L, List.of(), List.of());
        doThrow(new BusinessException(ErrorCode.AI_MODEL_NOT_FOUND, "missing"))
            .when(models).requireEnabledTextModel(4L);
        assertThatThrownBy(() -> service.create(command)).isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).getErrorCode())
            .isEqualTo(ErrorCode.AI_MODEL_NOT_FOUND);

        doThrow(new BusinessException(ErrorCode.AI_MODEL_DISABLED, "disabled"))
            .when(models).requireEnabledTextModel(4L);
        assertThatThrownBy(() -> service.create(command)).isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).getErrorCode())
            .isEqualTo(ErrorCode.AI_MODEL_DISABLED);

        doThrow(new BusinessException(ErrorCode.VALIDATION_ERROR, "incompatible"))
            .when(models).requireEnabledTextModel(4L);
        assertThatThrownBy(() -> service.create(command)).isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).getErrorCode())
            .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(repository, never()).create(any(), any());
    }

    @Test
    void rejectsMissingSkillsUnknownToolsAndInvalidLimits() {
        when(models.requireEnabledTextModel(4L)).thenReturn(new WorkflowAgentModel(4L, "text", "TEXT"));
        when(skills.detail("missing-skill")).thenThrow(
            new BusinessException(ErrorCode.WORKFLOW_SKILL_NOT_FOUND, "missing"));
        assertThatThrownBy(() -> service.create(command(4L, List.of("missing-skill"), List.of())))
            .isInstanceOf(BusinessException.class);

        when(tools.contains("unknown_tool")).thenReturn(false);
        assertThatThrownBy(() -> service.create(command(4L, List.of(), List.of("unknown_tool"))))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).getErrorCode())
            .isEqualTo(ErrorCode.VALIDATION_ERROR);

        WorkflowAgentCommand invalidLimits = new WorkflowAgentCommand("episode-agent", "Episode", null,
            "prompt", 4L, new BigDecimal("2.100"), 0, 21, "ENABLED", List.of(), List.of());
        assertThatThrownBy(() -> service.create(invalidLimits)).isInstanceOf(BusinessException.class);
        verify(repository, never()).create(any(), any());
    }

    @Test
    void promptTextNeverCreatesAnUnselectedToolAssociation() {
        when(models.requireEnabledTextModel(4L)).thenReturn(new WorkflowAgentModel(4L, "text", "TEXT"));
        WorkflowAgentCommand command = new WorkflowAgentCommand("episode-agent", "Episode", null,
            "First call save_episode_script", 4L, new BigDecimal("0.700"), 4096, 10,
            "ENABLED", List.of(), List.of());
        when(repository.create(any(), eq(17L))).thenAnswer(invocation -> record(invocation.getArgument(0)));

        service.create(command);

        ArgumentCaptor<WorkflowAgentCommand> saved = ArgumentCaptor.forClass(WorkflowAgentCommand.class);
        verify(repository).create(saved.capture(), eq(17L));
        assertThat(saved.getValue().toolCodes()).isEmpty();
    }

    @Test
    void disabledAgentCannotBeLoadedForFormalInvocation() {
        WorkflowAgentRecord disabled = record(command(4L, List.of(), List.of()), "DISABLED");
        when(repository.get("episode-agent")).thenReturn(disabled);

        assertThatThrownBy(() -> service.loadForRun("episode-agent"))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).getErrorCode())
            .isEqualTo(ErrorCode.WORKFLOW_AGENT_DISABLED);
    }

    @Test
    void referencedAgentCannotBeDeleted() {
        when(references.findReferences("episode-agent")).thenReturn(List.of("project-default-agent"));
        assertThatThrownBy(() -> service.delete("episode-agent"))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).getErrorCode())
            .isEqualTo(ErrorCode.WORKFLOW_AGENT_IN_USE);
        verify(repository, never()).delete(any());
    }

    private WorkflowAgentCommand command(Long modelId, List<String> skillCodes, List<String> toolCodes) {
        return new WorkflowAgentCommand("episode-agent", "Episode", "description", "prompt", modelId,
            new BigDecimal("0.700"), 4096, 10, "ENABLED", skillCodes, toolCodes);
    }

    private WorkflowAgentRecord record(WorkflowAgentCommand command) {
        return record(command, command.status());
    }

    private WorkflowAgentRecord record(WorkflowAgentCommand command, String status) {
        return new WorkflowAgentRecord(1L, command.code(), command.name(), command.description(),
            command.systemPrompt(), command.modelId(), command.temperature(), command.maxTokens(),
            command.maxSteps(), status, 0L, 17L, 17L, LocalDateTime.now(), LocalDateTime.now(),
            command.skillCodes(), command.toolCodes());
    }
}
