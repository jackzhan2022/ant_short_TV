package com.antshorttv.script;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class EpisodePromptContextServiceTest {
    @Test void reextractionRejectsMissingModelBeforeAnyDatabaseRead() {
        var jdbc=mock(JdbcTemplate.class);
        var repository=mock(EpisodePromptContextRepository.class);
        var service=new EpisodePromptContextService(jdbc,new EpisodePromptContextFactory(new ObjectMapper()),repository);
        assertThatThrownBy(()->service.prepareReextraction(new ScriptAnalysisTaskEntity(),1L,null))
            .hasMessageContaining("冻结文本模型");
        verifyNoInteractions(jdbc,repository);
    }

    @Test void operationIdentityNeverReadsAnalysisGlobalUnderstanding() {
        var jdbc=mock(JdbcTemplate.class);
        var repository=mock(EpisodePromptContextRepository.class);
        var service=new EpisodePromptContextService(jdbc,new EpisodePromptContextFactory(new ObjectMapper()),repository);
        when(jdbc.queryForList(anyString(),any(Object[].class)))
            .thenReturn(java.util.List.of(java.util.Map.of("content","本集正文","content_fingerprint","fp")));
        when(repository.createOrLoad(any())).thenAnswer(invocation->{
            EpisodePromptContextRepository.Draft draft=invocation.getArgument(0);
            assertThat(draft.commonPrefix()).contains("本集正文").contains("\"globalUnderstanding\":null");
            return new EpisodePromptContextRepository.Snapshot(1L,1L,2L,3L,4L,5L,"fp",
                draft.globalUnderstandingHash(),draft.contextHash(),draft.rulesRevision(),draft.toolProtocolRevision(),draft.commonPrefix());
        });
        var scope=new ScriptAnalysisTaskEntity();scope.setId(100L);scope.setTenantId(1L);scope.setProjectId(2L);scope.setScriptId(3L);
        assertThat(service.prepareReextraction(scope,4L,5L).commonPrefix()).contains("本集正文");
        var sql=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(),any(Object[].class));
        assertThat(sql.getValue()).contains("script_episode").doesNotContain("script_analysis_task","script_global_understanding");
    }
}
