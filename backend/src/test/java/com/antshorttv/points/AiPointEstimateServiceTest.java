package com.antshorttv.points;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.antshorttv.accounting.*;
import com.antshorttv.ai.*;
import com.antshorttv.aiimage.*;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.execution.*;
import com.antshorttv.productiontask.ProductionTaskService;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.review.ReviewWorkbenchService;
import com.antshorttv.security.*;
import com.antshorttv.video.*;
import com.antshorttv.workflowagent.run.WorkflowAgentRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AiPointEstimateServiceTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final TenantContextResolver tenants=mock(TenantContextResolver.class);
    final ProjectPermissionGuard permissions=mock(ProjectPermissionGuard.class);
    final AiImageTaskService images=mock(AiImageTaskService.class);
    final AiVideoTaskService videos=mock(AiVideoTaskService.class);
    final AssetImageBatchService batches=mock(AssetImageBatchService.class);
    final AiExecutionService executions=mock(AiExecutionService.class);
    final AiPointEstimateCalculator calculator=mock(AiPointEstimateCalculator.class);
    final ObjectMapper json=new ObjectMapper();
    final AiPointEstimateService service=new AiPointEstimateService(jdbc,tenants,permissions,
        mock(ProjectAiConfigService.class),mock(AiModelRouter.class),images,videos,batches,
        mock(ReviewWorkbenchService.class),mock(VideoDecompositionService.class),mock(ProductionTaskService.class),
        mock(AiExecutionAccessService.class),executions,mock(AiPointReservationMapper.class),
        mock(AiModelPointPriceVersionMapper.class),mock(AiModelPointPriceComponentMapper.class),
        mock(WorkflowAgentRunner.class),calculator,json);

    @BeforeEach
    void setUp() {
        when(tenants.requireActiveMember(7L)).thenReturn(new TenantContext(1L,7L,2L,"OWNER"));
        when(jdbc.queryForList(contains("team_point_account"),eq(7L))).thenReturn(List.of(
            Map.of("balance",new BigDecimal("100"),"trial_balance",new BigDecimal("90"))));
        when(calculator.calculate(eq(7L),any())).thenAnswer(call->{
            AiPointEstimatePlan plan=call.getArgument(1);
            return new AiPointEstimateCalculator.Line(plan.label(),plan.modelId(),20L,
                plan.usage().get(AiUsageMetric.CALL),plan.estimated(),plan.trialEligible());
        });
    }

    AiPointEstimatePlan plan(String label, String points) {
        return new AiPointEstimatePlan(label,9L,Map.of(AiUsageMetric.CALL,new BigDecimal(points)),Map.of(),false);
    }

    @Test
    void aggregatesEveryBatchItemWithoutCreatingTasksOrReservingPoints() {
        when(images.pointEstimateRequest(eq(7L),eq(3L),any())).thenReturn(plan("图片", "2.25"));
        var result=service.estimate(7L,List.of(
            new AiPointEstimateService.Operation("/api/projects/3/ai-image-tasks",json.createObjectNode()),
            new AiPointEstimateService.Operation("/api/projects/3/ai-image-tasks",json.createObjectNode())));
        assertThat(result.estimatedPoints()).isEqualByComparingTo("4.5");
        assertThat(result.lines()).hasSize(2);
        assertThat(result.sufficient()).isTrue();
        verify(images,times(2)).pointEstimateRequest(eq(7L),eq(3L),any());
        verifyNoMoreInteractions(images);
        verifyNoInteractions(executions);
        verify(jdbc).queryForList(contains("team_point_account"),eq(7L));
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void aVideoCannotSpendTrialPointsEvenWhenTheTotalBalanceIsLargeEnough() {
        when(videos.pointEstimateRequest(eq(7L),eq(3L),any())).thenReturn(plan("视频","11").paidOnly());
        var result=service.estimate(7L,List.of(new AiPointEstimateService.Operation("/api/projects/3/ai-video-tasks",json.createObjectNode())));
        assertThat(result.availablePoints()).isEqualByComparingTo("100");
        assertThat(result.paidAvailablePoints()).isEqualByComparingTo("10");
        assertThat(result.sufficient()).isFalse();
        verifyNoInteractions(executions);
    }

    @Test
    void usesTheBatchPlannerSoCompletedAndSkippedItemsAreNotCharged() {
        when(batches.pointEstimateRequest(eq(7L),eq(3L),any())).thenReturn(List.of(plan("待生成主图","4"),plan("待生成变装","4")));
        var result=service.estimate(7L,List.of(new AiPointEstimateService.Operation("/api/projects/3/asset-image-batches",json.createObjectNode())));
        assertThat(result.estimatedPoints()).isEqualByComparingTo("8");
        assertThat(result.lines()).hasSize(2);
        verifyNoInteractions(images,executions);
    }

    @Test
    void refusesCrossTenantAndUnsupportedOperationsInsteadOfInventingZeroPrices() {
        assertThatThrownBy(()->service.estimate(7L,List.of(new AiPointEstimateService.Operation(
            "/api/tenants/8/ai-executions/2/retry",null)))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.estimate(7L,List.of(new AiPointEstimateService.Operation(
            "/api/projects/3/unknown-ai-operation",null)))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(images,videos,executions,calculator);
    }

    @Test
    void aScriptRetryKeepsItsOriginalModelPriceAndTokenMetricsAfterProjectSettingsChange() throws Exception {
        var task=new AiExecutionTaskEntity();
        task.id=51L; task.tenantId=7L; task.projectId=3L; task.requestedModelId=9L;
        task.businessId=41L; task.businessType="SCRIPT_AI_OPERATION";
        when(executions.requireTask(51L)).thenReturn(task);
        var reservation=new AiPointReservationEntity();
        reservation.pointPriceVersionId=30L; reservation.discountRate=new BigDecimal("0.5"); reservation.trialEligible=true;
        var reservationMapper=(AiPointReservationMapper)org.springframework.test.util.ReflectionTestUtils.getField(service,"reservations");
        when(reservationMapper.selectByExecutionId(51L)).thenReturn(reservation);
        when(jdbc.queryForList(contains("select operation_type"),eq(7L),eq(41L))).thenReturn(List.of(Map.of(
            "operation_type","SCRIPT_EPISODE_SUMMARY","redacted_input_json","{\"episodeId\":2}")));
        when(jdbc.queryForList(contains("select content from script where"),eq(7L),eq(3L)))
            .thenReturn(List.of(Map.of("content","full script")));
        when(jdbc.queryForList(contains("select content from script_episode"),eq(7L),eq(3L),eq(2L)))
            .thenReturn(List.of(Map.of("content","episode body")));
        var configs=(ProjectAiConfigService)org.springframework.test.util.ReflectionTestUtils.getField(service,"configs");
        when(configs.resolveModelId(7L,3L,"TEXT")).thenReturn(99L);
        var input=new AiModelPointPriceComponentEntity(); input.metric="INPUT_TOKEN"; input.pointRate=BigDecimal.ONE; input.unitSize=BigDecimal.ONE;
        var output=new AiModelPointPriceComponentEntity(); output.metric="OUTPUT_TOKEN"; output.pointRate=BigDecimal.ONE; output.unitSize=BigDecimal.ONE;
        var components=(AiModelPointPriceComponentMapper)org.springframework.test.util.ReflectionTestUtils.getField(service,"components");
        when(components.selectByVersion(30L)).thenReturn(List.of(input,output));
        var agent=mock(com.antshorttv.workflowagent.agent.WorkflowAgentRecord.class);
        when(agent.maxTokens()).thenReturn(1024); when(agent.systemPrompt()).thenReturn("system");
        var runner=(WorkflowAgentRunner)org.springframework.test.util.ReflectionTestUtils.getField(service,"agents");
        when(runner.freezeFormal("short-drama-episode-summary",7L)).thenReturn(
            new com.antshorttv.workflowagent.run.WorkflowAgentExecutionPlan(agent,List.of()));
        service.estimate(7L,List.of(new AiPointEstimateService.Operation("/api/tenants/7/ai-executions/51/retry",null)));
        var captured=org.mockito.ArgumentCaptor.forClass(AiPointEstimatePlan.class);
        verify(calculator).calculate(eq(7L),captured.capture());
        assertThat(captured.getValue().modelId()).isEqualTo(9L);
        assertThat(captured.getValue().frozenPointPriceVersionId()).isEqualTo(30L);
        assertThat(captured.getValue().frozenDiscountRate()).isEqualByComparingTo("0.5");
        assertThat(captured.getValue().usage()).containsKeys(AiUsageMetric.INPUT_TOKEN,AiUsageMetric.OUTPUT_TOKEN);
        var versions=(AiModelPointPriceVersionMapper)org.springframework.test.util.ReflectionTestUtils.getField(service,"pointVersions");
        verifyNoInteractions(versions);
    }

    @Test
    void checksProjectPermissionBeforeInspectingAnyGenerationInputs() {
        when(permissions.require(7L,3L,"AI_SERVICE:USE")).thenThrow(new BusinessException(ErrorCode.FORBIDDEN,"denied"));
        assertThatThrownBy(()->service.estimate(7L,List.of(new AiPointEstimateService.Operation(
            "/api/projects/3/ai-image-tasks",null)))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(images,calculator);
    }
}
