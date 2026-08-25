package com.antshorttv.bootstrap;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antshorttv.member.MemberStatus;
import com.antshorttv.member.TenantMemberEntity;
import com.antshorttv.member.TenantMemberMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AuthBootstrapControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantMemberMapper tenantMemberMapper;

    @Test
    void returnsOnboardingContextForUserWithoutTenants() throws Exception {
        Registration user = register("13800000701");

        mockMvc.perform(get("/api/auth/bootstrap").cookie(user.session()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user.id", is(user.userId().intValue())))
            .andExpect(jsonPath("$.data.session.expiresAt").isNotEmpty())
            .andExpect(jsonPath("$.data.platform.roles").isArray())
            .andExpect(jsonPath("$.data.tenants").isEmpty())
            .andExpect(jsonPath("$.data.selectedTenant").doesNotExist())
            .andExpect(jsonPath("$.data.nextAction", is("CREATE_OR_JOIN_TEAM")));
    }

    @Test
    void selectsSingleTenantWithoutPersistingServerState() throws Exception {
        Registration user = register("13800000702");
        long tenantId = createTenant(user.session(), "Single Tenant");

        mockMvc.perform(get("/api/auth/bootstrap").cookie(user.session()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.selectedTenant.tenant.id", is((int) tenantId)))
            .andExpect(jsonPath("$.data.selectedTenant.permissions", hasItem("PROJECT:VIEW_ALL")))
            .andExpect(jsonPath("$.data.nextAction", is("ENTER_WORKSPACE")));
    }

    @Test
    void multipleTenantHeadersRemainIndependentAndMissingHeaderRequiresSelection() throws Exception {
        Registration user = register("13800000703");
        long first = createTenant(user.session(), "First Tenant");
        long second = createTenant(user.session(), "Second Tenant");

        mockMvc.perform(get("/api/auth/bootstrap").cookie(user.session()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.selectedTenant").doesNotExist())
            .andExpect(jsonPath("$.data.nextAction", is("SELECT_TENANT")));

        mockMvc.perform(get("/api/auth/bootstrap")
                .cookie(user.session())
                .header("X-Tenant-Id", first))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.selectedTenant.tenant.id", is((int) first)));

        mockMvc.perform(get("/api/auth/bootstrap")
                .cookie(user.session())
                .header("X-Tenant-Id", second))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.selectedTenant.tenant.id", is((int) second)));
    }

    @Test
    void removedSelectionReturnsRecoverableUnavailableContextWithoutPermissions() throws Exception {
        Registration user = register("13800000704");
        long tenantId = createTenant(user.session(), "Removed Tenant");
        TenantMemberEntity member = tenantMemberMapper.selectByTenantIdAndUserId(tenantId, user.userId());
        member.setStatus(MemberStatus.REMOVED.name());
        tenantMemberMapper.updateById(member);

        mockMvc.perform(get("/api/auth/bootstrap")
                .cookie(user.session())
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.selectedTenant").doesNotExist())
            .andExpect(jsonPath("$.data.unavailableSelectionReason", is("MEMBERSHIP_REMOVED")))
            .andExpect(jsonPath("$.data.nextAction", is("CREATE_OR_JOIN_TEAM")));
    }

    private Registration register(String mobile) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mobile":"%s","verificationCode":"123456","nickname":"Bootstrap User","password":"Password123"}
                    """.formatted(mobile)))
            .andExpect(status().isOk())
            .andReturn();
        Number userId = com.jayway.jsonpath.JsonPath.read(
            result.getResponse().getContentAsString(), "$.data.user.id");
        return new Registration(userId.longValue(), result.getResponse().getCookie("ANT_SHORT_SESSION"));
    }

    private long createTenant(Cookie session, String name) throws Exception {
        MvcResult safe = mockMvc.perform(get("/api/tenants/my").cookie(session))
            .andExpect(status().isOk())
            .andReturn();
        Cookie csrf = safe.getResponse().getCookie("XSRF-TOKEN");
        MvcResult result = mockMvc.perform(post("/api/tenants")
                .cookie(session, csrf)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","type":"STUDIO","description":"bootstrap"}
                    """.formatted(name)))
            .andExpect(status().isOk())
            .andReturn();
        Number id = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private record Registration(Long userId, Cookie session) {
    }
}
