package com.antshorttv.freecreation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.antshorttv.execution.*;
import com.antshorttv.security.*;
import com.antshorttv.video.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class FreeCreationLogicalStateTest {
    JdbcTemplate jdbc;
    FreeCreationMapper creations=mock(FreeCreationMapper.class);
    FreeCreationMediaMapper mediaMapper=mock(FreeCreationMediaMapper.class);
    FreeCreationMediaService media=mock(FreeCreationMediaService.class);
    AiExecutionService executions=mock(AiExecutionService.class);
    Map<Long,AiExecutionTaskEntity> tasks=new HashMap<>();
    Map<Long,FreeCreationEntity> records=new HashMap<>();
    FreeCreationService service;
    FreeCreationEntity root,child;
    TenantContext context=new TenantContext(2L,1L,3L,"OWNER");
    FreeCreationMediaEntity output;
    @BeforeEach void setup() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("create table free_creation(id bigint primary key,tenant_id bigint,creator_id bigint,execution_id bigint,media_type varchar(16),prompt varchar(200),provider_started_at timestamp,created_at timestamp,avatar_recovery_source_creation_id bigint)");
        jdbc.execute("create table ai_execution_task(id bigint primary key,tenant_id bigint,status varchar(32))");
        jdbc.execute("create table app_user(id bigint primary key,nickname varchar(80))");jdbc.update("insert into app_user values(2,'author')");
        jdbc.execute("create table seedance_avatar_recovery(id bigint primary key,source_creation_id bigint,tenant_id bigint,status varchar(32),phase varchar(128),retry_creation_id bigint,error_message varchar(500))");
        var tenants=mock(TenantContextResolver.class);when(tenants.requireActiveMember(1L)).thenReturn(context);
        when(executions.requireTask(anyLong())).thenAnswer(call->tasks.get(call.getArgument(0)));
        when(creations.selectById(anyLong())).thenAnswer(call->records.get(call.getArgument(0)));
        service=new FreeCreationService(creations,mediaMapper,media,tenants,mock(com.antshorttv.ai.AiModelRouter.class),executions,
            mock(AiVideoTaskService.class),mock(SeedanceVideoRequestValidator.class),mock(com.antshorttv.storage.ObjectStorageService.class),new ObjectMapper(),jdbc,mock(jakarta.validation.Validator.class));
        service.setAvatarRecovery(new FreeCreationAvatarRecoveryService(jdbc,creations,executions,null,null,true),mock(com.antshorttv.avatar.ArkAssetPool.class),mock(ModelAccessibleVideoUrlResolver.class));
        root=record(3,10,null);child=record(4,11,3L);task(10,"FAILED","QUERY");task(11,"PENDING","SUBMIT");
        jdbc.update("insert into seedance_avatar_recovery values(1,3,1,'PENDING','等待素材处理完成',null,null)");
        output=new FreeCreationMediaEntity();output.id=30L;output.tenantId=1L;output.creationId=4L;output.role="OUTPUT";output.kind="VIDEO";output.fileName="video.mp4";
        when(media.items(4L,"OUTPUT")).thenReturn(List.of(output));when(media.present(context,output)).thenReturn(Map.of("id",30L,"previewUrl","/result.mp4"));
        when(mediaMapper.selectById(30L)).thenReturn(output);when(media.grant(context,output,false)).thenReturn("/download.mp4");
    }
    @AfterEach void close() {jdbc.execute("shutdown");}
    FreeCreationEntity record(long id,long execution,Long parent) {
        var row=new FreeCreationEntity();row.id=id;row.tenantId=1L;row.creatorId=2L;row.executionId=execution;row.mediaType="VIDEO";
        row.avatarRecoverySourceCreationId=parent;row.parametersJson="{}";row.prompt="视频";row.createdAt=LocalDateTime.now();records.put(id,row);
        jdbc.update("insert into free_creation values(?,1,2,?,'VIDEO','视频',null,?,?)",id,execution,row.createdAt,parent);return row;
    }
    void task(long id,String status,String phase) {
        var task=new AiExecutionTaskEntity();task.id=id;task.tenantId=1L;task.status=status;task.phase=phase;task.errorMessage="原参考图片拒绝";tasks.put(id,task);
        jdbc.update("merge into ai_execution_task key(id) values(?,1,?)",id,status);
    }
    void recovered(String childStatus) {
        jdbc.update("update seedance_avatar_recovery set status='SUCCEEDED',retry_creation_id=4,phase='已创建关联重试视频' where id=1");task(11,childStatus,"QUERY");
    }
    @Test void preservesTheOriginalCardAcrossPreparationRetryAndCompletion() {
        var preparing=service.present(context,root);
        assertThat(preparing).containsEntry("id",3L).containsEntry("status","RUNNING").containsEntry("phase","AVATAR_PREPARING").containsEntry("errorMessage",null);
        assertThat((List<?>)preparing.get("allowedActions")).isEmpty();assertThat(tasks.get(10L).status).isEqualTo("FAILED");
        recovered("PENDING");var generating=service.present(context,root);
        assertThat(generating).containsEntry("id",3L).containsEntry("status","RUNNING").containsEntry("taskKey","FREE_CREATION:4");
        assertThat(generating.get("allowedActions")).isEqualTo(List.of("CANCEL"));
        recovered("SUCCEEDED");var done=service.present(context,root);
        assertThat(done).containsEntry("id",3L).containsEntry("status","SUCCEEDED").containsEntry("errorMessage",null);
        assertThat((List<?>)done.get("outputs")).hasSize(1);assertThat(tasks.get(10L).status).isEqualTo("FAILED");
    }
    @Test void originalRecordCanDownloadItsLinkedSuccessfulOutput() {
        recovered("SUCCEEDED");assertThat(service.download(1L,3L,30L)).containsEntry("url","/download.mp4");
        output.creationId=99L;assertThatThrownBy(()->service.download(1L,3L,30L)).isInstanceOf(com.antshorttv.common.BusinessException.class);
    }
    @Test void listCountsAndFiltersTheLogicalRootInsteadOfItsAutomaticChild() {
        var preparing=service.list(1L,"mine","VIDEO","RUNNING",null,1,20);
        assertThat(preparing).containsEntry("total",1L);assertThat((List<?>)preparing.get("records")).hasSize(1);
        assertThat(service.list(1L,"mine","VIDEO","FAILED",null,1,20)).containsEntry("total",0L);
        recovered("SUCCEEDED");assertThat(service.list(1L,"mine","VIDEO","SUCCEEDED",null,1,20)).containsEntry("total",1L);
        assertThat(service.list(1L,"mine",null,null,null,1,20)).containsEntry("total",1L);
    }
    @Test void preparationFailureAndRetryFailureExposeTheCurrentFailure() {
        jdbc.update("update seedance_avatar_recovery set status='FAILED',error_message='素材准备超时' where id=1");
        var failed=service.present(context,root);assertThat(failed).containsEntry("status","FAILED");assertThat(failed.get("errorMessage").toString()).contains("素材准备超时");
        recovered("FAILED");tasks.get(11L).errorMessage="关联视频生成失败";
        assertThat(service.present(context,root)).containsEntry("errorMessage","关联视频生成失败").containsEntry("taskKey","FREE_CREATION:4");
    }
    @Test void unrelatedLinkedRecordsNeverSupplyTheirResultsOrDownloads() {
        recovered("SUCCEEDED");child.tenantId=2L;
        assertThat(service.present(context,root)).containsEntry("status","FAILED");
        assertThat((List<?>)service.present(context,root).get("outputs")).isEmpty();
        assertThatThrownBy(()->service.download(1L,3L,30L)).isInstanceOf(com.antshorttv.common.BusinessException.class);
        verify(media,never()).grant(any(),any(),anyBoolean());
    }
}
