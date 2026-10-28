package com.antshorttv.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antshorttv.common.MediaPage;
import com.antshorttv.common.PageBounds;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProjectStatusFilterTest {
    @Test
    void bindsCommaSeparatedStatusesAndRejectsInvalidStatus() throws Exception {
        ProjectService service = mock(ProjectService.class);
        when(service.list(anyLong(), any(), any(), any(), any()))
            .thenReturn(new MediaPage<>(List.of(), 1, 20, 0));
        var mvc = MockMvcBuilders.standaloneSetup(new ProjectController(service)).build();
        mvc.perform(get("/api/projects").header("X-Tenant-Id", "11")
                .param("statuses", "NOT_STARTED,IN_PROGRESS"))
            .andExpect(status().isOk());
        verify(service).list(11L, null, null, null, List.of(ProjectStatus.NOT_STARTED, ProjectStatus.IN_PROGRESS));
        mvc.perform(get("/api/projects").header("X-Tenant-Id", "11"))
            .andExpect(status().isOk());
        verify(service).list(11L, null, null, null, null);
        mvc.perform(get("/api/projects").header("X-Tenant-Id", "11").param("statuses", "UNKNOWN"))
            .andExpect(status().isBadRequest());
        verifyNoMoreInteractions(service);
    }

    @Test
    void filtersAuthorizedRowsAndTotalsBeforePagination() throws Exception {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:status_" + UUID.randomUUID()
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
            create table project (id bigint primary key, tenant_id bigint, name varchar(100), code varchar(100),
                description varchar(100), cover_url varchar(100), cover_source varchar(100), cover_version varchar(100),
                cover_status varchar(100), owner_id bigint, status varchar(30), start_date date, end_date date,
                aspect_ratio varchar(20), file_format varchar(20), video_resolution varchar(20),
                video_generate_audio boolean, video_watermark boolean, script_type varchar(20),
                breakdown_strength varchar(20), visual_style varchar(20), created_by bigint,
                created_at timestamp, updated_at timestamp, deleted_at timestamp)
            """);
        jdbc.execute("create table project_member (project_id bigint, tenant_id bigint, user_id bigint, role_id bigint, status varchar(20))");
        jdbc.execute("create table project_role (id bigint, project_id bigint, tenant_id bigint, status varchar(20))");
        jdbc.execute("create table project_role_permission (role_id bigint, project_id bigint, tenant_id bigint, permission_id bigint)");
        jdbc.execute("create table permission (id bigint, code varchar(100))");
        jdbc.update("insert into permission values (1, 'MENU:PROJECTS')");
        var statuses = List.of("NOT_STARTED", "IN_PROGRESS", "ARCHIVED", "IN_PROGRESS");
        for (int i = 1; i <= 4; i++) {
            jdbc.update("insert into project (id,tenant_id,name,code,status,created_at) values (?,11,?,?,?,current_timestamp)",
                i, "Project " + i, "PROJECT_" + i, statuses.get(i - 1));
            if (i == 4) continue; // A matching but unauthorized project must not leak into rows or counts.
            jdbc.update("insert into project_member values (?,11,7,?,'ACTIVE')", i, i);
            jdbc.update("insert into project_role values (?,?,11,'ACTIVE')", i, i);
            jdbc.update("insert into project_role_permission values (?,?,11,1)", i, i);
        }
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(ProjectMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(config);
        try (var session = factory.getObject().openSession(true)) {
            var mapper = session.getMapper(ProjectMapper.class);
            var active = List.of("NOT_STARTED", "IN_PROGRESS");
            assertThat(mapper.countVisible(11L, 7L, false, null, active)).isEqualTo(2);
            assertThat(mapper.selectVisiblePage(11L, 7L, false, null, active, PageBounds.of(1, 1)))
                .extracting(project -> project.id).containsExactly(2L);
            assertThat(mapper.selectVisiblePage(11L, 7L, false, null, active, PageBounds.of(2, 1)))
                .extracting(project -> project.id).containsExactly(1L);
            assertThat(mapper.selectVisiblePage(11L, 7L, false, null, List.of("ARCHIVED"), PageBounds.of(1, 20)))
                .extracting(project -> project.id).containsExactly(3L);
            assertThat(mapper.countVisible(11L, 7L, false, null, List.of("ARCHIVED"))).isEqualTo(1);
            assertThat(mapper.countVisible(11L, 7L, false, null, null)).isEqualTo(3);
            assertThat(mapper.countVisible(11L, 7L, false, null, List.of())).isEqualTo(3);
            assertThat(mapper.countVisible(11L, 7L, true, null, active)).isEqualTo(3);
            assertThat(mapper.countVisible(12L, 7L, true, null, active)).isZero();
            assertThat(mapper.countVisible(11L, 7L, false, "PROJECT_2", active)).isEqualTo(1);
            assertThat(mapper.countVisible(11L, 7L, false, "PROJECT_3", active)).isZero();
        }
    }
}
