package com.antshorttv.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.antshorttv.rbac.RbacPermissionService;
import com.antshorttv.script.ScriptVersionMapper;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@Import(ProjectReadPerformanceIntegrationTest.QueryConfiguration.class)
class ProjectReadPerformanceIntegrationTest {
    private static final long TENANT = 882001L;
    private static final String BODY = "长剧本正文。".repeat(30000);

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProjectMapper projects;
    @Autowired private ProjectAccessResolver accessResolver;
    @Autowired private ScriptVersionMapper versions;
    @Autowired private QueryCapture queries;
    @MockBean private TenantContextResolver tenants;
    @MockBean private RbacPermissionService permissions;

    @BeforeEach
    void setUp() {
        TenantContext tenant = new TenantContext(1L, TENANT, 1L, "OWNER");
        when(tenants.requireActiveMember(TENANT)).thenReturn(tenant);
        when(permissions.permissionCodes(tenant)).thenReturn(Set.of("PROJECT:VIEW_ALL"));
        queries.sql.get().clear();
    }

    @Test
    void permissionChecksAvoidScriptBodyWhileFullProjectReadsRemainCompatible() {
        long projectId = insertProject();
        ProjectEntity project = accessResolver.requireView(TENANT, projectId).project();

        assertThat(project.initialScriptContent == null).as("permission query excludes script body").isTrue();
        assertThat(project.name).isEqualTo("Large Script");
        assertThat(project.videoResolution).isEqualTo("1080p");
        assertThat(projects.selectByTenantIdAndId(TENANT + 1, projectId)).isNull();
        assertThat(queries.sql.get().get(0)).contains("tenant_id", "deleted_at").doesNotContain("initial_script_content");
        assertThat(projects.selectByTenantIdAndId(TENANT, projectId).initialScriptContent).isEqualTo(BODY);
    }

    @Test
    void versionListsReadOnlySummaryColumnsAndPreserveOrderingAndTenantBoundary() {
        long projectId = insertProject();
        jdbc.update("""
            insert into script (tenant_id,project_id,title,source_type,content,status,created_by,created_at,updated_at)
            values (?,?,'Script','UPLOAD',?,'DRAFT',1,now(),now())
            """, TENANT, projectId, BODY);
        long scriptId = jdbc.queryForObject("select id from script where project_id=?", Long.class, projectId);
        for (int versionNo = 1; versionNo <= 3; versionNo++) {
            jdbc.update("""
                insert into script_version (tenant_id,project_id,script_id,version_no,source_type,input_summary,content,status,created_by,created_at)
                values (?,?,?,?,'MANUAL_EDIT','Version summary',?,'DRAFT',1,now())
                """, versionNo == 3 ? TENANT + 1 : TENANT, projectId, scriptId, versionNo, BODY);
        }
        queries.sql.get().clear();
        var result = versions.selectByScript(TENANT, scriptId);

        assertThat(result).extracting(item -> item.getVersionNo()).containsExactly(2, 1);
        assertThat(result).allSatisfy(item -> {
            assertThat(item.getContent() == null).as("version summary excludes script body").isTrue();
            assertThat(item.getScriptId()).isEqualTo(scriptId);
            assertThat(item.getInputSummary()).isEqualTo("Version summary");
            assertThat(item.getCreatedAt()).isNotNull();
        });
        assertThat(queries.sql.get()).singleElement().satisfies(sql -> {
            assertThat(sql).contains("version_no", "input_summary", "created_at", "tenant_id");
            assertThat(sql.substring(0, sql.indexOf("from"))).doesNotContain("content");
        });
        assertThat(versions.selectById(result.get(0).getId()).getContent()).isEqualTo(BODY);
    }

    private long insertProject() {
        jdbc.update("""
            insert into project (tenant_id,name,code,owner_id,status,video_resolution,initial_script_content,created_by,created_at,updated_at)
            values (?,'Large Script','READ_PERFORMANCE',1,'ACTIVE','1080p',?,1,now(),now())
            """, TENANT, BODY);
        return jdbc.queryForObject("select id from project where tenant_id=? and code='READ_PERFORMANCE'", Long.class, TENANT);
    }

    @TestConfiguration
    static class QueryConfiguration {
        @Bean
        QueryCapture queryCapture() { return new QueryCapture(); }
    }

    @Intercepts(@Signature(type = Executor.class, method = "query", args = {
        MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}))
    static class QueryCapture implements Interceptor {
        final ThreadLocal<List<String>> sql = ThreadLocal.withInitial(ArrayList::new);

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
            sql.get().add(statement.getBoundSql(invocation.getArgs()[1]).getSql().toLowerCase(java.util.Locale.ROOT));
            return invocation.proceed();
        }
    }
}
