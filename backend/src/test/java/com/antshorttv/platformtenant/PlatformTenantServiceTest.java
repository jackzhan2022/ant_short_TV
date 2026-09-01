package com.antshorttv.platformtenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;

import com.antshorttv.authsession.AuthenticatedUser;
import com.antshorttv.common.BusinessException;
import com.antshorttv.operationlog.OperationLogEntity;
import com.antshorttv.operationlog.OperationLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest
class PlatformTenantServiceTest {

    @Autowired private PlatformTenantService service;
    @SpyBean private JdbcTemplate jdbc;
    @Autowired private OperationLogMapper operationLogMapper;

    @BeforeEach
    void authenticateOperator() {
        AuthenticatedUser user = new AuthenticatedUser(901L, "13800000901", "session", LocalDateTime.now().plusHours(1));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, java.util.List.of()));
    }

    @Test
    void filtersAndAggregatesCurrentPageWithoutPerTenantQueries() {
        long first = insertTenant("OPS-A", "Alpha Studio", "ACTIVE", false);
        long second = insertTenant("OPS-B", "Beta Studio", "ACTIVE", false);
        insertOwner(first, 1001L, "Alpha Owner");
        insertOwner(second, 1002L, "Beta Owner");
        insertMember(first, 1003L, "MEMBER");
        jdbc.update("insert into team_point_account (tenant_id,balance,total_granted,total_consumed,reserved_balance,total_reserved,total_released,total_refunded,version,created_at,updated_at) values (?,?,?,?,0,0,0,0,0,now(),now())",
            first, new BigDecimal("88.5"), new BigDecimal("100"), new BigDecimal("11.5"));
        insertSubscription(first, "ACTIVE", "SUBSCRIPTION", "Pro Snapshot", "{\"name\":\"Pro Snapshot\"}",
            LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(29));
        clearInvocations(jdbc);

        PlatformTenantPageResponse result = service.list(PlatformTenantQuery.of("Alpha", "ACTIVE", "SUBSCRIPTION", 1, 20));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.records()).hasSize(1);
        PlatformTenantSummaryResponse tenant = result.records().get(0);
        assertThat(tenant.id()).isEqualTo(first);
        assertThat(tenant.owner().nickname()).isEqualTo("Alpha Owner");
        assertThat(tenant.activeMemberCount()).isEqualTo(2);
        assertThat(tenant.pointBalance()).isEqualByComparingTo("88.5");
        assertThat(tenant.currentPackage().packageType()).isEqualTo("SUBSCRIPTION");
        assertThat(tenant.currentPackage().name()).isEqualTo("Pro Snapshot");
        long queryCount = mockingDetails(jdbc).getInvocations().stream()
            .filter(invocation -> invocation.getMethod().getName().startsWith("query"))
            .filter(invocation -> invocation.getArguments().length > 0
                && invocation.getArguments()[0] instanceof String)
            .map(invocation -> (String) invocation.getArguments()[0])
            .distinct()
            .count();
        assertThat(queryCount).isEqualTo(6);
    }

    @Test
    void returnsDefaultsAndExcludesSoftDeletedTenants() {
        long tenantId = insertTenant("OPS-EMPTY", "Empty Tenant", "ACTIVE", false);
        insertOwner(tenantId, 1010L, "Empty Owner");
        insertTenant("OPS-DELETED", "Deleted Tenant", "ACTIVE", true);

        PlatformTenantPageResponse result = service.list(PlatformTenantQuery.of("OPS-EMPTY", null, null, 1, 20));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.records().get(0).pointBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.records().get(0).currentPackage()).isNull();
    }

    @Test
    void returnsDisabledTenantDetailWithSortedQueuedPackagesAndSnapshotFallback() {
        long tenantId = insertTenant("OPS-DISABLED", "Disabled Tenant", "DISABLED", false);
        insertOwner(tenantId, 1020L, "Disabled Owner");
        insertSubscription(tenantId, "QUEUED", "SUBSCRIPTION", "Later Version", "{broken",
            LocalDateTime.now().plusMonths(2), LocalDateTime.now().plusMonths(3));
        insertSubscription(tenantId, "QUEUED", "POINT_PACKAGE", "Soon Version", "{\"name\":\"Soon Snapshot\"}",
            LocalDateTime.now().plusMonths(1), LocalDateTime.now().plusMonths(2));

        PlatformTenantDetailResponse detail = service.detail(tenantId);

        assertThat(detail.status()).isEqualTo("DISABLED");
        assertThat(detail.queuedPackages()).extracting(PlatformTenantPackageResponse::name)
            .containsExactly("Soon Snapshot", "Later Version");
    }

    @Test
    void rejectsMissingTenantAndInvalidQueryValues() {
        assertThatThrownBy(() -> service.detail(Long.MAX_VALUE))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("租户不存在");
        assertThatThrownBy(() -> PlatformTenantQuery.of(null, "UNKNOWN", null, 1, 20))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> PlatformTenantQuery.of(null, null, null, 0, 20))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> PlatformTenantQuery.of(null, null, null, 1, 101))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void updatesStatusIdempotentlyAndAuditsOnlyActualChanges() {
        long tenantId = insertTenant("OPS-STATUS", "Status Tenant", "ACTIVE", false);
        insertOwner(tenantId, 1030L, "Status Owner");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.8");
        request.addHeader("User-Agent", "platform-test");

        PlatformTenantSummaryResponse disabled = service.updateStatus(
            tenantId, new UpdatePlatformTenantStatusRequest("DISABLED"), request);
        service.updateStatus(tenantId, new UpdatePlatformTenantStatusRequest("DISABLED"), request);
        PlatformTenantSummaryResponse active = service.updateStatus(
            tenantId, new UpdatePlatformTenantStatusRequest("ACTIVE"), request);

        assertThat(disabled.status()).isEqualTo("DISABLED");
        assertThat(active.status()).isEqualTo("ACTIVE");
        java.util.List<OperationLogEntity> logs = operationLogMapper.selectList(
            new LambdaQueryWrapper<OperationLogEntity>()
                .eq(OperationLogEntity::getOperation, "PLATFORM_UPDATE_TENANT_STATUS")
                .eq(OperationLogEntity::getTenantId, tenantId)
                .orderByAsc(OperationLogEntity::getId));
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).getDetailJson()).contains("\"previousStatus\":\"ACTIVE\"");
        assertThat(logs.get(0).getIp()).isEqualTo("127.0.0.8");
        assertThat(logs.get(0).getUserAgent()).isEqualTo("platform-test");
    }

    @Test
    void rejectsUnsupportedTargetStatus() {
        long tenantId = insertTenant("OPS-BAD-STATUS", "Bad Status Tenant", "ACTIVE", false);
        assertThatThrownBy(() -> service.updateStatus(
            tenantId, new UpdatePlatformTenantStatusRequest("ARCHIVED"), null))
            .isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("select status from tenant where id=?", String.class, tenantId)).isEqualTo("ACTIVE");
    }

    private long insertTenant(String code, String name, String status, boolean deleted) {
        jdbc.update("insert into tenant (code,name,type,status,created_at,updated_at,deleted_at) values (?,?, 'STUDIO', ?, now(), now(), ?)",
            code, name, status, deleted ? LocalDateTime.now() : null);
        return jdbc.queryForObject("select id from tenant where code=?", Long.class, code);
    }

    private void insertOwner(long tenantId, long userId, String nickname) {
        jdbc.update("insert into app_user (id,mobile,password_hash,nickname,status,token_version,created_at,updated_at) values (?,?,?,?,'ACTIVE',0,now(),now())",
            userId, "139" + userId, "hash", nickname);
        jdbc.update("insert into tenant_member (tenant_id,user_id,member_type,status,joined_at,created_at,updated_at) values (?,?,'OWNER','ACTIVE',now(),now(),now())",
            tenantId, userId);
        Long memberId = jdbc.queryForObject("select id from tenant_member where tenant_id=? and user_id=?", Long.class, tenantId, userId);
        jdbc.update("update tenant set owner_member_id=? where id=?", memberId, tenantId);
    }

    private void insertMember(long tenantId, long userId, String memberType) {
        jdbc.update("insert into app_user (id,mobile,password_hash,nickname,status,token_version,created_at,updated_at) values (?,?,?,'Member','ACTIVE',0,now(),now())",
            userId, "139" + userId, "hash");
        jdbc.update("insert into tenant_member (tenant_id,user_id,member_type,status,joined_at,created_at,updated_at) values (?,?,?,'ACTIVE',now(),now(),now())",
            tenantId, userId, memberType);
    }

    private void insertSubscription(long tenantId, String status, String packageType, String versionName,
                                    String snapshotJson, LocalDateTime startsAt, LocalDateTime endsAt) {
        String packageCode = "PKG-" + tenantId + "-" + status + "-" + startsAt;
        jdbc.update("insert into commercial_package (code,package_type,status,created_at,updated_at) values (?,?,'ACTIVE',now(),now())",
            packageCode, packageType);
        Long packageId = jdbc.queryForObject("select id from commercial_package where code=?", Long.class, packageCode);
        jdbc.update("insert into commercial_package_version (package_id,version_no,name,price,currency,effective_from,status,created_at) values (?,1,?,0,'CNY',now(),'PUBLISHED',now())",
            packageId, versionName);
        Long versionId = jdbc.queryForObject("select id from commercial_package_version where package_id=?", Long.class, packageId);
        jdbc.update("insert into team_subscription (tenant_id,package_version_id,source_order_id,status,starts_at,ends_at,snapshot_json,created_at,updated_at) values (?,?,0,?,?,?,?,now(),now())",
            tenantId, versionId, status, startsAt, endsAt, snapshotJson);
    }
}
