package com.antshorttv.points;

import com.antshorttv.accounting.AiAccountingJson;
import com.antshorttv.accounting.AiUsageMetric;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiPointPolicyService {
    private final AiPointPolicyVersionMapper versionMapper;
    private final AiPointPolicyComponentMapper componentMapper;

    public AiPointPolicyService(
        AiPointPolicyVersionMapper versionMapper,
        AiPointPolicyComponentMapper componentMapper
    ) {
        this.versionMapper = versionMapper;
        this.componentMapper = componentMapper;
    }

    @Transactional
    public AiPointPolicyVersionEntity publish(
        AiPointPolicyVersionEntity candidate,
        List<AiPointPolicyComponentEntity> components
    ) {
        validate(candidate, components);
        List<AiPointPolicyVersionEntity> existing = versionMapper.selectPublishedBySelector(
            candidate.scene,
            candidate.modelId,
            candidate.capability
        );
        existing.stream()
            .filter(version -> version.effectiveTo == null)
            .filter(version -> version.effectiveFrom.isBefore(candidate.effectiveFrom))
            .forEach(version -> {
                version.effectiveTo = candidate.effectiveFrom;
                versionMapper.updateById(version);
            });
        if (existing.stream().anyMatch(version -> overlaps(version, candidate))) {
            throw new IllegalArgumentException("Point policy effective interval overlap.");
        }
        LocalDateTime now = LocalDateTime.now();
        candidate.status = "PUBLISHED";
        candidate.publishedAt = now;
        candidate.createdAt = now;
        versionMapper.insert(candidate);
        for (AiPointPolicyComponentEntity component : components) {
            component.policyVersionId = candidate.id;
            component.createdAt = now;
            componentMapper.insert(component);
        }
        return candidate;
    }

    private void validate(
        AiPointPolicyVersionEntity candidate,
        List<AiPointPolicyComponentEntity> components
    ) {
        if (candidate == null || candidate.scene == null || candidate.scene.isBlank()
            || candidate.versionNo == null || candidate.effectiveFrom == null) {
            throw new IllegalArgumentException("Point policy version fields are required.");
        }
        if (candidate.effectiveTo != null && !candidate.effectiveTo.isAfter(candidate.effectiveFrom)) {
            throw new IllegalArgumentException("Point policy effective interval is invalid.");
        }
        if (components == null || components.isEmpty()) {
            throw new IllegalArgumentException("Point policy components are required.");
        }
        for (AiPointPolicyComponentEntity component : components) {
            if (!"FIXED_EXECUTION".equals(component.metric)) {
                AiUsageMetric.valueOf(component.metric);
            }
            if (component.unitSize == null || component.unitSize.signum() <= 0
                || component.pointRate == null || component.pointRate.signum() < 0) {
                throw new IllegalArgumentException("Point policy component is invalid.");
            }
            component.dimensionsJson = component.dimensionsJson == null
                ? AiAccountingJson.write(java.util.Map.of())
                : component.dimensionsJson;
        }
    }

    private boolean overlaps(AiPointPolicyVersionEntity left, AiPointPolicyVersionEntity right) {
        boolean leftStartsBeforeRightEnds = right.effectiveTo == null
            || left.effectiveFrom.isBefore(right.effectiveTo);
        boolean rightStartsBeforeLeftEnds = left.effectiveTo == null
            || right.effectiveFrom.isBefore(left.effectiveTo);
        return leftStartsBeforeRightEnds && rightStartsBeforeLeftEnds;
    }
}
