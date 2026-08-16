package com.antshorttv.schema;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class SchemaMigrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void flywayCreatesAccountTeamTables() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer tableCount = jdbc.queryForObject("""
            select count(distinct lower(table_name))
            from information_schema.tables
            where lower(table_name) in ('app_user', 'tenant', 'tenant_member', 'tenant_invitation', 'operation_log')
            """, Integer.class);

        assertThat(tableCount).isEqualTo(5);
    }
}
