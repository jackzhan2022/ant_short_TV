package com.antshorttv.freecreation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.antshorttv.observability.DatabaseQueryObservation;
import com.antshorttv.security.*;
import java.util.*;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@Import(FreeCreationReadPerformanceTest.QueryConfiguration.class)
class FreeCreationReadPerformanceTest {
    private static final long TENANT=892001L, USER=892001L;
    private static final String LARGE_RESULT="{\"images\":[\"data:image/png;base64,"+"A".repeat(2_000_000)+"\"]}";
    @Autowired FreeCreationService service;
    @Autowired FreeCreationMapper creations;
    @Autowired JdbcTemplate jdbc;
    @Autowired QueryCapture queries;
    @MockBean TenantContextResolver tenants;

    @BeforeEach void setup() {
        when(tenants.requireActiveMember(TENANT)).thenReturn(new TenantContext(USER,TENANT,1L,"OWNER"));
        jdbc.update("insert into app_user(id,mobile,password_hash,nickname,status,created_at,updated_at) values(?,'13900892001','unused','作者','ACTIVE',now(),now())",USER);
        jdbc.update("insert into app_user(id,mobile,password_hash,nickname,status,created_at,updated_at) values(?,'13900892002','unused','同事','ACTIVE',now(),now())",USER+1);
        for(int i=1;i<=10;i++) insert(i,TENANT,USER);
        insert(11,TENANT+1,USER);
        insert(12,TENANT,USER+1);
        queries.sql.get().clear();
    }

    private void insert(int index,long tenant,long user) {
        long id=892000L+index;
        jdbc.update("insert into ai_execution_task(id,tenant_id,user_id,scene,capability,business_type,business_id,status,phase,client_idempotency_key,trace_id,created_at,updated_at) values(?,?,?,'free_creation_image','IMAGE','FREE_CREATION',?,'SUCCEEDED','STORE',?, 'test',now(),now())",id,tenant,user,id,"read-test-"+index);
        jdbc.update("insert into free_creation(id,tenant_id,creator_id,execution_id,request_id,request_hash,media_type,model_id,model_name,provider_code,prompt,parameters_json,provider_result_json,video_request_snapshot_json,created_at,updated_at) values(?,?,?,?,?,'hash','IMAGE',1,'model','OpenAI',?,'{}',?,'private-snapshot',now(),now())",id,tenant,user,id,"read-test-"+index,"prompt-"+index,LARGE_RESULT);
    }

    @Test void listAvoidsLargeProviderPayloadsAndPreservesPagingAndScope() {
        var page=service.list(TENANT,"mine",null,null,null,1,10);
        assertThat(page).containsEntry("total",10L);
        var records=(List<Map<String,Object>>)page.get("records");
        assertThat(records).extracting(row->row.get("id")).containsExactly(892010L,892009L,892008L,892007L,892006L,892005L,892004L,892003L,892002L,892001L);
        assertThat(records).allSatisfy(row->assertThat(row).containsEntry("creatorName","作者").containsEntry("status","SUCCEEDED"));
        assertLightReads();
        assertThat(service.list(TENANT,"mine",null,null,null,2,10).get("records")).isEqualTo(List.of());
        assertThat(service.list(TENANT,"team",null,null,"prompt-1",1,10)).containsEntry("total",3L);
        // Execution and retry code must retain access to the full provider result.
        assertThat(creations.selectById(892001L).providerResultJson).isEqualTo(LARGE_RESULT);
    }

    @Test void databaseRoundTripsDoNotGrowPerOrdinaryListRecord() {
        long one=queryCount(1),ten=queryCount(10);
        assertThat(ten).as("SQL round trips for ten records versus one").isLessThanOrEqualTo(one+1);
        assertThat(ten).isLessThanOrEqualTo(10);
    }

    private long queryCount(int size) {
        try(var ignored=DatabaseQueryObservation.open()) {
            service.list(TENANT,"mine",null,null,null,1,size);
            return DatabaseQueryObservation.snapshot()[0];
        }
    }

    @Test void detailPollingAvoidsProviderPayloadAndStillChecksTenant() {
        assertThat(service.detail(TENANT,892001L)).containsEntry("prompt","prompt-1").containsEntry("status","SUCCEEDED");
        assertLightReads();
        assertThatThrownBy(()->service.detail(TENANT,892011L)).isInstanceOf(com.antshorttv.common.BusinessException.class);
    }

    @Test void recoveredVideoListMatchesDetailWithoutLoadingProviderResults() {
        insert(14,TENANT,USER);insert(15,TENANT,USER);
        jdbc.update("update free_creation set media_type='VIDEO',provider_code='VOLCENGINE_ARK' where id in (892014,892015)");
        jdbc.update("update free_creation set avatar_recovery_source_creation_id=892014 where id=892015");
        jdbc.update("update ai_execution_task set status='FAILED' where id=892014");
        jdbc.update("insert into seedance_avatar_recovery(source_creation_id,tenant_id,created_by,status,retry_creation_id,prepared_snapshot_json,deadline_at) values(892014,?,?,'SUCCEEDED',892015,?,dateadd('DAY',1,current_timestamp))",TENANT,USER,LARGE_RESULT);
        jdbc.update("insert into free_creation_media(tenant_id,creation_id,role,position,kind,file_name,object_key,mime_type,format,file_size,created_at) values(?,892015,'OUTPUT',0,'VIDEO','result.mp4','materials/test/result.mp4','video/mp4','mp4',100,now())",TENANT);
        queries.sql.get().clear();
        var detail=service.detail(TENANT,892014L);
        var page=service.list(TENANT,"mine","VIDEO","SUCCEEDED","prompt-14",1,10);
        assertThat(page).containsEntry("total",1L);
        assertThat((List<?>)page.get("records")).singleElement().isEqualTo(detail);
        assertThat(detail).containsEntry("id",892014L).containsEntry("executionId",892015L).containsEntry("taskKey","FREE_CREATION:892015");
        assertThat((List<?>)detail.get("outputs")).hasSize(1);
        assertLightReads();
    }

    private void assertLightReads() {
        assertThat(queries.sql.get()).isNotEmpty().allSatisfy(sql->
            assertThat(sql).doesNotContain("provider_result_json","video_request_snapshot_json"));
    }

    @TestConfiguration static class QueryConfiguration {
        @Bean QueryCapture queryCapture() {return new QueryCapture();}
    }
    @Intercepts(@Signature(type=Executor.class,method="query",args={MappedStatement.class,Object.class,RowBounds.class,ResultHandler.class}))
    static class QueryCapture implements Interceptor {
        final ThreadLocal<List<String>> sql=ThreadLocal.withInitial(ArrayList::new);
        @Override public Object intercept(Invocation invocation) throws Throwable {
            var statement=(MappedStatement)invocation.getArgs()[0];
            sql.get().add(statement.getBoundSql(invocation.getArgs()[1]).getSql().toLowerCase(Locale.ROOT));
            return invocation.proceed();
        }
    }
}
