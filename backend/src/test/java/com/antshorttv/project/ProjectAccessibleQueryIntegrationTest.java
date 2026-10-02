package com.antshorttv.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import com.antshorttv.common.PageBounds;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ProjectAccessibleQueryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private ProjectMemberMapper projectMemberMapper;

    @Autowired
    private ProjectRoleMapper projectRoleMapper;

    @Test
    void browseCountAndPageFilterViewPermissionBeforeLimit() {
        long tenantId = 881001L, userId = 881002L;
        if (jdbc.queryForObject("select count(*) from permission where code='PROJECT:VIEW'", Long.class) == 0)
            jdbc.update("""
                insert into permission (code,name,type,resource,action,created_at,updated_at)
                values ('PROJECT:VIEW','View','BUTTON','PROJECT','VIEW',current_timestamp,current_timestamp)
                """);
        for (int i = 0; i < 4; i++) {
            jdbc.update("""
                insert into project (tenant_id,name,code,owner_id,status,created_by,created_at,updated_at)
                values (?, 'Browse', ?, ?, 'ACTIVE', ?, current_timestamp, current_timestamp)
                """, tenantId, "BROWSE_" + i, userId, userId);
            Long id = jdbc.queryForObject("select id from project where tenant_id=? and code=?", Long.class,
                tenantId, "BROWSE_" + i);
            jdbc.update("""
                insert into project_role (tenant_id,project_id,name,code,is_system,status,created_by,created_at,updated_at)
                values (?,?,'Viewer','VIEWER',false,'ACTIVE',?,current_timestamp,current_timestamp)
                """, tenantId, id, userId);
            Long role = jdbc.queryForObject("select id from project_role where project_id=?", Long.class, id);
            jdbc.update("""
                insert into project_member (tenant_id,project_id,user_id,role_id,joined_at,status,created_by,created_at,updated_at)
                values (?,?,?,?,current_timestamp,'ACTIVE',?,current_timestamp,current_timestamp)
                """, tenantId, id, userId, role, userId);
            if (i != 3) jdbc.update("""
                insert into project_role_permission (tenant_id,project_id,role_id,permission_id,created_at)
                select ?,?,?,id,current_timestamp from permission where code='PROJECT:VIEW'
                """, tenantId, id, role);
        }
        assertThat(projectMapper.countVisible(tenantId, userId, false, null)).isEqualTo(3);
        var page = projectMapper.selectVisiblePage(tenantId, userId, false, null, PageBounds.of(2, 2));
        assertThat(page).hasSize(1);
        assertThat(page.get(0).code).isEqualTo("BROWSE_0");
        assertThat(projectMapper.countVisible(tenantId, userId, true, null)).isEqualTo(4);
        assertThat(projectMapper.selectVisiblePage(tenantId, userId, true, "BROWSE_3", PageBounds.of(1, 20)))
            .hasSize(1);
        ProjectMemberEntity removed = new ProjectMemberEntity();
        removed.status = "REMOVED";
        projectMemberMapper.update(removed, new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ProjectMemberEntity>()
            .eq("tenant_id", tenantId));
        assertThat(projectMapper.countVisible(tenantId, userId, false, null)).isZero();
    }

    @Test
    void oldVersionStateCannotReplaceNewCover() {
        jdbc.update("""
            insert into project (tenant_id,name,code,owner_id,status,created_by,created_at,updated_at,
                                 cover_url,cover_version,cover_status)
            values (881011,'Cover','COVER_VERSION',881012,'ACTIVE',881012,current_timestamp,current_timestamp,
                    'new-source','new-version','PENDING')
            """);
        Long id = jdbc.queryForObject("select id from project where code='COVER_VERSION'", Long.class);
        assertThat(projectMapper.updateCoverState(id, "old-version", "READY", null)).isZero();
        assertThat(projectMapper.canonicalizeCover(id, "old-version", "old-key")).isZero();
        assertThat(projectMapper.selectById(id).coverUrl).isEqualTo("new-source");
        assertThat(projectMapper.selectById(id).coverStatus).isEqualTo("PENDING");
        ProjectEntity stale = projectMapper.selectById(id);
        jdbc.update("update project set cover_url='newer-source',cover_version='newer-version',cover_status='READY' where id=?", id);
        stale.name = "Renamed";
        projectMapper.updateById(stale);
        assertThat(projectMapper.selectById(id).coverUrl).isEqualTo("newer-source");
        assertThat(projectMapper.selectById(id).coverVersion).isEqualTo("newer-version");
        assertThat(projectMapper.selectById(id).coverStatus).isEqualTo("READY");
        jdbc.update("update project set cover_url='new-source',cover_version='new-version' where id=?", id);
        jdbc.update("update project set cover_status='UNPROCESSED' where id=?", id);
        ProjectEntity project = projectMapper.selectById(id);
        assertThat(projectMapper.claimCover(project, "new-source", "new-version", false)).isEqualTo(1);
        assertThat(projectMapper.claimCover(project, "new-source", "new-version", false)).isZero();
        assertThat(projectMapper.selectById(id).coverStatus).isEqualTo("PENDING");
    }

    @Test
    void assignedProjectDisappearsAfterMembershipRemovalOrRoleDeactivation() {
        long tenantId = 880001L;
        long userId = 880002L;
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
            insert into project
              (tenant_id, name, code, description, cover_url, owner_id, status,
               created_by, created_at, updated_at, deleted_at)
            values (?, 'Accessible Project', 'ACCESS_QUERY_TEST', null, null, ?, 'ACTIVE',
                    ?, ?, ?, null)
            """, tenantId, userId, userId, now, now);
        Long projectId = jdbc.queryForObject(
            "select id from project where tenant_id = ? and code = 'ACCESS_QUERY_TEST'",
            Long.class,
            tenantId
        );
        jdbc.update("""
            insert into project_role
              (tenant_id, project_id, name, code, description, is_system, status,
               created_by, created_at, updated_at)
            values (?, ?, 'Writer', 'WRITER', null, false, 'ACTIVE', ?, ?, ?)
            """, tenantId, projectId, userId, now, now);
        Long roleId = jdbc.queryForObject(
            "select id from project_role where project_id = ? and code = 'WRITER'",
            Long.class,
            projectId
        );
        jdbc.update("""
            insert into project_member
              (tenant_id, project_id, user_id, role_id, joined_at, status,
               created_by, created_at, updated_at)
            values (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
            """, tenantId, projectId, userId, roleId, now, userId, now, now);

        assertThat(projectMapper.selectAccessibleByMember(tenantId, userId))
            .extracting(project -> project.id)
            .containsExactly(projectId);

        String script = "A long script. ".repeat(10000);
        jdbc.update("update project set initial_script_content = ? where id = ?", script, projectId);
        assertThat(projectMapper.selectByTenantId(tenantId)).singleElement()
            .satisfies(project -> assertThat(project.initialScriptContent).isNull());
        assertThat(projectMapper.selectAccessibleByMember(tenantId, userId)).singleElement()
            .satisfies(project -> assertThat(project.initialScriptContent).isNull());
        assertThat(projectMapper.selectByTenantIdAndId(tenantId, projectId).initialScriptContent)
            .isEqualTo(script);

        ProjectMemberEntity member = projectMemberMapper.selectByProjectIdAndUserId(tenantId, projectId, userId);
        member.status = ProjectMemberStatus.REMOVED.name();
        projectMemberMapper.updateById(member);
        assertThat(projectMapper.selectAccessibleByMember(tenantId, userId)).isEmpty();

        member.status = ProjectMemberStatus.ACTIVE.name();
        projectMemberMapper.updateById(member);
        ProjectRoleEntity role = projectRoleMapper.selectById(roleId);
        role.status = ProjectRoleStatus.DISABLED.name();
        projectRoleMapper.updateById(role);
        assertThat(projectMapper.selectAccessibleByMember(tenantId, userId)).isEmpty();
    }
}
