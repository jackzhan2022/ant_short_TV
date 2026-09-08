package com.antshorttv.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class CommercialEntitlementDefinitionMigrationTest {
    @Autowired private DataSource dataSource;

    @Test
    void createsCatalogSchemaAndSeedsProtectedSystemEntitlements() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertThat(jdbc.queryForObject("""
            select count(*) from information_schema.tables
             where lower(table_name) = 'commercial_entitlement_definition'
            """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("""
            select lower(column_name) from information_schema.columns
             where lower(table_name) = 'commercial_entitlement_definition'
            """, String.class)).contains(
                "id", "code", "name", "description", "category", "status",
                "sort_order", "created_at", "updated_at");
        List<String> indexes = jdbc.queryForList("""
            select lower(index_name) from information_schema.indexes
             where lower(table_name) = 'commercial_entitlement_definition'
            """, String.class);
        assertThat(indexes).contains("idx_commercial_entitlement_definition_list");
        assertThat(indexes).anyMatch(name -> name.startsWith("uk_commercial_entitlement_definition_code"));

        List<String> codes = jdbc.queryForList("""
            select code from commercial_entitlement_definition
             where category = 'SYSTEM' and status = 'ACTIVE'
             order by sort_order, id
            """, String.class);
        assertThat(codes).containsExactly(
            "ONE_TIME_POINTS", "PERIODIC_POINTS", "GLOBAL_DISCOUNT");
        assertThat(jdbc.queryForObject("""
            select count(*) from commercial_entitlement_definition
             where code in ('ONE_TIME_POINTS', 'PERIODIC_POINTS', 'GLOBAL_DISCOUNT')
            """, Integer.class)).isEqualTo(3);
    }

    @Test
    void preservesPackageEntitlementSnapshotColumns() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertThat(jdbc.queryForList("""
            select lower(column_name) from information_schema.columns
             where lower(table_name) = 'commercial_entitlement'
            """, String.class)).contains("entitlement_type", "numeric_value", "text_value", "config_json");
    }
}
