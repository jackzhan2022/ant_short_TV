package com.antshorttv.accounting;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiModelPriceService {
    private final AiModelPriceVersionMapper versionMapper;
    private final AiModelPriceComponentMapper componentMapper;
    private final AiModelPriceVersionAllocator versionAllocator;

    public AiModelPriceService(
        AiModelPriceVersionMapper versionMapper,
        AiModelPriceComponentMapper componentMapper,
        AiModelPriceVersionAllocator versionAllocator
    ) {
        this.versionMapper = versionMapper;
        this.componentMapper = componentMapper;
        this.versionAllocator = versionAllocator;
    }

    @Transactional
    public AiModelPriceVersionEntity publish(
        AiModelPriceVersionEntity candidate,
        List<AiModelPriceComponentEntity> components
    ) {
        validateCandidate(candidate, components);
        List<AiModelPriceVersionEntity> existingVersions = versionMapper.selectPublishedByModel(candidate.modelId);
        candidate.versionNo = versionAllocator.next(candidate.modelId, "COST");
        closeCurrentVersionForFuturePublication(candidate, existingVersions);
        if (existingVersions.stream().anyMatch(existing -> overlaps(existing, candidate))) {
            throw new IllegalArgumentException("Model price effective interval overlap.");
        }
        candidate.status = "PUBLISHED";
        candidate.publishedAt = candidate.publishedAt == null ? LocalDateTime.now() : candidate.publishedAt;
        candidate.createdAt = candidate.createdAt == null ? LocalDateTime.now() : candidate.createdAt;
        versionMapper.insert(candidate);
        for (AiModelPriceComponentEntity component : components) {
            component.priceVersionId = candidate.id;
            component.createdAt = component.createdAt == null ? LocalDateTime.now() : component.createdAt;
            componentMapper.insert(component);
        }
        return candidate;
    }

    @Transactional
    public AiModelPriceVersionEntity revoke(Long versionId, LocalDateTime at) {
        AiModelPriceVersionEntity version = require(versionId);
        if (!"PUBLISHED".equals(version.status) || !at.isBefore(version.effectiveFrom)) {
            throw new IllegalArgumentException("Only future model cost price versions can be revoked.");
        }
        version.status = "REVOKED";
        versionMapper.updateById(version);
        return version;
    }

    public AiModelPriceVersionEntity require(Long versionId) {
        AiModelPriceVersionEntity version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new IllegalArgumentException("Model cost price version not found.");
        }
        return version;
    }

    public List<AiModelPriceVersionEntity> list(Long modelId) {
        return versionMapper.selectByModel(modelId);
    }

    private void closeCurrentVersionForFuturePublication(
        AiModelPriceVersionEntity candidate,
        List<AiModelPriceVersionEntity> existingVersions
    ) {
        if (candidate.effectiveTo != null) {
            return;
        }
        existingVersions.stream()
            .filter(existing -> existing.effectiveTo == null)
            .filter(existing -> existing.effectiveFrom.isBefore(candidate.effectiveFrom))
            .forEach(existing -> {
                existing.effectiveTo = candidate.effectiveFrom;
                versionMapper.updateById(existing);
            });
    }

    private boolean overlaps(AiModelPriceVersionEntity left, AiModelPriceVersionEntity right) {
        LocalDateTime leftEnd = left.effectiveTo;
        LocalDateTime rightEnd = right.effectiveTo;
        boolean leftStartsBeforeRightEnds = rightEnd == null || left.effectiveFrom.isBefore(rightEnd);
        boolean rightStartsBeforeLeftEnds = leftEnd == null || right.effectiveFrom.isBefore(leftEnd);
        return leftStartsBeforeRightEnds && rightStartsBeforeLeftEnds;
    }

    private void validateCandidate(
        AiModelPriceVersionEntity candidate,
        List<AiModelPriceComponentEntity> components
    ) {
        if (candidate == null || candidate.modelId == null || candidate.effectiveFrom == null) {
            throw new IllegalArgumentException("Model price version fields are required.");
        }
        if (candidate.effectiveTo != null && !candidate.effectiveTo.isAfter(candidate.effectiveFrom)) {
            throw new IllegalArgumentException("Model price effective interval is invalid.");
        }
        if (components == null || components.isEmpty()) {
            throw new IllegalArgumentException("Model price components are required.");
        }
        for (AiModelPriceComponentEntity component : components) {
            AiUsageMetric.valueOf(component.metric);
            if (component.unitSize == null || component.unitSize.signum() <= 0
                || component.unitPrice == null || component.unitPrice.signum() < 0
                || component.currency == null || component.currency.isBlank()) {
                throw new IllegalArgumentException("Model price component is invalid.");
            }
        }
    }
}
