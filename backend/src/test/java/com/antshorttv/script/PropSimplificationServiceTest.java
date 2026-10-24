package com.antshorttv.script;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.antshorttv.execution.*;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.workflowagent.run.WorkflowAgentRunner;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PropSimplificationServiceTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager transactions;
    @Autowired PropProductionService production;
    private PropSimplificationService service;
    private WorkflowAgentRunner runner;
    private AiExecutionService executions;
    private long plan;
    @BeforeEach void seed() {
        jdbc.update("insert into tenant (id,code,name,type,status,created_at,updated_at) values (96301,'prop-reuse','props','TEAM','ACTIVE',now(),now())");
        jdbc.update("insert into project (id,tenant_id,name,code,owner_id,status,created_by,created_at,updated_at) values (96302,96301,'乡厨','prop-reuse',1,'ACTIVE',1,now(),now())");
        jdbc.update("insert into script (id,tenant_id,project_id,title,source_type,content,status,current_version_id,created_by,created_at,updated_at) values (96303,96301,96302,'乡厨','MANUAL_EDIT','原文','ACTIVE',96304,1,now(),now())");
        jdbc.update("insert into script_episode (id,tenant_id,project_id,script_id,stable_key,episode_no,title,content,content_fingerprint,reconciliation_status,status,created_at,updated_at) values (96305,96301,96302,96303,'ep',1,'第一集','王大山与李明各拿起一把炒勺，办公桌摆在办公室。','hash','NEW','ACTIVE',now(),now())");
        for(long id:new long[]{96311,96312,96313,96314}) jdbc.update("insert into prop_asset (id,tenant_id,project_id,script_id,name,prop_type,source,status,prompt,appearance,created_by,created_at,updated_at) values (?,96301,96302,96303,?,'KEY_PROP','AI','DRAFT','参考图提示词','普通外观',1,now(),now())",id,id==96311?"王大山的炒勺":id==96312?"李明的炒勺":id==96313?"办公桌":"铁锅");
        jdbc.update("insert into scene_asset (id,tenant_id,project_id,script_id,name,scene_type,source,status,prompt,created_by,created_at,updated_at) values (96320,96301,96302,96303,'办公室','INTERIOR','AI','DRAFT','办公室场景',1,now(),now())");
        for(long id:new long[]{96311,96312}) jdbc.update("insert into asset_visual_variant (id,tenant_id,project_id,asset_type,asset_id,name,source_type,generation_status,current_image_url,is_primary,created_by,created_at,updated_at) values (?,96301,96302,'PROP',?,'默认外观','AI','COMPLETED',?,true,1,now(),now())",id,id,"https://example.test/"+id+".png");
        var permissions=mock(ProjectPermissionGuard.class);
        when(permissions.require(anyLong(),anyLong(),anyString())).thenReturn(new com.antshorttv.security.TenantContext(1L,96301L,1L,"ADMIN"));
        runner=mock(WorkflowAgentRunner.class);executions=mock(AiExecutionService.class);
        service=new PropSimplificationService(jdbc,json,transactions,runner,permissions,mock(ScriptAiOperationService.class),mock(AssetNormalizationGuard.class),production,executions,mock(AiExecutionResponseMapper.class));
        JsonNode input=service.capture(96301,96302,96303);
        jdbc.update("insert into prop_simplification_plan (id,tenant_id,project_id,script_id,script_version_id,execution_id,input_fingerprint,input_json,decision_json,status,created_by,created_at,updated_at) values (96390,96301,96302,96303,96304,96399,?,?,?,'READY',1,now(),now())",EpisodePromptContextFactory.hash(write(input)),write(input),write(decision()));
        plan=96390;
    }
    private ObjectNode decision() {
        try{return (ObjectNode)json.readTree("""
            {"groups":[
              {"name":"普通炒勺","mode":"SHARED","targetId":96311,"memberIds":[96311,96312],"sceneIds":[],"variants":[],"reason":"同款复用","evidence":[{"episodeId":96305,"quote":"各拿起一把炒勺"}]},
              {"name":"办公桌","mode":"SCENE","targetId":null,"memberIds":[96313],"sceneIds":[96320],"variants":[],"reason":"场景固定家具","evidence":[{"episodeId":96305,"quote":"办公桌摆在办公室"}]},
              {"name":"铁锅","mode":"INDEPENDENT","targetId":96314,"memberIds":[96314],"sceneIds":[],"variants":[],"reason":"保留独特外观","evidence":[]}]}
            """);}catch(Exception e){throw new IllegalStateException(e);}
    }
    @Test void singleAnalysisSavesAReviewPlanWithoutApplyingProductionChanges() {
        var task=new AiExecutionTaskEntity();task.id=96398L;task.tenantId=96301L;task.projectId=96302L;
        task.userId=1L;task.executionVersion=1;task.status="RUNNING";task.claimExpiresAt=java.time.LocalDateTime.now().plusMinutes(5);
        var context=new AiExecutionContext(task,new AiExecutionClaim(task.id,96397L,"claim",1,"RUNNING"));
        var operation=new ScriptAiOperationEntity();operation.id=96396L;operation.tenantId=96301L;operation.projectId=96302L;
        operation.scriptId=96303L;operation.scriptVersionId=96304L;
        when(executions.requireTaskForUpdate(96398L)).thenReturn(task);
        when(runner.runFormal(any())).thenAnswer(call -> {
            com.antshorttv.workflowagent.run.WorkflowAgentRunInput input=call.getArgument(0);
            assertThat(input.input()).contains("各拿起一把炒勺","96311","96312");
            var tools=new com.antshorttv.workflowagent.tool.ToolExecutionContext(96301L,1L,96302L,null,96303L,96396L,null,1L,
                96398L,96397L,1,java.util.Set.of(),null,new com.antshorttv.workflowagent.tool.WorkflowToolRunState());
            return new com.antshorttv.workflowagent.run.WorkflowAgentRunResult(1L,service.save(tools,decision()).toString());
        });
        var result=service.analyze(operation,context);
        assertThat(result.resultType()).isEqualTo("PROP_SIMPLIFICATION");
        assertThat(jdbc.queryForObject("select status from prop_simplification_plan where execution_id=96398",String.class)).isEqualTo("READY");
        assertThat(jdbc.queryForObject("select count(*) from prop_production_policy",Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from prop_asset where project_id=96302 and deleted_at is null",Long.class)).isEqualTo(4);
        verify(runner,times(1)).runFormal(any());
    }
    @Test void appliesProductionReuseWithoutDeletingSourceAssetsAndUndoes() {
        service.apply(96301,96302,plan,decision());
        assertThat(jdbc.queryForObject("select count(*) from prop_asset where project_id=96302 and deleted_at is null",Long.class)).isEqualTo(4);
        assertThat(production.productionIds(96301,96302,java.util.List.of(96311L,96312L,96313L,96314L))).containsExactly(96311L,96314L);
        assertThat(production.target(96301,96302,96312,null,null).variantId()).isEqualTo(96311);
        assertThat(production.sceneContext(96301,96302,96320L)).contains("办公桌");
        assertThat(production.sceneContext(96301,96302,999L)).isEmpty();
        assertThat(production.catalog(96301,96302).path("needsReview").asBoolean()).isFalse();
        service.undo(96301,96302,plan);
        assertThat(production.policy(96301,96302,96312)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from asset_visual_variant where project_id=96302",Long.class)).isEqualTo(2);
    }
    @Test void rejectsChangedInventoryWithoutPartialApplication() {
        jdbc.update("update prop_asset set name='用户修改了名称' where id=96312");
        assertThatThrownBy(()->service.apply(96301,96302,plan,decision())).hasMessageContaining("已变化");
        assertThat(jdbc.queryForObject("select count(*) from prop_production_policy",Long.class)).isZero();
    }
    @Test void isolatesProjectAndTenant() {
        assertThatThrownBy(()->service.apply(96301,999,plan,decision())).hasMessageContaining("导入剧本");
        service.apply(96301,96302,plan,decision());
        assertThat(production.policy(999,96302,96312)).isEmpty();
        assertThat(production.policy(96301,999,96312)).isEmpty();
    }
    @Test void preservesDistinctStateImagesAndNeverFallsBackForUnknownState() {
        ObjectNode value=decision();ObjectNode group=(ObjectNode)value.path("groups").get(0);group.put("mode","VARIANTS");
        var versions=group.putArray("variants");
        for(long id:new long[]{96311,96312}) {
            var v=versions.addObject().put("name",id==96311?"录音界面":"转账界面").put("appearance","特写屏幕").put("prompt","手机屏幕特写提示词");
            v.putArray("sourceVariantIds").add(id);v.putArray("sourceAssetIds").add(id);
        }
        service.apply(96301,96302,plan,value);
        var first=production.target(96301,96302,96311,null,null);var second=production.target(96301,96302,96312,null,null);
        assertThat(first.variantId()).isNotEqualTo(second.variantId());
        assertThat(production.target(96301,96302,96312,999L,null).unresolved()).isTrue();
        assertThat(jdbc.queryForObject("select current_image_url from asset_visual_variant where id=?",String.class,second.variantId())).contains("96312");
        assertThat(jdbc.queryForObject("select asset_id from asset_visual_variant where id=96312",Long.class)).isEqualTo(96312);
        var resolver=new EpisodeAwareVisualResolver(jdbc);
        org.springframework.test.util.ReflectionTestUtils.setField(resolver,"propProduction",production);
        assertThat(resolver.resolve(96301L,96302L,"PROP",96312L,null).imageUrl()).contains("96312");
    }
    @Test void stopsUsingStaleReuseAndRequestsReviewAfterSourceChanges() {
        service.apply(96301,96302,plan,decision());
        jdbc.update("update prop_asset set appearance='特制金色炒勺' where id=96312");
        assertThat(production.policy(96301,96302,96312)).isEmpty();
        assertThat(production.catalog(96301,96302).path("needsReview").asBoolean()).isTrue();
        assertThat(production.productionIds(96301,96302,java.util.List.of(96311L,96312L))).containsExactly(96311L,96312L);
    }
    private String write(Object value) {try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
}
