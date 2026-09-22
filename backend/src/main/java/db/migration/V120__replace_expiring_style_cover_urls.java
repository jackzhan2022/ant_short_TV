package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Replaces persisted style-library presigned URLs with the stable application image endpoint. */
public class V120__replace_expiring_style_cover_urls extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        backfill(context.getConnection());
    }

    static void backfill(Connection connection) throws Exception {
        try (PreparedStatement styles = connection.prepareStatement(
            "select external_id from style_library where external_id is not null and trim(external_id) <> ''"
        ); ResultSet rows = styles.executeQuery();
             PreparedStatement projects = connection.prepareStatement(
                 "update project set cover_url=? where cover_url like ?"
             )) {
            while (rows.next()) {
                String externalId = rows.getString(1);
                projects.setString(1, "/api/style-library/images/" + externalId);
                projects.setString(2, "%/style-library/public/" + externalId + "/cover-compressed.jpg%");
                projects.addBatch();
            }
            projects.executeBatch();
        }
    }
}
