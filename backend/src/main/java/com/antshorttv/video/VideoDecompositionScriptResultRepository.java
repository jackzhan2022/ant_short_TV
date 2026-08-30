package com.antshorttv.video;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class VideoDecompositionScriptResultRepository {
    private final JdbcTemplate jdbc;

    public VideoDecompositionScriptResultRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public VideoDecompositionScriptResult insert(VideoDecompositionScriptResultCreate value) {
        LocalDateTime createdAt = LocalDateTime.now();
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                insert into video_decomposition_script_result
                  (tenant_id, batch_id, episode_id, analysis_id, ai_call_log_id,
                   content, format_version, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, value.tenantId());
            statement.setLong(2, value.batchId());
            statement.setLong(3, value.episodeId());
            statement.setLong(4, value.analysisId());
            if (value.aiCallLogId() == null) statement.setNull(5, java.sql.Types.BIGINT);
            else statement.setLong(5, value.aiCallLogId());
            statement.setString(6, value.content());
            statement.setString(7, value.formatVersion());
            statement.setTimestamp(8, Timestamp.valueOf(createdAt));
            return statement;
        }, key);
        return findByEpisode(value.tenantId(), value.episodeId())
            .orElseThrow(() -> new IllegalStateException("Inserted video decomposition result was not found"));
    }

    public Optional<VideoDecompositionScriptResult> findByEpisode(Long tenantId, Long episodeId) {
        List<VideoDecompositionScriptResult> values = jdbc.query("""
            select result.* from video_decomposition_script_result result
             where result.tenant_id = ? and result.episode_id = ?
            """, this::map, tenantId, episodeId);
        return values.stream().findFirst();
    }

    public List<VideoDecompositionScriptResult> listByBatch(Long tenantId, Long batchId) {
        return jdbc.query("""
            select result.*
              from video_decomposition_script_result result
              join video_decomposition_episode episode on episode.id = result.episode_id
             where result.tenant_id = ? and result.batch_id = ?
             order by episode.episode_no, episode.id
            """, this::map, tenantId, batchId);
    }

    private VideoDecompositionScriptResult map(java.sql.ResultSet row, int rowNum) throws java.sql.SQLException {
        Timestamp createdAt = row.getTimestamp("created_at");
        return new VideoDecompositionScriptResult(
            row.getLong("id"), row.getLong("tenant_id"), row.getLong("batch_id"),
            row.getLong("episode_id"), row.getLong("analysis_id"),
            nullableLong(row, "ai_call_log_id"), row.getString("content"),
            row.getString("format_version"), createdAt == null ? null : createdAt.toLocalDateTime()
        );
    }

    private Long nullableLong(java.sql.ResultSet row, String column) throws java.sql.SQLException {
        long value = row.getLong(column);
        return row.wasNull() ? null : value;
    }
}
