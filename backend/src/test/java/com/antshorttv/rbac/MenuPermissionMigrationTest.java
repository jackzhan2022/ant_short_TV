package com.antshorttv.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class MenuPermissionMigrationTest {
    @Test
    void upgradesExistingRolesWithoutChangingTheirBusinessPermissions() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:menu_upgrade;MODE=MySQL", "sa", "")) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V2__rbac_schema.sql"));
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            jdbc.update("""
                insert into `role` (tenant_id, code, name, role_type, status, is_default, created_at, updated_at)
                values (1, 'CUSTOM', 'Custom', 'CUSTOM', 'ACTIVE', false, current_timestamp, current_timestamp),
                       (1, 'MEMBER', 'Member', 'SYSTEM', 'DISABLED', true, current_timestamp, current_timestamp),
                       (2, 'CUSTOM', 'Deleted', 'CUSTOM', 'ACTIVE', false, current_timestamp, current_timestamp)
                """);
            jdbc.update("update `role` set deleted_at = current_timestamp where tenant_id = 2");
            jdbc.update("""
                insert into permission (code, name, type, resource, action, created_at, updated_at)
                values ('PROJECT:CREATE', 'Create', 'BUTTON', 'PROJECT', 'CREATE', current_timestamp, current_timestamp)
                """);
            jdbc.update("insert into role_permission (role_id, permission_id, created_at) values (1, 1, current_timestamp)");

            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V149__tenant_menu_permissions.sql"));
            assertThat(jdbc.queryForObject("select count(*) from permission where type = 'MENU'", Integer.class)).isEqualTo(10);
            assertThat(jdbc.queryForObject("select count(*) from role_permission where role_id = 1", Integer.class)).isEqualTo(11);
            assertThat(jdbc.queryForObject("select count(*) from role_permission where role_id = 2", Integer.class)).isEqualTo(10);
            assertThat(jdbc.queryForObject("select count(*) from role_permission where role_id = 3", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from role_permission where permission_id = 1", Integer.class)).isEqualTo(1);
        }
    }
}
