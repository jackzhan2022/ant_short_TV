package com.antshorttv.points;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class TeamPointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsZeroBalanceForNewTenant() throws Exception {
        String token = registerUser("13800019001", "Point Owner");
        Long tenantId = createTenant(token, "积分团队");

        mockMvc.perform(get("/api/tenants/%d/points/account".formatted(tenantId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.tenantId", is(tenantId.intValue())))
            .andExpect(jsonPath("$.data.balance", is(0)))
            .andExpect(jsonPath("$.data.totalGranted", is(0)))
            .andExpect(jsonPath("$.data.totalConsumed", is(0)));
    }

    @Test
    void ownerAdjustsTeamPointsAndListsTransactions() throws Exception {
        String token = registerUser("13800019002", "Point Admin");
        Long tenantId = createTenant(token, "积分充值团队");

        mockMvc.perform(post("/api/tenants/%d/points/adjust".formatted(tenantId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"amount":50,"description":"测试充值"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.balance", is(50)))
            .andExpect(jsonPath("$.data.totalGranted", is(50)))
            .andExpect(jsonPath("$.data.totalConsumed", is(0)));

        mockMvc.perform(post("/api/tenants/%d/points/adjust".formatted(tenantId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"amount":-10,"description":"测试扣减"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.balance", is(40)))
            .andExpect(jsonPath("$.data.totalGranted", is(50)))
            .andExpect(jsonPath("$.data.totalConsumed", is(10)));

        mockMvc.perform(get("/api/tenants/%d/points/transactions".formatted(tenantId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total", is(2)))
            .andExpect(jsonPath("$.data.records", hasSize(2)))
            .andExpect(jsonPath("$.data.records[0].changeAmount", is(-10)))
            .andExpect(jsonPath("$.data.records[0].balanceAfter", is(40)))
            .andExpect(jsonPath("$.data.records[0].transactionType", is("ADJUST_DEDUCT")))
            .andExpect(jsonPath("$.data.records[1].changeAmount", is(50)))
            .andExpect(jsonPath("$.data.records[1].transactionType", is("ADJUST_GRANT")));
    }

    @Test
    void preventsAdjustingBelowZero() throws Exception {
        String token = registerUser("13800019003", "Point Guard");
        Long tenantId = createTenant(token, "积分保护团队");

        mockMvc.perform(post("/api/tenants/%d/points/adjust".formatted(tenantId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"amount":-1,"description":"超额扣减"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode", is("TEAM_POINTS_INSUFFICIENT")));
    }

    @Test
    void exposesTenantScopedReconciliation() throws Exception {
        String token = registerUser("13800019004", "积分对账");
        Long tenantId = createTenant(token, "对账团队");
        mockMvc.perform(get("/api/tenants/%d/points/reconciliation".formatted(tenantId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.tenantId", is(tenantId.intValue())))
            .andExpect(jsonPath("$.data.matches", is(true)));
    }

    @Test
    void adjustmentIsIdempotentAndRejectsConflictingReuse() throws Exception {
        String token = registerUser("13800019005", "积分幂等");
        Long tenantId = createTenant(token, "幂等团队");
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/tenants/%d/points/adjust".formatted(tenantId))
                    .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                    .header("Idempotency-Key", "grant-once")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\":50,\"description\":\"一次充值\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance", is(50)));
        }
        mockMvc.perform(post("/api/tenants/%d/points/adjust".formatted(tenantId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("Idempotency-Key", "grant-once")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":60,\"description\":\"冲突充值\"}"))
            .andExpect(status().isBadRequest());
    }

    private String registerUser(String mobile, String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mobile":"%s","verificationCode":"123456","nickname":"%s","password":"Password123"}
                    """.formatted(mobile, nickname)))
            .andExpect(status().isOk())
            .andReturn();
        return com.antshorttv.support.SessionTestSupport.sessionCredential(result);
    }

    private Long createTenant(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tenants")
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","type":"STUDIO","description":"积分测试"}
                    """.formatted(name)))
            .andExpect(status().isOk())
            .andReturn();
        return readLong(result, "$.data.id");
    }

    private Long readLong(MvcResult result, String path) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value.longValue();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
