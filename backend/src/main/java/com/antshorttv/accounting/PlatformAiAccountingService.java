package com.antshorttv.accounting;

import com.antshorttv.ai.AiModelMapper;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.execution.AiExecutionResponseMapper;
import com.antshorttv.execution.AiExecutionTaskEntity;
import com.antshorttv.execution.AiExecutionTaskMapper;
import com.antshorttv.points.AiPointLedgerMapper;
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
    private final AiExecutionTaskMapper executionTaskMapper;
    private final AiExecutionResponseMapper executionResponseMapper;
    private final AiUsageLineMapper usageLineMapper;
    private final AiUsageCostLineMapper usageCostLineMapper;
    private final AiPointReservationMapper reservationMapper;
    private final AiPointLedgerMapper ledgerMapper;
    private final AiModelPointPriceService modelPointPriceService;
    private final CurrentPrincipal currentPrincipal;

    public PlatformAiAccountingService(
        AiModelMapper modelMapper,
        AiModelPriceService modelPriceService,
        AiModelPriceComponentMapper modelPriceComponentMapper,
        AiExecutionTaskMapper executionTaskMapper,
        AiExecutionResponseMapper executionResponseMapper,
        AiUsageLineMapper usageLineMapper,
        AiUsageCostLineMapper usageCostLineMapper,
        AiPointReservationMapper reservationMapper,
        AiPointLedgerMapper ledgerMapper,
        AiModelPointPriceService modelPointPriceService,
        CurrentPrincipal currentPrincipal
    ) {
        this.modelMapper = modelMapper;
        this.modelPriceService = modelPriceService;
        this.modelPriceComponentMapper = modelPriceComponentMapper;
        this.executionTaskMapper = executionTaskMapper;
        this.executionResponseMapper = executionResponseMapper;
        this.usageLineMapper = usageLineMapper;
        this.usageCostLineMapper = usageCostLineMapper;
        this.reservationMapper = reservationMapper;
        this.ledgerMapper = ledgerMapper;
        this.modelPointPriceService = modelPointPriceService;
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

    public ModelPointPriceVersionResponse publishModelPointPrice(
        Long modelId,
        PublishModelPointPriceRequest request
    ) {
        if (modelMapper.selectById(modelId) == null) {
            throw new BusinessException(ErrorCode.AI_MODEL_NOT_FOUND, "AI模型不存在。");
        }
        List<AiModelPointPriceComponentEntity> components = request.components().stream()
            .map(this::modelPointPriceComponent)
            .toList();
        AiModelPointPriceVersionEntity version = modelPointPriceService.publish(
            modelId, request.effectiveFrom(), request.effectiveTo(), components,
            currentPrincipal.require().userId()
        );
        return ModelPointPriceVersionResponse.from(version, modelPointPriceService.components(version.id));
    }

    public ModelBillingHistoryResponse billingHistory(Long modelId) {
        if (modelMapper.selectById(modelId) == null) {
            throw new BusinessException(ErrorCode.AI_MODEL_NOT_FOUND, "AI模型不存在。");
        }
        return new ModelBillingHistoryResponse(
            modelId,
            modelPriceService.list(modelId).stream().map(version -> ModelPriceVersionResponse.from(
                version, modelPriceComponentMapper.selectByVersion(version.id))).toList(),
            modelPointPriceService.list(modelId).stream().map(version -> ModelPointPriceVersionResponse.from(
                version, modelPointPriceService.components(version.id))).toList()
        );
    }

    public ModelPriceVersionResponse revokeCostPrice(Long modelId, Long versionId) {
        AiModelPriceVersionEntity version = modelPriceService.require(versionId);
        if (!modelId.equals(version.modelId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "成本价版本不属于指定模型。");
        }
        version = modelPriceService.revoke(versionId, LocalDateTime.now());
        return ModelPriceVersionResponse.from(version, modelPriceComponentMapper.selectByVersion(version.id));
    }

    public ModelPointPriceVersionResponse revokePointPrice(Long modelId, Long versionId) {
        AiModelPointPriceVersionEntity version = modelPointPriceService.require(versionId);
        if (!modelId.equals(version.modelId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "积分价版本不属于指定模型。");
        }
        modelPointPriceService.revoke(versionId, LocalDateTime.now());
        return ModelPointPriceVersionResponse.from(
            modelPointPriceService.require(versionId), modelPointPriceService.components(versionId));
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
            new BillingEvidenceResponse(
                execution.costPriceVersionId,
                execution.pointPriceVersionId,
                execution.pointPriceVersionId == null ? List.of() : modelPointPriceService
                    .components(execution.pointPriceVersionId).stream()
                    .map(component -> new PointPolicyComponentResponse(
                        component.id, component.metric, component.unitSize, component.pointRate
                    ))
                    .toList()
            ),
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

    private AiModelPointPriceComponentEntity modelPointPriceComponent(PointPolicyComponentRequest request) {
        AiModelPointPriceComponentEntity component = new AiModelPointPriceComponentEntity();
        component.metric = request.metric().trim();
        component.unitSize = request.unitSize();
        component.pointRate = request.pointRate();
        component.dimensions = request.dimensions();
        return component;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
