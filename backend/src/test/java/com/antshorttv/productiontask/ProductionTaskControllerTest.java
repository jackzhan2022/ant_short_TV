package com.antshorttv.productiontask;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.antshorttv.support.SessionTestSupport.authenticated;
import com.antshorttv.support.SessionTestSupport;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ProductionTaskControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.antshorttv.rbac.RbacService rbac;
    @Autowired com.antshorttv.execution.AiExecutionService executions;
    @Test void supportsJdbcBooleanAndNumericUnionRepresentations() {
        org.assertj.core.api.Assertions.assertThat(ProductionTaskService.truth(true)).isTrue();
        org.assertj.core.api.Assertions.assertThat(ProductionTaskService.truth(1)).isTrue();
        org.assertj.core.api.Assertions.assertThat(ProductionTaskService.truth(1L)).isTrue();
        org.assertj.core.api.Assertions.assertThat(ProductionTaskService.truth(0)).isFalse();
        org.assertj.core.api.Assertions.assertThat(ProductionTaskService.truth(null)).isFalse();
    }

    @Test void preservesIndependentCreatorWhenAnotherMembersBatchReusesExecution() throws Exception {
        String first=register("13800019807"), second=register("13800019808");
        long tenant=tenant(first), a=user("13800019807"), b=user("13800019808");
        jdbc.update("insert into tenant_member(tenant_id,user_id,member_type,status,joined_at,created_at,updated_at) values (?,?,'MEMBER','ACTIVE',now(),now(),now())",tenant,b);
        jdbc.update("insert into project(tenant_id,name,code,owner_id,status,created_by,created_at,updated_at) values (?,'shared','shared',?,'ACTIVE',?,now(),now())",tenant,a,a);
        long project=jdbc.queryForObject("select id from project where tenant_id=?",Long.class,tenant);
        var execution=executions.create(new com.antshorttv.execution.AiExecutionCreateCommand(tenant,a,project,"storyboard_breakdown","TEXT","SCRIPT_AI_OPERATION",1L,null,"SUBMIT","shared-center","trace",true,"{}"));
        jdbc.update("insert into script_ai_operation(tenant_id,project_id,operation_type,redacted_input_json,idempotency_key,status,execution_id,created_by,created_at,updated_at) values (?,?,'STORYBOARD_BREAKDOWN','{}','shared','PENDING',?,?,now(),now())",tenant,project,execution.id,a);
        jdbc.update("insert into storyboard_batch(tenant_id,project_id,script_id,name,idempotency_key,created_by,created_at) values (?,?,1,'shared batch','shared',?,now())",tenant,project,b);
        long batch=jdbc.queryForObject("select id from storyboard_batch where tenant_id=?",Long.class,tenant);
        jdbc.update("insert into storyboard_batch_item(batch_id,tenant_id,project_id,episode_id,episode_no,execution_id,created_at) values (?,?,?,1,1,?,now())",batch,tenant,project,execution.id);
        String base="/api/tenants/"+tenant+"/production-tasks";
        mvc.perform(get(base).with(authenticated(first))).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].type").value("SCRIPT_OPERATION"));
        mvc.perform(get(base+"/STORYBOARD_BATCH:"+batch+"/children").with(authenticated(second))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].creatorId").value(b)).andExpect(jsonPath("$.data.items[0].allowedActions.length()").value(0));
        jdbc.update("update storyboard_batch set created_by=? where id=?",a,batch);
        mvc.perform(get(base).with(authenticated(first))).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].type").value("STORYBOARD_BATCH"));
        mvc.perform(get(base+"/STORYBOARD_BATCH:"+batch+"/children").with(authenticated(first))).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].allowedActions").value(org.hamcrest.Matchers.hasItem("CANCEL")));
        long operation=jdbc.queryForObject("select id from script_ai_operation where execution_id=?",Long.class,execution.id);
        mvc.perform(get(base+"/SCRIPT_OPERATION:"+operation).with(authenticated(first))).andExpect(status().isOk());
        jdbc.update("update ai_execution_task set status='SUCCEEDED',completed_at=now() where id=?",execution.id);
        long provider=jdbc.queryForObject("select min(id) from ai_provider",Long.class);
        jdbc.update("insert into ai_model(provider_id,code,name,model_code,service_type,status,is_default,sort,created_at,updated_at) values (?,'CENTER_WARNING','warning','test','TEXT','ENABLED',false,1,now(),now())",provider);
        long model=jdbc.queryForObject("select id from ai_model where code='CENTER_WARNING'",Long.class);
        jdbc.update("insert into ai_workflow_agent_run(agent_code,run_type,tenant_id,user_id,project_id,status,model_id,temperature,max_tokens,max_steps,prompt_snapshot,started_at,created_at) values ('test','PRODUCTION',?,?,?,'SUCCEEDED',?,0,100,1,'secret',now(),now())",tenant,a,project,model);
        long run=jdbc.queryForObject("select id from ai_workflow_agent_run where tenant_id=?",Long.class,tenant);
        jdbc.update("insert into ai_call_log(tenant_id,user_id,service_type,business_scene,status,duration_ms,created_at,execution_id) values (?,?,'TEXT','storyboard_breakdown','SUCCEEDED',1,now(),?)",tenant,a,execution.id);
        long call=jdbc.queryForObject("select id from ai_call_log where execution_id=?",Long.class,execution.id);
        jdbc.update("insert into ai_workflow_agent_run_step(run_id,step_no,step_type,status,ai_call_log_id,started_at,created_at) values (?,1,'MODEL','SUCCEEDED',?,now(),now())",run,call);
        jdbc.update("insert into storyboard(tenant_id,project_id,episode_no,shot_no,visual_description,status,created_by,created_at,updated_at,generated_by_run_id,shot_plan_json) values (?,?,1,1,'secret body','ACTIVE',?,now(),now(),?,?)",tenant,project,a,run,"{\"warnings\":[\"quality warning\"]}");
        mvc.perform(get(base+"/STORYBOARD_BATCH:"+batch).with(authenticated(first))).andExpect(status().isOk()).andExpect(jsonPath("$.data.statusGroup").value("SUCCEEDED")).andExpect(jsonPath("$.data.warningSummary").isNotEmpty());
        jdbc.update("update storyboard set shot_plan_json=? where tenant_id=?","{\"warnings\": [ ]}",tenant);
        mvc.perform(get(base+"/STORYBOARD_BATCH:"+batch).with(authenticated(first))).andExpect(status().isOk()).andExpect(jsonPath("$.data.warningSummary").isEmpty());
    }

    @Test void pagesMixedPersistedSourcesWithoutLeakingBodiesOrDeadResultLinks() throws Exception {
        String token=register("13800019810");long tenant=tenant(token),user=user("13800019810");
        jdbc.update("insert into project(tenant_id,name,code,owner_id,status,created_by,created_at,updated_at) values (?,'mixed','mixed',?,'ACTIVE',?,now(),now())",tenant,user,user);
        long project=jdbc.queryForObject("select id from project where tenant_id=?",Long.class,tenant);
        jdbc.update("insert into ai_image_task(tenant_id,project_id,task_type,target_type,target_id,provider_code,model,prompt,aspect_ratio,image_count,status,created_by,created_at,updated_at) values (?,?,'CHARACTER','CHARACTER',999999,'test','test','private prompt','1:1',1,'SUCCEEDED',?,now(),now())",tenant,project,user);
        jdbc.update("insert into ai_video_task(tenant_id,project_id,storyboard_id,provider_code,model,prompt,first_frame_url,duration_seconds,aspect_ratio,status,created_by,created_at,updated_at) values (?,?,999999,'test','test','private video','private url',5,'1:1','RUNNING',?,now(),now())",tenant,project,user);
        jdbc.update("insert into script_analysis_task(tenant_id,project_id,script_id,script_version_id,workflow_code,status,overall_progress,idempotency_key,created_by,created_at,updated_at) values (?,?,1,1,'test','FAILED',45,'mixed',?,now(),now())",tenant,project,user);
        jdbc.update("insert into review_project(tenant_id,name,source_type,original_content,status,created_by,created_at,updated_at) values (?,'review','TEXT','private review','ACTIVE',?,now(),now())",tenant,user);
        long reviewProject=jdbc.queryForObject("select id from review_project where tenant_id=?",Long.class,tenant);
        jdbc.update("insert into review_task(tenant_id,project_id,script_version_id,round_no,review_mode,selected_dimensions_json,review_scope_type,status,overall_progress,idempotency_key,created_by,created_at,updated_at) values (?,?,1,1,'QUICK','[]','ALL','COMPLETED',100,'mixed',?,now(),now())",tenant,reviewProject,user);
        String base="/api/tenants/"+tenant+"/production-tasks";
        mvc.perform(get(base).param("pageSize","2").param("createdFrom","2000-01-01T00:00").with(authenticated(token))).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(4)).andExpect(jsonPath("$.data.items.length()").value(2)).andExpect(jsonPath("$.data.items[0].type").value("VIDEO"));
        mvc.perform(get(base+"/summary").with(authenticated(token))).andExpect(status().isOk()).andExpect(jsonPath("$.data.counts.SUCCEEDED").value(2)).andExpect(jsonPath("$.data.counts.FAILED").value(1));
        mvc.perform(get(base).param("type","IMAGE").with(authenticated(token))).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].destination").isEmpty()).andExpect(jsonPath("$.data.items[0].progress").isEmpty()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private prompt"))));
        var sql=new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc);
        String plan=sql.queryForObject("explain select * from ("+ProductionTaskSources.ALL+") t where t.parent_id is null and t.root_visible=true order by t.created_at desc limit 20",java.util.Map.of("tenant",tenant),String.class);
        org.assertj.core.api.Assertions.assertThat(plan.toLowerCase()).contains("tenant_id", "index", "fetch first 20");
    }

    @Test void mapsOnlyGenuineUserGatesAndBoundsMixedHistory() throws Exception {
        String token=register("13800019809");long tenant=tenant(token),user=user("13800019809");
        long batch=batch(tenant,user,"history");
        for(int i=1;i<=105;i++) jdbc.update("insert into video_decomposition_episode(batch_id,tenant_id,episode_no,source_file_name,storage_path,file_size,status,analysis_version,draft_version,created_by,created_at,updated_at) values (?,?,?,'clip.mp4','test',1,?,1,1,?,now(),now())",batch,tenant,i,i==1?"PENDING_REVIEW":i==2?"LEGACY_UNKNOWN":"SUCCEEDED",user);
        String base="/api/tenants/"+tenant+"/production-tasks/VIDEO_DECOMPOSITION:"+batch+"/children";
        mvc.perform(get(base).param("pageSize","999").with(authenticated(token))).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(100)).andExpect(jsonPath("$.data.total").value(105));
        mvc.perform(get(base).param("statusGroup","WAITING_USER").with(authenticated(token))).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].domainStatus").value("PENDING_REVIEW"));
        mvc.perform(get(base).param("statusGroup","FAILED").with(authenticated(token))).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].domainStatus").value("LEGACY_UNKNOWN"));
    }

    @Test void aggregatesBatchOutcomesAndPagesChildrenWithoutExecutionRecords() throws Exception {
        String owner=register("13800019803");long user=user("13800019803"), tenant=tenant(owner);
        long batch=batch(tenant,user,"mixed batch");
        for(int i=1;i<=3;i++) jdbc.update("""
            insert into video_decomposition_episode (batch_id,tenant_id,project_id,episode_no,source_file_name,
            storage_path,file_size,status,analysis_version,draft_version,created_by,created_at,updated_at)
            values (?,?,null,?,'clip.mp4','test',1,?,1,1,?,now(),now())
            """,batch,tenant,i,List.of("SUCCEEDED","FAILED","CANCELED").get(i-1),user);
        String base="/api/tenants/"+tenant+"/production-tasks";
        mvc.perform(get(base).with(authenticated(owner))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].statusGroup").value("PARTIAL"))
            .andExpect(jsonPath("$.data.items[0].childCounts.total").value(3)).andExpect(jsonPath("$.data.items[0].childCounts.success").value(1))
            .andExpect(jsonPath("$.data.items[0].childCounts.failed").value(1)).andExpect(jsonPath("$.data.items[0].childCounts.canceled").value(1));
        mvc.perform(get(base+"/VIDEO_DECOMPOSITION:"+batch+"/children").param("pageSize","2").with(authenticated(owner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3)).andExpect(jsonPath("$.data.items.length()").value(2));
        mvc.perform(get(base).param("statusGroup","FAILED").with(authenticated(owner))).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get(base+"/summary").param("statusGroup","PARTIAL").with(authenticated(owner))).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        jdbc.update("update video_decomposition_batch set deleted_at=now() where id=?",batch);
        mvc.perform(get(base+"/VIDEO_DECOMPOSITION:"+batch+"/children").with(authenticated(owner))).andExpect(status().isForbidden());
    }

    @Test void returnsRestrictedOwnProjectTasksAndRejectsForgedTeamIdentity() throws Exception {
        String token=register("13800019804");long user=user("13800019804"),tenant=tenant(token);
        long batch=batch(tenant,user,"secret project title");
        jdbc.update("update video_decomposition_batch set project_id=999999 where id=?",batch);
        String base="/api/tenants/"+tenant+"/production-tasks";
        mvc.perform(get(base).with(authenticated(token))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].restricted").value(true))
            .andExpect(jsonPath("$.data.items[0].projectId").isEmpty()).andExpect(jsonPath("$.data.items[0].destination").isEmpty())
            .andExpect(jsonPath("$.data.items[0].allowedActions.length()").value(0));
        jdbc.update("update tenant_member set member_type='MEMBER' where tenant_id=? and user_id=?",tenant,user);
        rbac.initializeTenant(tenant);
        Long member=jdbc.queryForObject("select id from tenant_member where tenant_id=? and user_id=?",Long.class,tenant,user);
        Long role=jdbc.queryForObject("select id from role where tenant_id=? and code='ADMIN'",Long.class,tenant);
        jdbc.update("update role set role_type='CUSTOM' where id=?",role);
        jdbc.update("insert into member_role(member_id,role_id,created_at) values (?,?,now())",member,role);
        mvc.perform(get(base).param("scope","team").with(authenticated(token))).andExpect(status().isForbidden());
        long other=tenant(register("13800019805"));
        mvc.perform(get("/api/tenants/"+other+"/production-tasks/VIDEO_DECOMPOSITION:"+batch).with(authenticated(token))).andExpect(status().isForbidden());
        jdbc.update("update tenant_member set status='REMOVED' where id=?",member);
        mvc.perform(get(base).with(authenticated(token))).andExpect(status().isForbidden());
    }

    @Test void controlsOwnScriptOperationAndPreservesBusinessIdentityOnRetry() throws Exception {
        String token=register("13800019806");long user=user("13800019806"),tenant=tenant(token);
        jdbc.update("insert into project(tenant_id,name,code,owner_id,status,created_by,created_at,updated_at) values (?,'test','test',?,'ACTIVE',?,now(),now())",tenant,user,user);
        long project=jdbc.queryForObject("select id from project where tenant_id=?",Long.class,tenant);
        var execution=executions.create(new com.antshorttv.execution.AiExecutionCreateCommand(tenant,user,project,"script_generate","TEXT","SCRIPT_AI_OPERATION",1L,null,"SUBMIT","center-test","trace",true,"{}"));
        jdbc.update("insert into script_ai_operation(tenant_id,project_id,operation_type,redacted_input_json,idempotency_key,status,execution_id,created_by,created_at,updated_at) values (?,?,'SCRIPT_GENERATE','secret','test','PENDING',?,?,now(),now())",tenant,project,execution.id,user);
        long operation=jdbc.queryForObject("select id from script_ai_operation where execution_id=?",Long.class,execution.id);
        String path="/api/tenants/"+tenant+"/production-tasks/SCRIPT_OPERATION:"+operation;
        mvc.perform(post(path+"/cancel").with(authenticated(token))).andExpect(status().isOk()).andExpect(jsonPath("$.data.statusGroup").value("CANCELED"));
        jdbc.update("update ai_execution_task set status='FAILED',retryable=true where id=?",execution.id);
        mvc.perform(post(path+"/retry").with(authenticated(token))).andExpect(status().isOk()).andExpect(jsonPath("$.data.taskKey").value("SCRIPT_OPERATION:"+operation)).andExpect(jsonPath("$.data.statusGroup").value("QUEUED"));
        mvc.perform(post(path+"/retry").with(authenticated(token))).andExpect(status().isForbidden());
        String adminToken=register("13800019811");long adminUser=user("13800019811");
        jdbc.update("insert into tenant_member(tenant_id,user_id,member_type,status,joined_at,created_at,updated_at) values (?,?,'MEMBER','ACTIVE',now(),now(),now())",tenant,adminUser);
        rbac.initializeTenant(tenant);
        long adminRole=jdbc.queryForObject("select id from role where tenant_id=? and code='ADMIN'",Long.class,tenant);
        long adminMember=jdbc.queryForObject("select id from tenant_member where tenant_id=? and user_id=?",Long.class,tenant,adminUser);
        jdbc.update("insert into member_role(member_id,role_id,created_at) values (?,?,now())",adminMember,adminRole);
        mvc.perform(get(path).with(authenticated(adminToken))).andExpect(status().isOk()).andExpect(jsonPath("$.data.allowedActions").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("CANCEL"))));
        mvc.perform(post(path+"/cancel").with(authenticated(adminToken))).andExpect(status().isForbidden());
        jdbc.update("update tenant_member set member_type='MEMBER' where tenant_id=? and user_id=?",tenant,user);
        jdbc.update("delete from member_role where member_id in (select id from tenant_member where tenant_id=? and user_id=?)",tenant,user);
        mvc.perform(post(path+"/cancel").with(authenticated(token))).andExpect(status().isForbidden());
    }

    @Test void listQueryCountDoesNotGrowWithPageRows() throws Exception {
        String token=register("13800019812");long tenant=tenant(token),user=user("13800019812");
        for(int i=0;i<30;i++) batch(tenant,user,"bounded-"+i);
        String base="/api/tenants/"+tenant+"/production-tasks";
        mvc.perform(get(base).with(authenticated(token))).andExpect(status().isOk());
        jdbc.execute("set query_statistics true");
        try {
            long before=queryCount();
            mvc.perform(get(base).param("pageSize","1").with(authenticated(token))).andExpect(status().isOk());
            long middle=queryCount();
            mvc.perform(get(base).param("pageSize","20").with(authenticated(token))).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(20));
            long after=queryCount();
            org.assertj.core.api.Assertions.assertThat(after-middle).isLessThanOrEqualTo(middle-before+2);
        } finally {jdbc.execute("set query_statistics false");}
    }
    private long queryCount() {return jdbc.queryForObject("select coalesce(sum(execution_count),0) from information_schema.query_statistics",Long.class);}

    @Test void scopesListsDetailsAndControlsByCurrentMembership() throws Exception {
        String owner = register("13800019801");
        String member = register("13800019802");
        long tenant = tenant(owner);
        long ownerId = user("13800019801"), memberId = user("13800019802");
        jdbc.update("insert into tenant_member (tenant_id,user_id,member_type,status,joined_at,created_at,updated_at) values (?,?,'MEMBER','ACTIVE',now(),now(),now())",tenant,memberId);
        long ownTask = batch(tenant,ownerId,"owner batch"), memberTask = batch(tenant,memberId,"member batch");
        String base = "/api/tenants/"+tenant+"/production-tasks";
        mvc.perform(get(base)).andExpect(status().isUnauthorized());
        mvc.perform(get(base).with(authenticated(member)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.items[0].taskKey").value("VIDEO_DECOMPOSITION:"+memberTask));
        mvc.perform(get(base).param("scope","team").with(authenticated(member))).andExpect(status().isForbidden());
        mvc.perform(get(base+"/VIDEO_DECOMPOSITION:"+ownTask).with(authenticated(member))).andExpect(status().isForbidden());
        mvc.perform(get(base).param("creatorId",""+ownerId).with(authenticated(member))).andExpect(status().isForbidden());
        mvc.perform(get(base).param("scope","team").with(authenticated(owner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
            .andExpect(jsonPath("$.data.canViewTeamTasks").value(true));
        mvc.perform(post(base+"/VIDEO_DECOMPOSITION:"+memberTask+"/cancel").with(authenticated(owner)))
            .andExpect(status().isForbidden());
        mvc.perform(get(base+"/summary").param("scope","team").with(authenticated(owner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2));
        Long membership = jdbc.queryForObject("select id from tenant_member where tenant_id=? and user_id=?",Long.class,tenant,memberId);
        rbac.initializeTenant(tenant);
        Long admin = jdbc.queryForObject("select id from role where tenant_id=? and code='ADMIN'",Long.class,tenant);
        jdbc.update("insert into member_role (member_id,role_id,created_at) values (?,?,now())",membership,admin);
        mvc.perform(get(base).param("scope","team").with(authenticated(member))).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2));
        jdbc.update("update role set status='DISABLED' where id=?",admin);
        mvc.perform(get(base+"/summary").param("scope","team").with(authenticated(member))).andExpect(status().isForbidden());
        jdbc.update("update tenant_member set member_type='MEMBER' where tenant_id=? and user_id=?",tenant,ownerId);
        jdbc.update("update tenant_member set member_type='OWNER' where tenant_id=? and user_id=?",tenant,memberId);
        mvc.perform(get(base).param("scope","team").with(authenticated(owner))).andExpect(status().isForbidden());
        mvc.perform(get(base).param("scope","team").with(authenticated(member))).andExpect(status().isOk());
    }

    private String register(String mobile) throws Exception {
        return SessionTestSupport.sessionCredential(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"mobile\":\""+mobile+"\",\"nickname\":\"Task tester\",\"verificationCode\":\"123456\",\"password\":\"Password123\"}"))
            .andExpect(status().isOk()).andReturn());
    }
    private long user(String mobile) { return jdbc.queryForObject("select id from app_user where mobile=?",Long.class,mobile); }
    private long tenant(String token) throws Exception {
        var result=mvc.perform(post("/api/tenants").with(authenticated(token)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Task center\",\"type\":\"STUDIO\"}")).andExpect(status().isOk()).andReturn();
        return ((Number)JsonPath.read(result.getResponse().getContentAsString(),"$.data.id")).longValue();
    }
    private long batch(long tenant,long creator,String name) {
        jdbc.update("insert into video_decomposition_batch (tenant_id,project_id,name,status,total_episodes,completed_episodes,failed_episodes,created_by,created_at,updated_at) values (?,null,?,'PENDING',0,0,0,?,now(),now())",tenant,name,creator);
        return jdbc.queryForObject("select id from video_decomposition_batch where tenant_id=? and name=?",Long.class,tenant,name);
    }
}
