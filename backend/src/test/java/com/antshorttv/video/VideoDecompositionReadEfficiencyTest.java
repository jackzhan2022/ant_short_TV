package com.antshorttv.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.antshorttv.execution.AiExecutionTaskEntity;
import com.antshorttv.execution.AiExecutionTaskMapper;
import com.antshorttv.security.TenantContextResolver;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VideoDecompositionReadEfficiencyTest {
    @Mock TenantContextResolver tenantContextResolver;
    @Mock VideoDecompositionBatchMapper batchMapper;
    @Mock VideoDecompositionEpisodeMapper episodeMapper;
    @Mock VideoDecompositionScriptResultRepository resultRepository;
    @Mock AiExecutionTaskMapper executionTaskMapper;
    @InjectMocks VideoDecompositionService service;

    @Test
    void readsFiftyEpisodeProgressValuesOnceForBothSummaryAndRows() {
        var batch = new VideoDecompositionBatchEntity();
        batch.setId(9L);
        batch.setTenantId(7L);
        batch.setStatus("RUNNING");
        List<VideoDecompositionEpisodeEntity> episodes = LongStream.rangeClosed(1, 50).mapToObj(id -> {
            var episode = new VideoDecompositionEpisodeEntity();
            episode.setId(id);
            episode.setExecutionId(id + 100);
            episode.setStatus("ANALYZING");
            return episode;
        }).toList();
        when(batchMapper.selectOne(any())).thenReturn(batch);
        when(episodeMapper.selectByBatch(7L, 9L)).thenReturn(episodes);
        when(resultRepository.listByBatch(7L, 9L)).thenReturn(List.of());
        when(executionTaskMapper.selectList(any(QueryWrapper.class))).thenReturn(episodes.stream().map(episode -> {
            var task = new AiExecutionTaskEntity();
            task.id = episode.getExecutionId();
            task.progress = 63;
            return task;
        }).toList());

        var result = service.allScreenplays(7L, 9L);
        assertThat(result.percentage()).isEqualTo(63);
        assertThat(result.episodes()).hasSize(50).allSatisfy(item -> assertThat(item.episode().percentage()).isEqualTo(63));
        verify(executionTaskMapper, times(1)).selectList(any(QueryWrapper.class));
        verify(executionTaskMapper, never()).selectById(any());

        clearInvocations(executionTaskMapper);
        var metadata = service.detail(7L, 9L);
        assertThat(metadata.percentage()).isEqualTo(63);
        verify(executionTaskMapper, times(1)).selectList(any(QueryWrapper.class));
        verify(executionTaskMapper, never()).selectById(any());
    }
}
