package com.antshorttv.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
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
