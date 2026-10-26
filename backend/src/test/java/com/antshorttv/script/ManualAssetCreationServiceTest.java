package com.antshorttv.script;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class ManualAssetCreationServiceTest {
    static final long TENANT=98901,PROJECT=98902,SCRIPT=98903;
    @Autowired ManualAssetCreationService service;
    @Autowired ManualAssetConfiguration config;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @MockBean ProjectPermissionGuard permissions;
    @SpyBean AssetVisualVariantService variants;
    @BeforeEach void seed() {
        for(String table:List.of("script_manual_asset_request","asset_visual_variant","script_asset_extraction_owner","script_asset_normalization_snapshot","character_asset","scene_asset","prop_asset","script","ai_execution_task"))
            jdbc.update("delete from "+table+" where tenant_id=?",TENANT);
        when(permissions.require(eq(TENANT),eq(PROJECT),anyString())).thenReturn(new TenantContext(99L,TENANT,98L,"MEMBER"));
        jdbc.update("insert into script(id,tenant_id,project_id,title,source_type,content,status,created_by,created_at,updated_at) values (?,?,?,'测试剧本','USER','正文','DRAFT',99,now(),now())",SCRIPT,TENANT,PROJECT);
    }
    ManualAssetCreationService.Command command(String type,String name,List<String> aliases) {
        return new ManualAssetCreationService.Command(UUID.randomUUID().toString(),SCRIPT,type,name,aliases,ManualAssetConfigurationTest.values(config,type),null);
    }
    @ParameterizedTest @ValueSource(strings={"CHARACTER","SCENE","PROP"})
    void createsFormalAssetAndPrimaryWithoutGenerating(String type) throws Exception {
        var command=command(type,"新资产",List.of("别名"));
        var created=service.create(TENANT,PROJECT,command);
        var row=jdbc.queryForMap("select * from "+type.toLowerCase()+"_asset where id=?",created.assetId());
        assertThat(row).containsEntry("source","USER").containsEntry("status","CONFIRMED").containsEntry("script_id",SCRIPT).containsEntry("normalized_name","新资产");
        var metadata=json.readTree((String)row.get("content_json"));
        assertThat(metadata.path("manualConfiguration").path("values").size()).isEqualTo(command.configuration().size());
        assertThat(metadata.path("aliases").get(0).path("name").asText()).isEqualTo("别名");
        var variant=jdbc.queryForMap("select * from asset_visual_variant where id=?",created.variantId());
        assertThat(variant).containsEntry("source_type","MANUAL").containsEntry("generation_status","NOT_STARTED").containsEntry("is_primary",true);
        assertThat(variant.get("prompt")).isNull(); assertThat(variant.get("current_image_url")).isNull();
        assertThat((String)row.get("prompt")).contains(config.definition(type).task());
        assertThat(jdbc.queryForObject("select count(*) from asset_visual_variant_episode where tenant_id=?",Integer.class,TENANT)).isZero();
    }
    @Test void retriesReturnSameAssetAndChangedPayloadIsRejected() {
        var command=command("CHARACTER","苏禾",List.of()); var first=service.create(TENANT,PROJECT,command);
        assertThat(service.create(TENANT,PROJECT,command)).isEqualTo(first);
        var changed=new ManualAssetCreationService.Command(command.requestId(),SCRIPT,"CHARACTER","别的人",List.of(),command.configuration(),null);
        assertThatThrownBy(()->service.create(TENANT,PROJECT,changed)).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("select count(*) from character_asset where tenant_id=?",Integer.class,TENANT)).isEqualTo(1);
    }
    @Test void matchesRenamedIdentityLegacyNullScopeAndAliasesInBothDirections() {
        var first=service.create(TENANT,PROJECT,command("CHARACTER","Alice",List.of("阿梨")));
        jdbc.update("update character_asset set name='新展示名',script_id=null where id=?",first.assetId());
        for(String name:List.of("a-l-i-c-e","新展示名","阿梨"))
            assertThatThrownBy(()->service.create(TENANT,PROJECT,command("CHARACTER",name,List.of()))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.create(TENANT,PROJECT,command("CHARACTER","另一人",List.of("新展示名")))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.create(TENANT,PROJECT,command("CHARACTER","另一人",List.of("阿梨")))).isInstanceOf(BusinessException.class);
        jdbc.update("update character_asset set deleted_at=now() where id=?",first.assetId());
        assertThat(service.create(TENANT,PROJECT,command("CHARACTER","Alice",List.of())).assetId()).isNotEqualTo(first.assetId());
    }
    @Test void enforcesPermissionsScriptAndFullConfiguration() {
        var command=command("SCENE","书店",List.of());
        when(permissions.require(TENANT,PROJECT,"ELEMENT:EDIT")).thenThrow(new BusinessException(ErrorCode.FORBIDDEN,"无权编辑"));
        assertThatThrownBy(()->service.create(TENANT,PROJECT,command)).isInstanceOf(BusinessException.class).hasMessageContaining("无权");
        doReturn(new TenantContext(99L,TENANT,98L,"MEMBER")).when(permissions).require(TENANT,PROJECT,"ELEMENT:EDIT");
        var incomplete=new ManualAssetCreationService.Command(UUID.randomUUID().toString(),SCRIPT,"SCENE","书店",List.of(),Map.of(),null);
        assertThatThrownBy(()->service.create(TENANT,PROJECT,incomplete)).isInstanceOf(BusinessException.class);
        jdbc.update("update script set deleted_at=now() where id=?",SCRIPT);
        assertThatThrownBy(()->service.create(TENANT,PROJECT,command)).isInstanceOf(BusinessException.class);
    }
    @Test void failedNormalizationBlocksCreation() {
        jdbc.update("insert into script_asset_normalization_snapshot(tenant_id,project_id,script_id,script_version_id,execution_id,input_fingerprint,input_json,status,created_at,updated_at) values (?,?,?,1,98909,'test','{}','FAILED',now(),now())",TENANT,PROJECT,SCRIPT);
        assertThatThrownBy(()->service.create(TENANT,PROJECT,command("PROP","怀表",List.of()))).isInstanceOf(BusinessException.class).hasMessageContaining("归一");
    }
    @Test void ownerMustBelongToCurrentProjectAndScript() {
        var owner=service.create(TENANT,PROJECT,command("CHARACTER","苏禾",List.of()));
        var base=command("PROP","苏禾的怀表",List.of());
        var command=new ManualAssetCreationService.Command(base.requestId(),SCRIPT,"PROP",base.name(),base.aliases(),base.configuration(),owner.assetId());
        var created=service.create(TENANT,PROJECT,command);
        assertThat(jdbc.queryForObject("select related_character from prop_asset where id=?",String.class,created.assetId())).isEqualTo("苏禾");
        jdbc.update("update character_asset set project_id=98999 where id=?",owner.assetId());
        var invalid=new ManualAssetCreationService.Command(UUID.randomUUID().toString(),SCRIPT,"PROP","另一个怀表",List.of(),base.configuration(),owner.assetId());
        assertThatThrownBy(()->service.create(TENANT,PROJECT,invalid)).isInstanceOf(BusinessException.class);
    }
    @Test void variantFailureRollsBackAssetAndReceipt() {
        doThrow(new IllegalStateException("variant failure")).when(variants).create(eq(TENANT),eq(PROJECT),eq("CHARACTER"),anyLong(),anyLong(),any());
        assertThatThrownBy(()->service.create(TENANT,PROJECT,command("CHARACTER","回滚",List.of()))).hasMessageContaining("variant failure");
        assertThat(jdbc.queryForObject("select count(*) from character_asset where tenant_id=?",Integer.class,TENANT)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from script_manual_asset_request where tenant_id=?",Integer.class,TENANT)).isZero();
    }
    @Test void concurrentRetriesCreateOnlyOneIdentity() throws Exception {
        var command=command("PROP","并发怀表",List.of());
        ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            var results=executor.invokeAll(List.of(()->service.create(TENANT,PROJECT,command),()->service.create(TENANT,PROJECT,command)));
            assertThat(results.get(0).get(15,TimeUnit.SECONDS)).isEqualTo(results.get(1).get(15,TimeUnit.SECONDS));
            assertThat(jdbc.queryForObject("select count(*) from prop_asset where tenant_id=?",Integer.class,TENANT)).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }
    @ParameterizedTest @ValueSource(strings={"PENDING","RUNNING","FAILED"})
    void extractionOwnerBlocksUntilReleased(String status) {
        jdbc.update("insert into ai_execution_task(id,tenant_id,user_id,project_id,scene,capability,business_type,status,phase,client_idempotency_key,trace_id,created_at,updated_at) values (98910,?,99,?,'TEST','TEXT','SCRIPT_AI_OPERATION',?,'EXTRACT','manual-test','manual-test',now(),now())",TENANT,PROJECT,status);
        jdbc.update("insert into script_asset_extraction_owner(tenant_id,project_id,script_id,execution_id,updated_at) values (?,?,?,98910,now())",TENANT,PROJECT,SCRIPT);
        assertThat(service.options(TENANT,PROJECT).canCreate()).isFalse();
        assertThatThrownBy(()->service.create(TENANT,PROJECT,command("PROP","受阻道具",List.of()))).isInstanceOf(BusinessException.class);
        jdbc.update("update script_asset_extraction_owner set execution_id=null where tenant_id=?",TENANT);
        assertThat(service.create(TENANT,PROJECT,command("PROP","受阻道具",List.of())).assetId()).isPositive();
    }
    @Test void rejectsStaleScriptAndMalformedIdentitiesWithoutMutation() {
        for(String name:List.of("", "---", "名".repeat(101)))
            assertThatThrownBy(()->service.create(TENANT,PROJECT,command("CHARACTER",name,List.of()))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.create(TENANT,PROJECT,command("CHARACTER","Alice",List.of("A-l-i-c-e")))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.create(TENANT,PROJECT,command("CHARACTER","Alice",Collections.nCopies(31,"别名")))).isInstanceOf(BusinessException.class);
        var command=command("CHARACTER","旧剧本角色",List.of());
        jdbc.update("update script set id=98999 where id=?",SCRIPT);
        assertThatThrownBy(()->service.create(TENANT,PROJECT,command)).isInstanceOf(BusinessException.class).hasMessageContaining("剧本");
        assertThat(jdbc.queryForObject("select count(*) from character_asset where tenant_id=?",Integer.class,TENANT)).isZero();
    }
    @Test void optionsReflectRealEditingPermission() {
        doThrow(new BusinessException(ErrorCode.FORBIDDEN,"无权编辑")).when(permissions).require(TENANT,PROJECT,"ELEMENT:EDIT");
        assertThat(service.options(TENANT,PROJECT).canCreate()).isFalse();
        assertThat(service.options(TENANT,PROJECT).scriptId()).isEqualTo(SCRIPT);
        doThrow(new BusinessException(ErrorCode.FORBIDDEN,"无权访问")).when(permissions).require(TENANT,PROJECT,"ELEMENT:VIEW");
        assertThatThrownBy(()->service.options(TENANT,PROJECT)).isInstanceOf(BusinessException.class);
    }

}
