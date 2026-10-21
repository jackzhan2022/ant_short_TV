package com.antshorttv.commercial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import com.antshorttv.adminauth.AdminAuthenticatorService;
import com.antshorttv.adminauth.AuthenticatorTotp;
import com.antshorttv.ai.AiSecretCodec;
import com.antshorttv.points.PointAccountingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "commercial.subscription.scheduler.enabled=false")
@AutoConfigureMockMvc
class AdminTenantGrantControllerTest extends com.antshorttv.support.RegistrationTestSupport {
    private static final AtomicInteger SEQUENCE = new AtomicInteger(7100);
    private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired CommercialPackageService packages;
    @Autowired CommercialSubscriptionGrantService periods;
    @Autowired AdminAuthenticatorService authenticator;
    @Autowired AiSecretCodec codec;
    @SpyBean PointAccountingService accounting;
    Cookie session, csrf;
    Long userId, tenantId;
    Instant time;

    @BeforeEach void setup() throws Exception {
        String mobile = "1390000" + SEQUENCE.incrementAndGet();
        MvcResult registration = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("mobile", mobile, "nickname", "Grant Admin", "password", "Password123",
                "verificationCode", registrationVerificationCode(mvc, mobile)))))
            .andExpect(status().isOk()).andReturn();
        userId = json.readTree(registration.getResponse().getContentAsString()).at("/data/user/id").asLong();
        session = registration.getResponse().getCookie("ANT_SHORT_SESSION");
        csrf = mvc.perform(get("/api/tenants/my").cookie(session)).andReturn().getResponse().getCookie("XSRF-TOKEN");
        grantPlatformAdmin(userId);
        String code = "ADMIN-GRANT-" + userId;
        jdbc.update("insert into tenant (code,name,type,status,created_at,updated_at) values (?,?,'STUDIO','ACTIVE',now(),now())", code, code);
        tenantId = jdbc.queryForObject("select id from tenant where code=?", Long.class, code);
        time = Instant.parse("2026-10-21T10:00:00Z");
        setTime(time);
    }

    @AfterEach void resetClock() { ReflectionTestUtils.setField(authenticator, "clock", Clock.systemUTC()); }

    @Test void onlyPlatformAdminsCanAccessEndpointsEvenWithTenantPermissions() throws Exception {
        jdbc.update("delete from platform_user_role where user_id=?", userId);
        mvc.perform(get("/api/platform/tenants/grant-options").cookie(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/platform/admin-authenticator").cookie(session)).andExpect(status().isForbidden());
        send("/api/platform/admin-authenticator/enrollment", Map.of("password", "Password123")).andExpect(status().isForbidden());
        grant(1L, UUID.randomUUID().toString(), "123456").andExpect(status().isForbidden());
    }

    @Test void bindingRequiresPasswordAndCodeAndNeverExposesStoredSecret() throws Exception {
        send("/api/platform/admin-authenticator/enrollment", Map.of("password", "wrong")).andExpect(status().isBadRequest());
        var result = send("/api/platform/admin-authenticator/enrollment", Map.of("password", "Password123"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andReturn();
        String secret = json.readTree(result.getResponse().getContentAsString()).at("/data/secret").asText();
        assertThat(secret).hasSize(32);
        send("/api/platform/admin-authenticator/confirmation", Map.of("code", "0000000")).andExpect(status().isBadRequest());
        send("/api/platform/admin-authenticator/confirmation", Map.of("code", AuthenticatorTotp.code(secret, time.getEpochSecond()/30)))
            .andExpect(status().isOk());
        mvc.perform(get("/api/platform/admin-authenticator").cookie(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.bound").value(true)).andExpect(jsonPath("$.data.secret").doesNotExist());
        assertThat(jdbc.queryForObject("select secret_cipher from admin_authenticator where user_id=?", String.class, userId)).doesNotContain(secret);
        send("/api/platform/admin-authenticator/enrollment", Map.of("password", "Password123")).andExpect(status().isBadRequest());
        grant(publish("POINT_PACKAGE", "50", 1), UUID.randomUUID().toString(), AuthenticatorTotp.code(secret, time.getEpochSecond()/30))
            .andExpect(status().isBadRequest());
    }

    @Test void passwordFailuresRemainRateLimitedAcrossNewRequests() throws Exception {
        for (int i=0;i<5;i++) send("/api/platform/admin-authenticator/enrollment", Map.of("password", "wrong")).andExpect(status().isBadRequest());
        send("/api/platform/admin-authenticator/enrollment", Map.of("password", "Password123")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select failed_attempts from admin_authenticator where user_id=?", Integer.class, userId)).isEqualTo(5);
        setTime(time.plusSeconds(901));
        send("/api/platform/admin-authenticator/enrollment", Map.of("password", "Password123")).andExpect(status().isOk());
    }

    @Test void expiredEnrollmentCannotBeConfirmed() throws Exception {
        var result = send("/api/platform/admin-authenticator/enrollment", Map.of("password", "Password123")).andReturn();
        String secret = json.readTree(result.getResponse().getContentAsString()).at("/data/secret").asText();
        setTime(time.plusSeconds(601));
        send("/api/platform/admin-authenticator/confirmation", Map.of("code", AuthenticatorTotp.code(secret, time.getEpochSecond()/30)))
            .andExpect(status().isBadRequest());
    }

    @Test void missingBindingInvalidExpiredAndMissingCodesNeverGrant() throws Exception {
        Long version = publish("POINT_PACKAGE", "50", 1);
        grant(version, UUID.randomUUID().toString(), code()).andExpect(status().isBadRequest());
        bind();
        grant(version, UUID.randomUUID().toString(), "").andExpect(status().isBadRequest());
        grant(version, UUID.randomUUID().toString(), "abcdef").andExpect(status().isBadRequest());
        grant(version, UUID.randomUUID().toString(), AuthenticatorTotp.code(SECRET, time.getEpochSecond()/30-5)).andExpect(status().isBadRequest());
        assertThat(count("admin_tenant_package_grant")).isZero();
        assertThat(count("point_ledger")).isZero();
    }

    @Test void oneTimeGrantIsIdempotentAndDoesNotCreatePaidOrders() throws Exception {
        bind();
        Long version = publish("POINT_PACKAGE", "250", 1);
        String request = UUID.randomUUID().toString();
        for (int i=0;i<2;i++) grant(version, request, code()).andExpect(status().isOk());
        assertThat(balance()).isEqualByComparingTo("250");
        assertThat(count("admin_tenant_package_grant")).isEqualTo(1);
        assertThat(count("point_ledger")).isEqualTo(1);
        assertThat(count("commercial_order")).isZero();
        assertThat(count("team_subscription")).isZero();
        grant(version, UUID.randomUUID().toString(), code()).andExpect(status().isBadRequest());
        grant(publish("POINT_PACKAGE", "500", 1), request, code()).andExpect(status().isBadRequest());
    }

    @Test void invalidTenantAndUnavailablePackageAreRejectedBeforeConsumingCode() throws Exception {
        bind();
        Long version = publish("POINT_PACKAGE", "100", 1);
        jdbc.update("update tenant set status='DISABLED' where id=?", tenantId);
        grant(version, UUID.randomUUID().toString(), code()).andExpect(status().isBadRequest());
        jdbc.update("update tenant set status='ACTIVE' where id=?", tenantId);
        jdbc.update("update commercial_package_version set status='OFF_SALE' where id=?", version);
        grant(version, UUID.randomUUID().toString(), code()).andExpect(status().isBadRequest());
        jdbc.update("update commercial_package_version set status='PUBLISHED' where id=?", version);
        grant(version, UUID.randomUUID().toString(), code()).andExpect(status().isOk());
    }

    @Test void failureRollsBackAllBenefitsButConsumesCode() throws Exception {
        bind();
        Long version = publish("POINT_PACKAGE", "100", 1);
        doThrow(new IllegalStateException("injected accounting failure")).when(accounting).grant(eq(tenantId), anyLong(), any(), anyString(), anyString());
        assertThatThrownBy(() -> grant(version, UUID.randomUUID().toString(), code())).hasRootCauseMessage("injected accounting failure");
        assertThat(count("admin_tenant_package_grant")).isZero();
        assertThat(count("point_ledger")).isZero();
        assertThat(jdbc.queryForObject("select last_used_step from admin_authenticator where user_id=?", Long.class, userId)).isEqualTo(time.getEpochSecond()/30);
    }

    @Test void subscriptionRenewalSwitchAndLaterPeriodsUseAdminSource() throws Exception {
        bind();
        Long first = publish("SUBSCRIPTION", "100", 3);
        grant(first, UUID.randomUUID().toString(), code()).andExpect(status().isOk());
        assertThat(balance()).isEqualByComparingTo("100");
        LocalDateTime start = jdbc.queryForObject("select starts_at from team_subscription where tenant_id=?", LocalDateTime.class, tenantId);
        setTime(time.plusSeconds(30));
        grant(first, UUID.randomUUID().toString(), code()).andExpect(status().isOk());
        assertThat(count("team_subscription")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select ends_at from team_subscription where tenant_id=?", LocalDateTime.class, tenantId)).isEqualTo(start.plusMonths(6));
        periods.processDue(start.plusMonths(1).plusSeconds(1));
        assertThat(balance()).isEqualByComparingTo("200");
        periods.processDue(start.plusMonths(1).plusSeconds(1));
        assertThat(balance()).isEqualByComparingTo("200");
        setTime(time.plusSeconds(30));
        grant(publish("SUBSCRIPTION", "300", 3), UUID.randomUUID().toString(), code()).andExpect(status().isOk());
        assertThat(balance()).isEqualByComparingTo("500");
        assertThat(jdbc.queryForObject("select count(*) from team_subscription where tenant_id=? and status='ACTIVE'", Integer.class, tenantId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from team_subscription where tenant_id=? and source_order_id is null and source_admin_grant_id is not null", Integer.class, tenantId)).isEqualTo(2);
    }

    @Test void concurrentReplayCanOnlyGrantOnce() throws Exception {
        bind();
        Long version = publish("POINT_PACKAGE", "90", 1);
        String otp = code();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var ready = new CountDownLatch(2);
            Callable<Integer> task = () -> { ready.countDown(); ready.await(5, TimeUnit.SECONDS);
                return grant(version, UUID.randomUUID().toString(), otp).andReturn().getResponse().getStatus(); };
            var a = executor.submit(task); var b = executor.submit(task);
            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,400);
        }
        finally { executor.shutdownNow(); }
        assertThat(balance()).isEqualByComparingTo("90");
        assertThat(count("admin_tenant_package_grant")).isEqualTo(1);
    }

    @Test void codeFailuresStayLockedAndCannotBeBypassedByRetrying() throws Exception {
        bind();
        Long version = publish("POINT_PACKAGE", "50", 1);
        for (int i=0;i<5;i++) grant(version, UUID.randomUUID().toString(), "0000000").andExpect(status().isBadRequest());
        grant(version, UUID.randomUUID().toString(), code()).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select failed_attempts from admin_authenticator where user_id=?", Integer.class, userId)).isEqualTo(5);
        setTime(time.plusSeconds(901));
        grant(version, UUID.randomUUID().toString(), code()).andExpect(status().isOk());
    }

    @Test void csrfAndRevokedAdminAreRejectedIncludingIdempotentRetries() throws Exception {
        bind();
        Long version = publish("POINT_PACKAGE", "50", 1);
        String request = UUID.randomUUID().toString();
        mvc.perform(post("/api/platform/tenants/"+tenantId+"/package-grants").cookie(session)
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("packageVersionId",version,"reason","补偿","requestId",request,"code",code()))))
            .andExpect(status().isForbidden());
        grant(version, request, code()).andExpect(status().isOk());
        jdbc.update("delete from platform_user_role where user_id=?", userId);
        grant(version, request, code()).andExpect(status().isForbidden());
        assertThat(balance()).isEqualByComparingTo("50");
    }

    @Test void subscriptionAndFirstPointsRollBackWhenPeriodicGrantFails() throws Exception {
        bind();
        var version = packages.createDraft(new CommercialPackageDraftCommand(null,"SUBSCRIPTION","组合套餐","说明","MONTHLY",3,
            BigDecimal.TEN,BigDecimal.TEN,"CNY",LocalDateTime.now().minusDays(1),null,
            List.of(new CommercialEntitlementInput("ONE_TIME_POINTS",new BigDecimal("50")),
                new CommercialEntitlementInput("PERIODIC_POINTS",new BigDecimal("100"))),userId));
        packages.publish(version.packageId(),version.versionId(),userId);
        doThrow(new IllegalStateException("periodic failure")).when(accounting).grant(eq(tenantId), anyLong(), eq(new BigDecimal("100.00000000")), anyString(), anyString());
        assertThatThrownBy(() -> grant(version.versionId(), UUID.randomUUID().toString(), code())).hasRootCauseMessage("periodic failure");
        assertThat(count("team_subscription")).isZero();
        assertThat(count("point_ledger")).isZero();
        assertThat(count("commercial_entitlement_grant")).isZero();
        assertThat(count("admin_tenant_package_grant")).isZero();
    }

    @Test void renewalLinksToSubscriptionAndRestartsExhaustedPeriodicSchedule() throws Exception {
        bind();
        Long version = publish("SUBSCRIPTION", "100", 1);
        grant(version, UUID.randomUUID().toString(), code()).andExpect(status().isOk());
        Long subscriptionId = jdbc.queryForObject("select id from team_subscription where tenant_id=?", Long.class, tenantId);
        LocalDateTime start = jdbc.queryForObject("select starts_at from team_subscription where id=?", LocalDateTime.class, subscriptionId);
        jdbc.update("update team_subscription set next_grant_at=null where id=?", subscriptionId);
        setTime(time.plusSeconds(30));
        grant(version, UUID.randomUUID().toString(), code()).andExpect(status().isOk());
        assertThat(jdbc.queryForList("select subscription_id from admin_tenant_package_grant where tenant_id=?", Long.class, tenantId))
            .containsExactly(subscriptionId, subscriptionId);
        periods.processDue(start.plusMonths(1).plusSeconds(1));
        assertThat(balance()).isEqualByComparingTo("200");
    }

    @Test void subscriptionWithoutPeriodicPointsDoesNotCreateAnUnfulfillableSchedule() throws Exception {
        bind();
        var version = packages.createDraft(new CommercialPackageDraftCommand(null,"SUBSCRIPTION","折扣会员","说明","MONTHLY",3,
            BigDecimal.TEN,BigDecimal.TEN,"CNY",LocalDateTime.now().minusDays(1),null,
            List.of(new CommercialEntitlementInput("GLOBAL_DISCOUNT",new BigDecimal("0.8"))),userId));
        packages.publish(version.packageId(),version.versionId(),userId);
        grant(version.versionId(), UUID.randomUUID().toString(), code()).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select next_grant_at from team_subscription where tenant_id=?", LocalDateTime.class, tenantId)).isNull();
        assertThat(count("point_ledger")).isZero();
    }

    private org.springframework.test.web.servlet.ResultActions send(String path, Object body) throws Exception {
        return mvc.perform(post(path).cookie(session, csrf).header("X-XSRF-TOKEN", csrf.getValue())
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
    private org.springframework.test.web.servlet.ResultActions grant(Long version, String request, String code) throws Exception {
        return send("/api/platform/tenants/"+tenantId+"/package-grants", Map.of("packageVersionId",version,"reason","客户补偿","requestId",request,"code",code));
    }
    private void grantPlatformAdmin(Long id) {
        Long role = jdbc.queryForObject("select id from platform_role where code='PLATFORM_ADMIN'", Long.class);
        jdbc.update("insert into platform_user_role (user_id,role_id,created_at) values (?,?,now())", id, role);
    }
    private void bind() {
        jdbc.update("insert into admin_authenticator (user_id,updated_at) select ?,now() where not exists (select 1 from admin_authenticator where user_id=?)", userId, userId);
        jdbc.update("update admin_authenticator set secret_cipher=? where user_id=?", codec.encrypt(SECRET), userId);
    }
    private void setTime(Instant value) { time=value; ReflectionTestUtils.setField(authenticator,"clock",Clock.fixed(time,ZoneOffset.UTC)); }
    private String code() { return AuthenticatorTotp.code(SECRET, time.getEpochSecond()/30); }
    private int count(String table) { return jdbc.queryForObject("select count(*) from "+table+" where tenant_id=?", Integer.class, tenantId); }
    private BigDecimal balance() { return jdbc.queryForObject("select balance from team_point_account where tenant_id=?",BigDecimal.class,tenantId); }
    private Long publish(String type, String amount, int months) {
        var version=packages.createDraft(new CommercialPackageDraftCommand(null,type,"管理员测试商品","说明","MONTHLY",months,
            BigDecimal.TEN,BigDecimal.TEN,"CNY",LocalDateTime.now().minusDays(1),null,
            List.of(new CommercialEntitlementInput("SUBSCRIPTION".equals(type)?"PERIODIC_POINTS":"ONE_TIME_POINTS",new BigDecimal(amount))),userId));
        packages.publish(version.packageId(),version.versionId(),userId);
        return version.versionId();
    }
}
