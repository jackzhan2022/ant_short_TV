package com.antshorttv.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class VolcengineXinjiMigrationTest {
    @Test
    void movesOnlyStandardAnd25AndPreservesAllModelSettingsAndPrices() {
        DataSource ds = new DriverManagerDataSource(
            "jdbc:h2:mem:xinji_upgrade;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        migrate(ds, "151");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        Long originalId = jdbc.queryForObject("select id from ai_provider where code = 'VOLCENGINE_ARK'", Long.class);
        jdbc.update("update ai_provider set status = 'ENABLED' where id = ?", originalId);
        jdbc.update("""
            update ai_provider_config set api_key_cipher = 'preserve-encrypted-key',
                base_url = 'https://original.example/api/v3', extra_config = '{"timeout":91}',
                status = 'ENABLED', last_test_status = 'SUCCESS', last_test_message = 'connected', last_test_at = now()
             where provider_id = ?
            """, originalId);
        Long standardId = jdbc.queryForObject("select id from ai_model where code = 'SEEDANCE_2_0_STANDARD'", Long.class);
        for (String kind : List.of("", "point_")) {
            String table = "ai_model_" + kind + "price_version";
            jdbc.update("insert into " + table + " (model_id, version_no, status, effective_from, created_at)"
                + " values (?, 1, 'PUBLISHED', now(), now())", standardId);
            Long versionId = jdbc.queryForObject("select id from " + table + " where model_id = ?", Long.class, standardId);
            String columns = kind.isEmpty() ? "unit_price, currency" : "point_rate";
            String values = kind.isEmpty() ? "0.123456, 'CNY'" : "17.250000";
            jdbc.update("insert into ai_model_" + kind + "price_component"
                + " (price_version_id, metric, unit_size, " + columns + ", dimensions_json, dimensions_key, created_at)"
                + " values (?, 'VIDEO_SECOND', 1, " + values + ", '{}', '', now())", versionId);
        }
        jdbc.update("insert into ai_model_execution_quota (model_id, concurrency_limit, updated_at) values (?, 7, now())", standardId);
        List<Map<String, Object>> models = jdbc.queryForList("select * from ai_model order by id");
        Map<String, List<Map<String, Object>>> unchanged = new LinkedHashMap<>();
        for (String table : List.of("ai_model_price_version", "ai_model_price_component", "ai_model_point_price_version",
            "ai_model_point_price_component", "ai_model_price_version_sequence", "ai_model_parameter_profile",
            "ai_model_capability", "ai_model_execution_quota", "ai_video_task", "ai_call_log")) {
            unchanged.put(table, jdbc.queryForList("select * from " + table));
        }
        migrate(ds, "155");
        Long xinjiId = jdbc.queryForObject("select id from ai_provider where code = 'VOLCENGINE_ARK_XINJI'", Long.class);
        assertThat(jdbc.queryForObject("select name from ai_provider where id = ?", String.class, xinjiId))
            .isEqualTo("\u706b\u5c71\uff08\u4fe1\u8ba1\uff09");
        assertThat(jdbc.queryForObject("select status from ai_provider where id = ?", String.class, xinjiId)).isEqualTo("ENABLED");
        String config = "select api_key_cipher, base_url, extra_config, status, last_test_status, last_test_message, last_test_at"
            + " from ai_provider_config where provider_id = ?";
        assertThat(jdbc.queryForMap(config, xinjiId)).isEqualTo(jdbc.queryForMap(config, originalId));
        for (Map<String, Object> model : models) {
            if (List.of("SEEDANCE_2_0_STANDARD", "SEEDANCE_2_5").contains(model.get("code"))) {
                model.put("provider_id", xinjiId);
            }
        }
        assertThat(jdbc.queryForList("select * from ai_model order by id")).isEqualTo(models);
        unchanged.forEach((table, rows) -> assertThat(jdbc.queryForList("select * from " + table)).as(table).isEqualTo(rows));
    }

    private static void migrate(DataSource ds, String target) {
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target(target).load().migrate();
    }
}
