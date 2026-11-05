package com.antshorttv.operations.telemetry;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.antshorttv.operations.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.*;
import java.sql.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;

class OperationsReconciliationBudgetTest {
    @Test void reconcilesWithinProductionTransactionBudgetWithDatabaseRoundTripLatency() {
        var jdbc=OperationsTestDatabase.create();
        jdbc.execute("create table ai_execution_task(id bigint primary key,execution_version int,tenant_id bigint,project_id bigint,resolved_model_id bigint,requested_model_id bigint,scene varchar(64),task_type varchar(32),status varchar(32),phase varchar(64),created_at datetime(3),updated_at datetime(3),started_at datetime(3),completed_at datetime(3),next_run_at datetime(3),heartbeat_at datetime(3),claim_expires_at datetime(3),point_settlement_status varchar(32),error_code varchar(64),error_message varchar(1000),trace_id varchar(128))");
        jdbc.execute("create table ai_call_log(id bigint primary key,created_at datetime(3),tenant_id bigint,provider_id bigint,model_id bigint,business_scene varchar(64),service_type varchar(32),trace_id varchar(128),execution_id bigint,attempt_id bigint,transport_outcome varchar(32),business_outcome varchar(32),status varchar(32),duration_ms bigint,prompt_tokens bigint,completion_tokens bigint,error_message varchar(1000))");
        Instant now=Instant.now();var at=LocalDateTime.ofInstant(now.minusSeconds(60),ZoneId.systemDefault());
        for(int id=1;id<=40;id++)jdbc.update("insert into ai_call_log(id,created_at,transport_outcome,business_outcome,status,duration_ms) values(?,?,'SUCCEEDED','SUCCEEDED','SUCCESS',10)",id,at);
        var delayed=new DelegatingDataSource(jdbc.getDataSource()) {
            @Override public Connection getConnection() throws SQLException {
                return (Connection)wrap(super.getConnection(),Connection.class);
            }
        };
        var database=new OperationsDatabase(new JdbcTemplate(delayed));database.transactions().setTimeout(1);
        var settings=mock(OperationsSettingsService.class);when(settings.instanceId()).thenReturn("budget-test");
        var telemetry=new OperationsTelemetry(database,settings,new ObjectMapper());
        var projections=new OperationsProjections(database,telemetry,new ObjectMapper());
        assertThatCode(()->projections.reconcile(now)).doesNotThrowAnyException();
        assertThat(jdbc.queryForObject("select count(*) from ops_ai_call_projection",Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("select value_json from ops_runtime_setting where setting_key='telemetry.calls.recent'",String.class)).contains("\"id\":10");
    }
    private static Object wrap(Object target,Class<?> type) {
        return Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,args)->{
            try {
                if(method.getName().startsWith("execute"))Thread.sleep(12);
                Object result=method.invoke(target,args);
                if(result instanceof PreparedStatement)return wrap(result,PreparedStatement.class);
                if(result instanceof Statement)return wrap(result,Statement.class);
                return result;
            } catch(InvocationTargetException error) {throw error.getCause();}
        });
    }
}
