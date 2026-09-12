package com.antshorttv.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.antshorttv.authsession.AuthenticatedUser;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(ReviewProjectProgressiveReadIntegrationTest.SqlCaptureConfiguration.class)
@Transactional
class ReviewProjectProgressiveReadIntegrationTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ReviewWorkbenchService service;
    @Autowired private SqlSessionTemplate sqlSessionTemplate;
    @Autowired private SqlRecorder sqlRecorder;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void summaryKeepsTheAccessibleSetAndOneDataQueryAsProjectCountGrows() {
        long tenantId = 910_000L;
        long userId = 910_001L;
        seedOwner(tenantId, userId, 910_002L);
        authenticate(userId);
        seedProject(tenantId, userId, 910_010L, "summary-a", false);
        seedProject(tenantId, userId, 910_011L, "summary-b", false);
        seedProject(tenantId + 1, userId, 910_012L, "cross-tenant", false);
        seedProject(tenantId, userId, 910_013L, "deleted", true);

        sqlRecorder.clear();
        List<ReviewProjectListSummaryResponse> small = service.listProjectSummaries(tenantId);
        List<String> smallDataSql = sqlRecorder.reviewDataStatements();

        assertThat(small).extracting(ReviewProjectListSummaryResponse::id)
            .containsExactly(910_011L, 910_010L);
        assertThat(smallDataSql).hasSize(1);
        assertSummaryProjection(smallDataSql.get(0));

        List<Long> addedProjectIds = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            long projectId = 910_100L + index;
            addedProjectIds.add(projectId);
            seedProject(tenantId, userId, projectId, "summary-large-" + index, false);
        }

        sqlSessionTemplate.clearCache();
        sqlRecorder.clear();
        List<ReviewProjectListSummaryResponse> large = service.listProjectSummaries(tenantId);
        List<String> largeDataSql = sqlRecorder.reviewDataStatements();

        assertThat(large).extracting(ReviewProjectListSummaryResponse::id)
            .containsExactlyElementsOf(expectedDescending(addedProjectIds, 910_011L, 910_010L));
        assertThat(largeDataSql).hasSize(1);
        assertSummaryProjection(largeDataSql.get(0));
    }

    @Test
    void metricsDeriveEveryWorkStateFromTheLatestTaskForMultipleProjects() {
        long tenantId = 920_000L;
        long userId = 920_001L;
        seedOwner(tenantId, userId, 920_002L);
        authenticate(userId);

        long emptyProject = seedProject(tenantId, userId, 920_010L, "empty", false);
        long runningProject = seedProject(tenantId, userId, 920_020L, "running", false);
        long markdownCompleteProject = seedProject(tenantId, userId, 920_030L, "markdown-complete", false);
        long markdownFailedProject = seedProject(tenantId, userId, 920_040L, "markdown-failed", false);
        long activeProject = seedProject(tenantId, userId, 920_050L, "active", false);
        long canceledProject = seedProject(tenantId, userId, 920_060L, "canceled", false);
        long latestTaskProject = seedProject(tenantId, userId, 920_070L, "latest-task", false);

        long runningVersion = seedVersion(tenantId, runningProject, 920_021L, 1, userId);
        long markdownVersion = seedVersion(tenantId, markdownCompleteProject, 920_031L, 1, userId);
        seedVersion(tenantId, markdownCompleteProject, 920_032L, 2, userId);
        long failedVersion = seedVersion(tenantId, markdownFailedProject, 920_041L, 1, userId);
        long activeVersion = seedVersion(tenantId, activeProject, 920_051L, 1, userId);
        long canceledVersion = seedVersion(tenantId, canceledProject, 920_061L, 1, userId);
        long latestVersion = seedVersion(tenantId, latestTaskProject, 920_071L, 1, userId);

        seedTask(tenantId, runningProject, runningVersion, 920_022L, 1, "PENDING", null, userId, CREATED_AT);
        seedTask(tenantId, markdownCompleteProject, markdownVersion, 920_033L, 2,
            "COMPLETED", "# report", userId, CREATED_AT);
        seedTask(tenantId, markdownFailedProject, failedVersion, 920_042L, 1,
            "FAILED", null, userId, CREATED_AT);
        seedTask(tenantId, activeProject, activeVersion, 920_052L, 1,
            "RUNNING", null, userId, CREATED_AT);
        seedTask(tenantId, canceledProject, canceledVersion, 920_062L, 1,
            "CANCELED", null, userId, CREATED_AT);
        seedTask(tenantId, latestTaskProject, latestVersion, 920_072L, 1,
            "FAILED", null, userId, CREATED_AT);
        seedTask(tenantId, latestTaskProject, latestVersion, 920_074L, 2,
            "COMPLETED", "# latest report", userId, CREATED_AT);

        List<ReviewProjectMetricsResponse> metrics = service.listProjectMetrics(tenantId);
        Map<Long, ReviewProjectMetricsResponse> byProject = metrics.stream()
            .collect(Collectors.toMap(ReviewProjectMetricsResponse::projectId, metric -> metric));

        assertThat(metrics).hasSize(7);
        assertMetric(byProject.get(emptyProject), 0, 0, "NOT_REVIEWED", "发起审核");
        assertMetric(byProject.get(runningProject), 1, 1, "RUNNING", "查看进度");
        assertMetric(byProject.get(markdownCompleteProject), 2, 2, "COMPLETED", "查看报告");
        assertMetric(byProject.get(markdownFailedProject), 1, 1, "NOT_REVIEWED", "重试审核");
        assertMetric(byProject.get(activeProject), 1, 1, "RUNNING", "查看进度");
        assertMetric(byProject.get(canceledProject), 1, 1, "NOT_REVIEWED", "重试审核");
        assertMetric(byProject.get(latestTaskProject), 1, 2, "COMPLETED", "查看报告");
    }

    @Test
    void metricsKeepThreeNarrowDataQueriesAsProjectCountGrows() {
        long tenantId = 930_000L;
        long userId = 930_001L;
        seedOwner(tenantId, userId, 930_002L);
        authenticate(userId);
        seedMetricFixture(tenantId, userId, 930_010L);

        sqlRecorder.clear();
        assertThat(service.listProjectMetrics(tenantId)).hasSize(1);
        List<String> smallDataSql = sqlRecorder.reviewDataStatements();

        assertThat(smallDataSql).hasSize(3);
        assertNarrowMetricProjections(smallDataSql);

        for (int index = 0; index < 12; index++) {
            seedMetricFixture(tenantId, userId, 930_100L + index * 10L);
        }

        sqlSessionTemplate.clearCache();
        sqlRecorder.clear();
        assertThat(service.listProjectMetrics(tenantId)).hasSize(13);
        List<String> largeDataSql = sqlRecorder.reviewDataStatements();

        assertThat(largeDataSql).hasSize(3);
        assertNarrowMetricProjections(largeDataSql);
    }

    private void assertSummaryProjection(String sql) {
        String projection = sql.substring(0, sql.indexOf(" from review_project rp "));
        assertThat(projection)
            .contains("select rp.id, rp.main_project_id, rp.name, rp.source_file_name, rp.source_type")
            .doesNotContain("original_content", "last_task_id", "created_by");
    }

    private void assertNarrowMetricProjections(List<String> statements) {
        assertThat(statements).anySatisfy(this::assertSummaryProjection);
        String combined = String.join(" ", statements);
        assertThat(combined).doesNotContain(
            "original_content", "selected_dimensions_json", "review_scope_json", "global_index_json",
            "result_json", "position_json", "excerpt", "problem", "evidence_json", "suggestion"
        );
        assertThat(combined).doesNotContain("task.report_markdown as report_markdown");
        assertThat(statements).anySatisfy(sql -> assertThat(sql)
            .contains("select project_id, count(*) as version_count")
            .doesNotContain(" content"));
        assertThat(statements).anySatisfy(sql -> assertThat(sql)
            .contains("as has_report_markdown")
            .doesNotContain("task.report_markdown as report_markdown"));
        assertThat(combined).doesNotContain("review_issue", "review_issue_hit", "review_issue_event");
    }

    private void assertMetric(
        ReviewProjectMetricsResponse metric,
        int versionCount,
        int roundNo,
        String state,
        String actionLabel
    ) {
        assertThat(metric).isNotNull();
        assertThat(metric.versionCount()).isEqualTo(versionCount);
        assertThat(metric.latestRoundNo()).isEqualTo(roundNo);
        assertThat(metric.reviewState()).isEqualTo(state);
        assertThat(metric.actionLabel()).isEqualTo(actionLabel);
    }

    private List<Long> expectedDescending(List<Long> added, Long... original) {
        List<Long> expected = new ArrayList<>(added);
        expected.addAll(List.of(original));
        expected.sort(java.util.Comparator.reverseOrder());
        return expected;
    }

    private void seedOwner(long tenantId, long userId, long memberId) {
        jdbc.update("""
            insert into app_user
              (id, mobile, password_hash, nickname, status, created_at, updated_at)
            values (?, ?, 'hash', 'progressive reader', 'ACTIVE', ?, ?)
            """, userId, "139" + userId, Timestamp.valueOf(CREATED_AT), Timestamp.valueOf(CREATED_AT));
        jdbc.update("""
            insert into tenant
              (id, code, name, type, status, owner_member_id, created_at, updated_at)
            values (?, ?, 'progressive tenant', 'STUDIO', 'ACTIVE', ?, ?, ?)
            """, tenantId, "progressive-" + tenantId, memberId,
            Timestamp.valueOf(CREATED_AT), Timestamp.valueOf(CREATED_AT));
        jdbc.update("""
            insert into tenant_member
              (id, tenant_id, user_id, member_type, status, joined_at, created_at, updated_at)
            values (?, ?, ?, 'OWNER', 'ACTIVE', ?, ?, ?)
            """, memberId, tenantId, userId, Timestamp.valueOf(CREATED_AT),
            Timestamp.valueOf(CREATED_AT), Timestamp.valueOf(CREATED_AT));
    }

    private long seedProject(long tenantId, long userId, long projectId, String name, boolean deleted) {
        jdbc.update("""
            insert into review_project
              (id, tenant_id, name, source_type, original_content, status, created_by,
               created_at, updated_at, deleted_at)
            values (?, ?, ?, 'TXT', ?, 'ACTIVE', ?, ?, ?, ?)
            """, projectId, tenantId, name, "long script body " + projectId, userId,
            Timestamp.valueOf(CREATED_AT), Timestamp.valueOf(CREATED_AT),
            deleted ? Timestamp.valueOf(CREATED_AT.plusDays(1)) : null);
        return projectId;
    }

    private long seedVersion(long tenantId, long projectId, long versionId, int versionNo, long userId) {
        jdbc.update("""
            insert into review_script_version
              (id, tenant_id, project_id, version_no, source_type, content, created_by, created_at, updated_at)
            values (?, ?, ?, ?, 'TXT', ?, ?, ?, ?)
            """, versionId, tenantId, projectId, versionNo, "version body " + versionId, userId,
            Timestamp.valueOf(CREATED_AT), Timestamp.valueOf(CREATED_AT));
        return versionId;
    }

    private long seedTask(
        long tenantId,
        long projectId,
        long versionId,
        long taskId,
        int roundNo,
        String status,
        String reportMarkdown,
        long userId,
        LocalDateTime createdAt
    ) {
        jdbc.update("""
            insert into review_task
              (id, tenant_id, project_id, script_version_id, round_no, review_mode,
               selected_dimensions_json, review_scope_type,
               report_markdown, status, overall_progress, idempotency_key, created_by,
               created_at, updated_at)
            values (?, ?, ?, ?, ?, 'QUICK', '[]', 'ALL', ?, ?, 100, ?, ?, ?, ?)
            """, taskId, tenantId, projectId, versionId, roundNo, reportMarkdown,
            status, "progressive-task-" + taskId, userId,
            Timestamp.valueOf(createdAt), Timestamp.valueOf(createdAt));
        return taskId;
    }

    private void seedMetricFixture(long tenantId, long userId, long baseId) {
        long projectId = seedProject(tenantId, userId, baseId, "metric-" + baseId, false);
        long versionId = seedVersion(tenantId, projectId, baseId + 1, 1, userId);
        seedTask(tenantId, projectId, versionId, baseId + 2, 1,
            "COMPLETED", "# report", userId, CREATED_AT);
    }

    private void authenticate(long userId) {
        AuthenticatedUser principal = new AuthenticatedUser(
            userId, "139" + userId, "progressive-session", CREATED_AT.plusHours(1));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SqlCaptureConfiguration {
        @Bean
        SqlRecorder sqlRecorder() {
            return new SqlRecorder();
        }

        @Bean
        Interceptor sqlCaptureInterceptor(SqlRecorder recorder) {
            return new SqlCaptureInterceptor(recorder);
        }
    }

    static final class SqlRecorder {
        private final List<String> statements = new CopyOnWriteArrayList<>();

        void add(String sql) {
            statements.add(sql.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT));
        }

        void clear() {
            statements.clear();
        }

        List<String> reviewDataStatements() {
            return statements.stream()
                .filter(sql -> sql.contains(" from review_project rp ")
                    || sql.contains(" from review_script_version ")
                    || sql.contains(" from review_task task ")
                    || sql.contains(" from review_issue "))
                .toList();
        }
    }

    @Intercepts({
        @Signature(type = Executor.class, method = "query",
            args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query",
            args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
                CacheKey.class, BoundSql.class})
    })
    static final class SqlCaptureInterceptor implements Interceptor {
        private final SqlRecorder recorder;

        SqlCaptureInterceptor(SqlRecorder recorder) {
            this.recorder = recorder;
        }

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
            BoundSql boundSql = invocation.getArgs().length == 6
                ? (BoundSql) invocation.getArgs()[5]
                : statement.getBoundSql(invocation.getArgs()[1]);
            recorder.add(boundSql.getSql());
            return invocation.proceed();
        }
    }
}
