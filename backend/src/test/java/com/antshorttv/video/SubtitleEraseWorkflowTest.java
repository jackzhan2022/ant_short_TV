package com.antshorttv.video;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.antshorttv.support.SessionTestSupport.authenticated;
import com.antshorttv.execution.*;
import com.antshorttv.support.SessionTestSupport;
import com.fasterxml.jackson.databind.*;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;

@SpringBootTest(properties={"ai.mediakit.api-key=mediakit-test-key","ark.avatar.worker-enabled=false"})
@AutoConfigureMockMvc
class SubtitleEraseWorkflowTest extends com.antshorttv.support.RegistrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired AiExecutionWorker worker;
    @MockBean AiExecutionPools pools;
    private static final AtomicInteger submissions=new AtomicInteger(), downloads=new AtomicInteger(), users=new AtomicInteger();
    private static final AtomicBoolean failDownload=new AtomicBoolean(), failProvider=new AtomicBoolean();
    private static final AtomicReference<JsonNode> lastBody=new AtomicReference<>();
    private static final HttpServer server=startServer();
    private static HttpServer startServer() {
        try {
            var http=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            http.createContext("/api/v1/tools/",exchange->{
                lastBody.set(new ObjectMapper().readTree(exchange.getRequestBody()));
                byte[] body=("{\"success\":true,\"task_id\":\"erase-"+submissions.incrementAndGet()+"\"}").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
            });
            http.createContext("/api/v1/tasks/",exchange->{
                long now=Instant.now().getEpochSecond();
                String response=failProvider.get()?"{\"success\":true,\"status\":\"failed\",\"error\":{\"code\":\"DownloadFailed\"}}":
                    "{\"success\":true,\"status\":\"completed\",\"finished_at\":"+now+",\"expires_at\":"+(now+86400)
                    +",\"result\":{\"video_url\":\"http://127.0.0.1:"+http.getAddress().getPort()+"/clean.mp4\",\"duration\":4,\"resolution\":\"720p\"}}";
                byte[] body=response.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
            });
            http.createContext("/clean.mp4",exchange->{
                downloads.incrementAndGet();byte[] body="clean-video".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(failDownload.getAndSet(false)?502:200,body.length);exchange.getResponseBody().write(body);exchange.close();
            });http.start();return http;
        } catch(Exception exception){throw new ExceptionInInitializerError(exception);}
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("ai.mediakit.base-url",()->"http://127.0.0.1:"+server.getAddress().getPort());}
    @AfterAll static void stop(){server.stop(0);}
    @BeforeEach void resetProvider(){submissions.set(0);downloads.set(0);failProvider.set(false);failDownload.set(false);}
    record Fixture(String token,long tenant,long project,long storyboard,long video,long user) {}
    private Fixture fixture() throws Exception {
        String mobile="138000298"+String.format("%02d",users.incrementAndGet());
        String token=SessionTestSupport.sessionCredential(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"mobile\":\""+mobile+"\",\"nickname\":\"Erase tester\",\"verificationCode\":\""+registrationVerificationCode(mvc,mobile)+"\",\"password\":\"Password123\"}"))
            .andExpect(status().isOk()).andReturn());
        long tenant=number(mvc.perform(post("/api/tenants").with(authenticated(token)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"字幕擦除团队\",\"type\":\"STUDIO\"}")).andExpect(status().isOk()).andReturn(),"$.data.id");
        long user=jdbc.queryForObject("select id from app_user where mobile=?",Long.class,mobile);
        jdbc.update("insert into project(tenant_id,name,code,owner_id,status,created_by,created_at,updated_at) values (?,'erase','erase',?,'ACTIVE',?,now(),now())",tenant,user,user);
        long project=jdbc.queryForObject("select id from project where tenant_id=?",Long.class,tenant);
        jdbc.update("insert into storyboard(tenant_id,project_id,episode_no,shot_no,visual_description,duration_seconds,status,created_by,created_at,updated_at) values (?,?,1,1,'字幕擦除测试分镜',4,'READY',?,now(),now())",tenant,project,user);
        long shot=jdbc.queryForObject("select id from storyboard where tenant_id=?",Long.class,tenant);
        jdbc.update("insert into ai_video_task(tenant_id,project_id,storyboard_id,provider_code,model,prompt,duration_seconds,aspect_ratio,status,created_by,created_at,updated_at) values (?,?,?,'VOLCENGINE_ARK','video','prompt',4,'9:16','SUCCEEDED',?,now(),now())",tenant,project,shot,user);
        long task=jdbc.queryForObject("select id from ai_video_task where tenant_id=?",Long.class,tenant);
        String path="materials/"+tenant+"/"+project+"/videos/source/original.mp4";
        jdbc.update("insert into ai_video_result(tenant_id,project_id,task_id,storyboard_id,video_url,storage_path,source_video_url,source_video_expires_at,width,height,duration_seconds,format,is_selected,status,created_at,updated_at) values (?,?,?,?,?,?,?, ?,720,1280,4,'mp4',true,'ACTIVE',now(),now())",
            tenant,project,task,shot,"/"+path,path,"https://ark.example/source.mp4?signature=original",LocalDateTime.ofInstant(Instant.now().plusSeconds(86400),ZoneOffset.UTC));
        long video=jdbc.queryForObject("select id from ai_video_result where tenant_id=?",Long.class,tenant);
        jdbc.update("update storyboard set current_video_result_id=?,current_video_url=? where id=?",video,"/"+path,shot);
        return new Fixture(token,tenant,project,shot,video,user);
    }
    private MvcResult create(Fixture f,String body,String key) throws Exception {
        return mvc.perform(post("/api/projects/"+f.project+"/ai-video-results/"+f.video+"/subtitle-erasure")
            .with(authenticated(f.token)).header("X-Tenant-Id",f.tenant).header("Idempotency-Key",key)
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn();
    }
    private void run(long execution){jdbc.update("update ai_execution_task set next_run_at=? where id=?",LocalDateTime.now().minusSeconds(1),execution);worker.run(execution);}
    private long number(MvcResult result,String path) throws Exception{return ((Number)JsonPath.read(result.getResponse().getContentAsString(),path)).longValue();}
    @Test void keepsOriginalAndPublishesOneNewVersionEvenAfterTransferFailureAndShowsItInTaskCenter() throws Exception {
        var f=fixture();String body="{\"method\":\"AUTO\",\"quality\":\"PRO\"}";
        var created=create(f,body,"request-1");long id=number(created,"$.data.id"), execution=number(created,"$.data.executionId");
        assertThat(number(create(f,body,"request-1"),"$.data.id")).isEqualTo(id);
        mvc.perform(get("/api/projects/"+f.project+"/subtitle-erase-tasks").param("storyboardIds",String.valueOf(f.storyboard))
            .header("X-Tenant-Id",f.tenant).with(authenticated(f.token))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value(id)).andExpect(jsonPath("$.data[0].result").isEmpty());
        String center="/api/tenants/"+f.tenant+"/production-tasks";
        mvc.perform(get(center).param("type","SUBTITLE_ERASE").with(authenticated(f.token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].statusGroup").value("QUEUED"));
        run(execution);assertThat(submissions.get()).isEqualTo(1);
        assertThat(lastBody.get().path("video_url").asText()).isEqualTo("https://ark.example/source.mp4?signature=original");
        mvc.perform(get(center+"/SUBTITLE_ERASE:"+id).with(authenticated(f.token))).andExpect(status().isOk()).andExpect(jsonPath("$.data.statusGroup").value("RUNNING"));
        failDownload.set(true);run(execution);
        assertThat(jdbc.queryForObject("select output_url from subtitle_erase_task where id=?",String.class,id))
            .as("execution %s",jdbc.queryForMap("select status,phase,error_message,next_run_at from ai_execution_task where id=?",execution)).endsWith("/clean.mp4");
        assertThat(jdbc.queryForObject("select count(*) from ai_video_result where tenant_id=? and status='ACTIVE'",Long.class,f.tenant)).isEqualTo(1);
        run(execution);
        assertThat(submissions.get()).isEqualTo(1);assertThat(downloads.get()).as("execution %s",jdbc.queryForMap("select status,phase,error_message,next_run_at from ai_execution_task where id=?",execution)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select status from ai_execution_task where id=?",String.class,execution)).isEqualTo("SUCCEEDED");
        var newVideo=jdbc.queryForMap("select * from ai_video_result where tenant_id=? and processing_type='SUBTITLE_ERASE'",f.tenant);
        assertThat(newVideo.get("status")).isEqualTo("ACTIVE");assertThat(newVideo.get("source_result_id")).isEqualTo(f.video);
        assertThat(newVideo.get("is_selected")).isEqualTo(false);assertThat(newVideo.get("cover_url")).isNotNull();
        mvc.perform(get("/api/projects/"+f.project+"/ai-video-results/"+newVideo.get("id")+"/cover").with(authenticated(f.token)))
            .andExpect(status().isFound()).andExpect(header().string("Location",org.hamcrest.Matchers.containsString("antvcdn.aixmax.cn")));
        assertThat(jdbc.queryForObject("select current_video_result_id from storyboard where id=?",Long.class,f.storyboard)).isEqualTo(f.video);
        assertThat(jdbc.queryForObject("select count(*) from ai_video_result where tenant_id=? and status='ACTIVE'",Long.class,f.tenant)).isEqualTo(2);
        mvc.perform(get("/api/projects/"+f.project+"/subtitle-erase-tasks").param("storyboardIds",String.valueOf(f.storyboard))
            .header("X-Tenant-Id",f.tenant).with(authenticated(f.token))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].result.processingType").value("SUBTITLE_ERASE"));
        mvc.perform(get(center+"/SUBTITLE_ERASE:"+id+"/content").with(authenticated(f.token))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sections[0].kind").value("VIDEO")).andExpect(jsonPath("$.data.sections[2].items[0].url").value("/api/projects/"+f.project+"/ai-video-results/"+newVideo.get("id")+"/playback"));
        mvc.perform(get(center+"/SUBTITLE_ERASE:"+id).with(authenticated(f.token))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.statusGroup").value("SUCCEEDED")).andExpect(jsonPath("$.data.destination").value("/projects/"+f.project+"/production-workbench/storyboard?episodeNo=1&current=1&storyboardId="+f.storyboard));
    }
    @Test void usesPlatformSourceWithinTwoHoursAndRetriesConfirmedProviderFailureFromTaskCenter() throws Exception {
        var f=fixture();jdbc.update("update ai_video_result set source_video_expires_at=? where id=?",LocalDateTime.ofInstant(Instant.now().plusSeconds(7199),ZoneOffset.UTC),f.video);
        var created=create(f,"{\"method\":\"REGION\",\"quality\":\"PRO\",\"region\":{\"left\":0.1,\"top\":0.1,\"right\":0.9,\"bottom\":0.4}}","request-2");
        long id=number(created,"$.data.id"), execution=number(created,"$.data.executionId");run(execution);
        assertThat(lastBody.get().path("video_url").asText()).contains("antvcdn.aixmax.cn").doesNotContain("ark.example");
        assertThat(lastBody.get().path("mode").asText()).isEqualTo("Text");
        failProvider.set(true);run(execution);
        String center="/api/tenants/"+f.tenant+"/production-tasks/SUBTITLE_ERASE:"+id;
        mvc.perform(get(center).with(authenticated(f.token))).andExpect(status().isOk()).andExpect(jsonPath("$.data.allowedActions").value(org.hamcrest.Matchers.hasItem("RETRY")));
        failProvider.set(false);
        mvc.perform(post(center+"/retry").with(authenticated(f.token)).header("Idempotency-Key","retry-1")).andExpect(status().isOk());
        run(execution);assertThat(submissions.get()).isEqualTo(2);
        assertThat(lastBody.get().path("client_token").asText()).isEqualTo("subtitle-erase-"+id+"-2");
        failProvider.set(true);run(execution);
        // A lost retry response must not create another paid provider task, even if it failed quickly.
        mvc.perform(post(center+"/retry").with(authenticated(f.token)).header("Idempotency-Key","retry-1")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select generation from subtitle_erase_task where id=?",Integer.class,id)).isEqualTo(2);
        failProvider.set(false);
        mvc.perform(post(center+"/retry").with(authenticated(f.token)).header("Idempotency-Key","retry-2")).andExpect(status().isOk());
        run(execution);assertThat(submissions.get()).isEqualTo(3);run(execution);
        assertThat(jdbc.queryForObject("select status from ai_execution_task where id=?",String.class,execution)).isEqualTo("SUCCEEDED");
    }
    @Test void validatesRegionAndDoesNotAllowAnotherTeamToProcessTheSource() throws Exception {
        var f=fixture();
        mvc.perform(post("/api/projects/"+f.project+"/ai-video-results/"+f.video+"/subtitle-erasure").with(authenticated(f.token))
            .header("X-Tenant-Id",f.tenant).header("Idempotency-Key","bad-region").contentType(MediaType.APPLICATION_JSON)
            .content("{\"method\":\"REGION\",\"quality\":\"PRO\",\"region\":{\"left\":0.8,\"top\":0.2,\"right\":0.1,\"bottom\":0.9}}"))
            .andExpect(status().isBadRequest());
        var other=fixture();
        mvc.perform(post("/api/projects/"+f.project+"/ai-video-results/"+f.video+"/subtitle-erasure").with(authenticated(other.token))
            .header("X-Tenant-Id",other.tenant).header("Idempotency-Key","foreign").contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"AUTO\",\"quality\":\"PRO\"}"))
            .andExpect(status().isForbidden());
        assertThat(submissions.get()).isZero();
    }
}
