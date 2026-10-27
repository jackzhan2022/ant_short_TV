package com.antshorttv.freecreation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.antshorttv.avatar.*;
import com.antshorttv.execution.*;
import com.antshorttv.rbac.RbacPermissionService;
import com.antshorttv.security.TenantContext;
import com.antshorttv.video.*;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.core.context.SecurityContextHolder;

class FreeCreationAvatarRecoveryServiceTest {
    JdbcTemplate jdbc;
    ObjectMapper json=new ObjectMapper();
    FreeCreationMapper creations=mock(FreeCreationMapper.class);
    AiExecutionService executions=mock(AiExecutionService.class);
    FreeCreationService free=mock(FreeCreationService.class);
    RbacPermissionService permissions=mock(RbacPermissionService.class);
    ArkAssetPool pool=mock(ArkAssetPool.class);
    FreeCreationAvatarRecoveryService recovery;
    SeedanceAvatarRecoveryService worker;
    AiVideoTaskMapper videos=mock(AiVideoTaskMapper.class);
    AiVideoTaskService videoService=mock(AiVideoTaskService.class);
    FreeCreationEntity source;
    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        var data=new org.h2.jdbcx.JdbcDataSource();data.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        jdbc=new JdbcTemplate(data);
        jdbc.execute("create table free_creation(id bigint primary key,tenant_id bigint,execution_id bigint,provider_started_at timestamp,provider_result_json clob)");
        jdbc.execute("create table ai_execution_task(id bigint primary key,status varchar(32))");
        jdbc.execute("create table ai_video_task(id bigint primary key,deleted_at timestamp,status varchar(32),external_status varchar(32),request_snapshot_json clob)");
        jdbc.execute("create table virtual_avatar_task(id bigint primary key,provider_project varchar(255))");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V164__seedance_avatar_recovery_and_capacity.sql"),
            new ClassPathResource("db/migration/V166__free_creation_avatar_recovery.sql")).execute(data);
        ObjectProvider<FreeCreationService> freeProvider=mock(ObjectProvider.class);when(freeProvider.getObject()).thenReturn(free);
        recovery=new FreeCreationAvatarRecoveryService(jdbc,creations,executions,freeProvider,permissions,true);
        ObjectProvider<AiVideoTaskService> videoProvider=mock(ObjectProvider.class);when(videoProvider.getObject()).thenReturn(videoService);
        worker=new SeedanceAvatarRecoveryService(jdbc,videos,pool,json,videoProvider,true);
        ObjectProvider<FreeCreationAvatarRecoveryService> recoveryProvider=mock(ObjectProvider.class);when(recoveryProvider.getObject()).thenReturn(recovery);
        worker.setFreeCreationRecovery(recoveryProvider);
        source=new FreeCreationEntity();source.id=11L;source.tenantId=12L;source.creatorId=13L;source.executionId=14L;
        source.mediaType="VIDEO";source.providerCode="VOLCENGINE_ARK";
        source.videoRequestSnapshotJson="{\"references\":[{\"reference\":{\"mediaType\":\"IMAGE\"},\"objectStoragePath\":\"original.png\",\"providerUrl\":\"https://example.com/original.png\"}]}";
        when(creations.selectById(source.id)).thenReturn(source);
        var execution=new AiExecutionTaskEntity();execution.status="FAILED";when(executions.requireTask(14L)).thenReturn(execution);
        when(pool.configured()).thenReturn(true);
        when(free.authorize(12L)).thenReturn(new TenantContext(13L,12L,15L,"MEMBER"));
        when(permissions.hasPermission(any(),eq("MENU:FREE_CREATION"))).thenReturn(true);
    }
    @AfterEach void close() {SecurityContextHolder.clearContext();jdbc.execute("shutdown");}
    long enqueue() {
        recovery.enqueue(source,"InputImageSensitiveContentDetected: content[1] image may contain real person");
        return jdbc.queryForObject("select id from seedance_avatar_recovery where source_creation_id=11",Long.class);
    }
    @Test void admissionIsIdempotentAndOnlyExplicitArkImageRejectionsQualify() {
        long id=enqueue();enqueue();
        assertThat(jdbc.queryForObject("select count(*) from seedance_avatar_recovery",Integer.class)).isEqualTo(1);
        assertThat(recovery.get(99L,11L)).isNull();
        assertThat(jdbc.queryForObject("select prepared_snapshot_json from seedance_avatar_recovery where id=?",String.class,id)).contains("original.png");
        source.id=21L;source.avatarRecoverySourceCreationId=11L;recovery.enqueue(source,"image may contain realperson");
        source.avatarRecoverySourceCreationId=null;recovery.enqueue(source,"input image rejected");
        recovery.enqueue(source,"InputVideoSensitiveContentDetected: input video may contain realperson");
        recovery.enqueue(source,"OutputVideoSensitiveContentDetected: output may contain realperson");
        source.providerCode="OTHER";recovery.enqueue(source,"image may contain realperson");
        assertThat(jdbc.queryForObject("select count(*) from seedance_avatar_recovery",Integer.class)).isEqualTo(1);
    }
    @Test void sharedWorkerFreezesInputsWaitsForActiveAndCreatesExactlyOneLinkedRetry() throws Exception {
        long id=enqueue();source.videoRequestSnapshotJson="{\"references\":[]}";
        when(pool.prepareFreeCreationImage(eq(id),eq(12L),any(),any(),any(),any()))
            .thenReturn(new ArkAssetPool.PreparedAsset(1L,"asset-1","Processing"))
            .thenReturn(new ArkAssetPool.PreparedAsset(1L,"asset-1","Active"));
        when(free.createAvatarRecovery(eq(12L),eq(11L),anyString(),any())).thenAnswer(call->{
            assertThat(call.getArgument(3).toString()).contains("original.png","asset-1");return 31L;
        });
        worker.process(id);worker.process(id);verifyNoInteractions(free);
        worker.process(id);worker.process(id);
        verify(free,times(1)).createAvatarRecovery(eq(12L),eq(11L),anyString(),any());
        assertThat(recovery.get(12L,11L)).containsEntry("status","SUCCEEDED").containsEntry("retryCreationId",31L);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
    @Test void lackOfPermissionsStopsTheAutomaticRetryWithoutCallingTheProvider() throws Exception {
        long id=enqueue();
        when(pool.prepareFreeCreationImage(anyLong(),anyLong(),any(),any(),any(),any())).thenReturn(new ArkAssetPool.PreparedAsset(1L,"asset-1","Active"));
        when(permissions.hasPermission(any(),anyString())).thenReturn(false);
        worker.process(id);worker.process(id);
        assertThat(recovery.get(12L,11L)).containsEntry("status","FAILED");
        verify(free,never()).createAvatarRecovery(anyLong(),anyLong(),anyString(),any());
    }
    @Test void manualRetryCannotDuplicatePendingOrAlreadyCreatedAutomaticRecovery() {
        long id=enqueue();
        assertThatThrownBy(()->recovery.retryTarget(12L,11L)).hasMessageContaining("自动");
        jdbc.update("update seedance_avatar_recovery set status='SUCCEEDED',retry_creation_id=31 where id=?",id);
        assertThat(recovery.retryTarget(12L,11L)).isEqualTo(31L);
    }
    @Test void retryCreationStoresPreparedSnapshotAndReservesANewExecutionWithoutChangingTheFailure() throws Exception {
        jdbc.execute("create table tenant(id bigint primary key)");jdbc.update("insert into tenant values(12)");
        jdbc.execute("create table app_user(id bigint primary key,nickname varchar(80))");jdbc.update("insert into app_user values(13,'author')");
        source.modelId=4L;source.prompt="海面";source.parametersJson="{\"durationSeconds\":5,\"resolution\":\"720p\",\"aspectRatio\":\"9:16\"}";
        var tenants=mock(com.antshorttv.security.TenantContextResolver.class);var context=new TenantContext(13L,12L,15L,"MEMBER");when(tenants.requireActiveMember(12L)).thenReturn(context);
        var router=mock(com.antshorttv.ai.AiModelRouter.class);var model=new com.antshorttv.ai.AiModelEntity();model.setId(4L);model.setModelCode("seedance");model.setConfigJson("{}");
        var provider=new com.antshorttv.ai.AiProviderEntity();provider.setCode("VOLCENGINE_ARK");
        when(router.route(4L,"VIDEO")).thenReturn(new com.antshorttv.ai.AiModelRoute(model,provider,new com.antshorttv.ai.AiProviderConfigEntity(),mock(com.antshorttv.ai.AiProviderAdapter.class)));
        var media=mock(FreeCreationMediaService.class);var image=new FreeCreationMediaEntity();image.id=21L;image.kind="IMAGE";image.fileName="original.png";image.objectKey="original.png";
        when(media.items(11L,"REFERENCE")).thenReturn(List.of(image));when(media.reference(eq(context),any())).thenReturn(image);
        when(videoService.videoAuthorizedUsage(any(),eq(5))).thenReturn(Map.of(com.antshorttv.accounting.AiUsageMetric.OUTPUT_TOKEN,java.math.BigDecimal.TEN));
        when(videoService.videoDimensions(any())).thenReturn(Map.of("resolution","720p"));
        var saved=new java.util.concurrent.atomic.AtomicReference<FreeCreationEntity>();
        doAnswer(call->{var row=call.getArgument(0,FreeCreationEntity.class);row.id=31L;saved.set(row);return 1;}).when(creations).insert(any(FreeCreationEntity.class));
        var execution=new AiExecutionTaskEntity();execution.id=41L;execution.status="PENDING";when(executions.requireTask(41L)).thenReturn(execution);
        when(executions.createWithReservation(any(),anyMap(),anyMap())).thenAnswer(call->{
            var command=call.getArgument(0,AiExecutionCreateCommand.class);assertThat(command.projectId()).isNull();assertThat(command.scene()).isEqualTo("free_creation_video");return execution;
        });
        var urls=mock(ModelAccessibleVideoUrlResolver.class);when(urls.refreshSnapshotUrls(any())).thenAnswer(call->call.getArgument(0));
        try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var actual=new FreeCreationService(creations,mock(FreeCreationMediaMapper.class),media,tenants,router,executions,videoService,
                mock(SeedanceVideoRequestValidator.class),mock(com.antshorttv.storage.ObjectStorageService.class),json,jdbc,factory.getValidator());
            actual.setAvatarRecovery(recovery,pool,urls);
            var snapshot=json.readTree("{\"references\":[{\"reference\":{\"mediaType\":\"IMAGE\"},\"objectStoragePath\":\"original.png\",\"providerAssetId\":\"asset-1\"}]}");
            assertThat(actual.createAvatarRecovery(12L,11L,"avatar-recovery-1",snapshot)).isEqualTo(31L);
            assertThat(saved.get().avatarRecoverySourceCreationId).isEqualTo(11L);
            assertThat(saved.get().executionId).isEqualTo(41L);
            assertThat(actual.videoTask(saved.get(),true).requestSnapshotJson).contains("asset-1","original.png");
            verify(pool).pinFreeCreation(31L,snapshot);assertThat(executions.requireTask(14L).status).isEqualTo("FAILED");
        }
    }

    @Test void existingStoryboardRecoveryStillUsesItsProjectAndVideoCreationPath() throws Exception {
        var video=new AiVideoTaskEntity();video.id=91L;video.tenantId=12L;video.projectId=92L;video.storyboardId=93L;video.createdBy=13L;
        video.providerCode="VOLCENGINE_ARK";video.status="FAILED";video.errorMessage="image may contain realperson";
        video.requestSnapshotJson=source.videoRequestSnapshotJson;when(videos.selectById(91L)).thenReturn(video);
        when(pool.prepareImage(anyLong(),eq(12L),eq(92L),any(),any(),any(),any())).thenReturn(new ArkAssetPool.PreparedAsset(1L,"asset-video","Active"));
        worker.enqueue(video);long id=jdbc.queryForObject("select id from seedance_avatar_recovery where source_task_id=91",Long.class);
        when(videoService.createAvatarRecoveryTask(eq(id),anyString(),any())).thenAnswer(call->{jdbc.update("update seedance_avatar_recovery set status='SUCCEEDED',retry_task_id=94 where id=?",id);return 94L;});
        worker.process(id);worker.process(id);
        verify(videoService).createAvatarRecoveryTask(eq(id),anyString(),any());verifyNoInteractions(free);
    }

    @Test void activeAndUncertainFreeCreationConsumersCannotBeEvictedAtCapacity() throws Exception {
        var client=mock(ArkAssetClient.class);when(client.project()).thenReturn("default");
        var assets=new ArrayList<JsonNode>();for(int i=0;i<50;i++)assets.add(json.createObjectNode().put("Id","asset-"+i).put("GroupId","group-1").put("Name","asset-"+i));
        when(client.listAssets("default")).thenReturn(assets);when(client.listGroups("default")).thenReturn(List.of());
        var actual=new ArkAssetPool(jdbc,new DataSourceTransactionManager(jdbc.getDataSource()),client,json,mock(VirtualAvatarStorage.class),"free-test","default",true);
        jdbc.update("insert into ark_managed_resource(scope_key,provider_project,resource_type,owner_key,provider_id,provider_name,status) values('free-test','default','ASSET','asset-0','asset-0','asset-0','ACTIVE')");
        jdbc.update("insert into free_creation(id,tenant_id,execution_id) values(11,12,14)");jdbc.update("insert into ai_execution_task(id,status) values(14,'RUNNING')");
        actual.pinFreeCreation(11,json.readTree("{\"references\":[{\"providerAssetId\":\"asset-0\"}]}"));
        actual.pinFreeCreation(11,json.readTree("{\"references\":[{\"providerAssetId\":\"asset-0\"}]}"));
        assertThat(jdbc.queryForObject("select count(*) from ark_asset_consumer where free_creation_id=11",Integer.class)).isEqualTo(1);
        jdbc.update("update ark_managed_resource set created_at=?,last_used_at=? where scope_key='free-test'",LocalDateTime.now().minusDays(2),LocalDateTime.now().minusDays(1));
        assertThatThrownBy(()->actual.reserveRecovery(99,1)).isInstanceOf(ArkAssetPool.Waiting.class);
        jdbc.update("update free_creation set provider_started_at=now() where id=11");jdbc.update("update ai_execution_task set status='FAILED' where id=14");
        assertThatThrownBy(()->actual.reserveRecovery(99,1)).isInstanceOf(ArkAssetPool.Waiting.class);
        verify(client,never()).deleteAsset(anyString(),anyString());
    }

    @Test void temporaryAssetFailuresStayQueuedWithoutSubmittingAnotherVideo() throws Exception {
        long id=enqueue();when(pool.prepareFreeCreationImage(anyLong(),anyLong(),any(),any(),any(),any())).thenThrow(new java.io.IOException("temporary"));
        worker.process(id);worker.process(id);
        assertThat(recovery.get(12L,11L)).containsEntry("status","PENDING");verifyNoInteractions(free);
    }
}
