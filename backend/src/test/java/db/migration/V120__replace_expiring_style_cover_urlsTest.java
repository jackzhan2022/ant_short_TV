package db.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.junit.jupiter.api.Test;

class V120__replace_expiring_style_cover_urlsTest {
    @Test
    void replacesOnlyStyleLibraryCoverUrls() throws Exception {
        try (var connection = DriverManager.getConnection(
            "jdbc:h2:mem:style_cover_url_migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", ""
        ); var statement = connection.createStatement()) {
            statement.execute("create table style_library (external_id varchar(64))");
            statement.execute("create table project (id bigint primary key, cover_url varchar(2048))");
            statement.execute("insert into style_library (external_id) values ('style-1')");
            statement.execute("insert into project (id, cover_url) values "
                + "(1, 'https://minio.example/ant-short-tv/style-library/public/style-1/cover-compressed.jpg?X-Amz-Expires=604800'), "
                + "(2, 'https://example.com/cover.jpg'), "
                + "(3, '/api/style-library/images/style-1')");

            V120__replace_expiring_style_cover_urls.backfill(connection);

            var result = statement.executeQuery("select id, cover_url from project order by id");
            result.next();
            assertThat(result.getString("cover_url")).isEqualTo("/api/style-library/images/style-1");
            result.next();
            assertThat(result.getString("cover_url")).isEqualTo("https://example.com/cover.jpg");
            result.next();
            assertThat(result.getString("cover_url")).isEqualTo("/api/style-library/images/style-1");
        }
    }
}
