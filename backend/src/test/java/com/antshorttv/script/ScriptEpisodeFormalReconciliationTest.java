package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class ScriptEpisodeFormalReconciliationTest {
    @Test
    void recordsRunProvenanceAndRetiresBindingsWithoutReviewState() {
        ScriptEpisodeMapper mapper = mock(ScriptEpisodeMapper.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ScriptEpisodeSummaryRepository summaries = mock(ScriptEpisodeSummaryRepository.class);
        ScriptEpisodeEntity existing = existingEpisode();
        when(mapper.selectList(any())).thenReturn(List.of(existing));
        when(mapper.insert(any(ScriptEpisodeEntity.class))).thenAnswer(invocation -> {
            ((ScriptEpisodeEntity) invocation.getArgument(0)).setId(902L);
            return 1;
        });
        ScriptEpisodeService service = new ScriptEpisodeService(mapper, jdbc, summaries);

        service.reconcileAndPersist(11L, 12L, 13L, 14L, 99L,
            List.of(new ScriptEpisodeResponse(1, "第1集：新篇", "全新正文")));

        ArgumentCaptor<ScriptEpisodeEntity> inserted = ArgumentCaptor.forClass(ScriptEpisodeEntity.class);
        verify(mapper).insert(inserted.capture());
        assertThat(inserted.getValue().getGeneratedByRunId()).isEqualTo(99L);
        assertThat(inserted.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(existing.getStatus()).isEqualTo("RETIRED");
        assertThat(existing.getReconciliationStatus()).isEqualTo("RETIRED");
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("binding_status = 'RETIRED'"),
            any(), any(), any(), any(), any());
    }

    private ScriptEpisodeEntity existingEpisode() {
        ScriptEpisodeEntity entity = new ScriptEpisodeEntity();
        entity.setId(901L);
        entity.setTenantId(11L);
        entity.setProjectId(12L);
        entity.setScriptId(13L);
        entity.setStableKey("old-stable");
        entity.setEpisodeNo(1);
        entity.setTitle("第1集：旧篇");
        entity.setContent("旧正文");
        entity.setStatus("ACTIVE");
        return entity;
    }
}
