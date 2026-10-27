package com.antshorttv.avatar;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties="ark.avatar.worker-enabled=false")
@Transactional
class ArkAssetPoolTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired ObjectMapper json;
    ArkAssetClient client;
    ArkAssetPool pool;
    List<JsonNode> assets;
    List<JsonNode> groups;
    @BeforeEach void setup() throws Exception {
        client=mock(ArkAssetClient.class);assets=new ArrayList<>();groups=new ArrayList<>();
        when(client.configured()).thenReturn(true);when(client.project()).thenReturn("default");
        when(client.listAssets(anyString())).thenAnswer(i -> new ArrayList<>(assets));
        when(client.listGroups(anyString())).thenAnswer(i -> new ArrayList<>(groups));
        when(client.createGroup(anyString())).thenReturn("group-new");
        when(client.createAsset(anyString(),anyString(),anyString())).thenReturn("asset-new");
        doAnswer(i -> {assets.removeIf(n -> n.path("Id").asText().equals(i.getArgument(1)));return null;}).when(client).deleteAsset(anyString(),anyString());
        var storage=mock(VirtualAvatarStorage.class);
        when(storage.sourceUrl(any(),anyString())).thenAnswer(i -> i.getArgument(1));
        pool=new ArkAssetPool(jdbc,manager,client,json,storage,"test-account","default,second-project",true);
    }
    private JsonNode asset(String id) {return json.createObjectNode().put("Id",id).put("GroupId","group-1").put("Name",id);}
    private long managed(String id) {
        jdbc.update("insert into ark_managed_resource(scope_key,provider_project,resource_type,owner_key,provider_id,provider_name,status,created_at,last_used_at) values('test-account','default','ASSET',?,?,?,'ACTIVE',?,?)",id,id,id,LocalDateTime.now().minusDays(2),LocalDateTime.now().minusDays(1));
        return jdbc.queryForObject("select id from ark_managed_resource where scope_key='test-account' and provider_id=?",Long.class,id);
    }
    @Test void externalResourcesCountAgainstTheLimitAndAreNeverDeleted() throws Exception {
        for(int i=0;i<25;i++)assets.add(asset("foreign-"+i)); // Both configured projects occupy 25 slots each.
        assertThatThrownBy(() -> pool.createManualAsset(1,"group-1","图","https://example.com/source.png"))
            .isInstanceOf(ArkAssetPool.Waiting.class).hasMessageContaining("容量不足");
        verify(client,never()).createAsset(anyString(),anyString(),anyString());
        verify(client,never()).deleteAsset(anyString(),anyString());
        verify(client,atLeastOnce()).listAssets("second-project");
    }
    @Test void reclaimsOnlyOwnedAssetsAtHighWaterAndInvalidatesMatchingBindings() throws Exception {
        when(client.listAssets("second-project")).thenReturn(List.of());
        for(int i=0;i<45;i++)assets.add(asset("asset-"+i));
        for(int i=0;i<5;i++)managed("asset-"+i);
        jdbc.update("insert into asset_visual_variant(id,tenant_id,project_id,asset_type,asset_id,name,source_type,generation_status,current_image_url,virtual_avatar_asset_id,is_primary,created_by,created_at,updated_at) values(98760,1,1,'CHARACTER',1,'原图','MANUAL','COMPLETED','https://example.com/original.png','asset-0',true,1,now(),now())");
        pool.maintain();
        assertThat(jdbc.queryForObject("select virtual_avatar_asset_id from asset_visual_variant where id=98760",String.class)).isNull();
        assertThat(jdbc.queryForObject("select current_image_url from asset_visual_variant where id=98760",String.class)).isEqualTo("https://example.com/original.png");
        assertThat(assets).hasSize(40);
        assertThat(jdbc.queryForObject("select count(*) from ark_managed_resource where scope_key='test-account' and status='EVICTED'",Long.class)).isEqualTo(5);
        verify(client,times(5)).deleteAsset(eq("default"),anyString());
    }
    @Test void protectsRecoveryConsumersWhenTheAccountIsFull() throws Exception {
        when(client.listAssets("second-project")).thenReturn(List.of());
        for(int i=0;i<50;i++)assets.add(asset("asset-"+i));
        long resource=managed("asset-0");
        jdbc.update("insert into seedance_avatar_recovery(source_task_id,tenant_id,project_id,storyboard_id,created_by,status,deadline_at) values(1,1,1,1,1,'RUNNING',?)",LocalDateTime.now().plusHours(1));
        long recovery=jdbc.queryForObject("select id from seedance_avatar_recovery where source_task_id=1",Long.class);
        jdbc.update("insert into ark_asset_consumer(resource_id,consumer_key,recovery_id) values(?,?,?)",resource,"recovery:"+recovery,recovery);
        pool.maintain();
        verify(client,never()).deleteAsset(anyString(),anyString());
        assertThat(assets).hasSize(50);
    }
    @Test void uncertainDeletionRetainsOccupancyUntilRemoteInventoryConfirmsAbsence() throws Exception {
        when(client.listAssets("second-project")).thenReturn(List.of());
        for(int i=0;i<45;i++)assets.add(asset("asset-"+i));managed("asset-0");
        doThrow(new java.net.http.HttpTimeoutException("delete timeout")).when(client).deleteAsset("default","asset-0");
        pool.maintain();
        assertThat(jdbc.queryForObject("select status from ark_managed_resource where scope_key='test-account' and provider_id='asset-0'",String.class)).isEqualTo("DELETING");
        assertThat(assets).hasSize(45);
        assets.removeIf(n -> n.path("Id").asText().equals("asset-0"));
        pool.maintain();
        assertThat(jdbc.queryForObject("select status from ark_managed_resource where scope_key='test-account' and provider_id='asset-0'",String.class)).isEqualTo("EVICTED");
    }
    @Test void reservesSharedCreateRateAndRetainsUncertainSubmissionSlots() throws Exception {
        when(client.listAssets("second-project")).thenReturn(List.of());
        when(client.createAsset(anyString(),anyString(),anyString())).thenThrow(new java.net.http.HttpTimeoutException("unknown"));
        assertThatThrownBy(() -> pool.createManualAsset(1,"group-1","图","https://example.com/source.png")).isInstanceOf(java.net.http.HttpTimeoutException.class);
        assertThatThrownBy(() -> pool.createManualAsset(1,"group-1","图","https://example.com/source.png")).isInstanceOf(ArkAssetPool.Waiting.class).hasMessageContaining("待确认");
        assertThatThrownBy(() -> pool.createManualAsset(2,"group-1","图","https://example.com/source.png")).isInstanceOf(ArkAssetPool.Waiting.class).hasMessageContaining("排队");
        verify(client,times(1)).createAsset(anyString(),anyString(),anyString());
        assertThat(jdbc.queryForObject("select count(*) from ark_managed_resource where scope_key='test-account' and status='UNKNOWN'",Long.class)).isEqualTo(1);
    }
    @Test void unavailableInventoryNeverAdmitsCreation() throws Exception {
        when(client.listAssets(anyString())).thenThrow(new java.io.IOException("inventory unavailable"));
        assertThatThrownBy(() -> pool.createManualAsset(1,"group-1","图","https://example.com/source.png")).isInstanceOf(java.io.IOException.class);
        verify(client,never()).createAsset(anyString(),anyString(),anyString());
    }
    @Test void aConsumerCannotPinAnAssetAlreadyClaimedForDeletion() throws Exception {
        long resource=managed("asset-deleting");
        jdbc.update("update ark_managed_resource set status='DELETING' where id=?",resource);
        var snapshot=json.readTree("{\"references\":[{\"providerAssetId\":\"asset-deleting\"}]}");
        assertThatThrownBy(() -> pool.pinVideo(10,snapshot)).isInstanceOf(ArkAssetPool.Waiting.class);
        assertThat(jdbc.queryForObject("select count(*) from ark_asset_consumer where resource_id=?",Long.class,resource)).isZero();
    }
    @Test void admittedVideoConsumersPreventCleanupEvenAfterTheOriginalRecoveryCompletes() throws Exception {
        when(client.listAssets("second-project")).thenReturn(List.of());
        for(int i=0;i<45;i++)assets.add(asset("asset-"+i));
        long resource=managed("asset-0");
        jdbc.update("insert into ai_video_task(id,tenant_id,project_id,storyboard_id,provider_code,model,prompt,duration_seconds,aspect_ratio,status,created_by,created_at,updated_at) values(98765,1,1,1,'VOLCENGINE_ARK','seedance','test',5,'9:16','PENDING',1,now(),now())");
        pool.pinVideo(98765,json.readTree("{\"references\":[{\"providerAssetId\":\"asset-0\"}]}"));
        jdbc.update("update ark_managed_resource set created_at=?,last_used_at=? where id=?",LocalDateTime.now().minusDays(2),LocalDateTime.now().minusDays(1),resource);
        pool.maintain();
        verify(client,never()).deleteAsset(anyString(),anyString());
    }

    @Test void reservesAllStoryboardCapacityBeforeUploadingAndSerializesCompetingRecoveries() throws Exception {
        when(client.listAssets("second-project")).thenReturn(List.of());
        for(int i=0;i<44;i++)assets.add(asset("external-"+i));
        jdbc.update("insert into seedance_avatar_recovery(source_task_id,tenant_id,project_id,storyboard_id,created_by,deadline_at) values(901,1,1,1,1,?)",LocalDateTime.now().plusHours(1));
        jdbc.update("insert into seedance_avatar_recovery(source_task_id,tenant_id,project_id,storyboard_id,created_by,deadline_at) values(902,1,1,2,1,?)",LocalDateTime.now().plusHours(1));
        long first=jdbc.queryForObject("select id from seedance_avatar_recovery where source_task_id=901",Long.class);
        long second=jdbc.queryForObject("select id from seedance_avatar_recovery where source_task_id=902",Long.class);
        assertThatThrownBy(() -> pool.reserveRecovery(first,9)).isInstanceOf(ArkAssetPool.Waiting.class).hasMessageContaining("当前分镜");
        assertThat(jdbc.queryForObject("select admitted_recovery_id from ark_asset_scope where scope_key='test-account'",Long.class)).isNull();
        assets.subList(40,44).clear();
        pool.reserveRecovery(first,9);
        assertThatThrownBy(() -> pool.reserveRecovery(second,9)).isInstanceOf(ArkAssetPool.Waiting.class).hasMessageContaining("其他分镜");
        assertThatThrownBy(() -> pool.createManualAsset(3,"group-1","manual","https://example.com/manual.png")).isInstanceOf(ArkAssetPool.Waiting.class);
        verify(client,never()).createAsset(anyString(),anyString(),anyString());
        jdbc.update("update seedance_avatar_recovery set status='SUCCEEDED' where id=?",first);
        pool.releaseRecovery(first);pool.reserveRecovery(second,9);
        assertThat(jdbc.queryForObject("select admitted_recovery_id from ark_asset_scope where scope_key='test-account'",Long.class)).isEqualTo(second);
    }
    @Test void honorsRecentRemoteInferenceEvenWhenTheLocalSourceIsOld() throws Exception {
        when(client.listAssets("second-project")).thenReturn(List.of());
        for(int i=0;i<45;i++)assets.add(asset("asset-"+i));managed("asset-0");
        ((com.fasterxml.jackson.databind.node.ObjectNode)assets.get(0)).put("LastInferenceTime",java.time.OffsetDateTime.now().toString());
        pool.maintain();verify(client,never()).deleteAsset(anyString(),anyString());
    }
    @Test
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentWorkersCannotBothConsumeTheLastSlot() throws Exception {
        var concurrentPool=new ArkAssetPool(jdbc,manager,client,json,mock(VirtualAvatarStorage.class),"concurrent-account","default",true);
        var storage=mock(VirtualAvatarStorage.class);when(storage.sourceUrl(any(),anyString())).thenAnswer(i -> i.getArgument(1));
        concurrentPool=new ArkAssetPool(jdbc,manager,client,json,storage,"concurrent-account","default",true);
        for(int i=0;i<49;i++)assets.add(asset("external-"+i));
        var entered=new java.util.concurrent.CountDownLatch(1);var finish=new java.util.concurrent.CountDownLatch(1);
        when(client.createAsset(anyString(),anyString(),anyString())).thenAnswer(i -> {entered.countDown();finish.await(10,java.util.concurrent.TimeUnit.SECONDS);return "only-slot";});
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
        final ArkAssetPool shared=concurrentPool;
        try {
            var first=executor.submit(() -> shared.createManualAsset(101,"group-1","one","https://example.com/one.png"));
            assertThat(entered.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> shared.createManualAsset(102,"group-1","two","https://example.com/two.png")).isInstanceOf(ArkAssetPool.Waiting.class);
            finish.countDown();assertThat(first.get(10,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("only-slot");
            verify(client,times(1)).createAsset(anyString(),anyString(),anyString());
        } finally {finish.countDown();executor.shutdownNow();jdbc.update("delete from ark_managed_resource where scope_key='concurrent-account'");jdbc.update("delete from ark_asset_scope where scope_key='concurrent-account'");}
    }

    @Test void enforcesTheIndependentGroupQuotaWithoutDeletingExternalGroups() throws Exception {
        when(client.listGroups("second-project")).thenReturn(List.of());
        for(int i=0;i<50;i++)groups.add(json.createObjectNode().put("Id","foreign-group-"+i));
        assertThatThrownBy(() -> pool.createManualGroup(8,"角色组")).isInstanceOf(ArkAssetPool.Waiting.class).hasMessageContaining("素材组容量不足");
        verify(client,never()).createGroup(anyString());verify(client,never()).deleteGroup(anyString(),anyString());
    }

    @Test void softDeletedUnknownSubmissionsAndQueriesStillProtectTheirInputs() throws Exception {
        when(client.listAssets("second-project")).thenReturn(List.of());
        for(int i=0;i<45;i++)assets.add(asset("asset-"+i));
        long first=managed("asset-0");managed("asset-1");
        for(int i=0;i<2;i++)jdbc.update("insert into ai_video_task(id,tenant_id,project_id,storyboard_id,provider_code,model,prompt,duration_seconds,aspect_ratio,status,external_status,request_snapshot_json,created_by,created_at,updated_at,deleted_at) values(?,1,1,1,'VOLCENGINE_ARK','seedance','test',5,'9:16','FAILED',?,?,1,now(),now(),now())",98800+i,i==0?"SUBMIT_UNKNOWN":"QUERY_UNKNOWN","{\"references\":[{\"providerAssetId\":\"asset-"+i+"\"}]}");
        jdbc.update("insert into ark_asset_consumer(resource_id,consumer_key,video_task_id) values(?,'video:98800',98800)",first);
        pool.maintain();verify(client,never()).deleteAsset(anyString(),anyString());
    }
    @Test void pendingDeletionIsRetriedEvenAfterOtherVictimsBringInventoryBelowHighWater() throws Exception {
        when(client.listAssets("second-project")).thenReturn(List.of());
        for(int i=0;i<45;i++)assets.add(asset("asset-"+i));for(int i=0;i<6;i++)managed("asset-"+i);
        doThrow(new java.net.http.HttpTimeoutException("unknown delete")).doAnswer(i -> {assets.removeIf(n -> n.path("Id").asText().equals("asset-0"));return null;}).when(client).deleteAsset("default","asset-0");
        pool.maintain();assertThat(assets).hasSize(40);
        assertThat(jdbc.queryForObject("select status from ark_managed_resource where scope_key='test-account' and provider_id='asset-0'",String.class)).isEqualTo("DELETING");
        pool.maintain();assertThat(assets).hasSize(39);
        assertThat(jdbc.queryForObject("select status from ark_managed_resource where scope_key='test-account' and provider_id='asset-0'",String.class)).isEqualTo("EVICTED");
    }
    @Test void definitiveDeletionRejectionRestoresTheLiveIdentity() throws Exception {
        when(client.listAssets("second-project")).thenReturn(List.of());
        for(int i=0;i<45;i++)assets.add(asset("asset-"+i));managed("asset-0");
        doThrow(new ArkAssetClient.ProviderException("AccessDenied",true)).when(client).deleteAsset("default","asset-0");
        pool.maintain();
        assertThat(jdbc.queryForObject("select status from ark_managed_resource where scope_key='test-account' and provider_id='asset-0'",String.class)).isEqualTo("ACTIVE");
    }
    @Test void explicitManualRetryAllocatesAFreshIdentityAndRetainsTheFailedOne() throws Exception {
        when(client.listAssets("second-project")).thenReturn(List.of());
        when(client.createAsset(anyString(),anyString(),anyString())).thenReturn("failed-asset","new-asset");
        assertThat(pool.createManualAsset(900,0,"group-1","图","https://example.com/original.png")).isEqualTo("failed-asset");
        pool.failed("failed-asset");
        jdbc.update("update ark_asset_scope set next_create_at=null where scope_key='test-account'");
        assertThat(pool.createManualAsset(900,1,"group-1","图","https://example.com/original.png")).isEqualTo("new-asset");
        assertThat(jdbc.queryForObject("select status from ark_managed_resource where scope_key='test-account' and provider_id='failed-asset'",String.class)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select count(*) from ark_managed_resource where scope_key='test-account' and resource_type='ASSET'",Long.class)).isEqualTo(2);
    }
    @Test void reusesSourceMatchedReadyAssetsAtFullCapacityWithoutReservingAnExtraSlot() throws Exception {
        when(client.listAssets("second-project")).thenReturn(List.of());
        when(client.getAsset("asset-new")).thenReturn(new ArkAssetClient.Asset("Active","https://example.com/ready.png","",""));
        pool.prepareImage(800,1,1,"角色","materials/frozen.png","https://example.com/old-signature.png",null);
        for(int i=0;i<49;i++)assets.add(asset("external-"+i));assets.add(asset("asset-new"));
        jdbc.update("insert into seedance_avatar_recovery(source_task_id,tenant_id,project_id,storyboard_id,created_by,deadline_at) values(903,1,1,1,1,?)",LocalDateTime.now().plusHours(1));
        long job=jdbc.queryForObject("select id from seedance_avatar_recovery where source_task_id=903",Long.class);
        var image=json.readTree("{\"objectStoragePath\":\"materials/frozen.png\",\"providerUrl\":\"https://example.com/new-signature.png\"}");
        pool.reserveRecovery(job,1,1,List.of(image));
        assertThat(jdbc.queryForObject("select admitted_recovery_id from ark_asset_scope where scope_key='test-account'",Long.class)).isEqualTo(job);
        verify(client,times(1)).createAsset(anyString(),anyString(),anyString());
    }

    @Test void retriesNeverDeleteAGroupThatNowContainsExternalAssets() throws Exception {
        when(client.listAssets("second-project")).thenReturn(List.of());when(client.listGroups("second-project")).thenReturn(List.of());
        assets.add(asset("foreign-in-group"));groups.add(json.createObjectNode().put("Id","group-1"));
        jdbc.update("insert into ark_managed_resource(scope_key,provider_project,resource_type,owner_key,provider_id,provider_name,status,deletion_previous_status) values('test-account','default','GROUP','old-group','group-1','group','DELETING','ACTIVE')");
        pool.maintain();verify(client,never()).deleteGroup(anyString(),anyString());
        assertThat(jdbc.queryForObject("select status from ark_managed_resource where scope_key='test-account' and provider_id='group-1'",String.class)).isEqualTo("ACTIVE");
    }

}
