package com.antshorttv.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class RetireOperationPermissionMigrationTest {
    @Test
    void deletesOperationGrantsPreservesProjectVisibilityAndAvoidsAdministrativeEscalation() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:menu_only_upgrade;MODE=MySQL", "sa", "")) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V2__rbac_schema.sql"));
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            jdbc.execute("create table flyway_schema_history (version varchar(20), success boolean, installed_on timestamp)");
            jdbc.update("insert into flyway_schema_history values ('149',true,current_timestamp)");
            jdbc.execute("""
                create table project_role_permission (id bigint auto_increment primary key,tenant_id bigint,
                project_id bigint,role_id bigint,permission_id bigint,created_at timestamp,
                unique (tenant_id,project_id,role_id,permission_id))
                """);
            jdbc.update("""
                insert into `role` (tenant_id,code,name,role_type,status,is_default,created_at,updated_at) values
                (1,'MEMBER','Member','SYSTEM','ACTIVE',true,'2020-01-01','2020-01-01'),
                (1,'ADMIN','Admin','SYSTEM','ACTIVE',true,'2020-01-01','2020-01-01'),
                (1,'VIEWER','Wide Viewer','CUSTOM','ACTIVE',false,'2020-01-01','2020-01-01'),
                (1,'NEW_ROLE','New Menu Role','CUSTOM','ACTIVE',false,'2099-01-01','2099-01-01')
                """);
            jdbc.update("""
                insert into permission (code,name,type,resource,action,created_at,updated_at) values
                ('PROJECT:VIEW','View','PAGE','PROJECT','VIEW',current_timestamp,current_timestamp),
                ('PROJECT:VIEW_ALL','All','PAGE','PROJECT','VIEW_ALL',current_timestamp,current_timestamp),
                ('ROLE:EDIT','Edit','BUTTON','ROLE','EDIT',current_timestamp,current_timestamp)
                """);
            jdbc.update("insert into role_permission(role_id,permission_id,created_at) values (2,2,current_timestamp),(2,3,current_timestamp),(3,2,current_timestamp)");
            jdbc.update("insert into project_role_permission(tenant_id,project_id,role_id,permission_id,created_at) values (1,10,20,1,current_timestamp)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V149__tenant_menu_permissions.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V150__retire_operation_permissions.sql"));
            assertThat(jdbc.queryForObject("select count(*) from permission", Integer.class)).isEqualTo(10);
            assertThat(jdbc.queryForObject("select count(*) from permission where type <> 'MENU'", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select project_access_all from `role` where id=3", Boolean.class)).isTrue();
            assertThat(jdbc.queryForObject("select project_access_all from `role` where id=1", Boolean.class)).isFalse();
            assertThat(jdbc.queryForList("select p.code from role_permission rp join permission p on p.id=rp.permission_id where rp.role_id=1", String.class))
                .doesNotContain("MENU:TEAM_MANAGEMENT", "MENU:AI_MANAGEMENT");
            assertThat(jdbc.queryForList("select p.code from role_permission rp join permission p on p.id=rp.permission_id where rp.role_id=4", String.class))
                .contains("MENU:TEAM_MANAGEMENT", "MENU:AI_MANAGEMENT");
            assertThat(jdbc.queryForList("select p.code from project_role_permission rp join permission p on p.id=rp.permission_id", String.class))
                .containsExactly("MENU:PROJECTS");
            assertThat(jdbc.queryForObject("select count(*) from role_permission rp left join permission p on p.id=rp.permission_id where p.id is null", Integer.class)).isZero();
        }
    }
}
