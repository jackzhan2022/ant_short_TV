package com.antshorttv.downloadcenter;

import static com.antshorttv.support.SessionTestSupport.authenticated;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.antshorttv.common.BusinessException;
import com.antshorttv.storage.ObjectStorageService;
import com.antshorttv.support.SessionTestSupport;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties="download-center.worker-enabled=false")
@AutoConfigureMockMvc
class DownloadCenterControllerTest extends com.antshorttv.support.RegistrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DownloadCenterService service;
    @Autowired ObjectStorageService storage;

    @Test void persistsZipDownloadsThenPhysicallyDeletesOnlyTheExpiredArchive() throws Exception {
        String mobile="13800028781";
        String token=register(mobile);
        long tenant=((Number)JsonPath.read(mvc.perform(post("/api/tenants").with(authenticated(token)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Download test\",\"type\":\"STUDIO\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),"$.data.id")).longValue();
        long user=jdbc.queryForObject("select id from app_user where mobile=?",Long.class,mobile);
        jdbc.update("insert into project(tenant_id,name,code,owner_id,status,created_by,created_at,updated_at) values (?,'download-project','download-project',?,'ACTIVE',?,now(),now())",tenant,user,user);
        long project=jdbc.queryForObject("select id from project where tenant_id=?",Long.class,tenant);
        String original="materials/"+tenant+"/"+project+"/test/202610/1/v1/original.png";
        putStoredObject(original,"原始文件".getBytes(StandardCharsets.UTF_8),"text/plain");
        jdbc.update("insert into ai_image_result(tenant_id,project_id,target_type,target_id,image_url,storage_path,mime_type,file_size,is_selected,status,created_at,updated_at) values (?,?,'VISUAL_VARIANT',1,?,?,'image/png',12,false,'ACTIVE',now(),now())",tenant,project,"/"+original,original);
        long image=jdbc.queryForObject("select id from ai_image_result where project_id=?",Long.class,project);
        mvc.perform(get("/api/downloads").header("X-Tenant-Id",tenant)).andExpect(status().isUnauthorized());
        String body="{\"name\":\"文件下载\",\"sources\":[{\"kind\":\"IMAGE\",\"projectId\":"+project+",\"id\":"+image+"}]}";
        var created=mvc.perform(post("/api/downloads").with(authenticated(token)).header("X-Tenant-Id",tenant)
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("QUEUED")).andReturn();
        String id=JsonPath.read(created.getResponse().getContentAsString(),"$.data.id");
        String file="/api/downloads/"+id+"/file";
        mvc.perform(get(file).param("tenantId",String.valueOf(tenant)).with(authenticated(token))).andExpect(status().isBadRequest());
        assertThat(service.claim(id)).isTrue();service.generate(id);
        var ready=mvc.perform(get("/api/downloads/"+id).with(authenticated(token)).header("X-Tenant-Id",tenant))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("READY")).andReturn().getResponse().getContentAsString();
        Instant completed=Instant.parse(JsonPath.read(ready,"$.data.completedAt"));Instant expires=Instant.parse(JsonPath.read(ready,"$.data.expiresAt"));
        assertThat(Duration.between(completed,expires)).isEqualTo(Duration.ofHours(24));
        for(int i=0;i<2;i++) {
            byte[] bytes=mvc.perform(get(file).param("tenantId",String.valueOf(tenant)).with(authenticated(token)))
                .andExpect(status().isOk()).andExpect(content().contentType("application/zip"))
                .andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsByteArray();
            try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8)) {
                assertThat(zip.getNextEntry().getName()).isEqualTo("图片_"+image+".png");assertThat(new String(zip.readAllBytes(),StandardCharsets.UTF_8)).isEqualTo("原始文件");
            }
        }
        String other=register("13800028782");
        mvc.perform(get(file).param("tenantId",String.valueOf(tenant)).with(authenticated(other))).andExpect(status().isForbidden());
        jdbc.update("update download_task set expires_at=? where id=?",Timestamp.from(Instant.now().minusSeconds(1)),id);
        mvc.perform(get(file).param("tenantId",String.valueOf(tenant)).with(authenticated(token))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/downloads").param("status","EXPIRED").with(authenticated(token)).header("X-Tenant-Id",tenant))
            .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].status").value("EXPIRED"));
        service.cleanup();
        assertThatThrownBy(()->storage.resource("download-archives/"+tenant+"/"+user+"/"+id+".zip")).isInstanceOf(BusinessException.class);
        try(var input=storage.resource(original).getInputStream()) {assertThat(new String(input.readAllBytes(),StandardCharsets.UTF_8)).isEqualTo("原始文件");}
        mvc.perform(post("/api/downloads/"+id+"/retry").with(authenticated(token)).header("X-Tenant-Id",tenant))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("QUEUED"))
            .andExpect(jsonPath("$.data.id").value(org.hamcrest.Matchers.not(id)));
    }
    private String register(String mobile) throws Exception {
        return SessionTestSupport.sessionCredential(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"mobile\":\""+mobile+"\",\"nickname\":\"Download tester\",\"verificationCode\":\""+registrationVerificationCode(mvc,mobile)+"\",\"password\":\"Password123\"}"))
            .andExpect(status().isOk()).andReturn());
    }
}
