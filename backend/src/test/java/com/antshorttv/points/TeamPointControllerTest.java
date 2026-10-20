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
class TeamPointControllerTest extends com.antshorttv.support.RegistrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void pointEstimateEndpointIsReadOnlyAndRejectsUnsupportedOperations() throws Exception {
        String token = registerUser("13800019007", "Estimate Owner");
        Long tenantId = createTenant(token, "预估团队");
        Long executions = jdbcTemplate.queryForObject("select count(*) from ai_execution_task where tenant_id=?", Long.class, tenantId);
        Long reservations = jdbcTemplate.queryForObject("select count(*) from ai_point_reservation where tenant_id=?", Long.class, tenantId);
        Long ledger = jdbcTemplate.queryForObject("select count(*) from point_ledger where tenant_id=?", Long.class, tenantId);
        mockMvc.perform(post("/api/tenants/%d/points/estimate".formatted(tenantId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"operations\":[{\"url\":\"/api/projects\",\"data\":{}}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.estimatedPoints", is(0)))
            .andExpect(jsonPath("$.data.lines", org.hamcrest.Matchers.hasSize(0)));
        mockMvc.perform(post("/api/tenants/%d/points/estimate".formatted(tenantId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"operations\":[{\"url\":\"/api/unknown-ai\"}]}"))
            .andExpect(status().isBadRequest());
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject("select count(*) from ai_execution_task where tenant_id=?", Long.class, tenantId)).isEqualTo(executions);
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject("select count(*) from ai_point_reservation where tenant_id=?", Long.class, tenantId)).isEqualTo(reservations);
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject("select count(*) from point_ledger where tenant_id=?", Long.class, tenantId)).isEqualTo(ledger);
    }

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

    @Test
    void explainsUsageScenariosAndAccountingStagesForExistingLedgerEntries() throws Exception {
        String token = registerUser("13800019008", "Usage History");
        Long tenantId = createTenant(token, "用途说明团队");
        Long projectId = insertProject(tenantId, "星河短剧");
        String[][] scenarios = {
            {"script_analysis", "SCRIPT_ANALYSIS_TASK", "剧本分析"},
            {"character_extract", "SCRIPT_AI_OPERATION", "AI提取角色"},
            {"ai_image_generate", "AI_IMAGE_TASK", "图片生成"},
            {"ai_video_generate", "AI_VIDEO_TASK", "视频生成"},
            {"video_decomposition_video_analysis", "VIDEO_DECOMPOSITION_EPISODE", "视频拆剧：视频理解"},
            {"video_decomposition_draft_generation", "VIDEO_DECOMPOSITION_EPISODE", "视频拆剧：剧本草稿生成"},
            {"script_review", "REVIEW_TASK", "剧本审核"},
        };
        for (int i = 0; i < scenarios.length; i++) {
            String[] scenario = scenarios[i];
            Long executionId = insertExecution(tenantId, projectId, scenario[0], scenario[1], (long) i + 1);
            insertLedgerEntry(tenantId, "SETTLE", 25, 100, "usage:" + i, null);
            jdbcTemplate.update("update point_ledger set execution_id=?, business_type=?, business_id=? where tenant_id=? and idempotency_key=?",
                executionId, scenario[1], i + 1, tenantId, "usage:" + i);
            mockMvc.perform(get("/api/tenants/%d/points/transactions".formatted(tenantId))
                    .with(com.antshorttv.support.SessionTestSupport.authenticated(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].description",
                    is("《星河短剧》 · " + scenario[2] + " · 任务 #" + executionId + " · 实际消耗（从预扣积分结算）")));
        }
        String[][] stages = {
            {"RESERVE", "积分预扣，任务完成后按实际用量结算"},
            {"INCREMENTAL_RESERVE", "追加预扣，供后续生成或模型调用使用"},
            {"RELEASE", "释放未使用的预扣积分，返还可用余额"},
            {"REFUND", "退回已消耗的积分"},
            {"SETTLEMENT_REVIEW", "用量待核对，尚未完成最终结算"},
        };
        for (String[] stage : stages) {
            insertLedgerEntry(tenantId, stage[0], 0, 100, "stage:" + stage[0], null);
            jdbcTemplate.update("update point_ledger set business_type='AI_IMAGE_TASK', business_id=81 where tenant_id=? and idempotency_key=?",
                tenantId, "stage:" + stage[0]);
            mockMvc.perform(get("/api/tenants/%d/points/transactions".formatted(tenantId))
                    .with(com.antshorttv.support.SessionTestSupport.authenticated(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].description", is("图片生成 · 业务任务 #81 · " + stage[1])));
        }
    }

    @Test
    void doesNotExposeAnotherTenantsTaskOrProjectInDescriptions() throws Exception {
        String token = registerUser("13800019009", "Scoped History");
        Long tenantId = createTenant(token, "明细团队");
        Long otherTenantId = createTenant(token, "其他团队");
        Long foreignProjectId = insertProject(otherTenantId, "其他团队的私有项目");
        Long foreignExecutionId = insertExecution(otherTenantId, foreignProjectId, "character_extract", "SCRIPT_AI_OPERATION", 1L);
        insertLedgerEntry(tenantId, "SETTLE", 25, 100, "foreign-execution", null);
        jdbcTemplate.update("update point_ledger set execution_id=? where tenant_id=?", foreignExecutionId, tenantId);
        mockMvc.perform(get("/api/tenants/%d/points/transactions".formatted(tenantId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records[0].description", is("AI任务 · 实际消耗（从预扣积分结算）")));

        Long executionId = insertExecution(tenantId, foreignProjectId, "future_scene", "SCRIPT_AI_OPERATION", 2L);
        jdbcTemplate.update("update point_ledger set execution_id=? where tenant_id=?", executionId, tenantId);
        mockMvc.perform(get("/api/tenants/%d/points/transactions".formatted(tenantId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records[0].description", is("剧本AI处理 · 任务 #" + executionId + " · 实际消耗（从预扣积分结算）")));
    }

    private Long insertProject(Long tenantId, String name) {
        jdbcTemplate.update("""
            insert into project (tenant_id, name, code, owner_id, status, created_by, created_at, updated_at)
            values (?, ?, 'point-history', 1, 'ACTIVE', 1, now(), now())
            """, tenantId, name);
        return jdbcTemplate.queryForObject("select id from project where tenant_id=? and code='point-history'", Long.class, tenantId);
    }

    private Long insertExecution(Long tenantId, Long projectId, String scene, String businessType, Long businessId) {
        String key = "point-history:" + scene + ":" + businessId;
        jdbcTemplate.update("""
            insert into ai_execution_task
                (tenant_id, user_id, project_id, scene, capability, business_type, business_id,
                 status, phase, client_idempotency_key, trace_id, created_at, updated_at)
            values (?, 1, ?, ?, 'TEXT', ?, ?, 'SUCCEEDED', 'DONE', ?, ?, now(), now())
            """, tenantId, projectId, scene, businessType, businessId, key, key);
        return jdbcTemplate.queryForObject("select id from ai_execution_task where tenant_id=? and client_idempotency_key=?", Long.class, tenantId, key);
    }

    private String registerUser(String mobile, String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mobile":"%s","verificationCode":"%s","nickname":"%s","password":"Password123"}
                    """.formatted(mobile, registrationVerificationCode(mockMvc, mobile), nickname)))
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
