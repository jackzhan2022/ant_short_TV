package com.antshorttv.script;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.antshorttv.execution.*;
import com.antshorttv.workflowagent.run.WorkflowAgentRunner;
import com.antshorttv.workflowagent.tool.*;
import com.fasterxml.jackson.databind.*;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AssetNormalizationServiceTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager transactions;
    private AssetNormalizationService service;
    private AssetExtractionCoordination coordination;
    private WorkflowAgentRunner runner;
    private AiExecutionContext execution;
    private ToolExecutionContext tools;

    @BeforeEach void seed() {
        jdbc.update("insert into tenant (id,code,name,type,status,created_at,updated_at) values (9801,'normalize-test','归一','TEAM','ACTIVE',now(),now())");
        jdbc.update("insert into project (id,tenant_id,name,code,owner_id,status,created_by,created_at,updated_at) values (9802,9801,'匠心匠心','normalize-test',1,'ACTIVE',1,now(),now())");
        jdbc.update("insert into script (id,tenant_id,project_id,title,source_type,content,status,current_version_id,created_by,created_at,updated_at) values (9803,9801,9802,'匠心匠心','MANUAL_EDIT','原文','ACTIVE',9804,1,now(),now())");
        for(long ep:new long[]{1,7,11,30}) jdbc.update("insert into script_episode (id,tenant_id,project_id,script_id,stable_key,episode_no,title,content,content_fingerprint,reconciliation_status,status,created_at,updated_at) values (?,9801,9802,9803,?,?,'Episode',?,'hash','NEW','ACTIVE',now(),now())",9800+ep,"ep"+ep,ep,ep==11?"陈志国背着自己的挎包。":ep==1?"林嘉欣拿起自己的挎包。":"林嘉欣母亲也叫林嘉欣妈妈，每月要七千元。");
        for(long id:new long[]{747,757}) jdbc.update("insert into character_asset (id,tenant_id,project_id,script_id,name,role_type,source,status,content_json,created_by,created_at,updated_at) values (?,9801,9802,9803,?,'SUPPORTING','AI','CONFIRMED','{}',1,now(),now())",id,id==747?"林嘉欣母亲":"林嘉欣妈妈");
        jdbc.update("insert into prop_asset (id,tenant_id,project_id,script_id,name,prop_type,source,status,content_json,created_by,created_at,updated_at) values (1511,9801,9802,9803,'挎包','KEY_PROP','AI','CONFIRMED','{}',1,now(),now())");
        for(long id:new long[]{747,757,1511}) jdbc.update("insert into asset_visual_variant (id,tenant_id,project_id,asset_type,asset_id,name,source_type,generation_status,current_image_url,is_primary,created_by,created_at,updated_at) values (?,9801,9802,?,?,'默认形态','AI','COMPLETED',?,true,1,now(),now())",id,id==1511?"PROP":"CHARACTER",id,"image-"+id);
        bind(1,"CHARACTER",747,7); bind(2,"CHARACTER",757,30); bind(3,"PROP",1511,1); bind(4,"PROP",1511,11);
        bind(5,"CHARACTER",757,7);
        jdbc.update("insert into storyboard (id,tenant_id,project_id,script_id,episode_id,episode_no,shot_no,visual_description,status,created_by,created_at,updated_at) values (9850,9801,9802,9803,9811,11,1,'shot','DRAFT',1,now(),now())");
        jdbc.update("insert into storyboard_asset_reference (tenant_id,project_id,storyboard_id,asset_type,asset_id,variant_id,reference_role,sort_order,resolution_status,source_type,created_by,created_at,updated_at) values (9801,9802,9850,'PROP',1511,1511,'PROP',1,'RESOLVED','AI',1,now(),now())");
        coordination=mock(AssetExtractionCoordination.class); runner=mock(WorkflowAgentRunner.class);
        service=new AssetNormalizationService(jdbc,json,coordination,transactions,runner);
        var task=new AiExecutionTaskEntity(); task.id=9890L; task.tenantId=9801L; task.projectId=9802L; task.executionVersion=1;
        execution=new AiExecutionContext(task,new AiExecutionClaim(9890L,9891L,"claim",1,"RUNNING"));
        tools=new ToolExecutionContext(9801L,1L,9802L,null,9803L,1L,null,null,9890L,9891L,1,Set.of(),null,new WorkflowToolRunState());
    }
    private void bind(long id,String type,long asset,long episode) {
        jdbc.update("insert into asset_visual_variant_episode (id,tenant_id,project_id,script_id,episode_id,asset_type,asset_id,variant_id,is_preferred,binding_status,created_by,created_at,updated_at) values (?,9801,9802,9803,?,?,?,?,true,'ACTIVE',1,now(),now())",id,9800+episode,type,asset,asset);
    }
    private JsonNode decision() throws Exception { return json.readTree("""
        {"groups":[{"assetType":"CHARACTER","retainId":747,"memberIds":[747,757],"evidence":[
          {"episodeId":9807,"quote":"林嘉欣母亲也叫林嘉欣妈妈"},{"episodeId":9830,"quote":"林嘉欣母亲也叫林嘉欣妈妈"}]},
          {"assetType":"PROP","retainId":1511,"memberIds":[1511],"evidence":[]}],
         "splits":[{"assetType":"PROP","sourceId":1511,"newName":"陈志国的挎包","bindingIds":[4],"evidence":[{"episodeId":9811,"quote":"陈志国背着自己的挎包"}]}]}
        """); }
    @Test void mergesAndSplitsAtomicallyPreservingImagesBindingsAndRedirects() throws Exception {
        long snapshot=service.prepare(9803,9804,execution);
        JsonNode result=service.publish(tools,decision());
        assertThat(result.path("mergedAssets").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select asset_id from asset_visual_variant where id=757",Long.class)).isEqualTo(747);
        assertThat(jdbc.queryForObject("select current_image_url from asset_visual_variant where id=757",String.class)).isEqualTo("image-757");
        assertThat(AssetNormalizationGuard.canonicalId(jdbc,9801,9802,"CHARACTER",757)).isEqualTo(747);
        long split=jdbc.queryForObject("select asset_id from asset_visual_variant_episode where id=4",Long.class);
        assertThat(split).isNotEqualTo(1511);
        assertThat(jdbc.queryForObject("select asset_id from asset_visual_variant_episode where id=3",Long.class)).isEqualTo(1511);
        assertThat(jdbc.queryForObject("select asset_id from storyboard_asset_reference where storyboard_id=9850",Long.class)).isEqualTo(split);
        assertThat(jdbc.queryForObject("select current_image_url from asset_visual_variant where asset_type='PROP' and asset_id=?",String.class,split)).isEqualTo("image-1511");
        assertThat(service.publish(tools,decision()).toString()).isEqualTo(result.toString());
        assertThat(service.normalize(9803,9804,execution).snapshotId()).isEqualTo(snapshot);
        verifyNoInteractions(runner);
    }
    @Test void rejectsStaleInputBeforeAnyMerge() throws Exception {
        service.prepare(9803,9804,execution);
        jdbc.update("update prop_asset set name='用户修改' where id=1511");
        assertThatThrownBy(()->service.publish(tools,decision())).isInstanceOf(com.antshorttv.common.BusinessException.class).hasMessageContaining("已变化");
        assertThat(jdbc.queryForObject("select merge_target_id from character_asset where id=757",Long.class)).isNull();
        assertThatThrownBy(()->service.normalize(9803,9804,execution)).hasMessageContaining("输入已变化");
        verifyNoInteractions(runner);
    }
    @Test void lostClaimCannotPublish() throws Exception {
        service.prepare(9803,9804,execution);
        doThrow(new AiExecutionClaimLostException(9890L)).when(coordination).requireOwned(tools);
        assertThatThrownBy(()->service.publish(tools,decision())).isInstanceOf(AiExecutionClaimLostException.class);
    }
    @Test void inventoryContainsAllAssetsWithoutImagePrompts() {
        service.prepare(9803,9804,execution);
        JsonNode inventory=service.inventory(tools);
        assertThat(inventory.path("assets").size()).isEqualTo(3);
        assertThat(inventory.path("variants").size()).isEqualTo(3);
        assertThat(inventory.toString()).doesNotContain("image-", "current_image_url");
    }

    @Test void deletedGeneratingVariantDoesNotBlockNormalization() {
        jdbc.update("update asset_visual_variant set deleted_at=now(),generation_status='GENERATING' where id=757");
        assertThat(service.prepare(9803,9804,execution)).isPositive();
        assertThat(service.inventory(tools).path("variants").size()).isEqualTo(2);
    }

    @Test void recoversCommittedExtractionEvenWhenRunDidNotFinishButOnlyForSameExecution() {
        long model=jdbc.queryForObject("select min(id) from ai_model",Long.class);
        jdbc.update("insert into ai_workflow_agent_run (id,agent_code,run_type,tenant_id,user_id,project_id,script_id,episode_id,task_id,status,model_id,temperature,max_tokens,max_steps,prompt_snapshot,started_at,created_at) values (9895,'short-drama-asset-recognition','FORMAL',9801,1,9802,9803,9811,9896,'RUNNING',?,0.1,1000,10,'prompt',now(),now())",model);
        jdbc.update("insert into ai_call_log (id,tenant_id,user_id,service_type,business_scene,status,duration_ms,execution_id,created_at) values (9897,9801,1,'TEXT','script_character_scene_recognition','SUCCESS',1,9890,now())");
        jdbc.update("insert into ai_workflow_agent_run_step (run_id,step_no,step_type,status,ai_call_log_id,started_at,created_at) values (9895,1,'MODEL','SUCCESS',9897,now(),now())");
        jdbc.update("insert into script_episode_asset_analysis (tenant_id,project_id,script_id,episode_id,schema_version,content_fingerprint,content_json,generated_by_run_id,created_by,updated_by,created_at,updated_at) values (9801,9802,9803,9811,1,'hash','{}',9895,1,1,now(),now())");
        var operation=new ScriptAiOperationEntity(); operation.id=9896L; operation.tenantId=9801L; operation.projectId=9802L; operation.scriptId=9803L;
        assertThat(service.committedEpisodeAssetRun(operation,9811,execution)).isEqualTo(9895);
        execution.task().id=9899L;
        assertThat(service.committedEpisodeAssetRun(operation,9811,execution)).isNull();
    }
}
