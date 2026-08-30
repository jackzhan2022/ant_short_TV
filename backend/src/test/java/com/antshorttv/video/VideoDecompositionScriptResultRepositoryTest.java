package com.antshorttv.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "ai.video.scheduler.enabled=false")
class VideoDecompositionScriptResultRepositoryTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private VideoDecompositionScriptResultRepository repository;

    private Long batchId;
    private Long firstEpisodeId;
    private Long secondEpisodeId;
    private Long firstAnalysisId;
    private Long secondAnalysisId;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from video_decomposition_script_result");
        jdbc.update("delete from video_decomposition_analysis");
        jdbc.update("delete from video_decomposition_attempt");
        jdbc.update("delete from video_decomposition_episode");
        jdbc.update("delete from video_decomposition_batch");
        jdbc.update("""
            insert into video_decomposition_batch
              (tenant_id, project_id, name, model_id, status, total_episodes,
               completed_episodes, failed_episodes, created_by, created_at, updated_at)
            values (901, null, '结果仓储测试', 10, 'RUNNING', 2, 0, 0, 99, now(), now())
            """);
        batchId = jdbc.queryForObject("select max(id) from video_decomposition_batch", Long.class);
        secondEpisodeId = insertEpisode(2);
        firstEpisodeId = insertEpisode(1);
        secondAnalysisId = insertAnalysis(secondEpisodeId);
        firstAnalysisId = insertAnalysis(firstEpisodeId);
    }

    @Test
    void insertsReadsAndOrdersImmutableResultsByEpisodeNumber() {
        VideoDecompositionScriptResult second = repository.insert(new VideoDecompositionScriptResultCreate(
            901L, batchId, secondEpisodeId, secondAnalysisId, null, "# 第2集：后续\n\n## 2-1 夜 内 房间\n\n出场人物：B\n\n剧情。\n\n——本集完", "markdown-screenplay-v1"
        ));
        VideoDecompositionScriptResult first = repository.insert(new VideoDecompositionScriptResultCreate(
            901L, batchId, firstEpisodeId, firstAnalysisId, null, "# 第1集：开始\n\n## 1-1 日 外 庭院\n\n出场人物：A\n\n剧情。\n\n——本集完", "markdown-screenplay-v1"
        ));

        assertThat(first.id()).isNotNull();
        assertThat(first.createdAt()).isBeforeOrEqualTo(LocalDateTime.now());
        assertThat(repository.findByEpisode(901L, firstEpisodeId)).contains(first);
        assertThat(repository.listByBatch(901L, batchId))
            .extracting(VideoDecompositionScriptResult::episodeId)
            .containsExactly(firstEpisodeId, secondEpisodeId);
        assertThat(second.formatVersion()).isEqualTo("markdown-screenplay-v1");
    }

    @Test
    void rejectsASecondResultForTheSameEpisode() {
        VideoDecompositionScriptResultCreate result = new VideoDecompositionScriptResultCreate(
            901L, batchId, firstEpisodeId, firstAnalysisId, null, "content", "markdown-screenplay-v1"
        );
        repository.insert(result);

        assertThatThrownBy(() -> repository.insert(result))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void repositoryExposesNoMutationAfterInsert() {
        assertThat(java.util.Arrays.stream(VideoDecompositionScriptResultRepository.class.getDeclaredMethods())
            .map(java.lang.reflect.Method::getName))
            .doesNotContain("update", "delete", "replace");
    }

    private Long insertEpisode(int episodeNo) {
        jdbc.update("""
            insert into video_decomposition_episode
              (batch_id, tenant_id, project_id, episode_no, source_file_name, storage_path,
               mime_type, file_size, status, analysis_version, draft_version,
               created_by, created_at, updated_at)
            values (?, 901, null, ?, ?, ?, 'video/mp4', 1024, 'ANALYZING', 0, 0, 99, now(), now())
            """, batchId, episodeNo, "episode-%d.mp4".formatted(episodeNo),
            "/materials/901/video-decomposition/episode-%d.mp4".formatted(episodeNo));
        return jdbc.queryForObject(
            "select id from video_decomposition_episode where batch_id = ? and episode_no = ?",
            Long.class, batchId, episodeNo);
    }

    private Long insertAnalysis(Long episodeId) {
        jdbc.update("""
            insert into video_decomposition_analysis
              (episode_id, schema_version, status, raw_response, normalized_json, created_at)
            values (?, 'v1', 'SUCCEEDED', '{}', '{}', now())
            """, episodeId);
        return jdbc.queryForObject(
            "select max(id) from video_decomposition_analysis where episode_id = ?",
            Long.class, episodeId);
    }
}
