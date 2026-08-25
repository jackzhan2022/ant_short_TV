package com.antshorttv.accounting;

import com.antshorttv.ai.AiModelMapper;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.execution.AiExecutionResponseMapper;
import com.antshorttv.execution.AiExecutionTaskEntity;
import com.antshorttv.execution.AiExecutionTaskMapper;
import com.antshorttv.points.AiPointLedgerMapper;
import com.antshorttv.points.AiPointPolicyComponentEntity;
import com.antshorttv.points.AiPointPolicyComponentMapper;
import com.antshorttv.points.AiPointPolicyService;
import com.antshorttv.points.AiPointPolicyVersionEntity;
import com.antshorttv.points.AiPointReservationEntity;
import com.antshorttv.points.AiPointReservationMapper;
import com.antshorttv.security.CurrentPrincipal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PlatformAiAccountingService {
    private final AiModelMapper modelMapper;
    private final AiModelPriceService modelPriceService;
    private final AiModelPriceComponentMapper modelPriceComponentMapper;
    private final AiPointPolicyService pointPolicyService;
    private final AiPointPolicyComponentMapper pointPolicyComponentMapper;
    private final AiExecutionTaskMapper executionTaskMapper;
    private final AiExecutionResponseMapper executionResponseMapper;
    private final AiUsageLineMapper usageLineMapper;
    private final AiUsageCostLineMapper usageCostLineMapper;
    private final AiPointReservationMapper reservationMapper;
    private final AiPointLedgerMapper ledgerMapper;
    private final CurrentPrincipal currentPrincipal;

    public PlatformAiAccountingService(
        AiModelMapper modelMapper,
        AiModelPriceService modelPriceService,
        AiModelPriceComponentMapper modelPriceComponentMapper,
        AiPointPolicyService pointPolicyService,
        AiPointPolicyComponentMapper pointPolicyComponentMapper,
        AiExecutionTaskMapper executionTaskMapper,
        AiExecutionResponseMapper executionResponseMapper,
        AiUsageLineMapper usageLineMapper,
        AiUsageCostLineMapper usageCostLineMapper,
        AiPointReservationMapper reservationMapper,
        AiPointLedgerMapper ledgerMapper,
        CurrentPrincipal currentPrincipal
    ) {
        this.modelMapper = modelMapper;
        this.modelPriceService = modelPriceService;
        this.modelPriceComponentMapper = modelPriceComponentMapper;
        this.pointPolicyService = pointPolicyService;
        this.pointPolicyComponentMapper = pointPolicyComponentMapper;
        this.executionTaskMapper = executionTaskMapper;
        this.executionResponseMapper = executionResponseMapper;
        this.usageLineMapper = usageLineMapper;
        this.usageCostLineMapper = usageCostLineMapper;
        this.reservationMapper = reservationMapper;
        this.ledgerMapper = ledgerMapper;
        this.currentPrincipal = currentPrincipal;
    }

    public ModelPriceVersionResponse publishModelPrice(
        Long modelId,
        PublishModelPriceRequest request
    ) {
        if (modelMapper.selectById(modelId) == null) {
            throw new BusinessException(ErrorCode.AI_MODEL_NOT_FOUND, "AI模型不存在。");
        }
        AiModelPriceVersionEntity version = new AiModelPriceVersionEntity();
        version.modelId = modelId;
        version.versionNo = request.versionNo();
        version.effectiveFrom = request.effectiveFrom();
        version.effectiveTo = request.effectiveTo();
        version.createdBy = currentPrincipal.require().userId();
        List<AiModelPriceComponentEntity> components = request.components().stream()
            .map(this::modelPriceComponent)
            .toList();
        modelPriceService.publish(version, components);
        return ModelPriceVersionResponse.from(
            version,
            modelPriceComponentMapper.selectByVersion(version.id)
        );
    }

    public PointPolicyVersionResponse publishPointPolicy(PublishPointPolicyRequest request) {
        if (request.modelId() != null && modelMapper.selectById(request.modelId()) == null) {
            throw new BusinessException(ErrorCode.AI_MODEL_NOT_FOUND, "AI模型不存在。");
        }
        AiPointPolicyVersionEntity version = new AiPointPolicyVersionEntity();
        version.scene = request.scene().trim();
        version.modelId = request.modelId();
        version.capability = blankToNull(request.capability());
        version.versionNo = request.versionNo();
        version.effectiveFrom = request.effectiveFrom();
        version.effectiveTo = request.effectiveTo();
        version.chargeProviderRejection = Boolean.TRUE.equals(request.chargeProviderRejection());
        version.chargeProviderBilledFailure = !Boolean.FALSE.equals(request.chargeProviderBilledFailure());
        version.chargeTimeout = !Boolean.FALSE.equals(request.chargeTimeout());
        version.chargeBusinessFailure = !Boolean.FALSE.equals(request.chargeBusinessFailure());
        List<AiPointPolicyComponentEntity> components = request.components().stream()
            .map(this::pointPolicyComponent)
            .toList();
        pointPolicyService.publish(version, components);
        return PointPolicyVersionResponse.from(
            version,
            pointPolicyComponentMapper.selectByPolicyVersion(version.id)
        );
    }

    public PlatformAiAccountingDetailResponse accountingDetail(Long executionId) {
        AiExecutionTaskEntity execution = executionTaskMapper.selectById(executionId);
        if (execution == null) {
            throw new BusinessException(ErrorCode.AI_EXECUTION_NOT_FOUND, "AI执行任务不存在。");
        }
        AiPointReservationEntity reservation = reservationMapper.selectByExecutionId(executionId);
        return new PlatformAiAccountingDetailResponse(
            executionResponseMapper.toResponse(execution),
            usageLineMapper.selectByExecutionId(executionId).stream().map(UsageLineResponse::from).toList(),
            usageCostLineMapper.selectByExecutionId(executionId).stream()
                .map(UsageCostLineResponse::from)
                .toList(),
            new PointSettlementDetailResponse(
                PointReservationResponse.from(reservation),
                ledgerMapper.selectByExecutionId(executionId).stream().map(PointLedgerResponse::from).toList()
            )
        );
    }

    private AiModelPriceComponentEntity modelPriceComponent(ModelPriceComponentRequest request) {
        AiModelPriceComponentEntity component = new AiModelPriceComponentEntity();
        component.metric = request.metric().trim();
        component.unitSize = request.unitSize();
        component.unitPrice = request.unitPrice();
        component.currency = request.currency().trim().toUpperCase();
        component.dimensionsJson = AiAccountingJson.write(request.dimensions());
        component.dimensionsKey = AiAccountingJson.canonicalKey(request.dimensions());
        return component;
    }

    private AiPointPolicyComponentEntity pointPolicyComponent(PointPolicyComponentRequest request) {
        AiPointPolicyComponentEntity component = new AiPointPolicyComponentEntity();
        component.metric = request.metric().trim();
        component.unitSize = request.unitSize();
        component.pointRate = request.pointRate();
        component.dimensionsJson = AiAccountingJson.write(request.dimensions());
        component.dimensionsKey = AiAccountingJson.canonicalKey(request.dimensions());
        component.createdAt = LocalDateTime.now();
        return component;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
