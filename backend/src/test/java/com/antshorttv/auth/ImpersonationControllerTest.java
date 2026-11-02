package com.antshorttv.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.antshorttv.adminauth.AdminAuthenticatorService;
import com.antshorttv.authsession.AuthSessionService;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.user.UserEntity;
import com.antshorttv.user.UserMapper;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ImpersonationControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserMapper users;
    @Autowired AuthSessionService sessions;
    @MockBean AdminAuthenticatorService authenticator;
    UserEntity admin;
    UserEntity target;
    Cookie adminCookie;
    Cookie targetCookie;
    Long tenantId;
    Long memberId;

    @BeforeEach
    void setup() {
        admin = user("Administrator");
        target = user("Target member");
        jdbc.update("insert into platform_user_role (user_id,role_id,created_at) select ?,id,now() from platform_role where code='PLATFORM_ADMIN'", admin.getId());
        adminCookie = cookie(admin);
        targetCookie = cookie(target);
        String code = UUID.randomUUID().toString().substring(0, 20);
        jdbc.update("insert into tenant (code,name,type,status,created_at,updated_at) values (?,?,'STUDIO','ACTIVE',now(),now())", code, "Target tenant");
        tenantId = jdbc.queryForObject("select id from tenant where code=?", Long.class, code);
        jdbc.update("insert into tenant_member (tenant_id,user_id,member_type,status,joined_at,created_at,updated_at) values (?,?,'OWNER','ACTIVE',now(),now(),now())", tenantId, target.getId());
        memberId = jdbc.queryForObject("select id from tenant_member where tenant_id=? and user_id=?", Long.class, tenantId, target.getId());
        jdbc.update("update tenant set owner_member_id=? where id=?", memberId, tenantId);
    }

    @Test
    void detailIncludesAllMembersAndTheirEligibility() throws Exception {
        UserEntity removed = user("Removed member");
        jdbc.update("insert into tenant_member (tenant_id,user_id,member_type,status,joined_at,created_at,updated_at) values (?,?,'MEMBER','REMOVED',now(),now(),now())", tenantId, removed.getId());
        mvc.perform(get("/api/platform/tenants/{id}", tenantId).cookie(adminCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.members.length()", is(2)))
            .andExpect(jsonPath("$.data.members[0].userId", is(target.getId().intValue())))
            .andExpect(jsonPath("$.data.members[0].canImpersonate", is(true)))
            .andExpect(jsonPath("$.data.members[1].status", is("REMOVED")))
            .andExpect(jsonPath("$.data.members[1].canImpersonate", is(false)));
    }

    @Test
    void switchesToTargetPermissionsAuditsWritesAndReturnsWithoutExtendingSession() throws Exception {
        LocalDateTime expiry = sessions.authenticate(adminCookie.getValue()).orElseThrow().expiresAt();
        String originalId = sessions.authenticate(adminCookie.getValue()).orElseThrow().sessionId();
        Cookie impersonated = start();
        assertThat(sessions.authenticate(adminCookie.getValue())).isEmpty();
        assertThat(sessions.authenticate(targetCookie.getValue())).isPresent();
        mvc.perform(get("/api/auth/bootstrap").cookie(impersonated).header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user.id", is(target.getId().intValue())))
            .andExpect(jsonPath("$.data.session.impersonation.administratorId", is(admin.getId().intValue())))
            .andExpect(jsonPath("$.data.selectedTenant.tenant.id", is(tenantId.intValue())))
            .andExpect(jsonPath("$.data.platform.roles").isEmpty());
        mvc.perform(get("/api/platform/tenants").cookie(impersonated)).andExpect(status().isForbidden());
        mvc.perform(put("/api/tenants/{id}", tenantId).cookie(impersonated).with(csrf())
                .header("X-Auth-Session-Id", originalId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Wrong identity\",\"type\":\"STUDIO\"}"))
            .andExpect(status().isConflict());
        mvc.perform(put("/api/tenants/{id}", tenantId).cookie(impersonated).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Updated by target\",\"type\":\"STUDIO\"}"))
            .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select name from tenant where id=?", String.class, tenantId)).isEqualTo("Updated by target");
        assertThat(jdbc.queryForObject("select detail_json from operation_log where operation='UPDATE_TENANT' and user_id=? and tenant_id=?", String.class, target.getId(), tenantId))
            .contains("\"administratorId\":" + admin.getId()).contains("\"targetUserId\":" + target.getId());
        assertThat(jdbc.queryForList("select detail_json from operation_log where operation='IMPERSONATION_REQUEST' and user_id=?", String.class, admin.getId()))
            .anySatisfy(detail -> assertThat(detail).contains("\"targetUserId\":" + target.getId()).contains("PUT"));
        Cookie restored = stop(impersonated);
        assertThat(sessions.authenticate(impersonated.getValue())).isEmpty();
        assertThat(sessions.authenticate(restored.getValue()).orElseThrow().userId()).isEqualTo(admin.getId());
        assertThat(sessions.authenticate(restored.getValue()).orElseThrow().expiresAt()).isEqualTo(expiry);
        mvc.perform(get("/api/platform/tenants").cookie(restored)).andExpect(status().isOk());
    }

    @Test
    void rejectsNonAdminCrossTenantRemovedDisabledAndAdministratorTargets() throws Exception {
        mvc.perform(post(startUrl()).cookie(targetCookie).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body()))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/platform/tenants/{tenant}/members/{member}/impersonation", tenantId + 99999, memberId)
                .cookie(adminCookie).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body()))
            .andExpect(status().isNotFound());
        jdbc.update("update tenant_member set status='REMOVED' where id=?", memberId);
        assertStartForbidden();
        jdbc.update("update tenant_member set status='ACTIVE' where id=?", memberId);
        jdbc.update("update tenant set status='DISABLED' where id=?", tenantId);
        assertStartForbidden();
        jdbc.update("update tenant set status='ACTIVE' where id=?", tenantId);
        jdbc.update("update app_user set status='DISABLED' where id=?", target.getId());
        assertStartForbidden();
        jdbc.update("update app_user set status='ACTIVE' where id=?", target.getId());
        jdbc.update("insert into platform_user_role (user_id,role_id,created_at) select ?,id,now() from platform_role where code='PLATFORM_ADMIN'", target.getId());
        assertStartForbidden();
        assertThat(sessions.authenticate(adminCookie.getValue())).isPresent();
    }

    @Test
    void rejectsBadVerificationAndMissingCsrfWithoutChangingIdentity() throws Exception {
        mvc.perform(post(startUrl()).cookie(adminCookie).contentType(MediaType.APPLICATION_JSON).content(body()))
            .andExpect(status().isForbidden());
        doThrow(new BusinessException(ErrorCode.VALIDATION_ERROR, "验证码错误")).when(authenticator).consume("123456");
        mvc.perform(post(startUrl()).cookie(adminCookie).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body()))
            .andExpect(status().isBadRequest());
        assertThat(sessions.authenticate(adminCookie.getValue())).isPresent();
    }

    @Test
    void canReturnAfterTargetDisabledButCannotReturnIfAdministratorInvalidated() throws Exception {
        Cookie impersonated = start();
        jdbc.update("update app_user set status='DISABLED' where id=?", target.getId());
        mvc.perform(get("/api/auth/bootstrap").cookie(impersonated)).andExpect(status().isUnauthorized());
        Cookie restored = stop(impersonated);
        assertThat(sessions.authenticate(restored.getValue()).orElseThrow().userId()).isEqualTo(admin.getId());
        jdbc.update("update app_user set status='ACTIVE' where id=?", target.getId());
        adminCookie = restored;
        Cookie next = start();
        sessions.invalidateAll(admin.getId(), "PASSWORD_CHANGED");
        mvc.perform(get("/api/auth/bootstrap").cookie(next)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/impersonation/stop").cookie(next).with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void roleRemovalInvalidatesImpersonationAndNestedSwitchIsRejected() throws Exception {
        Cookie impersonated = start();
        mvc.perform(post(startUrl()).cookie(impersonated).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body()))
            .andExpect(status().isForbidden());
        jdbc.update("delete from platform_user_role where user_id=?", admin.getId());
        mvc.perform(get("/api/auth/bootstrap").cookie(impersonated)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/impersonation/stop").cookie(impersonated).with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void targetPasswordChangeBlocksBusinessRequestsButStillAllowsReturn() throws Exception {
        Cookie impersonated = start();
        sessions.invalidateAll(target.getId(), "PASSWORD_CHANGED");
        mvc.perform(get("/api/auth/bootstrap").cookie(impersonated)).andExpect(status().isUnauthorized());
        Cookie restored = stop(impersonated);
        assertThat(sessions.authenticate(restored.getValue()).orElseThrow().userId()).isEqualTo(admin.getId());
    }

    @Test
    void targetPromotionToAdministratorCannotElevateImpersonation() throws Exception {
        Cookie impersonated = start();
        jdbc.update("insert into platform_user_role (user_id,role_id,created_at) select ?,id,now() from platform_role where code='PLATFORM_ADMIN'", target.getId());
        mvc.perform(get("/api/auth/bootstrap").cookie(impersonated)).andExpect(status().isUnauthorized());
        assertThat(sessions.authenticate(stop(impersonated).getValue()).orElseThrow().userId()).isEqualTo(admin.getId());
    }

    @Test
    void logoutCannotRestoreAdministratorAndExpiredSessionCannotBeRestored() throws Exception {
        Cookie impersonated = start();
        mvc.perform(post("/api/auth/logout").cookie(impersonated).with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/auth/impersonation/stop").cookie(impersonated).with(csrf())).andExpect(status().isUnauthorized());
        adminCookie = cookie(admin);
        Cookie expired = start();
        String sessionId = sessions.authenticate(expired.getValue()).orElseThrow().sessionId();
        jdbc.update("update auth_session set expires_at=? where session_id=?", LocalDateTime.now().minusSeconds(1), sessionId);
        mvc.perform(post("/api/auth/impersonation/stop").cookie(expired).with(csrf())).andExpect(status().isUnauthorized());
    }

    private void assertStartForbidden() throws Exception {
        mvc.perform(post(startUrl()).cookie(adminCookie).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body()))
            .andExpect(status().isForbidden());
    }
    private Cookie start() throws Exception {
        MvcResult result = mvc.perform(post(startUrl()).cookie(adminCookie).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.tenantId", is(tenantId.intValue()))).andReturn();
        return result.getResponse().getCookie("ANT_SHORT_SESSION");
    }
    private Cookie stop(Cookie session) throws Exception {
        return mvc.perform(post("/api/auth/impersonation/stop").cookie(session).with(csrf()))
            .andExpect(status().isOk()).andReturn().getResponse().getCookie("ANT_SHORT_SESSION");
    }
    private String startUrl() { return "/api/platform/tenants/" + tenantId + "/members/" + memberId + "/impersonation"; }
    private String body() { return "{\"code\":\"123456\",\"reason\":\"排查用户生成问题\"}"; }
    private Cookie cookie(UserEntity user) { return new Cookie("ANT_SHORT_SESSION", sessions.issue(user, "127.0.0.1", "test").credential()); }
    private UserEntity user(String name) {
        UserEntity user = new UserEntity();
        user.setMobile("139" + System.nanoTime());
        user.setNickname(name);
        user.setPasswordHash("{noop}Password123");
        user.setStatus("ACTIVE");
        user.setTokenVersion(0L);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        users.insert(user);
        return user;
    }
}
