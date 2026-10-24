package com.antshorttv.freecreation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.antshorttv.support.SessionTestSupport.authenticated;
import com.antshorttv.ai.AiSecretCodec;
import com.antshorttv.execution.AiExecutionWorker;
import com.antshorttv.support.ModelBillingTestSupport;
import com.antshorttv.support.SessionTestSupport;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;

@SpringBootTest(properties={"ai.execution.dispatcher.fixed-delay-ms=3600000","ai.execution.free-creation-reconcile-ms=3600000","media.free-creation-thumbnail.initial-delay-ms=3600000"})
@AutoConfigureMockMvc
class FreeCreationIntegrationTest extends com.antshorttv.support.RegistrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired com.antshorttv.rbac.RbacService rbac;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired AiSecretCodec secrets;
    @Autowired AiExecutionWorker worker;
    @Autowired com.antshorttv.execution.AiExecutionDispatcher dispatcher;
    @Autowired com.qcloud.cos.COS cos;
    @Autowired FreeCreationExecutionHandler freeHandler;
    static HttpServer server;
    static byte[] png;
    static final AtomicInteger calls=new AtomicInteger();
    static final AtomicInteger videoSubmits=new AtomicInteger();
    static final AtomicInteger videoPolls=new AtomicInteger();

    @BeforeAll static void provider() throws Exception {
        var bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(320,320,BufferedImage.TYPE_INT_RGB),"png",bytes);png=bytes.toByteArray();
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/responses",exchange->{
            calls.incrementAndGet();exchange.getRequestBody().readAllBytes();
            byte[] body=("{\"id\":\"free-test\",\"output\":[{\"type\":\"image_generation_call\",\"result\":\""+Base64.getEncoder().encodeToString(png)+"\"}]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });
        server.createContext("/contents/generations/tasks",exchange->{
            exchange.getRequestBody().readAllBytes(); String response;
            if("POST".equals(exchange.getRequestMethod())) { videoSubmits.incrementAndGet(); response="{\"id\":\"free-video-1\",\"request_id\":\"submit-free\"}"; }
            else {videoPolls.incrementAndGet();response="{\"id\":\"free-video-1\",\"status\":\"succeeded\",\"request_id\":\"poll-free\",\"content\":{\"video_url\":\"http://127.0.0.1:"+server.getAddress().getPort()+"/test-video.mp4\"},\"duration\":5,\"resolution\":\"720p\",\"usage\":{\"completion_tokens\":1000}}";}
            byte[] bytesResponse=response.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytesResponse.length);exchange.getResponseBody().write(bytesResponse);exchange.close();
        });
        server.createContext("/test-video.mp4",exchange->{byte[] bytesVideo=new byte[512];exchange.sendResponseHeaders(200,bytesVideo.length);exchange.getResponseBody().write(bytesVideo);exchange.close();});
        server.start();
    }
    @BeforeEach void signedModelUrls() throws Exception {
        when(cos.generatePresignedUrl(any(com.qcloud.cos.model.GeneratePresignedUrlRequest.class),eq(true)))
            .thenAnswer(call->new java.net.URL("https://offline-cos.example/"+((com.qcloud.cos.model.GeneratePresignedUrlRequest)call.getArgument(0)).getKey()+"?sign=offline"));
    }
    @AfterAll static void stop() {server.stop(0);}

    @Test void generatesAndBillsOnceAndSupportsTeamReuseWithoutProjectOrPermissionLeaks() throws Exception {
        User author=register("13800781001","自由作者",false);User colleague=register("13800781002","团队作者",false);User member=register("13800781003","普通成员",false);
        long tenant=team(author,"自由创作测试团队");join(tenant,colleague);join(tenant,member);long other=team(author,"隔离团队");
        long model=imageModel();
        JsonNode catalog=getJson(member,tenant,"/api/free-creations/models").path("data");
        assertThat(catalog.path("models").findValues("id").stream().map(JsonNode::asLong).toList()).contains(model);
        assertThat(catalog.path("providers").toString()).doesNotContain("apiKey","baseUrl","cipher");
        mvc.perform(get("/api/platform/ai/models").with(authenticated(member.token))).andExpect(status().isForbidden());
        mvc.perform(get("/api/platform/ai/providers").with(authenticated(member.token))).andExpect(status().isForbidden());
        mvc.perform(get("/api/free-creations/models").with(authenticated(member.token)).header("X-Tenant-Id",other)).andExpect(status().isForbidden());
        jdbc.update("insert into team_point_account(tenant_id,balance,total_granted,total_consumed,created_at,updated_at) values (?,100,100,0,now(),now()) on duplicate key update balance=100,total_granted=100",tenant);
        String upload=data(completeMediaUpload(mvc,author.token,tenant,"参考.png","image/png",png)).path("sessionToken").asText();
        Map<String,Object> body=request(model,"首次生成测试",List.of(Map.of("kind","IMAGE","uploadSessionToken",upload)));
        var estimate=postJson(author,tenant,"/api/tenants/"+tenant+"/points/estimate",Map.of("operations",List.of(Map.of("url","/api/free-creations","data",body))));
        assertThat(estimate.path("data").path("estimatedPoints").decimalValue()).isGreaterThan(BigDecimal.ZERO);
        assertThat(jdbc.queryForObject("select count(*) from free_creation where tenant_id=?",Integer.class,tenant)).isZero();
        JsonNode first=postJson(author,tenant,"/api/free-creations",body).path("data");long id=first.path("id").asLong();long execution=first.path("executionId").asLong();
        assertThat(first.path("status").asText()).isEqualTo("QUEUED");
        assertThat(postJson(author,tenant,"/api/free-creations",body).path("data").path("id").asLong()).isEqualTo(id);
        assertThat(jdbc.queryForObject("select count(*) from ai_point_reservation where execution_id=?",Integer.class,execution)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select project_id from ai_execution_task where id=?",Long.class,execution)).isNull();
        assertThat(jdbc.queryForObject("select count(*) from project where tenant_id=?",Integer.class,tenant)).isZero();
        Map<String,Object> changed=new HashMap<>(body);changed.put("prompt","不同提示词");
        mvc.perform(post("/api/free-creations").with(authenticated(author.token)).header("X-Tenant-Id",tenant).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(changed))).andExpect(status().isBadRequest());
        int before=calls.get();worker.run(execution);assertThat(calls.get()).as("execution %s",jdbc.queryForMap("select status,error_message from ai_execution_task where id=?",execution)).isEqualTo(before+1);
        JsonNode detail=getJson(author,tenant,"/api/free-creations/"+id).path("data");
        assertThat(detail.path("status").asText()).isEqualTo("SUCCEEDED");assertThat(detail.path("outputs").size()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from ai_point_reservation where execution_id=?",String.class,execution)).isEqualTo("SETTLED");
        assertThat(jdbc.queryForObject("select balance from team_point_account where tenant_id=?",BigDecimal.class,tenant)).isEqualByComparingTo("99");
        worker.run(execution);assertThat(calls.get()).isEqualTo(before+1);
        long output=detail.path("outputs").get(0).path("id").asLong();
        assertThat(detail.path("outputs").get(0).path("previewUrl").asText()).contains("materials/").doesNotContain("127.0.0.1");
        assertThat(getJson(colleague,tenant,"/api/free-creations/"+id+"/outputs/"+output+"/download").path("data").path("url").asText()).contains("materials/");
        assertThat(getJson(colleague,tenant,"/api/free-creations?scope=team").path("data").path("total").asInt()).isEqualTo(1);
        assertThat(getJson(colleague,tenant,"/api/free-creations?scope=mine").path("data").path("total").asInt()).isZero();
        assertThat(getJson(colleague,tenant,"/api/free-creations?scope=team&keyword=自由作者").path("data").path("total").asInt()).isEqualTo(1);
        long reference=detail.path("references").get(0).path("referenceId").asLong();
        JsonNode reused=postJson(colleague,tenant,"/api/free-creations",request(model,"复用团队参考素材",List.of(Map.of("kind","IMAGE","referenceId",reference)))).path("data");
        assertThat(reused.path("creatorId").asLong()).isEqualTo(colleague.id);
        assertThat(reused.path("references").size()).isEqualTo(1);
        mvc.perform(post("/api/free-creations").with(authenticated(colleague.token)).header("X-Tenant-Id",tenant).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(request(model,"不能复用他人上传会话",List.of(Map.of("kind","IMAGE","uploadSessionToken",upload)))))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/free-creations/"+id).with(authenticated(author.token)).header("X-Tenant-Id",other)).andExpect(status().isNotFound());
        mvc.perform(post("/api/free-creations").with(authenticated(author.token)).header("X-Tenant-Id",other).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(request(model,"不能跨团队复用",List.of(Map.of("kind","IMAGE","referenceId",reference)))))).andExpect(status().isNotFound());
        assertThat(getJson(member,tenant,"/api/free-creations?scope=team").path("data").path("total").asInt()).isEqualTo(2);
        mvc.perform(get("/api/tenants/"+tenant+"/ai-executions/"+execution).with(authenticated(member.token))).andExpect(status().isOk());
        for(String operation:List.of("retry","cancel","regenerate")) mvc.perform(post("/api/tenants/"+tenant+"/ai-executions/"+execution+"/"+operation)
            .with(authenticated(colleague.token)).contentType(MediaType.APPLICATION_JSON).content("{\"clientIdempotencyKey\":\"cannot-regenerate\",\"traceId\":\"free-control-denied\"}")).andExpect(status().isForbidden());
        jdbc.update("update ai_execution_task set status='RUNNING' where id=?",execution);
        mvc.perform(get("/api/free-creations/"+id+"/outputs/"+output+"/download").with(authenticated(author.token)).header("X-Tenant-Id",tenant)).andExpect(status().isNotFound());
        jdbc.update("update ai_execution_task set status='SUCCEEDED' where id=?",execution);
        completePendingImageJobs(mvc);
        assertThat(getJson(author,tenant,"/api/free-creations/"+id).path("data").path("outputs").get(0).path("previewUrl").asText()).contains("/derived/");
        assertThat(getJson(author,tenant,"/api/free-creations/"+id).path("data").path("outputs").get(0).path("thumbnailUrl").asText()).contains("/derived/thumbnail-480-v1.webp");
        jdbc.update("update tenant_member set status='REMOVED' where tenant_id=? and user_id=?",tenant,member.id);
        for(String path:List.of("/api/free-creations","/api/free-creations/models","/api/free-creations/"+id,
            "/api/tenants/"+tenant+"/ai-executions/"+execution,"/api/tenants/"+tenant+"/production-tasks/FREE_CREATION:"+id)) {
            mvc.perform(get(path).with(authenticated(member.token)).header("X-Tenant-Id",tenant)).andExpect(status().isForbidden());
        }
    }

    @Test void ambiguousProviderSubmissionDoesNotResubmitOrRefundAsUnused() throws Exception {
        User user=register("13800781004","恢复作者",true);long tenant=team(user,"恢复团队");long model=imageModel();
        jdbc.update("insert into team_point_account(tenant_id,balance,total_granted,total_consumed,created_at,updated_at) values (?,100,100,0,now(),now()) on duplicate key update balance=100,total_granted=100",tenant);
        JsonNode item=postJson(user,tenant,"/api/free-creations",request(model,"模拟供应商已接收但进程中断",List.of())).path("data");
        long id=item.path("id").asLong(), execution=item.path("executionId").asLong();
        jdbc.update("update free_creation set provider_started_at=now() where id=?",id);
        int before=calls.get();worker.run(execution);assertThat(calls.get()).isEqualTo(before);
        assertThat(getJson(user,tenant,"/api/free-creations/"+id).path("data").path("status").asText()).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select status from ai_point_reservation where execution_id=?",String.class,execution)).isEqualTo("SETTLEMENT_REVIEW_REQUIRED");
    }

    @Test void dispatchesVideoSubmitAndPollWithDurableProviderIdAndMeasuredTokenBilling() throws Exception {
        var info=new com.qcloud.cos.model.ciModel.mediaInfo.MediaInfoResponse();
        var metadata=new com.qcloud.cos.model.ciModel.mediaInfo.MediaInfoStreamObject();
        var stream=new com.qcloud.cos.model.ciModel.mediaInfo.MediaInfoStream();
        var video=new com.qcloud.cos.model.ciModel.mediaInfo.MediaInfoVideo();
        video.setWidth("1280");video.setHeight("720");video.setDuration("5");video.setFps("24");
        stream.setMediaInfoVideoList(List.of(video));metadata.setStream(stream);info.setMediaInfo(metadata);
        when(cos.generateMediainfo(any())).thenReturn(info);
        var cover=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(320,180,BufferedImage.TYPE_INT_RGB),"jpg",cover);
        when(cos.getSnapshot(any())).thenAnswer(call->new java.io.ByteArrayInputStream(cover.toByteArray()));
        User user=register("13800781005","视频作者",true);long tenant=team(user,"视频测试团队");
        jdbc.update("insert into team_point_account(tenant_id,balance,total_granted,total_consumed,created_at,updated_at) values (?,10000,10000,0,now(),now()) on duplicate key update balance=10000,total_granted=10000",tenant);
        long provider=jdbc.queryForObject("select id from ai_provider where code='VOLCENGINE_ARK'",Long.class);
        jdbc.update("update ai_provider set status='ENABLED' where id=?",provider);
        jdbc.update("update ai_provider_config set base_url=?,api_key_cipher=?,status='ENABLED' where provider_id=?","http://127.0.0.1:"+server.getAddress().getPort(),secrets.encrypt("offline-video-key"),provider);
        long model=jdbc.queryForObject("select id from ai_model where code='SEEDANCE_2_0_MINI'",Long.class);
        jdbc.update("update ai_model set status='ENABLED',model_code='ep-free-test' where id=?",model);
        jdbc.update("update ai_model_capability set status='ENABLED' where model_id=? and capability='VIDEO_GENERATION'",model);
        ModelBillingTestSupport.publish(jdbc,model,"OUTPUT_TOKEN",new BigDecimal("1000"),BigDecimal.ONE);
        JsonNode item=postJson(user,tenant,"/api/free-creations",Map.of("requestId",UUID.randomUUID().toString(),"mediaType","VIDEO","modelId",model,"prompt","飞鸟掠过清晨海面","parameters",Map.of("durationSeconds",5,"resolution","720p","aspectRatio","16:9"))).path("data");
        long id=item.path("id").asLong(),execution=item.path("executionId").asLong();int submitted=videoSubmits.get(),polled=videoPolls.get();
        dispatcher.dispatchOnce();awaitState(execution,"PENDING","QUERY");
        assertThat(videoSubmits.get()).isEqualTo(submitted+1);
        assertThat(jdbc.queryForObject("select external_task_id from free_creation where id=?",String.class,id)).isEqualTo("free-video-1");
        assertThat(getJson(user,tenant,"/api/free-creations/"+id).path("data").path("status").asText()).isEqualTo("RUNNING");
        jdbc.update("update ai_execution_task set next_run_at=now() where id=?",execution);
        dispatcher.dispatchOnce();awaitState(execution,"SUCCEEDED","STORE");
        assertThat(videoSubmits.get()).isEqualTo(submitted+1);assertThat(videoPolls.get()).isEqualTo(polled+1);
        JsonNode output=getJson(user,tenant,"/api/free-creations/"+id).path("data").path("outputs").get(0);
        assertThat(output.path("thumbnailUrl").isNull()).isTrue();
        assertThat(output.path("thumbnailStatus").asText()).isEqualTo("PENDING");
        assertThat(output.path("previewUrl").asText()).contains("free-creations").doesNotContain("127.0.0.1");
        assertThat(jdbc.queryForObject("select settled_points from ai_point_reservation where execution_id=?",BigDecimal.class,execution)).isEqualByComparingTo("1");
        assertThat(jdbc.queryForObject("select count(*) from ai_usage_line where execution_id=? and metric='OUTPUT_TOKEN'",Integer.class,execution)).isEqualTo(1);
        var correlation=jdbc.queryForMap("select u.attempt_id usage_attempt,l.attempt_id log_attempt from ai_usage_line u join ai_call_log l on l.id=u.ai_call_log_id where u.execution_id=?",execution);
        assertThat(correlation.get("usage_attempt")).isEqualTo(correlation.get("log_attempt"));
        JsonNode reused=postJson(user,tenant,"/api/free-creations",Map.of("requestId",UUID.randomUUID().toString(),"mediaType","VIDEO","modelId",model,"prompt","以团队视频为参考续写",
            "parameters",Map.of("durationSeconds",5,"resolution","720p"),"references",List.of(Map.of("kind","VIDEO","referenceId",output.path("id").asLong())))).path("data");
        assertThat(reused.path("references").get(0).path("durationSeconds").decimalValue()).isEqualByComparingTo("5");
    }

    @Test void taskCenterUnifiesVisibilityContentControlsAndIdempotentBilling() throws Exception {
        User author=register("13800781006","统一任务作者",false);
        User colleague=register("13800781007","只读成员",false);
        User member=register("13800781008","普通团队所有者",false);
        long tenant=team(member,"统一任务团队");join(tenant,author);join(tenant,colleague);
        long other=team(author,"统一任务隔离团队"),model=imageModel();
        jdbc.update("insert into team_point_account(tenant_id,balance,total_granted,total_consumed,created_at,updated_at) values (?,100,100,0,now(),now()) on duplicate key update balance=100,total_granted=100",tenant);
        String upload=data(completeMediaUpload(mvc,author.token,tenant,"原始参考.png","image/png",png)).path("sessionToken").asText();
        JsonNode item=postJson(author,tenant,"/api/free-creations",request(model,"统一任务提示词",List.of(Map.of("kind","IMAGE","uploadSessionToken",upload)))).path("data");
        long id=item.path("id").asLong(),execution=item.path("executionId").asLong();
        String base="/api/tenants/"+tenant+"/production-tasks",key="FREE_CREATION:"+id,path=base+"/"+key;
        assertThat(getJson(author,tenant,base+"?type=FREE_CREATION").path("data").path("items").get(0).path("taskKey").asText()).isEqualTo(key);
        assertThat(getJson(author,tenant,base+"/summary?type=FREE_CREATION").path("data").path("counts").path("QUEUED").asInt()).isEqualTo(1);
        JsonNode detail=getJson(author,tenant,path).path("data");
        assertThat(detail.path("allowedActions").toString()).contains("CANCEL");
        assertThat(detail.path("destination").asText()).isEqualTo("/free-creation?creation="+id);
        assertThat(getJson(member,tenant,base+"?scope=team&type=FREE_CREATION").path("data").path("total").asInt()).isEqualTo(1);
        assertThat(getJson(member,tenant,base+"/summary?scope=team&type=FREE_CREATION").path("data").path("total").asInt()).isEqualTo(1);
        assertThat(getJson(colleague,tenant,base+"?type=FREE_CREATION").path("data").path("total").asInt()).isZero();
        mvc.perform(get(base+"?scope=team&type=FREE_CREATION").with(authenticated(colleague.token))).andExpect(status().isForbidden());
        for(String suffix:List.of("","/content","/content/references-IMAGE")) mvc.perform(get(path+suffix).with(authenticated(member.token))).andExpect(status().isOk());
        mvc.perform(get("/api/tenants/"+other+"/production-tasks/"+key).with(authenticated(author.token))).andExpect(status().isForbidden());
        mvc.perform(post(path+"/cancel").with(authenticated(colleague.token))).andExpect(status().isForbidden());
        assertThat(getJson(colleague,tenant,path).path("data").path("allowedActions").toString()).doesNotContain("CANCEL","RETRY","REGENERATE");
        assertThat(getJson(colleague,tenant,path+"/content").path("data").path("sections").get(0).path("preview").asText()).isEqualTo("统一任务提示词");
        JsonNode sections=getJson(author,tenant,path+"/content").path("data").path("sections");
        assertThat(sections.get(0).path("preview").asText()).isEqualTo("统一任务提示词");
        assertThat(sections.get(3).path("items").get(0).path("url").asText()).contains("materials/");
        assertThat(getJson(author,tenant,path+"/content/submission").path("data").path("text").asText()).isEqualTo("统一任务提示词");
        assertThat(control(author,tenant,path,"cancel","cancel-key").path("statusGroup").asText()).isEqualTo("CANCELED");
        assertThat(control(author,tenant,path,"cancel","cancel-key").path("statusGroup").asText()).isEqualTo("CANCELED");
        freeHandler.reconcileTerminalReservations();
        assertThat(jdbc.queryForObject("select status from ai_point_reservation where execution_id=?",String.class,execution)).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("select balance from team_point_account where tenant_id=?",BigDecimal.class,tenant)).isEqualByComparingTo("100");
        var estimate=postJson(author,tenant,"/api/tenants/"+tenant+"/points/estimate",Map.of("operations",List.of(Map.of("url",path+"/regenerate"))));
        assertThat(estimate.path("data").path("estimatedPoints").decimalValue()).isEqualByComparingTo("1");
        JsonNode replacement=control(author,tenant,path,"regenerate","same-generation-key");
        String nextKey=replacement.path("taskKey").asText();long nextId=Long.parseLong(nextKey.split(":")[1]);
        assertThat(nextId).isNotEqualTo(id);
        assertThat(control(author,tenant,path,"regenerate","same-generation-key").path("taskKey").asText()).isEqualTo(nextKey);
        assertThat(jdbc.queryForObject("select count(*) from free_creation where tenant_id=?",Integer.class,tenant)).isEqualTo(2);
        JsonNode next=getJson(author,tenant,"/api/free-creations/"+nextId).path("data");long nextExecution=next.path("executionId").asLong();
        assertThat(next.path("references").get(0).path("fileName").asText()).isEqualTo("原始参考.png");
        assertThat(jdbc.queryForObject("select count(*) from ai_point_reservation where execution_id=?",Integer.class,nextExecution)).isEqualTo(1);
        // A remote polling continuation remains RUNNING in both list and summary.
        jdbc.update("update free_creation set provider_started_at=now(),external_task_id='pending-remote' where id=?",nextId);
        assertThat(getJson(author,tenant,base+"/"+nextKey).path("data").path("statusGroup").asText()).isEqualTo("RUNNING");
        jdbc.update("update free_creation set provider_started_at=null,external_task_id=null where id=?",nextId);
        int before=calls.get();worker.run(nextExecution);assertThat(calls.get()).isEqualTo(before+1);
        JsonNode result=getJson(author,tenant,base+"/"+nextKey+"/content").path("data").path("sections").get(2);
        assertThat(result.path("availability").asText()).isEqualTo("AVAILABLE");
        assertThat(result.path("items").get(0).path("downloadUrl").asText()).contains("materials/");
        assertThat(getJson(author,tenant,base+"/"+nextKey+"/content/results").path("data").path("items").size()).isEqualTo(1);
        // Generic execution endpoints use the same business creation and idempotency namespace.
        JsonNode regenerated=postJson(author,tenant,"/api/tenants/"+tenant+"/ai-executions/"+nextExecution+"/regenerate",
            Map.of("clientIdempotencyKey","shared-key","traceId","unified-free-creation")).path("data");
        assertThat(control(author,tenant,base+"/"+nextKey,"regenerate","shared-key").path("taskKey").asText()).isEqualTo("FREE_CREATION:"+regenerated.path("businessId").asLong());
        long failedId=regenerated.path("businessId").asLong(),failedExecution=regenerated.path("id").asLong();
        jdbc.update("update ai_execution_task set status='FAILED',error_message='pre-call failure' where id=?",failedExecution);
        freeHandler.reconcileTerminalReservations();
        String failedPath=base+"/FREE_CREATION:"+failedId;
        mvc.perform(post(failedPath+"/retry").with(authenticated(author.token))).andExpect(status().isBadRequest());
        JsonNode retried=control(author,tenant,failedPath,"retry","same-retry-key");
        assertThat(control(author,tenant,failedPath,"retry","same-retry-key").path("taskKey").asText()).isEqualTo(retried.path("taskKey").asText());
        assertThat(jdbc.queryForObject("select status from ai_execution_task where id=?",String.class,failedExecution)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select count(*) from ai_point_reservation where tenant_id=?",Integer.class,tenant)).isEqualTo(4);
        mvc.perform(post(base+"/"+retried.path("taskKey").asText()+"/retry").with(authenticated(author.token)).header("Idempotency-Key","invalid-active-retry")).andExpect(status().isBadRequest());
        // Insufficient credit rolls back the newly inserted business row as well as its execution.
        jdbc.update("update team_point_account set balance=0,trial_balance=0 where tenant_id=?",tenant);
        long rows=jdbc.queryForObject("select count(*) from free_creation where tenant_id=?",Long.class,tenant);
        mvc.perform(post(failedPath+"/retry").with(authenticated(author.token)).header("Idempotency-Key","insufficient-points")).andExpect(status().is4xxClientError());
        assertThat(jdbc.queryForObject("select count(*) from free_creation where tenant_id=?",Long.class,tenant)).isEqualTo(rows);
        // The free-creation menu alone must retain its own controls through the shared task endpoint.
        jdbc.update("insert into `role` (tenant_id,code,name,role_type,status,is_default,created_at,updated_at) values (?,'FREE_ONLY','Free only','CUSTOM','ACTIVE',false,now(),now())",tenant);
        long freeRole=jdbc.queryForObject("select id from `role` where tenant_id=? and code='FREE_ONLY'",Long.class,tenant);
        long authorMember=jdbc.queryForObject("select id from tenant_member where tenant_id=? and user_id=?",Long.class,tenant,author.id);
        jdbc.update("insert into role_permission(role_id,permission_id,created_at) select ?,id,now() from permission where code='MENU:FREE_CREATION'",freeRole);
        jdbc.update("delete from member_role where member_id=?",authorMember);
        jdbc.update("insert into member_role(member_id,role_id,created_at) values (?,?,now())",authorMember,freeRole);
        mvc.perform(get(base).with(authenticated(author.token))).andExpect(status().isForbidden());
        control(author,tenant,base+"/"+retried.path("taskKey").asText(),"cancel","free-only-cancel");
        getJson(author,tenant,"/api/tenants/"+tenant+"/ai-executions/"+execution);
        mvc.perform(post(base+"/IMAGE:999/cancel").with(authenticated(author.token))).andExpect(status().isForbidden());
        jdbc.update("delete from role_permission where role_id=?",freeRole);
        mvc.perform(get("/api/free-creations/"+id).header("X-Tenant-Id",tenant).with(authenticated(author.token))).andExpect(status().isForbidden());
        mvc.perform(get("/api/tenants/"+tenant+"/ai-executions/"+execution).with(authenticated(author.token))).andExpect(status().isForbidden());
    }

    private JsonNode control(User user,long tenant,String path,String action,String key) throws Exception {
        return data(mvc.perform(post(path+"/"+action).with(authenticated(user.token)).header("X-Tenant-Id",tenant).header("Idempotency-Key",key))
            .andExpect(status().isOk()).andReturn());
    }

    private void awaitState(long execution,String state,String phase) throws Exception {
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        while(System.nanoTime()<deadline) {
            var row=jdbc.queryForMap("select status,phase from ai_execution_task where id=?",execution);
            if(state.equals(row.get("status")) && phase.equals(row.get("phase"))) return;
            dispatcher.dispatchOnce();Thread.sleep(100);
        }
        assertThat(jdbc.queryForMap("select status,phase,error_message from ai_execution_task where id=?",execution)).containsEntry("status",state).containsEntry("phase",phase);
    }

    private Map<String,Object> request(long model,String prompt,List<?> refs) {return Map.of("requestId",UUID.randomUUID().toString(),"mediaType","IMAGE","modelId",model,"prompt",prompt,"parameters",Map.of("count",1,"aspectRatio","1:1"),"references",refs);}
    private JsonNode postJson(User user,long tenant,String path,Object value) throws Exception {return json.readTree(mvc.perform(post(path).with(authenticated(user.token)).header("X-Tenant-Id",tenant).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(value))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());}
    private JsonNode getJson(User user,long tenant,String path) throws Exception {return json.readTree(mvc.perform(get(path).with(authenticated(user.token)).header("X-Tenant-Id",tenant)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());}
    private JsonNode data(MvcResult result) throws Exception {return json.readTree(result.getResponse().getContentAsByteArray()).path("data");}
    private User register(String mobile,String name,boolean admin) throws Exception {
        MvcResult result=mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("mobile",mobile,"nickname",name,"password","Password123","verificationCode",registrationVerificationCode(mvc,mobile))))).andExpect(status().isOk()).andReturn();
        long id=jdbc.queryForObject("select id from app_user where mobile=?",Long.class,mobile);
        jdbc.update("delete from platform_user_role where user_id=?",id);
        if(admin) jdbc.update("insert into platform_user_role(user_id,role_id,created_at) select ?,id,now() from platform_role where code='PLATFORM_ADMIN'",id);
        return new User(id,SessionTestSupport.sessionCredential(result));
    }
    private long team(User user,String name) throws Exception {return postJson(user,1,"/api/tenants",Map.of("name",name,"type","STUDIO","description","自由创作测试")).path("data").path("id").asLong();}
    private void join(long tenant,User user) {jdbc.update("insert into tenant_member(tenant_id,user_id,member_type,status,joined_at,created_at,updated_at) values (?,?,'MEMBER','ACTIVE',now(),now(),now())",tenant,user.id);rbac.initializeTenant(tenant);}
    private long imageModel() {
        long provider=jdbc.queryForObject("select id from ai_provider where code='OpenAI'",Long.class);
        jdbc.update("update ai_provider set status='ENABLED' where id=?",provider);
        jdbc.update("update ai_provider_config set base_url=?,api_key_cipher=?,status='ENABLED' where provider_id=?","http://127.0.0.1:"+server.getAddress().getPort(),secrets.encrypt("offline-free-creation-key"),provider);
        jdbc.update("insert ignore into ai_model(provider_id,code,name,model_code,service_type,status,is_default,sort,created_at,updated_at) values (?,'FREE_CREATION_TEST_IMAGE','测试图片模型','gpt-image-2','IMAGE','ENABLED',false,1,now(),now())",provider);
        long model=jdbc.queryForObject("select id from ai_model where code='FREE_CREATION_TEST_IMAGE'",Long.class);
        jdbc.update("insert ignore into ai_model_capability(model_id,capability,status,created_at,updated_at) values (?,'IMAGE_GENERATION','ENABLED',now(),now())",model);
        ModelBillingTestSupport.publish(jdbc,model,"IMAGE",BigDecimal.ONE,BigDecimal.ONE);
        ModelBillingTestSupport.publish(jdbc,model,"CALL",BigDecimal.ONE,BigDecimal.ZERO);
        ModelBillingTestSupport.publish(jdbc,model,"INPUT_IMAGE",BigDecimal.ONE,BigDecimal.ZERO);return model;
    }
    record User(long id,String token) {}
}
