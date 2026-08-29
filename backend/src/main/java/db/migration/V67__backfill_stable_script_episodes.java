package db.migration;

import com.antshorttv.script.ScriptEpisodeParser;
import com.antshorttv.script.ScriptEpisodeReconciler;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V67__backfill_stable_script_episodes extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        String selectSql = """
            select s.id, s.tenant_id, s.project_id, s.current_version_id, s.content
              from script s
             where s.deleted_at is null and s.current_version_id is not null
               and not exists (
                   select 1 from script_episode e
                    where e.tenant_id = s.tenant_id and e.project_id = s.project_id
                      and e.script_id = s.id and e.retired_at is null
               )
            """;
        String insertSql = """
            insert into script_episode
              (tenant_id, project_id, script_id, script_version_id, stable_key, episode_no,
               title, summary, content, content_fingerprint, heading_key,
               reconciliation_status, status, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        var reconciler = new ScriptEpisodeReconciler();
        try (PreparedStatement select = context.getConnection().prepareStatement(selectSql);
             ResultSet rows = select.executeQuery();
             PreparedStatement insert = context.getConnection().prepareStatement(insertSql)) {
            while (rows.next()) {
                var parsed = ScriptEpisodeParser.parse(rows.getString("content"));
                if (parsed.isEmpty()) {
                    continue;
                }
                var reconciliation = reconciler.reconcile(List.of(), parsed);
                Timestamp now = Timestamp.valueOf(LocalDateTime.now());
                for (var episode : reconciliation.active()) {
                    insert.setLong(1, rows.getLong("tenant_id"));
                    insert.setLong(2, rows.getLong("project_id"));
                    insert.setLong(3, rows.getLong("id"));
                    insert.setLong(4, rows.getLong("current_version_id"));
                    insert.setString(5, episode.stableKey());
                    insert.setInt(6, episode.episodeNo());
                    insert.setString(7, episode.title());
                    insert.setString(8, episode.summary());
                    insert.setString(9, episode.content());
                    insert.setString(10, episode.contentFingerprint());
                    insert.setString(11, episode.headingKey());
                    insert.setString(12, episode.status());
                    insert.setString(13, "AMBIGUOUS".equals(episode.status()) ? "NEEDS_REVIEW" : "ACTIVE");
                    insert.setTimestamp(14, now);
                    insert.setTimestamp(15, now);
                    insert.addBatch();
                }
                insert.executeBatch();
            }
        }
    }
}
