package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.antshorttv.execution.AiExecutionTaskEntity;
import com.antshorttv.execution.AiExecutionTaskMapper;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class StoryboardBatchReadIntegrationTest {
    private final List<String> selects = new ArrayList<>();
    private JdbcTemplate jdbc;
    private SqlSession session;
    private StoryboardBatchService service;

    @BeforeEach
    void setUp() throws Exception {
        DataSource source = new DriverManagerDataSource("jdbc:h2:mem:batch_" + UUID.randomUUID()
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        DataSource counted = new DelegatingDataSource(source) {
            @Override
            public Connection getConnection() throws java.sql.SQLException {
                Connection connection = super.getConnection();
                return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                        try {
                            Object result = method.invoke(connection, args);
                            if (method.getName().equals("prepareStatement") && args[0] instanceof String sql) {
                                PreparedStatement statement = (PreparedStatement) result;
                                return Proxy.newProxyInstance(getClass().getClassLoader(),
                                    new Class<?>[] {PreparedStatement.class}, (p, m, a) -> {
                                        if ((m.getName().equals("executeQuery") || m.getName().equals("execute"))
                                            && sql.stripLeading().toLowerCase().startsWith("select")) {
                                            selects.add(sql.toLowerCase());
                                        }
                                        try { return m.invoke(statement, a); }
                                        catch (InvocationTargetException e) { throw e.getCause(); }
                                    });
                            }
                            return result;
                        } catch (InvocationTargetException e) { throw e.getCause(); }
                    });
            }
        };
        jdbc = new JdbcTemplate(counted);
        createEntityTable("storyboard_batch", StoryboardBatchEntity.class);
        createEntityTable("storyboard_batch_item", StoryboardBatchItemEntity.class);
        createEntityTable("ai_execution_task", AiExecutionTaskEntity.class);
        jdbc.execute("create table storyboard (id bigint primary key, tenant_id bigint, project_id bigint,"
            + " episode_id bigint, generated_by_run_id bigint, deleted_at timestamp, shot_plan_json clob)");
        jdbc.execute("create table ai_call_log (id bigint primary key, execution_id bigint)");
        jdbc.execute("create table ai_workflow_agent_run_step (id bigint primary key, run_id bigint, ai_call_log_id bigint)");
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(StoryboardBatchMapper.class);
        configuration.addMapper(StoryboardBatchItemMapper.class);
        configuration.addMapper(AiExecutionTaskMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(counted);
        factory.setConfiguration(configuration);
        session = factory.getObject().openSession(true);
        service = new StoryboardBatchService(mock(ProjectPermissionGuard.class), mock(ScriptMapper.class),
            mock(ScriptAiOperationService.class), session.getMapper(StoryboardBatchMapper.class),
            session.getMapper(StoryboardBatchItemMapper.class), session.getMapper(AiExecutionTaskMapper.class),
            jdbc, new ObjectMapper());
        batch(90, 1, 2);
    }

    @AfterEach
    void close() { if (session != null) session.close(); }

    @Test
    void latestWith85CompletedItemsUsesAtMostFourActualBusinessSelects() {
        for (int i = 85; i >= 1; i--) {
            item(i, 90, 1, 2, 1000 + i, i, 2000 + i, "SUCCEEDED");
            run(3000 + i, 2000 + i, 4000 + i);
            plan(i, 1, 2, 1000 + i, 4000 + i, null, "{\"warnings\":[\"w\"]}");
        }
        selects.clear();
        StoryboardBatchResponse result = service.latest(1L, 2L);
        assertThat(result.total()).isEqualTo(85);
        assertThat(result.warning()).isEqualTo(85);
        assertThat(result.status()).isEqualTo("SUCCEEDED_WITH_WARNING");
        assertThat(result.items()).extracting(StoryboardBatchItemResponse::episodeNo)
            .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 85).boxed().toList());
        assertThat(selects).as("actual JDBC business SELECT count for 85 completed items")
            .hasSizeLessThanOrEqualTo(4);
        assertThat(selects).hasSize(3);
        assertThat(selects).filteredOn(sql -> sql.contains("ai_execution_task"))
            .allSatisfy(sql -> assertThat(sql).doesNotContain("redacted_input_json", "provider_cost_summary_json"));
    }

    @Test
    void warningsCountOnlyArraysAndPreserveExactAssociationWithoutDuplicateStepInflation() {
        item(1, 90, 1, 2, 1001, 1, 2001, "SUCCEEDED");
        item(2, 90, 1, 2, 1002, 1, 2002, "SUCCEEDED"); // ties ordered by item id
        batch(91, 1, 2);
        item(3, 91, 1, 2, 1001, 1, 2003, "SUCCEEDED");
        item(4, 90, 9, 2, 1001, 1, 2004, "SUCCEEDED");
        item(5, 90, 1, 9, 1001, 1, 2005, "SUCCEEDED");
        // Existing execution lookup is ID-only, even for historical inconsistent execution ownership.
        jdbc.update("update ai_execution_task set tenant_id=99, project_id=99 where id=2001");
        run(3001, 2001, 4001);
        run(3002, 2001, 4001);
        run(3003, 2003, 4003);
        plan(1, 1, 2, 1001, 4001, null,
            "{\"warnings\":[null,{},[]],\"diagnostics\":{\"classificationWarnings\":[1,2]}}");
        plan(2, 1, 2, 1001, 4001, null, "{\"warnings\":[],\"diagnostics\":{\"classificationWarnings\":[null,\"x\"]}}");
        String[] ignored = {"{bad", "", "null", "[]", "{\"warnings\":\"text\",\"diagnostics\":{\"classificationWarnings\":{\"x\":1}}}",
            "{\"warnings\":null,\"diagnostics\":null}", "{\"warnings\":3,\"diagnostics\":{\"classificationWarnings\":true}}"};
        for (int i = 0; i < ignored.length; i++) plan(10 + i, 1, 2, 1001, 4001, null, ignored[i]);
        plan(20, 1, 2, 1001, 4001, null, null);
        plan(21, 9, 2, 1001, 4001, null, "{\"warnings\":[1]}");
        plan(22, 1, 9, 1001, 4001, null, "{\"warnings\":[1]}");
        plan(23, 1, 2, 1002, 4001, null, "{\"warnings\":[1]}");
        plan(24, 1, 2, 1001, 4003, null, "{\"warnings\":[1]}");
        plan(25, 1, 2, 1001, 9999, null, "{\"warnings\":[1]}");
        plan(26, 1, 2, 1001, 4001, "2026-01-01 00:00:00", "{\"warnings\":[1]}");
        StoryboardBatchResponse result = service.get(1L, 2L, 90L);
        assertThat(result.items()).extracting(StoryboardBatchItemResponse::id).containsExactly(1L, 2L);
        assertThat(result.items()).extracting(StoryboardBatchItemResponse::warningCount).containsExactly(7, 0);
        assertThat(result.warning()).isEqualTo(1);
        assertThat(result.succeeded()).isEqualTo(1);
    }

    @Test
    void preservesFailureNonterminalSettlementNullCountersAndErrors() {
        String[] statuses = {"SUCCEEDED", "FAILED", "CANCELED", "TIMED_OUT", "RUNNING", "QUEUED"};
        for (int i = 0; i < statuses.length; i++) {
            item(i + 1, 90, 1, 2, 1001 + i, i + 1, 2001 + i, statuses[i]);
            run(3001 + i, 2001 + i, 4001 + i);
            plan(i + 1, 1, 2, 1001 + i, 4001 + i, null, "{\"warnings\":[1]}");
        }
        jdbc.update("update ai_execution_task set business_call_count=3,technical_retry_count=2,settled_points=1.25,error_code='ERR',error_message='failure' where id=2002");
        StoryboardBatchResponse result = service.latest(1L, 2L);
        assertThat(result.status()).isEqualTo("RUNNING");
        assertThat(result.pending()).isEqualTo(1);
        assertThat(result.running()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(3);
        assertThat(result.warning()).isEqualTo(1);
        assertThat(result.businessCallCount()).isEqualTo(3);
        assertThat(result.technicalRetryCount()).isEqualTo(2);
        assertThat(result.settledPoints()).isEqualByComparingTo("1.25");
        assertThat(result.items()).extracting(StoryboardBatchItemResponse::warningCount).containsExactly(1, 0, 0, 0, 0, 0);
        assertThat(result.items().get(0).settledPoints()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.items().get(0).businessCallCount()).isZero();
        assertThat(result.items().get(1).errorCode()).isEqualTo("ERR");
        assertThat(result.items().get(1).errorMessage()).isEqualTo("failure");
    }

    @Test
    void missingExecutionKeepsExactExceptionInsteadOfSilentlyDroppingItem() {
        item(1, 90, 1, 2, 1001, 1, 2001, "SUCCEEDED");
        jdbc.update("delete from ai_execution_task where id=2001");
        assertThatThrownBy(() -> service.get(1L, 2L, 90L)).isInstanceOf(IllegalStateException.class)
            .hasMessage("Storyboard batch execution is missing: 2001");
    }

    @Test
    void emptyBatchAndAbsentLatestKeepOriginalResponse() {
        StoryboardBatchResponse result = service.latest(1L, 2L);
        assertThat(result.total()).isZero();
        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.settledPoints()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(service.latest(8L, 2L)).isNull();
    }

    private void createEntityTable(String name, Class<?> entity) {
        List<String> columns = new ArrayList<>();
        for (var field : entity.getFields()) {
            String column = field.getName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
            Class<?> type = field.getType();
            String sqlType = type == Long.class ? "bigint" : type == Integer.class ? "int"
                : type == BigDecimal.class ? "decimal(24,8)" : type == Boolean.class ? "boolean"
                : type == java.time.LocalDateTime.class ? "timestamp" : "varchar(10000)";
            columns.add(column + " " + sqlType + (column.equals("id") ? " primary key" : ""));
        }
        jdbc.execute("create table " + name + " (" + String.join(",", columns) + ")");
    }

    private void batch(long id, long tenant, long project) {
        jdbc.update("insert into storyboard_batch (id,tenant_id,project_id,name,created_at) values (?,?,?,'Batch',now())", id, tenant, project);
    }

    private void item(long id, long batch, long tenant, long project, long episode, int no, long execution, String status) {
        jdbc.update("insert into ai_execution_task (id,tenant_id,project_id,status) values (?,?,?,?)", execution, tenant, project, status);
        jdbc.update("insert into storyboard_batch_item (id,batch_id,tenant_id,project_id,episode_id,episode_no,execution_id) values (?,?,?,?,?,?,?)", id,batch,tenant,project,episode,no,execution);
    }

    private void run(long call, long execution, long run) {
        jdbc.update("insert into ai_call_log values (?,?)", call, execution);
        jdbc.update("insert into ai_workflow_agent_run_step values (?,?,?)", call, run, call);
    }

    private void plan(long id, long tenant, long project, long episode, long run, String deleted, String json) {
        jdbc.update("insert into storyboard values (?,?,?,?,?,?,?)", id,tenant,project,episode,run,deleted,json);
    }
}
