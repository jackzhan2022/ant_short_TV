package db.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V76__backfill_formal_script_analysis_data extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        backfill(context.getConnection());
    }

    public static void backfill(Connection connection) throws Exception {
        backfillSummaries(connection);
        backfillAssets(connection, "character_asset");
        backfillAssets(connection, "scene_asset");
        backfillAssets(connection, "prop_asset");
    }

    private static void backfillSummaries(Connection connection) throws Exception {
        String selectSql = """
            select e.tenant_id, e.project_id, e.script_id, e.id episode_id, e.summary, s.created_by
              from script_episode e
              join script s on s.id = e.script_id and s.tenant_id = e.tenant_id
             where e.summary is not null and trim(e.summary) <> ''
               and not exists (
                   select 1 from script_episode_summary d
                    where d.tenant_id = e.tenant_id and d.episode_id = e.id
               )
            """;
        String insertSql = """
            insert into script_episode_summary
              (tenant_id, project_id, script_id, episode_id, schema_version, content_json,
               source, generated_by_run_id, created_by, updated_by, created_at, updated_at)
            values (?, ?, ?, ?, 1, ?, 'LEGACY', null, ?, ?, now(), now())
            """;
        ObjectMapper json = new ObjectMapper();
        try (PreparedStatement select = connection.prepareStatement(selectSql);
             ResultSet rows = select.executeQuery();
             PreparedStatement insert = connection.prepareStatement(insertSql)) {
            while (rows.next()) {
                ObjectNode content = json.createObjectNode();
                content.put("summary", rows.getString("summary").trim());
                content.putArray("highlights");
                content.putNull("endingHook");
                insert.setLong(1, rows.getLong("tenant_id"));
                insert.setLong(2, rows.getLong("project_id"));
                insert.setLong(3, rows.getLong("script_id"));
                insert.setLong(4, rows.getLong("episode_id"));
                insert.setString(5, json.writeValueAsString(content));
                insert.setLong(6, rows.getLong("created_by"));
                insert.setLong(7, rows.getLong("created_by"));
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static void backfillAssets(Connection connection, String table) throws Exception {
        String selectSql = "select id, tenant_id, project_id, name from " + table
            + " where deleted_at is null and (script_id is null or normalized_name is null or source is null)";
        String scriptSql = """
            select min(id) script_id, count(*) script_count
              from script
             where tenant_id = ? and project_id = ? and deleted_at is null
            """;
        String updateSql = "update " + table
            + " set script_id = coalesce(script_id, ?), normalized_name = coalesce(normalized_name, ?),"
            + " source = coalesce(source, 'LEGACY'), updated_at = now() where id = ? and tenant_id = ?";
        try (PreparedStatement select = connection.prepareStatement(selectSql);
             ResultSet rows = select.executeQuery();
             PreparedStatement findScript = connection.prepareStatement(scriptSql);
             PreparedStatement update = connection.prepareStatement(updateSql)) {
            while (rows.next()) {
                findScript.setLong(1, rows.getLong("tenant_id"));
                findScript.setLong(2, rows.getLong("project_id"));
                Long scriptId = null;
                try (ResultSet scripts = findScript.executeQuery()) {
                    if (scripts.next() && scripts.getLong("script_count") == 1) {
                        scriptId = scripts.getLong("script_id");
                    }
                }
                update.setObject(1, scriptId);
                update.setString(2, normalizeName(rows.getString("name")));
                update.setLong(3, rows.getLong("id"));
                update.setLong(4, rows.getLong("tenant_id"));
                update.addBatch();
            }
            update.executeBatch();
        }
    }

    private static String normalizeName(String name) {
        if (name == null) {
            return null;
        }
        String normalized = name.toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{S}\\s]+", "");
        return normalized.isBlank() ? null : normalized;
    }
}
