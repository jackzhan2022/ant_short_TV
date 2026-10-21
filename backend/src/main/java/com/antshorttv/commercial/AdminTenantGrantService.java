package com.antshorttv.commercial;

import com.antshorttv.adminauth.AdminAuthenticatorService;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.operationlog.OperationLogService;
import com.antshorttv.operationlog.OperationResult;
import com.antshorttv.platform.PlatformPermissionGuard;
import com.antshorttv.security.CurrentPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AdminTenantGrantService {
    private final PlatformPermissionGuard guard;
    private final CurrentPrincipal principal;
    private final AdminAuthenticatorService authenticator;
    private final CommercialPackageService packages;
    private final CommercialPackageVersionMapper versions;
    private final CommercialPackageMapper packageMapper;
    private final AdminTenantGrantMapper grants;
    private final CommercialBenefitFulfillmentService benefits;
    private final OperationLogService logs;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public AdminTenantGrantService(PlatformPermissionGuard guard, CurrentPrincipal principal, AdminAuthenticatorService authenticator,
        CommercialPackageService packages, CommercialPackageVersionMapper versions, CommercialPackageMapper packageMapper,
        AdminTenantGrantMapper grants, CommercialBenefitFulfillmentService benefits, OperationLogService logs,
        ObjectMapper json, JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        this.guard=guard; this.principal=principal; this.authenticator=authenticator; this.packages=packages;
        this.versions=versions; this.packageMapper=packageMapper; this.grants=grants; this.benefits=benefits;
        this.logs=logs; this.json=json; this.jdbc=jdbc; this.transaction=new TransactionTemplate(transactions);
    }

    public List<CommercialCatalogItemResponse> options() {
        guard.requireAdministrator();
        return packages.listForSale(LocalDateTime.now()).stream()
            .filter(item -> "ACTIVE".equals(packageMapper.selectById(item.packageId()).status)).toList();
    }

    public Result grant(Long tenantId, Command command, HttpServletRequest request) {
        guard.requireAdministrator();
        Long operator = principal.require().userId();
        AdminTenantGrantEntity existing = existing(operator, command.requestId());
        if (existing != null) return matching(existing, tenantId, command);
        requireTenant(tenantId, false);
        requireVersion(command.packageVersionId(), false);
        // A consumed OTP and failed attempts must survive rollback of the business transaction.
        authenticator.consume(command.code());
        try {
            return Objects.requireNonNull(transaction.execute(tx -> {
                // Serializes idempotency across tenants for one operator as well as concurrent requests.
                jdbc.queryForObject("select id from app_user where id=? for update", Long.class, operator);
                AdminTenantGrantEntity duplicate = existing(operator, command.requestId());
                if (duplicate != null) return matching(duplicate, tenantId, command);
                requireTenant(tenantId, true);
                CommercialPackageVersionEntity version = requireVersion(command.packageVersionId(), true);
                AdminTenantGrantEntity record = new AdminTenantGrantEntity();
                record.tenantId=tenantId; record.operatorId=operator; record.packageVersionId=version.id;
                record.reason=command.reason().trim(); record.requestId=command.requestId(); record.status="COMPLETED";
                record.createdAt=LocalDateTime.now(); record.packageSnapshotJson=snapshot(version.id);
                grants.insert(record);
                record.subscriptionId = benefits.fulfill(new CommercialBenefitFulfillmentService.Source(tenantId, operator, version.id, null,
                    record.id, record.packageSnapshotJson, record.createdAt));
                grants.updateById(record);
                logs.record(operator, tenantId, "PLATFORM_GRANT_TENANT_PACKAGE", record.id, OperationResult.SUCCESS, request,
                    Map.of("packageVersionId", version.id, "reason", record.reason, "source", "ADMIN_GRANT", "requestId", record.requestId));
                return result(record);
            }));
        } catch (RuntimeException exception) {
            logs.record(operator, tenantId, "PLATFORM_GRANT_TENANT_PACKAGE", tenantId, OperationResult.FAILURE, request,
                Map.of("packageVersionId", command.packageVersionId(), "requestId", command.requestId()));
            throw exception;
        }
    }

    private void requireTenant(Long tenantId, boolean lock) {
        List<String> states = jdbc.query("select status from tenant where id=? and deleted_at is null" + (lock ? " for update" : ""),
            (rs,n) -> rs.getString(1), tenantId);
        if (states.isEmpty() || !"ACTIVE".equals(states.get(0))) throw invalid("租户不存在或已停用，无法发放。");
    }
    private CommercialPackageVersionEntity requireVersion(Long id, boolean lock) {
        QueryWrapper<CommercialPackageVersionEntity> query = new QueryWrapper<CommercialPackageVersionEntity>().eq("id", id);
        if (lock) query.last("for update");
        CommercialPackageVersionEntity version = versions.selectOne(query);
        LocalDateTime now = LocalDateTime.now();
        if (version == null || !"PUBLISHED".equals(version.status) || version.effectiveFrom == null || version.effectiveFrom.isAfter(now)
            || version.effectiveTo != null && !version.effectiveTo.isAfter(now)) throw invalid("商品版本未发布、已下架或不在有效期内。");
        CommercialPackageEntity pack = packageMapper.selectById(version.packageId);
        if (pack == null || !"ACTIVE".equals(pack.status) || !packages.hasFulfillableBenefits(version)) throw invalid("该商品当前不可发放。");
        return version;
    }
    private AdminTenantGrantEntity existing(Long operator, String requestId) {
        return grants.selectOne(new QueryWrapper<AdminTenantGrantEntity>().eq("operator_id", operator).eq("request_id", requestId));
    }
    private Result matching(AdminTenantGrantEntity record, Long tenantId, Command command) {
        if (!Objects.equals(record.tenantId, tenantId) || !Objects.equals(record.packageVersionId, command.packageVersionId())
            || !Objects.equals(record.reason, command.reason().trim())) throw invalid("请求标识已用于其他发放内容，请重新发起操作。");
        return result(record);
    }
    private Result result(AdminTenantGrantEntity record) { return new Result(record.id, record.tenantId, record.packageVersionId, record.status, record.createdAt); }
    private String snapshot(Long id) {
        try { return json.writeValueAsString(packages.snapshot(id)); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Failed to snapshot administrator grant", exception); }
    }
    private BusinessException invalid(String message) { return new BusinessException(ErrorCode.VALIDATION_ERROR, message); }
    public record Command(@NotNull @Positive Long packageVersionId, @NotBlank @Size(max=500) String reason,
        @NotBlank @Pattern(regexp="[a-zA-Z0-9-]{16,64}") String requestId, @NotBlank @Size(max=20) String code) {}
    public record Result(Long id, Long tenantId, Long packageVersionId, String status, LocalDateTime createdAt) {}
}
