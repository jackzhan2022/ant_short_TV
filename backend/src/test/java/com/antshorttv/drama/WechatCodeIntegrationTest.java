package com.antshorttv.drama;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.antshorttv.support.SessionTestSupport.authenticated;

@SpringBootTest
@AutoConfigureMockMvc
class WechatCodeIntegrationTest extends com.antshorttv.support.RegistrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @MockBean WechatComponentService component;
    @MockBean WechatComponentClient client;
    Cookie session;
    long user, tenant, id;
    String appid;
    static final String BODY = "{\"templateId\":0,\"version\":\"1.0\",\"description\":\"体验测试\"}";
    @BeforeEach void setup() throws Exception {
        String mobile = "139" + String.format("%08d", new java.security.SecureRandom().nextInt(100000000));
        String code = registrationVerificationCode(mvc, mobile);
        var registered = mvc.perform(post("/api/auth/register").contentType("application/json")
            .content(json.writeValueAsString(Map.of("mobile", mobile, "verificationCode", code, "nickname", "代码测试", "password", "Password123"))))
            .andExpect(status().isOk()).andReturn();
        session = registered.getResponse().getCookie("ANT_SHORT_SESSION");
        user = json.readTree(registered.getResponse().getContentAsString()).at("/data/user/id").asLong();
        var created = mvc.perform(post("/api/tenants").with(authenticated(session.getValue())).contentType("application/json")
            .content("{\"name\":\"代码管理测试\",\"type\":\"STUDIO\"}")).andExpect(status().isOk()).andReturn();
        tenant = json.readTree(created.getResponse().getContentAsString()).at("/data/id").asLong();
        admin();
        appid = "wx" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        db.update("insert into team_miniapp (tenant_id,appid,name,binding_type,enabled,auth_status,created_at) values (?,?,'测试','OFFICIAL',true,'AUTHORIZED',now())", tenant, appid);
        id = db.queryForObject("select id from team_miniapp where appid=?", Long.class, appid);
        db.update("insert into wechat_authorizer (appid,tenant_id,auth_status,scopes,event_time,event_type) values (?,?,'AUTHORIZED',?,1,'callback')", appid, tenant, "[{\"funcscope_category\":{\"id\":18}}]");
        when(component.componentToken()).thenReturn("component-test-token");
        when(component.authorizerToken(appid)).thenReturn("authorizer-test-token");
        when(client.getTemplates(anyString())).thenReturn(json.readTree("{\"errcode\":0,\"template_list\":[{\"template_id\":0,\"template_type\":0,\"user_version\":\"1.0\",\"access_token\":\"hidden-token\"}]}"));
        when(client.commitCode(anyString(), anyMap())).thenReturn(json.readTree("{\"errcode\":0}"));
        when(client.trialQrCode(anyString(), nullable(String.class))).thenReturn(new WechatCodeModels.QrCode("image/png", new byte[]{(byte)137,80,78,71,13,10,26,10}));
    }
    void admin() {
        db.update("insert into platform_user_role (user_id,role_id,created_at) select ?,id,now() from platform_role where code='PLATFORM_ADMIN'", user);
    }
    org.springframework.test.web.servlet.ResultActions commit(String body) throws Exception {
        return mvc.perform(post("/api/team-miniapps/{id}/code/commit", id).with(authenticated(session.getValue()))
            .header("X-Tenant-Id", tenant).contentType("application/json").content(body));
    }
    @Test void allEndpointsRequireLoginAndCommitRequiresCsrf() throws Exception {
        mvc.perform(get("/api/team-miniapps/code/templates")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/team-miniapps/{id}/code/trial-qrcode", id)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/team-miniapps/{id}/code/commit", id).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
            .contentType("application/json").content(BODY)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/team-miniapps/{id}/code/commit", id).cookie(session).header("X-Tenant-Id", tenant)
            .contentType("application/json").content(BODY)).andExpect(status().isForbidden());
        mvc.perform(post("/api/team-miniapps/{id}/code/commit", id).cookie(session).header("X-Tenant-Id", tenant)
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf().useInvalidToken())
            .contentType("application/json").content(BODY)).andExpect(status().isForbidden());
        verifyNoInteractions(client);
    }
    @Test void templateZeroCommitWhitelistAndImageHeaders() throws Exception {
        var result = mvc.perform(get("/api/team-miniapps/code/templates").cookie(session).header("X-Tenant-Id", tenant))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].templateId").value(0)).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("hidden-token"));
        commit(BODY.substring(0, BODY.length()-1) + ",\"ext_json\":\"evil\",\"appid\":\"evil\",\"token\":\"evil\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
        verify(client, times(1)).commitCode(eq("authorizer-test-token"), argThat(body -> body.get("ext_json").toString().contains(appid) && !body.toString().contains("evil")));
        mvc.perform(get("/api/team-miniapps/{id}/code/trial-qrcode", id).cookie(session).header("X-Tenant-Id", tenant))
            .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
            .andExpect(header().string("Cache-Control", "no-store")).andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }
    @Test void ordinaryRoleAndMissingTeamPermissionDeniedAcrossAllEndpoints() throws Exception {
        db.update("delete from platform_user_role where user_id=?", user);
        allForbidden();
        admin();
        db.update("update tenant_member set member_type='MEMBER' where tenant_id=? and user_id=?", tenant, user);
        db.update("delete from member_role where member_id in (select id from tenant_member where tenant_id=? and user_id=?)", tenant, user);
        allForbidden();
        verifyNoInteractions(client);
    }
    void allForbidden() throws Exception {
        mvc.perform(get("/api/team-miniapps/code/templates").cookie(session).header("X-Tenant-Id", tenant)).andExpect(status().isForbidden());
        commit(BODY).andExpect(status().isForbidden());
        mvc.perform(get("/api/team-miniapps/{id}/code/trial-qrcode", id).cookie(session).header("X-Tenant-Id", tenant)).andExpect(status().isForbidden());
    }
    @Test void foreignTargetAndForeignTeamDenied() throws Exception {
        db.update("update team_miniapp set tenant_id=? where id=?", tenant + 999999, id);
        commit(BODY).andExpect(status().isNotFound());
        mvc.perform(get("/api/team-miniapps/{id}/code/trial-qrcode", id).cookie(session).header("X-Tenant-Id", tenant)).andExpect(status().isNotFound());
        mvc.perform(get("/api/team-miniapps/code/templates").cookie(session).header("X-Tenant-Id", tenant + 999999)).andExpect(status().isForbidden());
        verifyNoInteractions(client);
    }
    @Test void manualDisabledRevokedAndMissingWechatScopeDenied() throws Exception {
        for (String change : List.of("binding_type='MANUAL'", "binding_type='OFFICIAL',enabled=false", "enabled=true,auth_status='REVOKED'")) {
            db.update("update team_miniapp set " + change + " where id=?", id);
            commit(BODY).andExpect(status().isForbidden());
        }
        db.update("update team_miniapp set auth_status='AUTHORIZED' where id=?", id);
        db.update("update wechat_authorizer set auth_status='REVOKED' where appid=?", appid);
        commit(BODY).andExpect(status().isForbidden());
        db.update("update wechat_authorizer set auth_status='AUTHORIZED',scopes='[]' where appid=?", appid);
        commit(BODY).andExpect(status().isForbidden());
        verify(client, never()).commitCode(anyString(), anyMap());
    }
    @Test void invalidInputAndAbsentTemplateRejectedBeforeCommit() throws Exception {
        for (String body : List.of("{}", BODY.replace("\"templateId\":0", "\"templateId\":-1"),
            BODY.replace("1.0", "x".repeat(65)), BODY.replace("体验测试", "x".repeat(1025)))) commit(body).andExpect(status().isBadRequest());
        when(client.getTemplates(anyString())).thenReturn(json.readTree("{\"errcode\":0,\"template_list\":[]}"));
        commit(BODY).andExpect(status().isBadRequest());
        verify(client, never()).commitCode(anyString(), anyMap());
    }
    @Test void scope86AllowsOnlyQrAndQrErrorsAreSafe() throws Exception {
        db.update("update wechat_authorizer set scopes=? where appid=?", "[{\"funcscope_category\":{\"id\":86}}]", appid);
        commit(BODY).andExpect(status().isForbidden());
        mvc.perform(get("/api/team-miniapps/{id}/code/trial-qrcode", id).cookie(session).header("X-Tenant-Id", tenant))
            .andExpect(status().isOk());
        when(client.trialQrCode(anyString(), nullable(String.class))).thenThrow(WechatCodeModels.rejected(40001));
        mvc.perform(get("/api/team-miniapps/{id}/code/trial-qrcode", id).cookie(session).header("X-Tenant-Id", tenant))
            .andExpect(status().isBadGateway()).andExpect(jsonPath("$.errorCode").value("WECHAT_CODE_REJECTED"))
            .andExpect(header().string("Cache-Control", "no-store")).andExpect(header().string("X-Content-Type-Options", "nosniff"));
        verify(client, never()).commitCode(anyString(), anyMap());
    }
    @Test void removedMembershipAndDisabledPlatformRoleDenied() throws Exception {
        db.update("update tenant_member set status='REMOVED' where tenant_id=? and user_id=?", tenant, user);
        allForbidden();
        db.update("update tenant_member set status='ACTIVE' where tenant_id=? and user_id=?", tenant, user);
        db.update("update platform_role set status='DISABLED' where code='PLATFORM_ADMIN'");
        try { allForbidden(); }
        finally { db.update("update platform_role set status='ACTIVE' where code='PLATFORM_ADMIN'"); }
        verifyNoInteractions(client);
    }
    @Test void unknownCommitResponseIsExplicitAndSanitized() throws Exception {
        when(client.commitCode(anyString(), anyMap())).thenThrow(new RuntimeException("https://api.weixin.qq.com?access_token=authorizer-test-token"));
        var response = commit(BODY).andExpect(status().isBadGateway()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data.status").value("UNKNOWN")).andExpect(jsonPath("$.errorCode").value("WECHAT_COMMIT_UNKNOWN")).andReturn();
        assertFalse(response.getResponse().getContentAsString().contains("authorizer-test-token"));
        verify(client, times(1)).commitCode(anyString(), anyMap());
    }
}
