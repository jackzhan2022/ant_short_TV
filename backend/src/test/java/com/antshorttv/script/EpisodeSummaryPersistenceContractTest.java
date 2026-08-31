package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EpisodeSummaryPersistenceContractTest {
    @Test
    void repositoryPersistsSourceAndGeneratingRunProvenance() throws Exception {
        ScriptEpisodeSummaryMapper mapper = mock(ScriptEpisodeSummaryMapper.class);
        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.insert(any(ScriptEpisodeSummaryEntity.class))).thenAnswer(invocation -> {
            ((ScriptEpisodeSummaryEntity) invocation.getArgument(0)).setId(31L);
            return 1;
        });
        ScriptEpisodeSummaryRepository repository = new ScriptEpisodeSummaryRepository(
            mapper, new ObjectMapper());

        long id = repository.upsert(new ScriptEpisodeSummaryDocument(
            null, 1L, 2L, 3L, 4L, 1,
            new ObjectMapper().readTree("{\"summary\":\"概要\",\"highlights\":[\"一\",\"二\"],\"endingHook\":null}"),
            "AI", 99L, 5L, 5L, null, null));

        ArgumentCaptor<ScriptEpisodeSummaryEntity> entity =
            ArgumentCaptor.forClass(ScriptEpisodeSummaryEntity.class);
        verify(mapper).insert(entity.capture());
        assertThat(id).isEqualTo(31L);
        assertThat(entity.getValue().getSource()).isEqualTo("AI");
        assertThat(entity.getValue().getGeneratedByRunId()).isEqualTo(99L);
        assertThat(entity.getValue().getContentJson()).contains("highlights", "endingHook");
    }

    @Test
    void formalSummaryReadRemainsAuthoritativeOverLegacyText() throws Exception {
        ScriptEpisodeSummaryMapper mapper = mock(ScriptEpisodeSummaryMapper.class);
        ScriptEpisodeSummaryEntity entity = new ScriptEpisodeSummaryEntity();
        entity.setId(1L); entity.setTenantId(2L); entity.setProjectId(3L); entity.setScriptId(4L);
        entity.setEpisodeId(5L); entity.setSchemaVersion(1);
        entity.setContentJson("{\"summary\":\"正式概要\",\"highlights\":[\"一\",\"二\"],\"endingHook\":null}");
        entity.setSource("USER"); entity.setCreatedBy(6L); entity.setUpdatedBy(6L);
        when(mapper.selectOne(any())).thenReturn(entity);
        ScriptEpisodeSummaryRepository repository = new ScriptEpisodeSummaryRepository(mapper, new ObjectMapper());

        Optional<ScriptEpisodeSummaryDocument> formal = repository.findCurrent(2L, 4L, 5L);

        assertThat(formal).isPresent();
        assertThat(formal.orElseThrow().content().path("summary").asText()).isEqualTo("正式概要");
    }
}
