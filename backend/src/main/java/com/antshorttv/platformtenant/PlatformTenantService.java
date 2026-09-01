package com.antshorttv.platformtenant;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.operationlog.OperationLogService;
import com.antshorttv.operationlog.OperationResult;
import com.antshorttv.security.CurrentPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformTenantService {
    private static final Set<String> EDITABLE_STATUSES = Set.of("ACTIVE", "DISABLED");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final CurrentPrincipal currentPrincipal;
    private final OperationLogService operationLogService;

    public PlatformTenantService(
        JdbcTemplate jdbc,
        ObjectMapper objectMapper,
        CurrentPrincipal currentPrincipal,
        OperationLogService operationLogService
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.currentPrincipal = currentPrincipal;
        this.operationLogService = operationLogService;
    }

    public PlatformTenantPageResponse list(PlatformTenantQuery query) {
        SqlAndArgs filtered = filteredTenantSql(query);
        Long total = jdbc.queryForObject("select count(*) " + filtered.sql(), Long.class, filtered.args().toArray());
        List<Object> pageArgs = new ArrayList<>(filtered.args());
        pageArgs.add(query.pageSize());
        pageArgs.add((query.current() - 1) * query.pageSize());
        List<TenantRow> tenants = jdbc.query("select t.* " + filtered.sql()
                + " order by t.created_at desc, t.id desc limit ? offset ?",
            this::tenantRow, pageArgs.toArray());
        return new PlatformTenantPageResponse(
            summaries(tenants), total == null ? 0 : total, query.current(), query.pageSize());
    }

    public PlatformTenantDetailResponse detail(Long tenantId) {
        TenantRow tenant = findTenant(tenantId, false);
        PlatformTenantSummaryResponse summary = summaries(List.of(tenant)).get(0);
        return new PlatformTenantDetailResponse(
            summary.id(), summary.code(), summary.name(), summary.type(), summary.status(),
            tenant.logo(), tenant.description(), summary.owner(), summary.activeMemberCount(),
            summary.pointBalance(), summary.currentPackage(), queuedPackages(tenantId),
            summary.createdAt(), tenant.updatedAt());
    }

    @Transactional
    public PlatformTenantSummaryResponse updateStatus(
        Long tenantId,
        UpdatePlatformTenantStatusRequest request,
        HttpServletRequest servletRequest
    ) {
        String targetStatus = normalizeStatus(request == null ? null : request.status());
        TenantRow tenant = findTenant(tenantId, true);
        if (tenant.status().equals(targetStatus)) {
            return summaries(List.of(tenant)).get(0);
        }
        jdbc.update("update tenant set status=?, updated_at=now() where id=?", targetStatus, tenantId);
        operationLogService.record(
            currentPrincipal.require().userId(), tenantId, "PLATFORM_UPDATE_TENANT_STATUS", tenantId,
            OperationResult.SUCCESS, servletRequest,
            Map.of("previousStatus", tenant.status(), "newStatus", targetStatus, "source", "PLATFORM")
        );
        return summaries(List.of(tenant.withStatus(targetStatus, LocalDateTime.now()))).get(0);
    }

    private SqlAndArgs filteredTenantSql(PlatformTenantQuery query) {
        StringBuilder sql = new StringBuilder("from tenant t where t.deleted_at is null");
        List<Object> args = new ArrayList<>();
        if (query.keyword() != null) {
            sql.append(" and (lower(t.name) like ? or lower(t.code) like ?)");
            String keyword = "%" + query.keyword().toLowerCase() + "%";
            args.add(keyword);
            args.add(keyword);
        }
        if (query.status() != null) {
            sql.append(" and t.status = ?");
            args.add(query.status());
        }
        if (query.packageType() != null) {
            sql.append(" and exists (select 1 from team_subscription s")
                .append(" join commercial_package_version v on v.id=s.package_version_id")
                .append(" join commercial_package p on p.id=v.package_id")
                .append(" where s.tenant_id=t.id and s.status='ACTIVE' and p.package_type=?)");
            args.add(query.packageType());
        }
        return new SqlAndArgs(sql.toString(), args);
    }

    private List<PlatformTenantSummaryResponse> summaries(List<TenantRow> tenants) {
        if (tenants.isEmpty()) {
            return List.of();
        }
        List<Long> tenantIds = tenants.stream().map(TenantRow::id).toList();
        String placeholders = placeholders(tenantIds.size());
        Map<Long, PlatformTenantOwnerResponse> owners = loadOwners(tenantIds, placeholders);
        Map<Long, Long> memberCounts = loadMemberCounts(tenantIds, placeholders);
        Map<Long, BigDecimal> balances = loadBalances(tenantIds, placeholders);
        Map<Long, PlatformTenantPackageResponse> packages = loadCurrentPackages(tenantIds, placeholders);
        return tenants.stream().map(tenant -> new PlatformTenantSummaryResponse(
            tenant.id(), tenant.code(), tenant.name(), tenant.type(), tenant.status(), owners.get(tenant.id()),
            memberCounts.getOrDefault(tenant.id(), 0L), balances.getOrDefault(tenant.id(), BigDecimal.ZERO),
            packages.get(tenant.id()), tenant.createdAt()
        )).toList();
    }

    private Map<Long, PlatformTenantOwnerResponse> loadOwners(List<Long> ids, String placeholders) {
        Map<Long, PlatformTenantOwnerResponse> result = new HashMap<>();
        jdbc.query("""
            select t.id tenant_id, m.id member_id, u.id user_id, u.nickname, u.mobile, u.email
              from tenant t
              left join tenant_member m on m.id=t.owner_member_id and m.status='ACTIVE'
              left join app_user u on u.id=m.user_id and u.deleted_at is null
             where t.id in (%s)
            """.formatted(placeholders), (RowCallbackHandler) rs -> {
                Long userId = rs.getObject("user_id", Long.class);
                if (userId != null) {
                    result.put(rs.getLong("tenant_id"), new PlatformTenantOwnerResponse(
                        rs.getObject("member_id", Long.class), userId, rs.getString("nickname"),
                        rs.getString("mobile"), rs.getString("email")));
                }
            }, ids.toArray());
        return result;
    }

    private Map<Long, Long> loadMemberCounts(List<Long> ids, String placeholders) {
        return jdbc.query("select tenant_id, count(*) member_count from tenant_member"
                + " where status='ACTIVE' and tenant_id in (" + placeholders + ") group by tenant_id",
            rs -> {
                Map<Long, Long> result = new HashMap<>();
                while (rs.next()) result.put(rs.getLong("tenant_id"), rs.getLong("member_count"));
                return result;
            }, ids.toArray());
    }

    private Map<Long, BigDecimal> loadBalances(List<Long> ids, String placeholders) {
        return jdbc.query("select tenant_id, balance from team_point_account where tenant_id in (" + placeholders + ")",
            rs -> {
                Map<Long, BigDecimal> result = new HashMap<>();
                while (rs.next()) result.put(rs.getLong("tenant_id"), rs.getBigDecimal("balance"));
                return result;
            }, ids.toArray());
    }

    private Map<Long, PlatformTenantPackageResponse> loadCurrentPackages(List<Long> ids, String placeholders) {
        Map<Long, PlatformTenantPackageResponse> result = new LinkedHashMap<>();
        jdbc.query(subscriptionSql("s.status='ACTIVE' and s.tenant_id in (" + placeholders + ")")
                + " order by s.tenant_id, s.ends_at desc, s.id desc",
            (RowCallbackHandler) rs -> result.putIfAbsent(rs.getLong("tenant_id"), packageResponse(rs)), ids.toArray());
        return result;
    }

    private List<PlatformTenantPackageResponse> queuedPackages(Long tenantId) {
        return jdbc.query(subscriptionSql("s.status='QUEUED' and s.tenant_id=?")
                + " order by s.starts_at asc, s.id asc",
            (rs, rowNum) -> packageResponse(rs), tenantId);
    }

    private String subscriptionSql(String where) {
        return "select s.tenant_id,s.id subscription_id,s.status subscription_status,s.starts_at,s.ends_at,"
            + "s.snapshot_json,v.id package_version_id,v.name version_name,p.id package_id,p.package_type "
            + "from team_subscription s join commercial_package_version v on v.id=s.package_version_id "
            + "join commercial_package p on p.id=v.package_id where " + where;
    }

    private PlatformTenantPackageResponse packageResponse(ResultSet rs) throws SQLException {
        return new PlatformTenantPackageResponse(
            rs.getLong("subscription_id"), rs.getLong("package_id"), rs.getLong("package_version_id"),
            rs.getString("package_type"), snapshotName(rs.getString("snapshot_json"), rs.getString("version_name")),
            rs.getString("subscription_status"), localDateTime(rs.getTimestamp("starts_at")),
            localDateTime(rs.getTimestamp("ends_at")));
    }

    private String snapshotName(String snapshotJson, String fallback) {
        try {
            JsonNode name = objectMapper.readTree(snapshotJson).get("name");
            return name != null && name.isTextual() && !name.asText().isBlank() ? name.asText() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private TenantRow findTenant(Long tenantId, boolean forUpdate) {
        List<TenantRow> rows = jdbc.query("select * from tenant where id=? and deleted_at is null"
                + (forUpdate ? " for update" : ""), this::tenantRow, tenantId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "租户不存在。");
        }
        return rows.get(0);
    }

    private TenantRow tenantRow(ResultSet rs, int rowNum) throws SQLException {
        return new TenantRow(
            rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("type"),
            rs.getString("status"), rs.getString("logo"), rs.getString("description"),
            localDateTime(rs.getTimestamp("created_at")), localDateTime(rs.getTimestamp("updated_at")));
    }

    private String normalizeStatus(String rawStatus) {
        String status = rawStatus == null ? "" : rawStatus.trim().toUpperCase();
        if (!EDITABLE_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "租户状态仅支持 ACTIVE 或 DISABLED。");
        }
        return status;
    }

    private String placeholders(int size) {
        return java.util.stream.IntStream.range(0, size).mapToObj(ignored -> "?").collect(Collectors.joining(","));
    }

    private LocalDateTime localDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private record SqlAndArgs(String sql, List<Object> args) {}

    private record TenantRow(
        Long id, String code, String name, String type, String status, String logo, String description,
        LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        TenantRow withStatus(String newStatus, LocalDateTime newUpdatedAt) {
            return new TenantRow(id, code, name, type, newStatus, logo, description, createdAt, newUpdatedAt);
        }
    }
}
