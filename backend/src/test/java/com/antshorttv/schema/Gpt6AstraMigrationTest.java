package com.antshorttv.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class Gpt6AstraMigrationTest {

    @Test
    void copiesOnlyEffectiveTerraPricesAndPreservesSourceHistory() {
        DataSource dataSource = dataSource("astra_terra_upgrade");
        migrate(dataSource, "134");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Long providerId = jdbc.queryForObject("select id from ai_provider where code = 'MiniMax'", Long.class);
        jdbc.update("""
            insert into ai_model
              (provider_id, code, name, model_code, service_type, status, is_default, sort, created_at, updated_at)
            values (?, 'gpt-5.6-terra-xiongxiong', 'GPT-5.6 Terra', 'gpt-5.6-terra',
                    'TEXT', 'ENABLED', false, 100, now(), now())
            """, providerId);
        Long terraId = jdbc.queryForObject(
            "select id from ai_model where code = 'gpt-5.6-terra-xiongxiong'", Long.class);

        for (String kind : List.of("", "point_")) {
            String table = "ai_model_" + kind + "price_version";
            jdbc.update("""
                insert into %s
                  (model_id, version_no, status, effective_from, effective_to, published_at, created_at)
                values
                  (?, 1, 'PUBLISHED', '2000-01-01', '2001-01-01', now(), now()),
                  (?, 2, 'PUBLISHED', '2001-01-01', null, now(), now()),
                  (?, 3, 'DRAFT', '2002-01-01', null, null, now()),
                  (?, 4, 'PUBLISHED', '2999-01-01', null, now(), now()),
                  (?, 5, 'REVOKED', '2003-01-01', null, now(), now())
                """.formatted(table), terraId, terraId, terraId, terraId, terraId);
            for (int version = 1; version <= 5; version++) {
                Long versionId = jdbc.queryForObject(
                    "select id from " + table + " where model_id = ? and version_no = ?",
                    Long.class, terraId, version);
                seedComponents(jdbc, kind, versionId, version);
            }
        }
        var sourceCostsBefore = jdbc.queryForList("select * from ai_model_price_version");
        var sourcePointsBefore = jdbc.queryForList("select * from ai_model_point_price_version");
        var sourceCostComponentsBefore = jdbc.queryForList("select * from ai_model_price_component");
        var sourcePointComponentsBefore = jdbc.queryForList("select * from ai_model_point_price_component");

        migrate(dataSource, "137");

        Long astraId = jdbc.queryForObject("select id from ai_model where code = 'gpt-6-astra'", Long.class);
        assertThat(jdbc.queryForObject("select provider_id from ai_model where id = ?", Long.class, astraId))
            .isEqualTo(providerId);
        for (String kind : List.of("", "point_")) {
            String table = "ai_model_" + kind + "price_version";
            String componentTable = "ai_model_" + kind + "price_component";
            String columns = "metric, unit_size, " + (kind.isEmpty() ? "unit_price, currency" : "point_rate")
                + ", dimensions_json, dimensions_key";
            Long sourceVersion = jdbc.queryForObject(
                "select id from " + table + " where model_id = ? and version_no = 2", Long.class, terraId);
            Long targetVersion = jdbc.queryForObject(
                "select id from " + table + " where model_id = ? and status = 'PUBLISHED'"
                    + " and effective_from <= now() and effective_to is null", Long.class, astraId);
            assertThat(targetVersion).isNotEqualTo(sourceVersion);
            assertThat(jdbc.queryForList("select " + columns + " from " + componentTable
                + " where price_version_id = ? order by metric, dimensions_key", targetVersion))
                .isEqualTo(jdbc.queryForList("select " + columns + " from " + componentTable
                    + " where price_version_id = ? order by metric, dimensions_key", sourceVersion));
        }
        assertThat(jdbc.queryForList("select * from ai_model_price_version where model_id = ?", terraId))
            .isEqualTo(sourceCostsBefore);
        assertThat(jdbc.queryForList("select * from ai_model_point_price_version where model_id = ?", terraId))
            .isEqualTo(sourcePointsBefore);
        assertThat(jdbc.queryForList("select * from ai_model_price_component where price_version_id in"
            + " (select id from ai_model_price_version where model_id = ?)", terraId))
            .isEqualTo(sourceCostComponentsBefore);
        assertThat(jdbc.queryForList("select * from ai_model_point_price_component where price_version_id in"
            + " (select id from ai_model_point_price_version where model_id = ?)", terraId))
            .isEqualTo(sourcePointComponentsBefore);
        assertThat(jdbc.queryForList("select last_version_no from ai_model_price_version_sequence"
            + " where model_id = ? order by price_type", Integer.class, astraId)).containsExactly(1, 1);
    }

    @Test
    void freshInstallDoesNotInventPricesWhenTerraIsNotConfigured() {
        DataSource dataSource = dataSource("astra_fresh_install");
        migrate(dataSource, "137");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("select count(*) from ai_model where code = 'gpt-6-astra'", Integer.class))
            .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from ai_model_price_version", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from ai_model_point_price_version", Integer.class)).isZero();
    }

    private static void seedComponents(JdbcTemplate jdbc, String kind, Long versionId, int version) {
        var metrics = List.of("CALL", "INPUT_TOKEN", "CACHED_INPUT_TOKEN", "CACHE_WRITE_TOKEN", "OUTPUT_TOKEN");
        var rates = List.of("0", "2.25", "0.045", "2.25", "9");
        for (int index = 0; index < metrics.size(); index++) {
            String columns = kind.isEmpty() ? "unit_price, currency" : "point_rate";
            String placeholders = kind.isEmpty() ? "?, 'CNY'" : "?";
            BigDecimal rate = new BigDecimal(rates.get(index)).multiply(BigDecimal.valueOf(version));
            if (!kind.isEmpty()) rate = rate.multiply(BigDecimal.valueOf(210));
            jdbc.update("insert into ai_model_" + kind + "price_component"
                + " (price_version_id, metric, unit_size, " + columns + ", dimensions_json, dimensions_key, created_at)"
                + " values (?, ?, ?, " + placeholders + ", ?, ?, now())",
                versionId, metrics.get(index), index == 0 ? 1 : 1000000, rate,
                index == 4 ? "{\"context\":\"long\"}" : "{}", index == 4 ? "context=long" : "");
        }
    }

    private static DataSource dataSource(String name) {
        return new DriverManagerDataSource(
            "jdbc:h2:mem:" + name + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    }

    private static void migrate(DataSource dataSource, String target) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
            .target(target).load().migrate();
    }
}
