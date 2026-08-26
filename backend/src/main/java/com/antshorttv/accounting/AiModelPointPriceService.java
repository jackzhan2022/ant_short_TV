package com.antshorttv.accounting;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiModelPointPriceService {
    private final AiModelPointPriceVersionMapper versionMapper;
    private final AiModelPointPriceComponentMapper componentMapper;
    private final AiModelPriceVersionAllocator versionAllocator;

    public AiModelPointPriceService(
        AiModelPointPriceVersionMapper versionMapper,
        AiModelPointPriceComponentMapper componentMapper,
        AiModelPriceVersionAllocator versionAllocator
    ) {
        this.versionMapper = versionMapper;
        this.componentMapper = componentMapper;
        this.versionAllocator = versionAllocator;
    }

    @Transactional
    public AiModelPointPriceVersionEntity publish(
        Long modelId,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        List<AiModelPointPriceComponentEntity> components,
        Long createdBy
    ) {
        validate(modelId, effectiveFrom, effectiveTo, components);
        List<AiModelPointPriceVersionEntity> allVersions = versionMapper.selectByModel(modelId);
        List<AiModelPointPriceVersionEntity> existing = allVersions.stream()
            .filter(version -> !"REVOKED".equals(version.status)).toList();
        AiModelPointPriceVersionEntity candidate = new AiModelPointPriceVersionEntity();
        candidate.modelId = modelId;
        candidate.versionNo = versionAllocator.next(modelId, "POINT");
        candidate.status = "PUBLISHED";
        candidate.effectiveFrom = effectiveFrom;
        candidate.effectiveTo = effectiveTo;
        candidate.createdBy = createdBy;
        candidate.createdAt = LocalDateTime.now();
        candidate.publishedAt = candidate.createdAt;
        if (candidate.effectiveTo == null) {
            existing.stream()
                .filter(version -> version.effectiveTo == null)
                .filter(version -> version.effectiveFrom.isBefore(candidate.effectiveFrom))
                .forEach(version -> {
                    version.effectiveTo = candidate.effectiveFrom;
                    versionMapper.updateById(version);
                });
        }
        if (existing.stream().anyMatch(version -> overlaps(version, candidate))) {
            throw new IllegalArgumentException("Model point price effective interval overlap.");
        }
        versionMapper.insert(candidate);
        for (AiModelPointPriceComponentEntity component : components) {
            component.priceVersionId = candidate.id;
            component.dimensionsJson = AiAccountingJson.write(component.dimensions);
            component.dimensionsKey = AiAccountingJson.canonicalKey(component.dimensions);
            component.createdAt = candidate.createdAt;
            componentMapper.insert(component);
        }
        return candidate;
    }

    @Transactional
    public void revoke(Long versionId, LocalDateTime at) {
        AiModelPointPriceVersionEntity version = require(versionId);
        if (!"PUBLISHED".equals(version.status) || !at.isBefore(version.effectiveFrom)) {
            throw new IllegalArgumentException("Only future point price versions can be revoked.");
        }
        version.status = "REVOKED";
        versionMapper.updateById(version);
    }

    public AiModelPointPriceVersionEntity require(Long id) {
        AiModelPointPriceVersionEntity version = versionMapper.selectById(id);
        if (version == null) {
            throw new IllegalArgumentException("Model point price version not found.");
        }
        return version;
    }

    public List<AiModelPointPriceVersionEntity> list(Long modelId) { return versionMapper.selectByModel(modelId); }

    public List<AiModelPointPriceComponentEntity> components(Long versionId) {
        return componentMapper.selectByVersion(versionId);
    }

    private void validate(Long modelId, LocalDateTime from, LocalDateTime to, List<AiModelPointPriceComponentEntity> components) {
        if (modelId == null || from == null || (to != null && !to.isAfter(from)) || components == null || components.isEmpty()) {
            throw new IllegalArgumentException("Model point price fields are invalid.");
        }
        for (AiModelPointPriceComponentEntity component : components) {
            AiUsageMetric.valueOf(component.metric);
            if (component.unitSize == null || component.unitSize.signum() <= 0
                || component.pointRate == null || component.pointRate.signum() < 0) {
                throw new IllegalArgumentException("Model point price component is invalid.");
            }
        }
    }

    private boolean overlaps(AiModelPointPriceVersionEntity left, AiModelPointPriceVersionEntity right) {
        return (right.effectiveTo == null || left.effectiveFrom.isBefore(right.effectiveTo))
            && (left.effectiveTo == null || right.effectiveFrom.isBefore(left.effectiveTo));
    }
}
