package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.execution.AiExecutionResponse;
import com.antshorttv.rbac.RbacPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AutoStoryboardDispatchServiceTest {
    private JdbcTemplate jdbc;
    private AutoStoryboardEventRepository events;
    private ScriptAiOperationService operations;
    private RbacPermissionService permissions;
    private AutoStoryboardDispatchService dispatcher;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:auto_storyboard_dispatch;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        for (String table : new String[]{"episode_auto_storyboard_event", "storyboard",
            "script_episode", "script", "tenant_member"}) {
            jdbc.execute("drop table if exists " + table);
        }
        jdbc.execute("create table script (id bigint primary key, current_version_id bigint)");
        jdbc.execute("create table script_episode (id bigint primary key, tenant_id bigint, project_id bigint, script_id bigint, stable_key varchar(128), content_fingerprint varchar(128), status varchar(32), retired_at timestamp)");
        jdbc.execute("create table storyboard (id bigint auto_increment primary key, tenant_id bigint, project_id bigint, episode_id bigint, deleted_at timestamp)");
        jdbc.execute("create table tenant_member (id bigint primary key, tenant_id bigint, user_id bigint, member_type varchar(32), status varchar(32))");
        jdbc.execute("""
            create table episode_auto_storyboard_event (
              id bigint auto_increment primary key, tenant_id bigint not null, project_id bigint not null,
              script_id bigint not null, episode_id bigint not null, episode_key varchar(128) not null,
              source_fingerprint varchar(128) not null, asset_analysis_id bigint not null,
              context_snapshot_id bigint null, policy_version varchar(32) not null,
              status varchar(32) not null, attempt_no int not null default 0,
              execution_id bigint null, error_code varchar(128) null, error_message varchar(1000) null,
              created_by bigint not null, created_at timestamp not null, updated_at timestamp not null,
              next_attempt_at timestamp null, finished_at timestamp null,
              unique (tenant_id, script_id, episode_id, source_fingerprint, policy_version)
            )
            """);
        jdbc.update("insert into script values (9, 90)");
        jdbc.update("insert into script_episode values (10,7,8,9,'episode-1','fp','ACTIVE',null)");
        jdbc.update("insert into tenant_member values (70,7,40,'OWNER','ACTIVE')");
        events = new AutoStoryboardEventRepository(jdbc);
        operations = mock(ScriptAiOperationService.class);
        permissions = mock(RbacPermissionService.class);
        dispatcher = new AutoStoryboardDispatchService(events, operations, permissions, jdbc, true, 120);
    }

    @Test
    void dispatchesThroughTheExistingBilledOperationEntry() {
        long eventId = events.recordPending(draft());
        when(permissions.hasPermission(any(), any(), any())).thenReturn(true);
        AiExecutionResponse execution = mock(AiExecutionResponse.class);
        when(execution.id()).thenReturn(999L);
        when(operations.submit(any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(execution);

        dispatcher.dispatchOne();

        assertThat(jdbc.queryForMap("select status, execution_id from episode_auto_storyboard_event where id=?", eventId))
            .containsEntry("status", "DISPATCHED")
            .containsEntry("execution_id", 999L);
    }

    @Test
    void protectsAnyExistingStoryboardFromAutomaticOverwrite() {
        long eventId = events.recordPending(draft());
        jdbc.update("insert into storyboard(tenant_id,project_id,episode_id) values (7,8,10)");

        dispatcher.dispatchOne();

        assertThat(jdbc.queryForObject(
            "select status from episode_auto_storyboard_event where id=?", String.class, eventId))
            .isEqualTo("PROTECTED");
        verify(operations, never()).submit(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private AutoStoryboardEventRepository.Draft draft() {
        return new AutoStoryboardEventRepository.Draft(
            7L, 8L, 9L, 10L, "episode-1", "fp", 20L, 30L, 40L);
    }
}
