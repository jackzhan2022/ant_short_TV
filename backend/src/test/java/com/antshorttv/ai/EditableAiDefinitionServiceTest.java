package com.antshorttv.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.antshorttv.authsession.AuthenticatedUser;
import com.antshorttv.common.BusinessException;
import com.antshorttv.operationlog.OperationLogService;
import com.antshorttv.security.CurrentPrincipal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class EditableAiDefinitionServiceTest {
    private final AiAgentDefinitionMapper agentMapper = mock(AiAgentDefinitionMapper.class);
    private final AiSkillDefinitionMapper skillMapper = mock(AiSkillDefinitionMapper.class);
    private final CurrentPrincipal principal = mock(CurrentPrincipal.class);
    private final OperationLogService operationLog = mock(OperationLogService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AuthenticatedUser user = new AuthenticatedUser(7L, "13800000007", "session", LocalDateTime.now().plusHours(1));
    private final EditableAiDefinitionService service = new EditableAiDefinitionService(agentMapper, skillMapper, principal, operationLog, jdbc);

    @Test
    void publishingAgentRecordsAuditAndLeavesSinglePublishedVersion() {
        AiAgentDefinitionEntity old = agent("script-review", 1, true);
        AiAgentDefinitionEntity draft = agent("script-review", 2, false);
        when(principal.require()).thenReturn(user);
        when(agentMapper.selectOne(any())).thenReturn(draft);
        when(agentMapper.selectList(any())).thenReturn(List.of(old));

        EditableAgentResponse response = service.publishAgent("script-review", null);

        assertThat(response.published()).isTrue();
        verify(agentMapper).updateById(old);
        verify(agentMapper).updateById(draft);
        verify(operationLog).record(eq(7L), isNull(), eq("PUBLISH_AI_AGENT_DEFINITION"), any(), any(), isNull());
    }

    @Test
    void updateAgentCreatesNextDraftVersionWithoutUnpublishingCurrentVersion() {
        AiAgentDefinitionEntity current = agent("script-review", 3, true);
        when(principal.require()).thenReturn(user);
        when(agentMapper.selectOne(any())).thenReturn(current, current);

        EditableAgentResponse response = service.updateAgent("script-review",
            new EditableAgentRequest("审核新版", "desc", "prompt ${script}", "{}"), null);

        assertThat(response.versionNo()).isEqualTo(4);
        assertThat(response.published()).isFalse();
        verify(agentMapper, never()).updateById(current);
        verify(agentMapper).insert(any(AiAgentDefinitionEntity.class));
    }

    @Test
    void rollbackAgentPublishesRequestedHistoricalVersionOnly() {
        AiAgentDefinitionEntity current = agent("script-review", 2, true);
        AiAgentDefinitionEntity target = agent("script-review", 1, false);
        when(principal.require()).thenReturn(user);
        when(agentMapper.selectOne(any())).thenReturn(target);
        when(agentMapper.selectList(any())).thenReturn(List.of(current));

        EditableAgentResponse response = service.rollbackAgent("script-review", 1, null);

        assertThat(response.versionNo()).isEqualTo(1);
        assertThat(response.published()).isTrue();
        verify(agentMapper).updateById(current);
        verify(agentMapper).updateById(target);
    }

    @Test
    void rejectsBlankSkillContent() {
        when(principal.require()).thenReturn(user);

        assertThatThrownBy(() -> service.updateSkill("strict-json-output",
            new EditableSkillRequest("JSON", "OUTPUT", "   "), null))
            .isInstanceOf(BusinessException.class);
        verifyNoInteractions(skillMapper);
    }

    private AiAgentDefinitionEntity agent(String code, int version, boolean published) {
        AiAgentDefinitionEntity entity = new AiAgentDefinitionEntity();
        entity.setId((long) version);
        entity.setCode(code);
        entity.setVersionNo(version);
        entity.setName(code);
        entity.setPromptTemplate("prompt");
        entity.setPublished(published);
        entity.setStatus("ENABLED");
        return entity;
    }
}
