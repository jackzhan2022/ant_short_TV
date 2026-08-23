package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V25__project_cover_source_fix extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        ensureColumn(context.getConnection(), "cover_source", "varchar(32) null");
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
