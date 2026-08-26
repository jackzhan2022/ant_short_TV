package com.antshorttv.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class AiUsageCostAccountingTest {

    private static final LocalDateTime CONTACTED_AT = LocalDateTime.of(2026, 8, 25, 8, 0);

    @Autowired
    private AiUsageAccountingService accountingService;

    @Autowired
    private AiUsageExtractor usageExtractor;

    @Autowired
    private AiModelPriceService priceService;

    @Autowired
    private AiModelPriceVersionMapper priceVersionMapper;

    @Autowired
    private AiModelPriceComponentMapper priceComponentMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanAccountingTables() {
        jdbc.update("delete from ai_usage_cost_line");
        jdbc.update("delete from ai_usage_line");
        jdbc.update("delete from ai_execution_task where id = 1907");
        jdbc.update("delete from ai_model_price_component");
        jdbc.update("delete from ai_model_price_version");
    }

    @Test
    void usesCostVersionFrozenOnExecutionAfterANewerVersionBecomesEffective() {
        Long frozenVersionId = priceVersion(107L);
        priceComponent(frozenVersionId, AiUsageMetric.IMAGE, "1", "0.04000000", Map.of());

        AiModelPriceVersionEntity replacement = new AiModelPriceVersionEntity();
        replacement.modelId = 107L;
        replacement.effectiveFrom = CONTACTED_AT.plusHours(1);
        Long replacementVersionId = priceService.publish(
            replacement,
            List.of(component(AiUsageMetric.IMAGE, "1", "0.09000000", Map.of()))
        ).id;
        insertExecutionWithFrozenCostVersion(1907L, 107L, frozenVersionId);

        accountingService.record(AiUsageCommand.resultMeasured(
            context(1907L, 107L), AiUsageMetric.IMAGE, "1", Map.of(), CONTACTED_AT.plusHours(2)
        ));

        AiExecutionCostSummary summary = accountingService.priceExecution(1907L, Set.of(AiUsageMetric.IMAGE));
        Long usedVersionId = jdbc.queryForObject(
            "select price_version_id from ai_usage_cost_line where execution_id = 1907",
            Long.class
        );
        assertThat(replacementVersionId).isNotEqualTo(frozenVersionId);
        assertThat(usedVersionId).isEqualTo(frozenVersionId);
        assertThat(summary.totalsByCurrency()).containsEntry("USD", new BigDecimal("0.04000000"));
    }

    @Test
    void pricesTokenAndPerCallUsageAsCompositeCost() {
        Long versionId = priceVersion(101L);
        priceComponent(versionId, AiUsageMetric.CALL, "1", "0.01000000", Map.of());
        priceComponent(versionId, AiUsageMetric.INPUT_TOKEN, "1000", "0.00100000", Map.of());
        priceComponent(versionId, AiUsageMetric.OUTPUT_TOKEN, "1000", "0.00200000", Map.of());
        AiUsageContext context = context(1001L, 101L);

        accountingService.record(AiUsageCommand.requestDerived(context, AiUsageMetric.CALL, "1", Map.of(), CONTACTED_AT));
        accountingService.record(AiUsageCommand.providerReported(context, AiUsageMetric.INPUT_TOKEN, "1000", Map.of(), CONTACTED_AT));
        accountingService.record(AiUsageCommand.providerReported(context, AiUsageMetric.OUTPUT_TOKEN, "2000", Map.of(), CONTACTED_AT));

        AiExecutionCostSummary summary = accountingService.priceExecution(
            1001L,
            Set.of(AiUsageMetric.CALL, AiUsageMetric.INPUT_TOKEN, AiUsageMetric.OUTPUT_TOKEN)
        );

        assertThat(summary.status()).isEqualTo(AiUsageCostStatus.PRICED);
        assertThat(summary.totalsByCurrency()).containsEntry("USD", new BigDecimal("0.01500000"));
        assertThat(accountingService.summarizeCallLog(1201L))
            .containsEntry("USD", new BigDecimal("0.01500000"));
    }

    @Test
    void extractsProviderRequestAndResultUsageWithExplicitSources() {
        AiUsageContext context = context(1010L, 110L);

        List<AiUsageCommand> tokens = usageExtractor.providerTokens(context, 12, 8, CONTACTED_AT);
        AiUsageCommand call = usageExtractor.requestCall(context, CONTACTED_AT);
        AiUsageCommand characters = usageExtractor.requestCharacters(context, 240, CONTACTED_AT);
        AiUsageCommand images = usageExtractor.resultImages(
            context, 2, Map.of("size", "1024x1024"), CONTACTED_AT
        );
        AiUsageCommand video = usageExtractor.resultVideoSeconds(
            context, new BigDecimal("5.5"), Map.of("resolution", "1080p"), CONTACTED_AT
        );

        assertThat(tokens).extracting(AiUsageCommand::metric)
            .containsExactly(AiUsageMetric.INPUT_TOKEN, AiUsageMetric.OUTPUT_TOKEN);
        assertThat(tokens).allMatch(command -> command.source() == AiUsageSource.PROVIDER_REPORTED);
        assertThat(call.source()).isEqualTo(AiUsageSource.REQUEST_DERIVED);
        assertThat(characters.metric()).isEqualTo(AiUsageMetric.CHARACTER);
        assertThat(images.source()).isEqualTo(AiUsageSource.RESULT_MEASURED);
        assertThat(video.quantity()).isEqualByComparingTo("5.5");
    }

    @Test
    void pricesImagesAndVideoSecondsUsingMatchingDimensions() {
        Long imageVersion = priceVersion(102L);
        priceComponent(imageVersion, AiUsageMetric.CALL, "1", "0.01000000", Map.of());
        priceComponent(imageVersion, AiUsageMetric.IMAGE, "1", "0.04000000", Map.of("size", "1024x1024"));
        AiUsageContext imageContext = context(1002L, 102L);
        accountingService.record(AiUsageCommand.requestDerived(imageContext, AiUsageMetric.CALL, "1", Map.of(), CONTACTED_AT));
        accountingService.record(AiUsageCommand.resultMeasured(
            imageContext, AiUsageMetric.IMAGE, "2", Map.of("size", "1024x1024"), CONTACTED_AT
        ));

        Long videoVersion = priceVersion(103L);
        priceComponent(videoVersion, AiUsageMetric.VIDEO_SECOND, "1", "0.02000000", Map.of("resolution", "1080p"));
        AiUsageContext videoContext = context(1003L, 103L);
        accountingService.record(AiUsageCommand.resultMeasured(
            videoContext, AiUsageMetric.VIDEO_SECOND, "5.5", Map.of("resolution", "1080p"), CONTACTED_AT
        ));

        assertThat(accountingService.priceExecution(1002L, Set.of(AiUsageMetric.CALL, AiUsageMetric.IMAGE))
            .totalsByCurrency()).containsEntry("USD", new BigDecimal("0.09000000"));
        assertThat(accountingService.priceExecution(1003L, Set.of(AiUsageMetric.VIDEO_SECOND))
            .totalsByCurrency()).containsEntry("USD", new BigDecimal("0.11000000"));
    }

    @Test
    void marksMissingPriceUnpricedAndMissingRequiredUsageIncomplete() {
        priceVersion(104L);
        AiUsageContext context = context(1004L, 104L);
        accountingService.record(AiUsageCommand.resultMeasured(
            context, AiUsageMetric.IMAGE, "1", Map.of("size", "2048x2048"), CONTACTED_AT
        ));

        AiExecutionCostSummary unpriced = accountingService.priceExecution(1004L, Set.of(AiUsageMetric.IMAGE));
        AiExecutionCostSummary incomplete = accountingService.priceExecution(
            1004L,
            Set.of(AiUsageMetric.IMAGE, AiUsageMetric.VIDEO_SECOND)
        );

        assertThat(unpriced.status()).isEqualTo(AiUsageCostStatus.UNPRICED);
        assertThat(unpriced.totalsByCurrency()).isEmpty();
        assertThat(incomplete.status()).isEqualTo(AiUsageCostStatus.INCOMPLETE);
    }

    @Test
    void resolvesPriceByProviderContactTimeAndRejectsOverlappingVersions() {
        Long currentVersionId = priceVersion(106L);
        priceComponent(currentVersionId, AiUsageMetric.IMAGE, "1", "0.04000000", Map.of());

        AiModelPriceVersionEntity future = new AiModelPriceVersionEntity();
        future.modelId = 106L;
        future.versionNo = 2;
        future.status = "PUBLISHED";
        future.effectiveFrom = CONTACTED_AT.plusDays(1);
        future.createdAt = CONTACTED_AT;
        future.publishedAt = CONTACTED_AT;
        priceService.publish(future, List.of(component(AiUsageMetric.IMAGE, "1", "0.08000000", Map.of())));

        AiModelPriceVersionEntity overlapping = new AiModelPriceVersionEntity();
        overlapping.modelId = 106L;
        overlapping.versionNo = 3;
        overlapping.status = "PUBLISHED";
        overlapping.effectiveFrom = CONTACTED_AT;
        overlapping.effectiveTo = CONTACTED_AT.plusDays(2);
        overlapping.createdAt = CONTACTED_AT;

        assertThatThrownBy(() -> priceService.publish(
            overlapping,
            List.of(component(AiUsageMetric.IMAGE, "1", "0.06000000", Map.of()))
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("overlap");

        AiUsageContext context = context(1006L, 106L);
        accountingService.record(AiUsageCommand.resultMeasured(
            context, AiUsageMetric.IMAGE, "1", Map.of(), CONTACTED_AT
        ));
        assertThat(accountingService.priceExecution(1006L, Set.of(AiUsageMetric.IMAGE)).totalsByCurrency())
            .containsEntry("USD", new BigDecimal("0.04000000"));
    }

    @Test
    void preservesCorrectionsAsAdjustmentLinesAndReconcilesToSources() {
        Long versionId = priceVersion(105L);
        priceComponent(versionId, AiUsageMetric.IMAGE, "1", "0.04000000", Map.of());
        AiUsageLineEntity original = accountingService.record(AiUsageCommand.resultMeasured(
            context(1005L, 105L), AiUsageMetric.IMAGE, "2", Map.of(), CONTACTED_AT
        ));

        AiUsageLineEntity adjustment = accountingService.adjustQuantity(
            original.id,
            new BigDecimal("3"),
            CONTACTED_AT.plusHours(1)
        );
        AiExecutionCostSummary summary = accountingService.priceExecution(1005L, Set.of(AiUsageMetric.IMAGE));
        AiUsageCostReconciliation reconciliation = accountingService.reconcileExecution(1005L);

        assertThat(adjustment.source).isEqualTo(AiUsageSource.ADJUSTMENT.name());
        assertThat(adjustment.adjustmentOfUsageLineId).isEqualTo(original.id);
        assertThat(adjustment.quantity).isEqualByComparingTo("1");
        assertThat(summary.totalsByCurrency()).containsEntry("USD", new BigDecimal("0.12000000"));
        assertThat(reconciliation.balanced()).isTrue();
        assertThat(reconciliation.usageLineCount()).isEqualTo(2);
        assertThat(reconciliation.costLineCount()).isEqualTo(2);
    }

    private AiUsageContext context(Long executionId, Long modelId) {
        return new AiUsageContext(1L, executionId, executionId + 100, executionId + 200, modelId);
    }

    private void insertExecutionWithFrozenCostVersion(Long executionId, Long modelId, Long costVersionId) {
        jdbc.update("""
            insert into ai_execution_task (
              id, tenant_id, user_id, scene, capability, business_type, requested_model_id,
              cost_price_version_id, redacted_input_json, status, phase, progress,
              execution_version, client_idempotency_key, trace_id, priority, next_run_at,
              retryable, usage_cost_status, point_settlement_status, reserved_points,
              settled_points, released_points, created_at, updated_at
            ) values (?, 1, 1, 'test', 'IMAGE', 'test', ?, ?, '{}', 'RUNNING', 'GENERATE', 0,
                      1, ?, ?, 100, now(), true, 'PENDING', 'PENDING', 0, 0, 0, now(), now())
            """,
            executionId, modelId, costVersionId, "frozen-cost-" + executionId, "trace-" + executionId
        );
    }

    private Long priceVersion(Long modelId) {
        AiModelPriceVersionEntity version = new AiModelPriceVersionEntity();
        version.modelId = modelId;
        version.versionNo = 1;
        version.status = "PUBLISHED";
        version.effectiveFrom = CONTACTED_AT.minusDays(1);
        version.effectiveTo = null;
        version.publishedAt = CONTACTED_AT.minusDays(1);
        version.createdAt = CONTACTED_AT.minusDays(1);
        priceVersionMapper.insert(version);
        return version.id;
    }

    private void priceComponent(
        Long versionId,
        AiUsageMetric metric,
        String unitSize,
        String unitPrice,
        Map<String, String> dimensions
    ) {
        AiModelPriceComponentEntity component = new AiModelPriceComponentEntity();
        component.priceVersionId = versionId;
        component.metric = metric.name();
        component.unitSize = new BigDecimal(unitSize);
        component.unitPrice = new BigDecimal(unitPrice);
        component.currency = "USD";
        component.dimensionsJson = AiAccountingJson.write(dimensions);
        component.dimensionsKey = AiAccountingJson.canonicalKey(dimensions);
        component.createdAt = CONTACTED_AT.minusDays(1);
        priceComponentMapper.insert(component);
    }

    private AiModelPriceComponentEntity component(
        AiUsageMetric metric,
        String unitSize,
        String unitPrice,
        Map<String, String> dimensions
    ) {
        AiModelPriceComponentEntity component = new AiModelPriceComponentEntity();
        component.metric = metric.name();
        component.unitSize = new BigDecimal(unitSize);
        component.unitPrice = new BigDecimal(unitPrice);
        component.currency = "USD";
        component.dimensionsJson = AiAccountingJson.write(dimensions);
        component.dimensionsKey = AiAccountingJson.canonicalKey(dimensions);
        component.createdAt = CONTACTED_AT;
        return component;
    }
}
