package com.antshorttv.commercial;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommercialPackageService {
    private static final Set<String> SUPPORTED = Set.of("ONE_TIME_POINTS", "PERIODIC_POINTS", "GLOBAL_DISCOUNT");
    private final CommercialPackageMapper packageMapper;
    private final CommercialPackageVersionMapper versionMapper;
    private final CommercialEntitlementMapper entitlementMapper;

    public CommercialPackageService(CommercialPackageMapper packageMapper, CommercialPackageVersionMapper versionMapper, CommercialEntitlementMapper entitlementMapper) {
        this.packageMapper = packageMapper; this.versionMapper = versionMapper; this.entitlementMapper = entitlementMapper;
    }

    @Transactional
    public CommercialPackageVersionResponse createDraft(CommercialPackageDraftCommand command) {
        if (command.price() == null || command.price().signum() < 0) throw new IllegalArgumentException("Price must be non-negative");
        if (command.effectiveFrom() == null) throw new IllegalArgumentException("Effective date is required");
        if ("SUBSCRIPTION".equals(command.packageType()) && (command.periodMonths() == null || command.periodMonths() <= 0)) throw new IllegalArgumentException("Subscription period must be positive");
        command.entitlements().forEach(input -> { if (!SUPPORTED.contains(input.type())) throw new IllegalArgumentException("Unsupported entitlement: " + input.type()); });
        CommercialPackageEntity pack = packageMapper.selectOne(new QueryWrapper<CommercialPackageEntity>().eq("code", command.code()));
        if (pack == null) { pack = new CommercialPackageEntity(); pack.code = command.code(); pack.packageType = command.packageType(); pack.status = "ACTIVE"; pack.createdBy = command.operatorId(); pack.createdAt = LocalDateTime.now(); pack.updatedAt = pack.createdAt; packageMapper.insert(pack); }
        Integer latest = versionMapper.selectList(new QueryWrapper<CommercialPackageVersionEntity>().eq("package_id", pack.id).orderByDesc("version_no").last("limit 1")).stream().findFirst().map(v -> v.versionNo).orElse(0);
        CommercialPackageVersionEntity version = new CommercialPackageVersionEntity(); version.packageId = pack.id; version.versionNo = latest + 1; version.name = command.name(); version.description = command.description(); version.billingPeriod = command.billingPeriod(); version.periodMonths = command.periodMonths(); version.price = command.price(); version.listPrice = command.listPrice(); version.currency = command.currency(); version.effectiveFrom = command.effectiveFrom(); version.effectiveTo = command.effectiveTo(); version.status = "DRAFT"; version.createdBy = command.operatorId(); version.createdAt = LocalDateTime.now(); versionMapper.insert(version);
        for (CommercialEntitlementInput input : command.entitlements()) { CommercialEntitlementEntity e = new CommercialEntitlementEntity(); e.packageVersionId = version.id; e.entitlementType = input.type(); e.numericValue = input.value(); e.createdAt = LocalDateTime.now(); entitlementMapper.insert(e); }
        return response(version);
    }

    @Transactional public CommercialPackageVersionResponse publish(Long packageId, Long versionId, Long operatorId) { CommercialPackageVersionEntity v = require(versionId); if (!packageId.equals(v.packageId)) throw new IllegalArgumentException("Package mismatch"); if (!"DRAFT".equals(v.status)) throw new IllegalStateException("Only draft versions can be published"); v.status = "PUBLISHED"; v.publishedAt = LocalDateTime.now(); versionMapper.updateById(v); return response(v); }
    @Transactional public CommercialPackageVersionResponse unpublish(Long packageId, Long versionId) { CommercialPackageVersionEntity v = require(versionId); if (!packageId.equals(v.packageId)) throw new IllegalArgumentException("Package mismatch"); if (!"PUBLISHED".equals(v.status)) throw new IllegalStateException("Only published versions can be unpublished"); v.status = "OFF_SALE"; versionMapper.updateById(v); return response(v); }
    public List<CommercialPackageSummaryResponse> listPackages() { return packageMapper.selectList(new QueryWrapper<CommercialPackageEntity>().orderByAsc("id")).stream().map(p -> new CommercialPackageSummaryResponse(p.id, p.code, p.packageType, p.status)).toList(); }
    public List<CommercialCatalogItemResponse> listForSale(LocalDateTime now) {
        return versionMapper.selectList(new QueryWrapper<CommercialPackageVersionEntity>()
            .eq("status", "PUBLISHED").le("effective_from", now)
            .and(wrapper -> wrapper.isNull("effective_to").or().gt("effective_to", now))
            .orderByAsc("price")).stream().map(version -> {
                CommercialPackageEntity pack = packageMapper.selectById(version.packageId);
                return new CommercialCatalogItemResponse(pack.id, version.id, pack.code, pack.packageType,
                    version.name, version.description, version.billingPeriod, version.periodMonths,
                    version.price, version.listPrice, version.currency, response(version).entitlements());
            }).toList();
    }
    public List<CommercialPackageVersionResponse> history(Long packageId) { return versionMapper.selectList(new QueryWrapper<CommercialPackageVersionEntity>().eq("package_id", packageId).orderByDesc("version_no")).stream().map(this::response).toList(); }
    @Transactional public void updateDraftName(Long versionId, String name) { CommercialPackageVersionEntity v = require(versionId); if (!"DRAFT".equals(v.status)) throw new IllegalStateException("Published package versions are immutable"); v.name = name; versionMapper.updateById(v); }
    private CommercialPackageVersionEntity require(Long id) { CommercialPackageVersionEntity v = versionMapper.selectById(id); if (v == null) throw new IllegalArgumentException("Package version not found"); return v; }
    private CommercialPackageVersionResponse response(CommercialPackageVersionEntity v) { return new CommercialPackageVersionResponse(v.packageId, v.id, v.versionNo, v.status, v.name, v.description, v.billingPeriod, v.periodMonths, v.price, v.listPrice, v.currency, v.effectiveFrom, v.effectiveTo, entitlementMapper.selectList(new QueryWrapper<CommercialEntitlementEntity>().eq("package_version_id", v.id)).stream().map(e -> new CommercialEntitlementInput(e.entitlementType, e.numericValue)).toList()); }
}

record CommercialPackageDraftCommand(String code, String packageType, String name, String description, String billingPeriod, Integer periodMonths, BigDecimal price, BigDecimal listPrice, String currency, LocalDateTime effectiveFrom, LocalDateTime effectiveTo, List<CommercialEntitlementInput> entitlements, Long operatorId) {}
record CommercialEntitlementInput(String type, BigDecimal value) {}
record CommercialPackageVersionResponse(Long packageId, Long versionId, Integer versionNo, String status, String name, String description, String billingPeriod, Integer periodMonths, BigDecimal price, BigDecimal listPrice, String currency, LocalDateTime effectiveFrom, LocalDateTime effectiveTo, List<CommercialEntitlementInput> entitlements) {}
record CommercialPackageSummaryResponse(Long id, String code, String packageType, String status) {}
record CommercialCatalogItemResponse(Long packageId, Long packageVersionId, String code, String packageType, String name, String description, String billingPeriod, Integer periodMonths, BigDecimal price, BigDecimal listPrice, String currency, List<CommercialEntitlementInput> entitlements) {}
