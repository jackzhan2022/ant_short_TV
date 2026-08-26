package com.antshorttv.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antshorttv.support.SessionTestSupport;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class PlatformAiAccountingControllerTest {
    private static final AtomicInteger MOBILE_SEQUENCE = new AtomicInteger(17100);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void ordinaryUserCannotPublishPricingOrViewAccounting() throws Exception {
        Registration user = registerUser();

        mockMvc.perform(post("/api/platform/ai/models/1/price-versions")
                .with(SessionTestSupport.authenticated(user.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(modelPriceRequest()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode", is("FORBIDDEN")));

        mockMvc.perform(post("/api/platform/ai/models/1/point-price-versions")
                .with(SessionTestSupport.authenticated(user.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(pointPolicyRequest()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode", is("FORBIDDEN")));

        mockMvc.perform(get("/api/platform/ai/executions/1/accounting")
                .with(SessionTestSupport.authenticated(user.token())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode", is("FORBIDDEN")));
    }

    @Test
    void platformAdministratorPublishesVersionsAndViewsSecretFreeAccountingDetail() throws Exception {
        Registration admin = registerUser();
        grantPlatformAdmin(admin.userId());
        Long modelId = createModel(admin.userId());

        MvcResult priceResult = mockMvc.perform(post("/api/platform/ai/models/{modelId}/price-versions", modelId)
                .with(SessionTestSupport.authenticated(admin.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(modelPriceRequest()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id", notNullValue()))
            .andExpect(jsonPath("$.data.modelId", is(modelId.intValue())))
            .andExpect(jsonPath("$.data.status", is("PUBLISHED")))
            .andExpect(jsonPath("$.data.components", hasSize(2)))
            .andReturn();
        Long priceVersionId = ((Number) JsonPath.read(
            priceResult.getResponse().getContentAsString(), "$.data.id")).longValue();

        MvcResult policyResult = mockMvc.perform(post(
                "/api/platform/ai/models/{modelId}/point-price-versions", modelId)
                .with(SessionTestSupport.authenticated(admin.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(pointPolicyRequest()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id", notNullValue()))
            .andExpect(jsonPath("$.data.modelId", is(modelId.intValue())))
            .andExpect(jsonPath("$.data.versionNo", is(1)))
            .andExpect(jsonPath("$.data.status", is("PUBLISHED")))
            .andExpect(jsonPath("$.data.components", hasSize(2)))
            .andReturn();
        Long policyVersionId = ((Number) JsonPath.read(
            policyResult.getResponse().getContentAsString(), "$.data.id")).longValue();

        mockMvc.perform(get("/api/platform/ai/models/{modelId}/billing", modelId)
                .with(SessionTestSupport.authenticated(admin.token()))
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.costPrices", hasSize(1)))
            .andExpect(jsonPath("$.data.pointPrices", hasSize(1)));

        Long executionId = createAccountingDetail(
            admin.userId(), modelId, priceVersionId, policyVersionId);
        MvcResult detailResult = mockMvc.perform(get(
                "/api/platform/ai/executions/{executionId}/accounting", executionId)
                .with(SessionTestSupport.authenticated(admin.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.execution.id", is(executionId.intValue())))
            .andExpect(jsonPath("$.data.execution.usageCostStatus", is("PRICED")))
            .andExpect(jsonPath("$.data.usageLines[0].metric", is("CALL")))
            .andExpect(jsonPath("$.data.costLines[0].currency", is("USD")))
            .andExpect(jsonPath("$.data.billingEvidence.costPriceVersionId", is(priceVersionId.intValue())))
            .andExpect(jsonPath("$.data.billingEvidence.pointPriceVersionId", is(policyVersionId.intValue())))
            .andExpect(jsonPath("$.data.billingEvidence.pointComponents[0].id", notNullValue()))
            .andExpect(jsonPath("$.data.settlement.reservation.status", is("SETTLED")))
            .andExpect(jsonPath("$.data.settlement.ledger", hasSize(1)))
            .andReturn();

        String responseBody = detailResult.getResponse().getContentAsString();
        assertThat(responseBody)
            .doesNotContain("stored-provider-secret")
            .doesNotContain("stored-prompt-secret")
            .doesNotContain("authorizedUsageJson")
            .doesNotContain("redactedInputJson");
    }

    @Test
    void crossModelCostPriceRevocationDoesNotMutateTheVersion() throws Exception {
        Registration admin = registerUser();
        grantPlatformAdmin(admin.userId());
        Long ownerModelId = createModel(admin.userId());
        Long otherModelId = createModel(admin.userId() + 100000L);
        MvcResult result = mockMvc.perform(post(
                "/api/platform/ai/models/{modelId}/price-versions", ownerModelId)
                .with(SessionTestSupport.authenticated(admin.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(modelPriceRequest()))
            .andExpect(status().isOk())
            .andReturn();
        Long versionId = ((Number) JsonPath.read(
            result.getResponse().getContentAsString(), "$.data.id")).longValue();

        mockMvc.perform(post(
                "/api/platform/ai/models/{modelId}/cost-price-versions/{versionId}/revoke",
                otherModelId, versionId)
                .with(SessionTestSupport.authenticated(admin.token())))
            .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject(
            "select status from ai_model_price_version where id = ?", String.class, versionId
        )).isEqualTo("PUBLISHED");
    }

    private Registration registerUser() throws Exception {
        String mobile = "138000" + MOBILE_SEQUENCE.incrementAndGet();
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mobile":"%s","verificationCode":"123456","nickname":"Accounting Operator","password":"Password123"}
                    """.formatted(mobile)))
            .andExpect(status().isOk())
            .andReturn();
        Number userId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.user.id");
        return new Registration(userId.longValue(), SessionTestSupport.sessionCredential(result));
    }

    private void grantPlatformAdmin(Long userId) {
        jdbc.update("""
            insert into platform_user_role (user_id, role_id, created_at)
            select ?, id, now() from platform_role where code = 'PLATFORM_ADMIN'
            """, userId);
    }

    private Long createModel(Long userId) {
        Long providerId = jdbc.queryForObject(
            "select id from ai_provider where code = 'OpenAI'", Long.class);
        String code = "ACCOUNTING_TEST_" + userId;
        jdbc.update("""
            insert into ai_model
              (provider_id, code, name, model_code, service_type, description,
               status, is_default, sort, config_json, created_at, updated_at)
            values (?, ?, 'Accounting Test', 'accounting-test', 'TEXT', null,
                    'ENABLED', false, 0, null, now(), now())
            """, providerId, code);
        return jdbc.queryForObject("select id from ai_model where code = ?", Long.class, code);
    }

    private Long createAccountingDetail(
        Long userId,
        Long modelId,
        Long priceVersionId,
        Long policyVersionId
    ) {
        long tenantId = 81000L + userId;
        String clientKey = "accounting-detail-" + userId;
        jdbc.update("""
            insert into ai_execution_task
              (tenant_id, user_id, project_id, scene, capability, business_type, business_id,
               requested_model_id, resolved_model_id, redacted_input_json, status, phase,
               progress, execution_version, client_idempotency_key, trace_id, priority,
               retryable, usage_cost_status, provider_cost_summary_json,
               point_settlement_status, reserved_points, settled_points, released_points,
               cost_price_version_id, point_price_version_id, created_at, updated_at, completed_at)
            values (?, ?, null, 'script_generate', 'TEXT_GENERATION', 'SCRIPT_AI_OPERATION', 91,
                    ?, ?, '{"prompt":"stored-prompt-secret"}', 'SUCCEEDED', 'COMPLETED',
                    100, 1, ?, ?, 100, false, 'PRICED', '{"USD":0.01}',
                    'SETTLED', 2, 1, 1, ?, ?, now(), now(), now())
            """, tenantId, userId, modelId, modelId, clientKey, "trace-" + userId,
            priceVersionId, policyVersionId);
        Long executionId = jdbc.queryForObject(
            "select id from ai_execution_task where tenant_id = ? and client_idempotency_key = ?",
            Long.class, tenantId, clientKey);

        jdbc.update("""
            insert into ai_usage_line
              (tenant_id, execution_id, attempt_id, ai_call_log_id, model_id, metric,
               quantity, unit, source, dimensions_json, dimensions_key, observed_at, created_at)
            values (?, ?, null, null, ?, 'CALL', 1, 'call', 'REQUEST_DERIVED',
                    '{"credential":"stored-provider-secret"}', '', now(), now())
            """, tenantId, executionId, modelId);
        Long usageLineId = jdbc.queryForObject(
            "select id from ai_usage_line where execution_id = ?", Long.class, executionId);
        Long priceComponentId = jdbc.queryForObject(
            "select min(id) from ai_model_price_component where price_version_id = ?",
            Long.class, priceVersionId);
        jdbc.update("""
            insert into ai_usage_cost_line
              (tenant_id, execution_id, usage_line_id, price_version_id, price_component_id,
               model_id, metric, quantity, unit_size, unit_price, currency, raw_cost,
               rounded_cost, pricing_status, created_at)
            values (?, ?, ?, ?, ?, ?, 'CALL', 1, 1, 0.01, 'USD', 0.01, 0.01,
                    'PRICED', now())
            """, tenantId, executionId, usageLineId, priceVersionId, priceComponentId, modelId);
        jdbc.update("""
            insert into ai_point_reservation
              (tenant_id, user_id, execution_id, execution_version, business_type, business_id,
               scene, policy_version_id, point_price_version_id, status, authorized_usage_json, dimensions_json,
               reserved_points, settled_points, released_points, refunded_points,
               idempotency_key, created_at, settled_at, updated_at)
            values (?, ?, ?, 1, 'SCRIPT_AI_OPERATION', 91, 'script_generate', null, ?, 'SETTLED',
                    '{"apiKey":"stored-provider-secret"}', '{}', 2, 1, 1, 0,
                    ?, now(), now(), now())
            """, tenantId, userId, executionId, policyVersionId, "reservation-" + userId);
        Long reservationId = jdbc.queryForObject(
            "select id from ai_point_reservation where execution_id = ?", Long.class, executionId);
        jdbc.update("""
            insert into point_ledger
              (tenant_id, user_id, execution_id, execution_version, business_type, business_id,
               reservation_id, policy_version_id, entry_type, amount,
               available_balance_after, reserved_balance_after, idempotency_key, created_at)
            values (?, ?, ?, 1, 'SCRIPT_AI_OPERATION', 91, ?, ?, 'SETTLE', 1, 9, 0, ?, now())
            """, tenantId, userId, executionId, reservationId, policyVersionId, "ledger-" + userId);
        return executionId;
    }

    private String modelPriceRequest() {
        LocalDateTime effectiveFrom = LocalDateTime.now().plusDays(1).withNano(0);
        return """
            {
              "effectiveFrom": "%s",
              "components": [
                {"metric":"CALL","unitSize":1,"unitPrice":0.01,"currency":"USD","dimensions":{}},
                {"metric":"INPUT_TOKEN","unitSize":1000,"unitPrice":0.002,"currency":"USD","dimensions":{}}
              ]
            }
            """.formatted(effectiveFrom);
    }

    private String pointPolicyRequest() {
        LocalDateTime effectiveFrom = LocalDateTime.now().plusDays(1).withNano(0);
        return """
            {
              "effectiveFrom":"%s",
              "components":[
                {"metric":"CALL","unitSize":1,"pointRate":1,"dimensions":{}},
                {"metric":"INPUT_TOKEN","unitSize":1000,"pointRate":0.5,"dimensions":{}}
              ]
            }
            """.formatted(effectiveFrom);
    }

    private record Registration(Long userId, String token) {
    }
}
