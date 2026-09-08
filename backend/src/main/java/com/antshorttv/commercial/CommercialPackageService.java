package com.antshorttv.commercial;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommercialPackageService {
    private static final Set<String> SUPPORTED = Set.of("ONE_TIME_POINTS", "PERIODIC_POINTS", "GLOBAL_DISCOUNT");
    private final CommercialPackageMapper packageMapper;
    private final CommercialPackageVersionMapper versionMapper;
    private final CommercialEntitlementMapper entitlementMapper;
    private final CommercialEntitlementCatalogService entitlementCatalogService;

    public CommercialPackageService(CommercialPackageMapper packageMapper, CommercialPackageVersionMapper versionMapper, CommercialEntitlementMapper entitlementMapper, CommercialEntitlementCatalogService entitlementCatalogService) {
        this.packageMapper = packageMapper; this.versionMapper = versionMapper; this.entitlementMapper = entitlementMapper; this.entitlementCatalogService = entitlementCatalogService;
    }

    @Transactional
    public CommercialPackageVersionResponse createDraft(CommercialPackageDraftCommand command) {
        if (command.price() == null || command.price().signum() < 0) throw validation("售价必须为非负数。");
        if (command.effectiveFrom() == null) throw validation("生效时间不能为空。");
        if ("SUBSCRIPTION".equals(command.packageType()) && (command.periodMonths() == null || command.periodMonths() <= 0)) throw validation("会员订阅的周期月数必须大于 0。");
        List<ValidatedEntitlement> validatedEntitlements = validateEntitlements(command.entitlements());
        String code = command.code() == null || command.code().isBlank() ? generatePackageCode() : command.code();
        CommercialPackageEntity pack = packageMapper.selectOne(new QueryWrapper<CommercialPackageEntity>().eq("code", code));
        if (pack == null) { pack = new CommercialPackageEntity(); pack.code = code; pack.packageType = command.packageType(); pack.status = "ACTIVE"; pack.createdBy = command.operatorId(); pack.createdAt = LocalDateTime.now(); pack.updatedAt = pack.createdAt; packageMapper.insert(pack); }
        Integer latest = versionMapper.selectList(new QueryWrapper<CommercialPackageVersionEntity>().eq("package_id", pack.id).orderByDesc("version_no").last("limit 1")).stream().findFirst().map(v -> v.versionNo).orElse(0);
        CommercialPackageVersionEntity version = new CommercialPackageVersionEntity(); version.packageId = pack.id; version.versionNo = latest + 1; version.name = command.name(); version.description = command.description(); version.billingPeriod = command.billingPeriod(); version.periodMonths = command.periodMonths(); version.price = command.price(); version.listPrice = command.listPrice(); version.currency = command.currency(); version.effectiveFrom = command.effectiveFrom(); version.effectiveTo = command.effectiveTo(); version.status = "DRAFT"; version.createdBy = command.operatorId(); version.createdAt = LocalDateTime.now(); versionMapper.insert(version);
        for (ValidatedEntitlement validated : validatedEntitlements) { CommercialEntitlementEntity e = new CommercialEntitlementEntity(); e.packageVersionId = version.id; e.entitlementType = validated.definition().code; e.numericValue = validated.input().value(); e.textValue = validated.definition().name; e.createdAt = LocalDateTime.now(); entitlementMapper.insert(e); }
        return response(version);
    }

    @Transactional public CommercialPackageVersionResponse publish(Long packageId, Long versionId, Long operatorId) { CommercialPackageVersionEntity v = require(versionId); if (!packageId.equals(v.packageId)) throw new IllegalArgumentException("Package mismatch"); if (!"DRAFT".equals(v.status)) throw new IllegalStateException("Only draft versions can be published"); validateStoredEntitlements(versionId); v.status = "PUBLISHED"; v.publishedAt = LocalDateTime.now(); versionMapper.updateById(v); return response(v); }
    @Transactional public CommercialPackageVersionResponse unpublish(Long packageId, Long versionId) { CommercialPackageVersionEntity v = require(versionId); if (!packageId.equals(v.packageId)) throw new IllegalArgumentException("Package mismatch"); if (!"PUBLISHED".equals(v.status)) throw new IllegalStateException("Only published versions can be unpublished"); v.status = "OFF_SALE"; versionMapper.updateById(v); return response(v); }
    public List<CommercialPackageSummaryResponse> listPackages() {
        return packageMapper.selectList(new QueryWrapper<CommercialPackageEntity>().orderByAsc("id")).stream().map(pack -> {
            CommercialPackageVersionEntity latest = versionMapper.selectList(new QueryWrapper<CommercialPackageVersionEntity>()
                .eq("package_id", pack.id).orderByDesc("version_no").last("limit 1"))
                .stream().findFirst().orElse(null);
            return new CommercialPackageSummaryResponse(
                pack.id, pack.code, pack.packageType, pack.status,
                latest == null ? null : latest.versionNo,
                latest == null ? null : latest.name,
                latest == null ? null : latest.price,
                latest == null ? null : latest.currency,
                latest == null ? null : latest.status,
                latest == null ? List.of() : response(latest).entitlements(),
                latest == null ? null : latest.createdAt
            );
        }).toList();
    }
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
    CommercialPackageVersionResponse snapshot(Long versionId) { return response(require(versionId)); }
    @Transactional public void updateDraftName(Long versionId, String name) { CommercialPackageVersionEntity v = require(versionId); if (!"DRAFT".equals(v.status)) throw new IllegalStateException("Published package versions are immutable"); v.name = name; versionMapper.updateById(v); }
    private CommercialPackageVersionEntity require(Long id) { CommercialPackageVersionEntity v = versionMapper.selectById(id); if (v == null) throw new IllegalArgumentException("Package version not found"); return v; }
    private BusinessException validation(String message) { return new BusinessException(ErrorCode.VALIDATION_ERROR, message); }
    private String generatePackageCode() { return "PKG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(); }
    private List<ValidatedEntitlement> validateEntitlements(List<CommercialEntitlementInput> entitlements) {
        if (entitlements == null || entitlements.isEmpty()) throw validation("套餐至少需要一项权益。");
        Set<String> codes = new HashSet<>();
        return entitlements.stream().map(input -> {
            if (input == null || input.type() == null || input.type().isBlank()) throw validation("权益类型不能为空。");
            if (!codes.add(input.type())) throw validation("权益类型不能重复：" + input.type());
            CommercialEntitlementDefinitionEntity definition = entitlementCatalogService.requireByCode(input.type());
            validateDefinitionAndValue(definition, input.value());
            return new ValidatedEntitlement(input, definition);
        }).toList();
    }

    private void validateStoredEntitlements(Long versionId) {
        List<CommercialEntitlementEntity> stored = entitlementMapper.selectList(
            new QueryWrapper<CommercialEntitlementEntity>().eq("package_version_id", versionId));
        for (CommercialEntitlementEntity entitlement : stored) {
            CommercialEntitlementDefinitionEntity definition = entitlementCatalogService.requireByCode(entitlement.entitlementType);
            validateDefinitionAndValue(definition, entitlement.numericValue);
        }
    }

    private void validateDefinitionAndValue(CommercialEntitlementDefinitionEntity definition, BigDecimal value) {
        if (!CommercialEntitlementCatalogService.ACTIVE.equals(definition.status)) {
            throw validation("权益已停用：" + definition.name);
        }
        if (CommercialEntitlementCatalogService.SYSTEM.equals(definition.category)) {
            if (!SUPPORTED.contains(definition.code)) throw validation("不支持的系统权益：" + definition.code);
            if (value == null || value.signum() < 0) throw validation("系统权益值必须为非负数：" + definition.name);
            return;
        }
        if (value != null) throw validation("展示权益无需填写数值：" + definition.name);
    }

    private CommercialPackageVersionResponse response(CommercialPackageVersionEntity v) { return new CommercialPackageVersionResponse(v.packageId, v.id, v.versionNo, v.status, v.name, v.description, v.billingPeriod, v.periodMonths, v.price, v.listPrice, v.currency, v.effectiveFrom, v.effectiveTo, entitlementMapper.selectList(new QueryWrapper<CommercialEntitlementEntity>().eq("package_version_id", v.id)).stream().map(this::entitlementResponse).toList()); }
    private CommercialEntitlementInput entitlementResponse(CommercialEntitlementEntity entitlement) {
        String category = SUPPORTED.contains(entitlement.entitlementType) ? CommercialEntitlementCatalogService.SYSTEM : CommercialEntitlementCatalogService.DISPLAY;
        String name = entitlement.textValue;
        if (name == null || name.isBlank()) name = systemEntitlementName(entitlement.entitlementType);
        return new CommercialEntitlementInput(entitlement.entitlementType, entitlement.numericValue, name, category);
    }
    private String systemEntitlementName(String code) {
        return switch (code) {
            case "ONE_TIME_POINTS" -> "一次性积分";
            case "PERIODIC_POINTS" -> "周期积分";
            case "GLOBAL_DISCOUNT" -> "全局折扣";
            default -> code;
        };
    }
}

record CommercialPackageDraftCommand(String code, String packageType, String name, String description, String billingPeriod, Integer periodMonths, BigDecimal price, BigDecimal listPrice, String currency, LocalDateTime effectiveFrom, LocalDateTime effectiveTo, List<CommercialEntitlementInput> entitlements, Long operatorId) {}
record CommercialEntitlementInput(String type, BigDecimal value, String name, String category) {
    CommercialEntitlementInput(String type, BigDecimal value) { this(type, value, null, null); }
}
record CommercialPackageVersionResponse(Long packageId, Long versionId, Integer versionNo, String status, String name, String description, String billingPeriod, Integer periodMonths, BigDecimal price, BigDecimal listPrice, String currency, LocalDateTime effectiveFrom, LocalDateTime effectiveTo, List<CommercialEntitlementInput> entitlements) {}
record CommercialPackageSummaryResponse(
    Long id, String code, String packageType, String status, Integer latestVersionNo,
    String latestName, BigDecimal latestPrice, String latestCurrency, String latestStatus,
    List<CommercialEntitlementInput> latestEntitlements, LocalDateTime updatedAt
) {}
record CommercialCatalogItemResponse(Long packageId, Long packageVersionId, String code, String packageType, String name, String description, String billingPeriod, Integer periodMonths, BigDecimal price, BigDecimal listPrice, String currency, List<CommercialEntitlementInput> entitlements) {}
record ValidatedEntitlement(CommercialEntitlementInput input, CommercialEntitlementDefinitionEntity definition) {}
