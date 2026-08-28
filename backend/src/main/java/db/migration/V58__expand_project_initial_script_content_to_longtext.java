package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Expands project script storage beyond MySQL TEXT's 64 KiB limit. */
public class V58__expand_project_initial_script_content_to_longtext extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!columnExists(connection)) {
            return;
        }

        String product = connection.getMetaData().getDatabaseProductName().toLowerCase();
        String sql = product.contains("h2")
            ? "alter table project alter column initial_script_content longtext"
            : "alter table project modify column initial_script_content longtext null";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }

    private boolean columnExists(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
            select count(*)
              from information_schema.columns
             where lower(table_name) = 'project'
               and lower(column_name) = 'initial_script_content'
            """)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }
}
