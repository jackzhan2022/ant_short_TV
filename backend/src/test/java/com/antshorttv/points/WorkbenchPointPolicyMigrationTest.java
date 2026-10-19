package com.antshorttv.points;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class WorkbenchPointPolicyMigrationTest {
    @Test
    void freezesOnlySameTenantWorkbenchTextHistoryWithoutChangingHistoricalCharges() {
        var dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:workbench_policy_migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table ai_execution_task(id bigint primary key,tenant_id bigint,project_id bigint,capability varchar(32),scene varchar(128))");
        jdbc.execute("create table ai_point_reservation(id bigint primary key,tenant_id bigint,execution_id bigint,settled_points decimal(24,8))");
        jdbc.update("insert into ai_execution_task values (1,7,50,'TEXT','script_analysis'),(2,7,50,'TEXT','storyboard_breakdown'),"
            + "(3,7,50,'IMAGE','script_analysis'),(4,8,51,'TEXT','script_analysis'),(5,7,50,'TEXT','script_generate')");
        jdbc.update("insert into ai_point_reservation values (1,7,1,100),(2,7,2,200),(3,7,3,300),(4,7,4,400),(5,7,5,500)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V139__workbench_point_policy.sql")).execute(dataSource);
        assertThat(jdbc.queryForList("select id from ai_point_reservation where workbench_project_id=50 order by id", Long.class))
            .isEqualTo(List.of(1L, 2L));
        assertThat(jdbc.queryForObject("select count(*) from ai_point_reservation where workbench_project_id is null", Integer.class))
            .isEqualTo(3);
        assertThat(jdbc.queryForObject("select sum(settled_points) from ai_point_reservation", java.math.BigDecimal.class))
            .isEqualByComparingTo("1500");
        assertThat(jdbc.queryForObject("select count(*) from ai_point_reservation where workbench_baseline_points is null and workbench_assessed_points is null", Integer.class))
            .isEqualTo(5);
    }
}
