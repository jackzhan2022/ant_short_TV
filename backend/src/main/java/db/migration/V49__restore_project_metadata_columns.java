package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Restores project metadata columns after the legacy V25/V26 migrations were archived. */
public class V49__restore_project_metadata_columns extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        ensureColumn(connection, "aspect_ratio", "varchar(16) null");
        ensureColumn(connection, "file_format", "varchar(32) null");
        ensureColumn(connection, "script_type", "varchar(32) null");
        ensureColumn(connection, "breakdown_strength", "varchar(32) null");
        ensureColumn(connection, "cover_source", "varchar(32) null");
        ensureColumn(connection, "visual_style", "varchar(120) null");
        ensureColumn(connection, "initial_script_content", "text null");
    }

    private void ensureColumn(Connection connection, String columnName, String columnDefinition) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
            select count(*)
              from information_schema.columns
             where lower(table_name) = 'project'
               and lower(column_name) = ?
            """)) {
            statement.setString(1, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                if (resultSet.getInt(1) > 0) {
                    return;
                }
            }
        }

        try (PreparedStatement statement = connection.prepareStatement(
            "alter table project add column " + columnName + " " + columnDefinition
        )) {
            statement.execute();
        }
    }
}
