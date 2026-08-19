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
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
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
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"amount":50,"description":"测试充值"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.balance", is(50)))
            .andExpect(jsonPath("$.data.totalGranted", is(50)))
            .andExpect(jsonPath("$.data.totalConsumed", is(0)));

        mockMvc.perform(post("/api/tenants/%d/points/adjust".formatted(tenantId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"amount":-10,"description":"测试扣减"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.balance", is(40)))
            .andExpect(jsonPath("$.data.totalGranted", is(50)))
            .andExpect(jsonPath("$.data.totalConsumed", is(10)));

        mockMvc.perform(get("/api/tenants/%d/points/transactions".formatted(tenantId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
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
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"amount":-1,"description":"超额扣减"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode", is("TEAM_POINTS_INSUFFICIENT")));
    }

    private String registerUser(String mobile, String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mobile":"%s","verificationCode":"123456","nickname":"%s","password":"Password123"}
                    """.formatted(mobile, nickname)))
            .andExpect(status().isOk())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private Long createTenant(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tenants")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
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
