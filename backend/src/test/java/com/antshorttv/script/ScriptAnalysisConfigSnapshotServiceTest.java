package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.antshorttv.ai.AiAgentDefinitionEntity;
import com.antshorttv.ai.AiAgentDefinitionMapper;
import com.antshorttv.ai.AiModelParameterProfileEntity;
import com.antshorttv.ai.AiModelParameterProfileMapper;
import com.antshorttv.ai.BuiltInAgentRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ScriptAnalysisConfigSnapshotServiceTest {
    private final ScriptAnalysisConfigSnapshotMapper snapshotMapper = mock(ScriptAnalysisConfigSnapshotMapper.class);
    private final AiAgentDefinitionMapper agentMapper = mock(AiAgentDefinitionMapper.class);
    private final AiModelParameterProfileMapper parameterMapper = mock(AiModelParameterProfileMapper.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ScriptAnalysisConfigSnapshotService service = new ScriptAnalysisConfigSnapshotService(
        snapshotMapper, agentMapper, parameterMapper, new BuiltInAgentRegistry(), jdbc, new ObjectMapper());

    @Test
    void snapshotIsIdempotentAcrossRetries() {
        ScriptAnalysisTaskEntity task = new ScriptAnalysisTaskEntity();
        task.setId(41L);
        AiAgentDefinitionEntity agent = new AiAgentDefinitionEntity();
        agent.setCode("script-global-understanding");
        agent.setVersionNo(3);
        agent.setPublished(true);
        agent.setStatus("ENABLED");
        agent.setPromptTemplate("frozen ${scriptContent}");
        agent.setOutputSchema("{}");
        AiModelParameterProfileEntity params = new AiModelParameterProfileEntity();
        params.setId(91L);
        params.setModelId(12L);
        params.setVersionNo(4);
        params.setPublished(true);
        when(snapshotMapper.selectOne(any())).thenReturn(null, newSnapshot(41L, 91L, 4));
        when(agentMapper.selectOne(any())).thenReturn(agent);
        when(parameterMapper.selectOne(any())).thenReturn(params);

        service.snapshot(task, 12L);
        service.snapshot(task, 12L);

        verify(snapshotMapper, times(1)).insert(any(ScriptAnalysisConfigSnapshotEntity.class));
    }

    @Test
    void parametersForUsesTheVersionStoredInTaskSnapshot() {
        ScriptAnalysisConfigSnapshotEntity snapshot = newSnapshot(41L, 91L, 4);
        AiModelParameterProfileEntity params = new AiModelParameterProfileEntity();
        params.setId(91L);
        params.setVersionNo(4);
        params.setMaxTokens(8192);
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(parameterMapper.selectOne(any())).thenReturn(params);

        AiModelParameterProfileEntity resolved = service.parametersFor(41L);

        assertThat(resolved).isSameAs(params);
        verify(parameterMapper).selectOne(any());
    }

    private ScriptAnalysisConfigSnapshotEntity newSnapshot(Long taskId, Long profileId, Integer version) {
        ScriptAnalysisConfigSnapshotEntity snapshot = new ScriptAnalysisConfigSnapshotEntity();
        snapshot.setTaskId(taskId);
        snapshot.setModelParameterProfileId(profileId);
        snapshot.setModelParameterVersionNo(version);
        return snapshot;
    }
}
