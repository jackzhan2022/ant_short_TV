package com.antshorttv.productiontask;

import static com.antshorttv.support.SessionTestSupport.authenticated;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.antshorttv.support.SessionTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties="ark.avatar.worker-enabled=false")
@AutoConfigureMockMvc
class UnifiedVideoTaskTest extends com.antshorttv.support.RegistrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test void keepsOneVisibleTaskAcrossPreparationRetryAndFinalResult() throws Exception {
        String mobile="13800019681";
        String token=SessionTestSupport.sessionCredential(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"mobile\":\""+mobile+"\",\"nickname\":\"Video tester\",\"verificationCode\":\""+registrationVerificationCode(mvc,mobile)+"\",\"password\":\"Password123\"}"))
            .andExpect(status().isOk()).andReturn());
        long tenant=((Number)JsonPath.read(mvc.perform(post("/api/tenants").with(authenticated(token)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Unified video\",\"type\":\"STUDIO\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),"$.data.id")).longValue();
        long user=jdbc.queryForObject("select id from app_user where mobile=?",Long.class,mobile);
        jdbc.update("insert into project(tenant_id,name,code,owner_id,status,created_by,created_at,updated_at) values (?,'unified-video','unified-video',?,'ACTIVE',?,now(),now())",tenant,user,user);
        long project=jdbc.queryForObject("select id from project where tenant_id=?",Long.class,tenant);
        jdbc.update("insert into ai_video_task(tenant_id,project_id,storyboard_id,provider_code,model,prompt,duration_seconds,aspect_ratio,status,error_message,created_by,created_at,updated_at) values (?,?,1,'VOLCENGINE_ARK','video-model','original prompt',5,'16:9','FAILED','InputImageSensitiveContentDetected: image may contain real person',?,now(),now())",tenant,project,user);
        long source=jdbc.queryForObject("select id from ai_video_task where tenant_id=?",Long.class,tenant);
        jdbc.update("insert into seedance_avatar_recovery(source_task_id,tenant_id,project_id,storyboard_id,created_by,status,deadline_at) values (?,?,?,1,?,'PENDING',dateadd('DAY',1,current_timestamp))",source,tenant,project,user);
        String base="/api/tenants/"+tenant+"/production-tasks";
        String detail=base+"/VIDEO:"+source;
        mvc.perform(get(base).param("type","VIDEO").with(authenticated(token))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].taskKey").value("VIDEO:"+source))
            .andExpect(jsonPath("$.data.items[0].statusGroup").value("RUNNING"))
            .andExpect(jsonPath("$.data.items[0].phase").value("智能虚拟化中"));
        mvc.perform(get(base).param("type","VIDEO").param("statusGroup","FAILED").with(authenticated(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/projects/"+project+"/ai-video-tasks").header("X-Tenant-Id",tenant).param("status","GENERATING").with(authenticated(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/projects/"+project+"/ai-video-tasks/"+source).header("X-Tenant-Id",tenant).with(authenticated(token))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(source)).andExpect(jsonPath("$.data.status").value("GENERATING"))
            .andExpect(jsonPath("$.data.executionPhase").value("AVATAR_PREPARING")).andExpect(jsonPath("$.data.errorMessage").isEmpty());
        jdbc.update("insert into ai_video_task(tenant_id,project_id,storyboard_id,provider_code,model,prompt,duration_seconds,aspect_ratio,status,avatar_recovery_source_task_id,created_by,created_at,updated_at) values (?,?,1,'VOLCENGINE_ARK','video-model','original prompt',5,'16:9','GENERATING',?,?,now(),now())",tenant,project,source,user);
        long child=jdbc.queryForObject("select id from ai_video_task where avatar_recovery_source_task_id=?",Long.class,source);
        jdbc.update("update seedance_avatar_recovery set status='SUCCEEDED',retry_task_id=? where source_task_id=?",child,source);
        mvc.perform(get(base).param("type","VIDEO").with(authenticated(token))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].statusGroup").value("RUNNING"))
            .andExpect(jsonPath("$.data.items[0].phase").value("生成中"));
        mvc.perform(get(base+"/summary").param("type","VIDEO").with(authenticated(token))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.counts.RUNNING").value(1)).andExpect(jsonPath("$.data.counts.FAILED").value(0));
        jdbc.update("update ai_video_task set status='SUCCEEDED',completed_at=now() where id=?",child);
        jdbc.update("insert into ai_video_result(tenant_id,project_id,storyboard_id,task_id,video_url,storage_path,duration_seconds,is_selected,status,created_at,updated_at) values (?,?,1,?,'/video.mp4','video/video.mp4',5,true,'ACTIVE',now(),now())",tenant,project,child);
        mvc.perform(get(detail).with(authenticated(token))).andExpect(status().isOk()).andExpect(jsonPath("$.data.statusGroup").value("SUCCEEDED"));
        mvc.perform(get(detail+"/content").with(authenticated(token))).andExpect(status().isOk()).andExpect(jsonPath("$.data.sections[2].items.length()").value(1));
        mvc.perform(get(detail+"/content/results").with(authenticated(token))).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1));
        mvc.perform(get("/api/projects/"+project+"/ai-video-tasks/"+source).header("X-Tenant-Id",tenant).with(authenticated(token))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(source)).andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.data.results.length()").value(1));
        mvc.perform(get("/api/projects/"+project+"/ai-video-tasks").header("X-Tenant-Id",tenant).with(authenticated(token))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.data[0].id").value(source));
        mvc.perform(get("/api/projects/"+project+"/ai-video-tasks").header("X-Tenant-Id",tenant).param("status","SUCCEEDED").with(authenticated(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/projects/"+project+"/ai-video-results").header("X-Tenant-Id",tenant).param("taskId",String.valueOf(source)).with(authenticated(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        jdbc.update("update seedance_avatar_recovery set status='FAILED',retry_task_id=null,error_message='参考图片处理失败' where source_task_id=?",source);
        mvc.perform(get(detail).with(authenticated(token))).andExpect(status().isOk()).andExpect(jsonPath("$.data.statusGroup").value("FAILED"));
        mvc.perform(get("/api/projects/"+project+"/ai-video-tasks/"+source).header("X-Tenant-Id",tenant).with(authenticated(token))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FAILED")).andExpect(jsonPath("$.data.errorMessage").value("参考图片处理失败"));
        assertThat(jdbc.queryForObject("select status from ai_video_task where id=?",String.class,source)).isEqualTo("FAILED");
    }
}
