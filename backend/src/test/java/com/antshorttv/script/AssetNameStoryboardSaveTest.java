package com.antshorttv.script;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.antshorttv.project.ProjectAccessContext;
import com.antshorttv.project.ProjectAccessResolver;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties="spring.datasource.hikari.transaction-isolation=TRANSACTION_REPEATABLE_READ")
class AssetNameStoryboardSaveTest {
    @Autowired ScriptWorkflowService workflow;
    @Autowired AssetNameService names;
    @Autowired AssetVisualVariantService variants;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @MockBean TenantContextResolver tenants;
    @MockBean ProjectAccessResolver projects;
    @MockBean ProjectPermissionGuard permissions;
    private static final long TENANT=98901,PROJECT=98902;
    private static final AtomicLong IDS=new AtomicLong(98903000);
    private final TenantContext tenant=new TenantContext(1L,TENANT,1L,"OWNER");

    @BeforeEach void authorize() {
        when(tenants.requireActiveMember(TENANT)).thenReturn(tenant);
        when(projects.requireView(TENANT,PROJECT)).thenReturn(mock(ProjectAccessContext.class));
    }
    private Fixture seed(String type) throws Exception {
        long asset=IDS.incrementAndGet(),shot=IDS.incrementAndGet(),history=IDS.incrementAndGet();
        String kind=switch(type){case "CHARACTER"->"role_type";case "SCENE"->"scene_type";default->"prop_type";};
        jdbc.update("insert into "+type.toLowerCase()+"_asset (id,tenant_id,project_id,name,"+kind+",status,created_by,created_at,updated_at) values (?,?,?,'原资产','TEST','CONFIRMED',1,now(),now())",asset,TENANT,PROJECT);
        long variant=variants.create(TENANT,PROJECT,type,asset,1L,new AssetVisualVariantService.VariantCommand("原形态",null,null,"MANUAL","COMPLETED",null,"/kept.png",true)).id();
        JsonNode document=json.readTree("""
            {"version":2,"nodes":[
              {"type":"text","text":"原资产与原形态是用户正文，不替换"},
              {"type":"mention","mediaType":"IMAGE","sourceType":"ASSET_VISUAL_VARIANT","sourceId":%d,"variantId":%d,"assetType":"%s","assetId":%d,"displayName":"原资产 / 原形态","imageUrl":"/kept.png"},
              {"type":"mention","mediaType":"VIDEO","sourceType":"AI_VIDEO_RESULT","sourceId":%d,"displayName":"原视频名"}
            ]}
            """.formatted(variant,variant,type,asset,variant));
        for(long id:new long[]{shot,history}) jdbc.update("insert into storyboard (id,tenant_id,project_id,episode_no,shot_no,visual_description,prompt_document_json,status,created_by,created_at,updated_at) values (?,?,?,1,1,'原正文',?,'DRAFT',1,now(),now())",id,TENANT,PROJECT,document.toString());
        jdbc.update("update storyboard set deleted_at=now() where id=?",history);
        return new Fixture(type,asset,variant,shot,history,document);
    }
    private SaveStoryboardRequest request(JsonNode document) {
        return new SaveStoryboardRequest(1,1,1,null,null,"编辑后正文",null,null,null,null,null,null,null,null,null,document,"DRAFT");
    }
    private JsonNode stored(long shot) throws Exception {
        return json.readTree(jdbc.queryForObject("select prompt_document_json from storyboard where id=?",String.class,shot));
    }
    private void assertCurrent(Fixture fixture,long shot) throws Exception {
        var result=stored(shot);
        assertThat(result.path("nodes").get(1).path("displayName").asText()).isEqualTo("新资产 / 新形态");
        assertThat(result.path("nodes").get(0)).isEqualTo(fixture.document().path("nodes").get(0));
        assertThat(result.path("nodes").get(2)).isEqualTo(fixture.document().path("nodes").get(2));
        assertThat(result.path("nodes").get(1).path("imageUrl").asText()).isEqualTo("/kept.png");
        assertThat(fixture.document().path("nodes").get(1).path("displayName").asText()).isEqualTo("原资产 / 原形态");
        assertThat(stored(fixture.history())).isEqualTo(fixture.document());
    }
    @ParameterizedTest @CsvSource({"CHARACTER,UPDATE","SCENE,UPDATE","PROP,UPDATE","CHARACTER,CREATE","SCENE,CREATE","PROP,CREATE","CHARACTER,ADD_REFERENCE","SCENE,ADD_REFERENCE","PROP,ADD_REFERENCE"})
    void oldEditorSaveAndNewReferencesUseCurrentNames(String type,String mode) throws Exception {
        var fixture=seed(type);
        if("ADD_REFERENCE".equals(mode)) jdbc.update("update storyboard set prompt_document_json=? where id=?","{\"version\":2,\"nodes\":[{\"type\":\"text\",\"text\":\"正文\"}]}",fixture.shot());
        names.renameAsset(TENANT,PROJECT,type,fixture.asset(),"新资产");
        names.renameVariant(TENANT,PROJECT,fixture.variant(),"新形态");
        long saved=fixture.shot();
        if("CREATE".equals(mode)) {
            workflow.createStoryboard(TENANT,PROJECT,request(fixture.document()),null);
            saved=jdbc.queryForObject("select max(id) from storyboard where tenant_id=? and project_id=? and deleted_at is null",Long.class,TENANT,PROJECT);
        } else workflow.updateStoryboard(TENANT,PROJECT,fixture.shot(),request(fixture.document()),null);
        assertCurrent(fixture,saved);
        assertThat(jdbc.queryForObject("select visual_description from storyboard where id=?",String.class,saved)).isEqualTo("编辑后正文");
    }
    @ParameterizedTest
    @CsvSource({"CHARACTER,UPDATE","SCENE,UPDATE","PROP,UPDATE","CHARACTER,CREATE","SCENE,CREATE","PROP,CREATE"})
    void staleEditorRetainsDeletedAssetTextBeforeLockingNames(String type, String mode) throws Exception {
        var fixture=seed(type);
        jdbc.update("update "+type.toLowerCase()+"_asset set deleted_at=now() where id=?",fixture.asset());
        long saved=fixture.shot();
        if ("CREATE".equals(mode)) {
            workflow.createStoryboard(TENANT,PROJECT,request(fixture.document()),null);
            saved=jdbc.queryForObject("select max(id) from storyboard where tenant_id=? and project_id=? and deleted_at is null",Long.class,TENANT,PROJECT);
        } else {
            workflow.updateStoryboard(TENANT,PROJECT,fixture.shot(),request(fixture.document()),null);
        }
        var result=stored(saved);
        assertThat(result.path("nodes").get(1).path("type").asText()).isEqualTo("text");
        assertThat(result.path("nodes").get(1).path("text").asText()).contains("原资产", "原形态");
        assertThat(result.path("nodes").get(2)).isEqualTo(fixture.document().path("nodes").get(2));
    }
    @Test void saveReadsCurrentAssetNameEvenWhenAuthorizationReadPrecedesRename() throws Exception {
        var fixture=seed("CHARACTER");
        var readBeforeRename=new CountDownLatch(1);var renamed=new CountDownLatch(1);
        var executor=Executors.newSingleThreadExecutor();
        when(tenants.requireActiveMember(TENANT)).thenAnswer(invocation->{
            jdbc.queryForObject("select name from character_asset where id=?",String.class,fixture.asset());
            readBeforeRename.countDown();
            assertThat(renamed.await(10,TimeUnit.SECONDS)).isTrue();
            return tenant;
        });
        try {
            var save=executor.submit(()->workflow.updateStoryboard(TENANT,PROJECT,fixture.shot(),request(fixture.document()),null));
            assertThat(readBeforeRename.await(5,TimeUnit.SECONDS)).isTrue();
            names.renameAsset(TENANT,PROJECT,"CHARACTER",fixture.asset(),"新资产");
            names.renameVariant(TENANT,PROJECT,fixture.variant(),"新形态");
            renamed.countDown();
            try {
                save.get(15,TimeUnit.SECONDS);
            } catch (ExecutionException failure) {
                // H2 RR aborts promotion of a snapshot row changed by another transaction.
                // MySQL locking reads instead see the current row; do not change production locking for H2.
                assertThat(failure.getCause()).isInstanceOf(org.springframework.dao.CannotAcquireLockException.class);
                var conflict=(org.springframework.dao.CannotAcquireLockException)failure.getCause();
                assertThat(conflict.getCause()).isInstanceOf(java.sql.SQLException.class);
                assertThat(((java.sql.SQLException)conflict.getCause()).getSQLState()).isEqualTo("40001");
                assertThat(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>)connection -> connection.getMetaData().getDatabaseProductName())).isEqualTo("H2");
                assertCurrent(fixture,fixture.shot());
                assertThat(jdbc.queryForObject("select visual_description from storyboard where id=?",String.class,fixture.shot())).isEqualTo("原正文");
                when(tenants.requireActiveMember(TENANT)).thenReturn(tenant);
                workflow.updateStoryboard(TENANT,PROJECT,fixture.shot(),request(fixture.document()),null);
            }
            assertCurrent(fixture,fixture.shot());
        } finally {renamed.countDown();executor.shutdownNow();}
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"CHARACTER","SCENE","PROP"})
    void actualSaveCompletingBeforeRenameAlsoKeepsCurrentNames(String type) throws Exception {
        var fixture=seed(type);
        workflow.updateStoryboard(TENANT,PROJECT,fixture.shot(),request(fixture.document()),null);
        names.renameAsset(TENANT,PROJECT,type,fixture.asset(),"新资产");
        names.renameVariant(TENANT,PROJECT,fixture.variant(),"新形态");
        assertCurrent(fixture,fixture.shot());
    }
    @Test void simultaneousRealSavesWithReversedAssetOrderDoNotDeadlock() throws Exception {
        var first=seed("CHARACTER");var second=seed("SCENE");
        for(var fixture:new Fixture[]{first,second}) {
            names.renameAsset(TENANT,PROJECT,fixture.type(),fixture.asset(),"新资产");
            names.renameVariant(TENANT,PROJECT,fixture.variant(),"新形态");
        }
        var forward=json.createObjectNode().put("version",2);
        forward.putArray("nodes").add(first.document().path("nodes").get(1)).add(second.document().path("nodes").get(1));
        var reverse=json.createObjectNode().put("version",2);
        reverse.putArray("nodes").add(second.document().path("nodes").get(1)).add(first.document().path("nodes").get(1));
        var start=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        try {
            var one=executor.submit(()->{start.await();return workflow.updateStoryboard(TENANT,PROJECT,first.shot(),request(forward),null);});
            var two=executor.submit(()->{start.await();return workflow.updateStoryboard(TENANT,PROJECT,second.shot(),request(reverse),null);});
            start.countDown();one.get(15,TimeUnit.SECONDS);two.get(15,TimeUnit.SECONDS);
            for(long shot:new long[]{first.shot(),second.shot()}) for(var node:stored(shot).path("nodes"))
                assertThat(node.path("displayName").asText()).isEqualTo("新资产 / 新形态");
        } finally {start.countDown();executor.shutdownNow();}
    }
    @Test void keepsUnknownAndForeignProjectReferencesUnchanged() throws Exception {
        var fixture=seed("CHARACTER");
        var incoming=fixture.document().deepCopy();
        var nodes=(com.fasterxml.jackson.databind.node.ArrayNode)incoming.path("nodes");
        var unknown=json.createObjectNode().put("type","mention").put("mediaType","IMAGE")
            .put("sourceType","ASSET_VISUAL_VARIANT").put("sourceId",123).put("assetId",456).put("displayName","历史引用");
        jdbc.update("insert into asset_visual_variant (tenant_id,project_id,asset_type,asset_id,name,source_type,generation_status,is_primary,created_by,created_at,updated_at) values (?,?,'CHARACTER',456,'不可泄露的名称','MANUAL','NOT_STARTED',true,1,now(),now())",TENANT+1,PROJECT+1);
        long foreignId=jdbc.queryForObject("select max(id) from asset_visual_variant where tenant_id=?",Long.class,TENANT+1);
        var foreign=unknown.deepCopy().put("sourceId",foreignId).put("displayName","别的项目引用");
        nodes.add(unknown);nodes.add(foreign);
        workflow.updateStoryboard(TENANT,PROJECT,fixture.shot(),request(incoming),null);
        assertThat(stored(fixture.shot()).path("nodes").get(3).toString()).isEqualTo(unknown.toString());
        assertThat(stored(fixture.shot()).path("nodes").get(4).toString()).isEqualTo(foreign.toString());
    }
    @Test void rejectsOwnerChangesEvenWhenTheNewOwnerWasAlsoPrepared() throws Exception {
        var first=seed("CHARACTER");var second=seed("CHARACTER");
        var incoming=json.createObjectNode().put("version",2);
        var nodes=incoming.putArray("nodes");
        for(var fixture:new Fixture[]{first,second}) nodes.addObject().put("type","mention").put("mediaType","IMAGE")
            .put("sourceType","ASSET_VISUAL_VARIANT").put("sourceId",fixture.variant()).put("displayName","旧名称");
        var transaction=new org.springframework.transaction.support.TransactionTemplate(transactions);
        assertThatThrownBy(()->transaction.execute(status->{
            var prepared=names.lockDocumentAssets(TENANT,PROJECT,incoming);
            jdbc.update("update asset_visual_variant set is_primary=false,asset_id=? where id=?",second.asset(),first.variant());
            return names.normalizeDocument(TENANT,PROJECT,incoming,prepared);
        })).isInstanceOf(com.antshorttv.common.BusinessException.class).hasMessageContaining("归属已变化");
        assertThat(jdbc.queryForObject("select asset_id from asset_visual_variant where id=?",Long.class,first.variant())).isEqualTo(first.asset());
    }
    private record Fixture(String type,long asset,long variant,long shot,long history,JsonNode document) {}
}
