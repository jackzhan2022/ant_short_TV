package com.antshorttv.video;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.antshorttv.avatar.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties="ark.avatar.worker-enabled=false")
@Transactional
class SeedanceAvatarRecoveryServiceTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    SeedanceAvatarRecoveryService service;
    AiVideoTaskMapper videos;
    ArkAssetPool pool;
    AiVideoTaskService videoService;
    AiVideoTaskEntity source;
    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        videos=mock(AiVideoTaskMapper.class);pool=mock(ArkAssetPool.class);videoService=mock(AiVideoTaskService.class);
        ObjectProvider<AiVideoTaskService> provider=mock(ObjectProvider.class);when(provider.getObject()).thenReturn(videoService);
        service=new SeedanceAvatarRecoveryService(jdbc,videos,pool,json,provider,true);
        source=new AiVideoTaskEntity();source.id=98761L;source.tenantId=98762L;source.projectId=98763L;
        source.storyboardId=98764L;source.createdBy=98765L;source.providerCode="VOLCENGINE_ARK";source.status="FAILED";
        source.errorMessage="InputImageSensitiveContentDetected: image may contain realperson";
        source.requestSnapshotJson="{\"compiledPrompt\":\"original prompt\",\"references\":[{\"reference\":{\"mediaType\":\"IMAGE\"},\"providerUrl\":\"https://example.com/original.png\"}]}";
        when(videos.selectById(source.id)).thenReturn(source);when(pool.configured()).thenReturn(true);
    }
    private long enqueue() {service.enqueue(source);return jdbc.queryForObject("select id from seedance_avatar_recovery where source_task_id=?",Long.class,source.id);}
    @Test void deduplicatesAndNeverAutomaticallyRecoversAChildOrGenericModerationError() {
        long id=enqueue();service.enqueue(source);
        assertThat(jdbc.queryForObject("select count(*) from seedance_avatar_recovery where source_task_id=?",Long.class,source.id)).isEqualTo(1);
        assertThat(service.get(source.tenantId,source.projectId+1,source.id)).isNull();
        source.id=98771L;source.avatarRecoverySourceTaskId=98761L;service.enqueue(source);
        source.id=98772L;source.avatarRecoverySourceTaskId=null;source.errorMessage="InputImageSensitiveContentDetected: prohibited content";service.enqueue(source);
        assertThat(jdbc.queryForObject("select count(*) from seedance_avatar_recovery where tenant_id=?",Long.class,source.tenantId)).isEqualTo(1);
    }
    @Test void retainsFrozenInputsAndWaitsForActiveBeforeCreatingTheChild() throws Exception {
        long id=enqueue();source.requestSnapshotJson="{\"references\":[]}";
        when(pool.prepareImage(anyLong(),anyLong(),anyLong(),any(),any(),any(),any()))
            .thenReturn(new ArkAssetPool.PreparedAsset(null,"asset-1","Processing"))
            .thenReturn(new ArkAssetPool.PreparedAsset(null,"asset-1","Active"));
        when(videoService.createAvatarRecoveryTask(eq(id),anyString(),any())).thenAnswer(i -> {
            assertThat(i.getArgument(2).toString()).contains("original prompt","original.png","asset-1");
            jdbc.update("update seedance_avatar_recovery set status='SUCCEEDED',retry_task_id=99 where id=?",id);return 99L;
        });
        service.process(id);verifyNoInteractions(videoService);
        service.process(id);verifyNoInteractions(videoService);
        service.process(id);service.process(id);
        verify(videoService,times(1)).createAvatarRecoveryTask(eq(id),anyString(),any());
        verify(pool,times(2)).prepareImage(eq(id),eq(source.tenantId),eq(source.projectId),any(),any(),eq("https://example.com/original.png"),any());
    }
    @Test void transientQueriesKeepTheSameDurableWorkflowWithoutCreatingAnotherVideo() throws Exception {
        long id=enqueue();
        when(pool.prepareImage(anyLong(),anyLong(),anyLong(),any(),any(),any(),any())).thenThrow(new java.io.IOException("temporary query failure"));
        service.process(id);service.process(id);
        assertThat(jdbc.queryForObject("select status from seedance_avatar_recovery where id=?",String.class,id)).isEqualTo("PENDING");
        verifyNoInteractions(videoService);
        assertThat(jdbc.queryForObject("select count(*) from seedance_avatar_recovery where source_task_id=?",Long.class,source.id)).isEqualTo(1);
    }
    @Test void processingFailureIsVisibleAndDoesNotSubmitVideo() throws Exception {
        long id=enqueue();
        when(pool.prepareImage(anyLong(),anyLong(),anyLong(),any(),any(),any(),any())).thenThrow(new IllegalStateException("参考图片素材处理失败"));
        service.process(id);service.process(id);
        assertThat(service.get(source.tenantId,source.projectId,source.id)).containsEntry("status","FAILED").containsEntry("errorMessage","参考图片素材处理失败");
        verifyNoInteractions(videoService);
    }
    @Test void keepsScenePropAndAudioReferencesWithoutUploadingNonPortraitInputs() throws Exception {
        source.requestSnapshotJson="{\"references\":[{\"reference\":{\"mediaType\":\"IMAGE\",\"assetType\":\"CHARACTER\"},\"providerUrl\":\"https://example.com/character.png\"},{\"reference\":{\"mediaType\":\"IMAGE\",\"assetType\":\"SCENE\"},\"providerUrl\":\"https://example.com/scene.png\"},{\"reference\":{\"mediaType\":\"IMAGE\",\"assetType\":\"PROP\"},\"providerUrl\":\"https://example.com/prop.png\"},{\"reference\":{\"mediaType\":\"AUDIO\"},\"providerUrl\":\"https://example.com/voice.mp3\"}]}";
        long id=enqueue();
        when(pool.prepareImage(anyLong(),anyLong(),anyLong(),any(),any(),any(),any())).thenReturn(new ArkAssetPool.PreparedAsset(null,"asset-person","Active"));
        when(videoService.createAvatarRecoveryTask(eq(id),anyString(),any())).thenAnswer(i -> {
            com.fasterxml.jackson.databind.JsonNode snapshot=i.getArgument(2);
            assertThat(snapshot.path("references").size()).isEqualTo(4);
            assertThat(snapshot.path("references").get(1).path("providerAssetId").asText("")).isEmpty();
            assertThat(snapshot.path("references").get(2).path("providerUrl").asText()).isEqualTo("https://example.com/prop.png");
            assertThat(snapshot.path("references").get(3).path("providerUrl").asText()).isEqualTo("https://example.com/voice.mp3");
            jdbc.update("update seedance_avatar_recovery set status='SUCCEEDED',retry_task_id=99 where id=?",id);return 99L;
        });
        service.process(id);service.process(id);
        verify(pool,times(1)).prepareImage(eq(id),anyLong(),anyLong(),any(),any(),eq("https://example.com/character.png"),any());
        verify(videoService,times(1)).createAvatarRecoveryTask(eq(id),anyString(),any());
    }
    @Test void usesProviderContentPositionsToTargetTheRejectedImageOnly() throws Exception {
        source.errorMessage="InputImageSensitiveContentDetected: content[2] may contain real person";
        source.requestSnapshotJson="{\"references\":[{\"reference\":{\"mediaType\":\"IMAGE\",\"assetType\":\"CHARACTER\"},\"providerUrl\":\"https://example.com/accepted.png\"},{\"reference\":{\"mediaType\":\"IMAGE\",\"sourceType\":\"IMAGE_MATERIAL\"},\"providerUrl\":\"https://example.com/rejected.png\"}]}";
        long id=enqueue();
        when(pool.prepareImage(anyLong(),anyLong(),anyLong(),any(),any(),any(),any())).thenReturn(new ArkAssetPool.PreparedAsset(null,"asset-person","Active"));
        service.process(id);service.process(id);
        verify(pool,times(1)).prepareImage(eq(id),anyLong(),anyLong(),any(),any(),eq("https://example.com/rejected.png"),any());
        verify(videoService,times(1)).createAvatarRecoveryTask(eq(id),anyString(),any());
    }

    @Test @SuppressWarnings("unchecked") void disablingNewRecoveriesStillDrainsAlreadyQueuedWork() throws Exception {
        long id=enqueue();
        ObjectProvider<AiVideoTaskService> provider=mock(ObjectProvider.class);when(provider.getObject()).thenReturn(videoService);
        var disabled=new SeedanceAvatarRecoveryService(jdbc,videos,pool,json,provider,false);
        when(pool.prepareImage(anyLong(),anyLong(),anyLong(),any(),any(),any(),any())).thenReturn(new ArkAssetPool.PreparedAsset(null,"asset-drain","Active"));
        disabled.pollDue();
        verify(pool,times(1)).prepareImage(eq(id),anyLong(),anyLong(),any(),any(),any(),any());
        assertThat(service.get(source.tenantId,source.projectId,source.id)).containsEntry("status","RUNNING");
        source.id=98780L;disabled.enqueue(source);
        assertThat(jdbc.queryForObject("select count(*) from seedance_avatar_recovery where source_task_id=98780",Long.class)).isZero();
    }

}
