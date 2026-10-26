package com.antshorttv.avatar;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.antshorttv.rbac.RbacPermissionService;
import com.antshorttv.security.*;
import com.antshorttv.querycache.QueryResultCache;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties="ark.avatar.worker-enabled=false")
@Transactional
class VirtualAvatarServiceTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager tx;
    ArkAssetClient client=mock(ArkAssetClient.class);
    VirtualAvatarStorage storage=mock(VirtualAvatarStorage.class);
    VirtualAvatarService service;
    @BeforeEach void setup() throws Exception {
        var tenants=mock(TenantContextResolver.class); var permissions=mock(RbacPermissionService.class);
        var context=new TenantContext(9999L,9901L,9998L,"OWNER");
        when(tenants.requireActiveMember(9901L)).thenReturn(context);
        when(permissions.hasProjectPermission(eq(context),eq(9902L),anyString())).thenReturn(true);
        when(client.project()).thenReturn("default");
        when(client.createGroup(anyString())).thenReturn("group-1");
        when(client.createAsset(anyString(),anyString(),anyString())).thenReturn("asset-1");
        when(storage.sourceUrl(anyString(),anyString())).thenReturn("https://example.com/source.png");
        when(storage.save(anyLong(),anyLong(),anyLong(),anyString())).thenReturn("materials/9901/9902/avatar/1.png");
        service=new VirtualAvatarService(jdbc,tx,tenants,permissions,client,storage,QueryResultCache.disabled());
        jdbc.update("insert into character_asset(id,tenant_id,project_id,name,role_type,status,created_by,created_at,updated_at) values(9910,9901,9902,'角色','LEAD','CONFIRMED',9999,now(),now())");
        jdbc.update("insert into asset_visual_variant(id,tenant_id,project_id,asset_type,asset_id,name,source_type,generation_status,current_image_url,is_primary,created_by,created_at,updated_at) values(9920,9901,9902,'CHARACTER',9910,'形态','MANUAL','COMPLETED','https://example.com/original.png',true,9999,now(),now())");
    }
    @Test void createsPollsPersistsAndBindsOnlyAfterActive() throws Exception {
        long id=((Number)service.create(9901,9902,List.of(9920L)).get(0).get("id")).longValue();
        assertThat(service.create(9901,9902,List.of(9920L)).get(0).get("id")).isEqualTo(id);
        service.process(id);
        assertThat(jdbc.queryForObject("select provider_asset_id from virtual_avatar_task where id=?",String.class,id)).isEqualTo("asset-1");
        when(client.getAsset("asset-1")).thenReturn(new ArkAssetClient.Asset("Processing","","",""));
        service.process(id);
        assertThat(jdbc.queryForObject("select virtual_avatar_asset_id from asset_visual_variant where id=9920",String.class)).isNull();
        when(client.getAsset("asset-1")).thenReturn(new ArkAssetClient.Asset("Active","https://example.com/result.png","",""));
        service.process(id);
        assertThat(jdbc.queryForObject("select status from virtual_avatar_task where id=?",String.class,id)).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("select result_storage_path from virtual_avatar_task where id=?",String.class,id)).startsWith("materials/");
        assertThat(jdbc.queryForObject("select virtual_avatar_asset_id from asset_visual_variant where id=9920",String.class)).isEqualTo("asset-1");
        verify(client,times(1)).createAsset(anyString(),anyString(),anyString());
    }
    @Test void doesNotBindAnOldImageAfterReplacement() throws Exception {
        long id=((Number)service.create(9901,9902,List.of(9920L)).get(0).get("id")).longValue();service.process(id);
        jdbc.update("update asset_visual_variant set current_image_url='https://example.com/new.png' where id=9920");
        when(client.getAsset("asset-1")).thenReturn(new ArkAssetClient.Asset("Active","https://example.com/result.png","",""));
        service.process(id);
        assertThat(jdbc.queryForObject("select error_code from virtual_avatar_task where id=?",String.class,id)).isEqualTo("SOURCE_CHANGED");
        assertThat(jdbc.queryForObject("select virtual_avatar_asset_id from asset_visual_variant where id=9920",String.class)).isNull();
    }
    @Test void recordsProviderFailureAndPreventsUncertainResubmission() throws Exception {
        long id=((Number)service.create(9901,9902,List.of(9920L)).get(0).get("id")).longValue();
        when(client.createAsset(anyString(),anyString(),anyString())).thenThrow(new java.net.http.HttpTimeoutException("secret query must not leak"));
        service.process(id);service.process(id);
        assertThat(jdbc.queryForObject("select error_code from virtual_avatar_task where id=?",String.class,id)).isEqualTo("SUBMISSION_UNKNOWN");
        assertThatThrownBy(()->service.retry(9901,9902,id)).isInstanceOf(com.antshorttv.common.BusinessException.class);
        verify(client,times(1)).createAsset(anyString(),anyString(),anyString());
    }
    @Test void retriesStorageWithoutCreatingAnotherProviderAsset() throws Exception {
        long id=((Number)service.create(9901,9902,List.of(9920L)).get(0).get("id")).longValue();service.process(id);
        when(client.getAsset("asset-1")).thenReturn(new ArkAssetClient.Asset("Active","https://example.com/result.png","",""));
        when(storage.save(anyLong(),anyLong(),anyLong(),anyString())).thenThrow(new IllegalStateException("storage down")).thenReturn("materials/saved.png");
        service.process(id);
        assertThat(jdbc.queryForObject("select error_code from virtual_avatar_task where id=?",String.class,id)).isEqualTo("STORAGE_FAILED");
        service.retry(9901,9902,id);service.process(id);
        assertThat(jdbc.queryForObject("select status from virtual_avatar_task where id=?",String.class,id)).isEqualTo("SUCCEEDED");
        verify(client,times(1)).createAsset(anyString(),anyString(),anyString());
    }
    @Test void mixedBatchKeepsValidItemsAndDoesNotExposeOtherProjectImages() {
        var result=service.create(9901,9902,List.of(9920L,123456L,9920L));
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsKey("id");
        assertThat(result.get(1)).containsKey("errorMessage");
        assertThat(service.candidates(9901,9902,1,9920L)).singleElement().satisfies(row->assertThat(row.get("characterName")).isEqualTo("角色"));
    }

    @Test void replacingAnImageCreatesANewTaskInsteadOfReusingTheOldOne() {
        long old=((Number)service.create(9901,9902,List.of(9920L)).get(0).get("id")).longValue();
        jdbc.update("update asset_visual_variant set current_image_url='https://example.com/new.png' where id=9920");
        long next=((Number)service.create(9901,9902,List.of(9920L)).get(0).get("id")).longValue();
        assertThat(next).isNotEqualTo(old);
        assertThat(service.candidates(9901,9902,1,9920L)).singleElement().satisfies(row->assertThat(row.get("taskId")).isEqualTo(next));
    }
    @Test void dueQueueDoesNotStarveNewImagesBehindProcessingAssets() {
        long first=((Number)service.create(9901,9902,List.of(9920L)).get(0).get("id")).longValue();
        jdbc.update("update asset_visual_variant set current_image_url='https://example.com/new.png' where id=9920");
        long second=((Number)service.create(9901,9902,List.of(9920L)).get(0).get("id")).longValue();
        jdbc.update("update virtual_avatar_task set next_poll_at=? where id=?",java.time.LocalDateTime.now().minusMinutes(2),second);
        assertThat(service.due()).containsSubsequence(second,first);
    }

}
