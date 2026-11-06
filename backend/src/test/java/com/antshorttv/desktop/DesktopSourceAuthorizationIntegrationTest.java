package com.antshorttv.desktop;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.antshorttv.authsession.AuthenticatedUser;
import com.antshorttv.common.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DesktopSourceAuthorizationIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DesktopSourceService service;
    @Autowired MockMvc mvc;
    @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;
    long tenant=990011L,user=990012L,other=990013L;
    Long project;

    @BeforeEach void setup() {
        jdbc.update("""
            insert into tenant(id,code,name,type,status,created_at,updated_at)
             values(?,'DESKTOP_AUTH_TEST','Desktop','STUDIO','ACTIVE',now(),now())
            """,tenant);
        jdbc.update("""
            insert into tenant_member(tenant_id,user_id,member_type,status,joined_at,created_at,updated_at)
             values(?,?,'OWNER','ACTIVE',now(),now(),now()),(?,?,'MEMBER','ACTIVE',now(),now(),now())
            """,tenant,user,tenant,other);
        jdbc.update("""
            insert into project(tenant_id,name,code,owner_id,status,created_by,created_at,updated_at)
             values(?,'Desktop target','DESKTOP_TARGET',?,'ACTIVE',?,now(),now())
            """,tenant,user,user);
        project=jdbc.queryForObject("select id from project where tenant_id=? and code='DESKTOP_TARGET'",Long.class,tenant);
        principal(user);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void realProjectGuardBlocksUnassignedMemberAndRechecksMembershipAfterSync() {
        var p=service.sync(tenant,new DesktopSourceModels.SyncRequest("connection","team","project","script","Source",true,
            List.of(new DesktopSourceModels.SyncAsset("hero",null,"Hero","CHARACTER"))));
        service.bind(tenant,p.id(),project);
        assertThat(service.assets(tenant,p.id())).hasSize(1);
        principal(other);
        assertThat(service.list(tenant)).isEmpty();
        assertThatThrownBy(()->service.assets(tenant,p.id())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.bind(tenant,p.id(),project)).isInstanceOf(BusinessException.class);
        principal(user);
        jdbc.update("update tenant_member set status='REMOVED' where tenant_id=? and user_id=?",tenant,user);
        // JDBC bypasses MyBatis' first-level cache in this test's outer transaction.
        sqlSession.clearCache();
        assertThatThrownBy(()->service.assets(tenant,p.id())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.list(tenant)).isInstanceOf(BusinessException.class);
    }

    @Test void headerCannotGrantMembershipAndAnonymousHttpRequestsAreRejected() throws Exception {
        principal(other+100);
        assertThatThrownBy(()->service.list(tenant)).isInstanceOf(BusinessException.class);
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/desktop/source-projects").with(anonymous()).header("X-Tenant-Id",tenant)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/desktop/source-projects/sync-current").with(anonymous()).with(csrf()).header("X-Tenant-Id",tenant)
            .contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
    }

    @Test void currentReplacementCannotRetireAnInaccessibleBoundInventory() {
        var old=service.sync(tenant,new DesktopSourceModels.SyncRequest("connection","team","project","old","Source",true,List.of()));
        service.bind(tenant,old.id(),project);
        principal(other);
        assertThatThrownBy(()->service.syncCurrent(tenant,new DesktopSourceModels.SyncRequest(
            "connection","team","project","current","Source",true,List.of()))).isInstanceOf(BusinessException.class);
        principal(user);
        assertThat(service.list(tenant)).extracting(DesktopSourceModels.SourceProject::id).containsExactly(old.id());
    }

    @Test void inventorySchemaStoresOnlyNamesIdentifiersAndMappingMetadata() {
        var columns=jdbc.queryForList("select column_name from information_schema.columns where table_name in ('desktop_source_project','desktop_source_asset')",String.class);
        assertThat(columns).doesNotContain("payload","raw_payload","script_content","prompt","image","image_url","credential","token","model_key","api_key");
    }

    private void principal(long id) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            new AuthenticatedUser(id,"desktop","test-session",LocalDateTime.now().plusHours(1)),null,List.of()));
    }
}
