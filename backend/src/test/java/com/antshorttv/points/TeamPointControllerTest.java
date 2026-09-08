package com.antshorttv.points;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class TeamPointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void manualPointAdjustmentEndpointIsUnavailable() throws Exception {
        String token = registerUser("13800019002", "Point Admin");
        Long tenantId = createTenant(token, "积分充值团队");

        mockMvc.perform(post("/api/tenants/%d/points/adjust".formatted(tenantId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"amount":50,"description":"测试充值"}
                    """))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/tenants/%d/points/account".formatted(tenantId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.balance", is(0)))
            .andExpect(jsonPath("$.data.totalGranted", is(0)))
            .andExpect(jsonPath("$.data.totalConsumed", is(0)));
    }

    @Test
    void distinguishesCommercialAndHistoricalGrantTransactions() throws Exception {
        String token = registerUser("13800019003", "Point History");
        Long tenantId = createTenant(token, "积分历史团队");
        mockMvc.perform(get("/api/tenants/%d/points/account".formatted(tenantId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token)))
            .andExpect(status().isOk());
        jdbcTemplate.update("""
            insert into point_ledger
              (tenant_id, entry_type, amount, available_balance_after, reserved_balance_after,
               idempotency_key, description, created_at)
            values (?, 'GRANT', 25, 25, 0, 'legacy-manual-grant', '历史手工增加', now())
            """, tenantId);
        jdbcTemplate.update("""
            insert into point_ledger
              (tenant_id, entry_type, amount, available_balance_after, reserved_balance_after,
               idempotency_key, description, created_at)
            values (?, 'GRANT', 100, 125, 0, 'order:101:points', '商业化权益发放', now())
            """, tenantId);
        jdbcTemplate.update("""
            insert into point_ledger
              (tenant_id, entry_type, amount, available_balance_after, reserved_balance_after,
               idempotency_key, description, created_at)
            values (?, 'GRANT', 300, 425, 0, 'subscription:201:period:1', '会员周期积分发放', now())
            """, tenantId);

        mockMvc.perform(get("/api/tenants/%d/points/transactions".formatted(tenantId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total", is(3)))
            .andExpect(jsonPath("$.data.records[0].transactionType", is("ENTITLEMENT_GRANT")))
            .andExpect(jsonPath("$.data.records[0].description", is("会员周期积分发放")))
            .andExpect(jsonPath("$.data.records[1].transactionType", is("ENTITLEMENT_GRANT")))
            .andExpect(jsonPath("$.data.records[1].description", is("商业化权益发放")))
            .andExpect(jsonPath("$.data.records[2].transactionType", is("ADJUST_GRANT")))
            .andExpect(jsonPath("$.data.records[2].description", is("历史手工增加")));
    }

    @Test
    void returnsDebitTransactionAmountsAsNegativeValues() throws Exception {
        String token = registerUser("13800019005", "Point Signs");
        Long tenantId = createTenant(token, "积分方向团队");
        mockMvc.perform(get("/api/tenants/%d/points/account".formatted(tenantId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token)))
            .andExpect(status().isOk());
        insertLedgerEntry(tenantId, "RESERVE", 40, 60, "reserve:sign", "积分预扣");
        insertLedgerEntry(tenantId, "INCREMENTAL_RESERVE", 5, 55, "reserve:sign:incremental", "追加预扣");
        insertLedgerEntry(tenantId, "SETTLE", 30, 60, "settle:sign", "积分消耗");
        insertLedgerEntry(tenantId, "RELEASE", 10, 70, "release:sign", "预扣释放");
        insertLedgerEntry(tenantId, "REFUND", 30, 100, "refund:sign", "积分退回");

        mockMvc.perform(get("/api/tenants/%d/points/transactions".formatted(tenantId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records[0].changeAmount", is(30)))
            .andExpect(jsonPath("$.data.records[1].changeAmount", is(10)))
            .andExpect(jsonPath("$.data.records[2].changeAmount", is(-30)))
            .andExpect(jsonPath("$.data.records[3].transactionType", is("INCREMENTAL_RESERVE")))
            .andExpect(jsonPath("$.data.records[3].changeAmount", is(-5)))
            .andExpect(jsonPath("$.data.records[4].changeAmount", is(-40)));
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

    private void insertLedgerEntry(
        Long tenantId,
        String entryType,
        int amount,
        int balanceAfter,
        String idempotencyKey,
        String description
    ) {
        jdbcTemplate.update("""
            insert into point_ledger
              (tenant_id, entry_type, amount, available_balance_after, reserved_balance_after,
               idempotency_key, description, created_at)
            values (?, ?, ?, ?, 0, ?, ?, now())
            """, tenantId, entryType, amount, balanceAfter, idempotencyKey, description);
    }

    private Long readLong(MvcResult result, String path) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value.longValue();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
