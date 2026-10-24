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
    final WorkbenchPointPolicy workbench=mock(WorkbenchPointPolicy.class);
    final AiPointEstimateService service=new AiPointEstimateService(jdbc,tenants,permissions,
        mock(ProjectAiConfigService.class),mock(AiModelRouter.class),images,videos,batches,
        mock(ReviewWorkbenchService.class),mock(VideoDecompositionService.class),mock(ProductionTaskService.class),
        mock(AiExecutionAccessService.class),executions,mock(AiPointReservationMapper.class),
        mock(AiModelPointPriceVersionMapper.class),mock(AiModelPointPriceComponentMapper.class),
        mock(WorkflowAgentRunner.class),calculator,json,workbench);

    @BeforeEach
    void setUp() {
        when(workbench.estimateRemaining(anyLong(), any())).thenReturn(new BigDecimal("1000"));
        when(tenants.requireActiveMember(7L)).thenReturn(new TenantContext(1L,7L,2L,"OWNER"));
        when(jdbc.queryForList(contains("team_point_account"),eq(7L))).thenReturn(List.of(
            Map.of("balance",new BigDecimal("100"),"trial_balance",new BigDecimal("90"))));
        when(calculator.calculate(eq(7L),any(),any(AiPointEstimateCalculator.Context.class))).thenAnswer(call->{
            AiPointEstimatePlan plan=call.getArgument(1);
            return new AiPointEstimateCalculator.Line(plan.label(),plan.modelId(),20L,
                plan.usage().get(AiUsageMetric.CALL),plan.estimated(),plan.trialEligible());
        });
    }

    @Test
    void allLinesForTheSameWorkbenchProjectShareItsRemainingCap() {
        when(workbench.estimateRemaining(7L, 3L)).thenReturn(new BigDecimal("350"));
        when(images.pointEstimateRequest(eq(7L), eq(3L), any())).thenReturn(plan("分析", "300").workbench(3L));
        var result = service.estimate(7L, List.of(
            new AiPointEstimateService.Operation("/api/projects/3/ai-image-tasks", json.createObjectNode()),
            new AiPointEstimateService.Operation("/api/projects/3/ai-image-tasks", json.createObjectNode())));
        assertThat(result.estimatedPoints()).isEqualByComparingTo("350");
        assertThat(result.lines().get(0).points()).isEqualByComparingTo("300");
        assertThat(result.lines().get(1).points()).isEqualByComparingTo("50");
        assertThat(result.notice()).contains("20%", "1000");
        verify(workbench, times(1)).estimateRemaining(7L, 3L);
        verifyNoInteractions(executions);
    }

    @Test
    void workbenchCapDoesNotReduceUnrelatedItemsInTheSameEstimate() {
        when(workbench.estimateRemaining(7L, 3L)).thenReturn(BigDecimal.ZERO);
        when(images.pointEstimateRequest(eq(7L), eq(3L), any())).thenReturn(plan("分析", "200").workbench(3L));
        when(images.pointEstimateRequest(eq(7L), eq(4L), any())).thenReturn(plan("图片", "40"));
        var result = service.estimate(7L, List.of(
            new AiPointEstimateService.Operation("/api/projects/3/ai-image-tasks", json.createObjectNode()),
            new AiPointEstimateService.Operation("/api/projects/4/ai-image-tasks", json.createObjectNode())));
        assertThat(result.estimatedPoints()).isEqualByComparingTo("40");
        assertThat(result.lines().get(0).points()).isZero();
        assertThat(result.lines().get(1).points()).isEqualByComparingTo("40");
    }

    @Test
    void separateNewProjectPlansDoNotShareOneNullProjectCeiling() {
        var configs = (ProjectAiConfigService) org.springframework.test.util.ReflectionTestUtils.getField(service, "configs");
        var router = (AiModelRouter) org.springframework.test.util.ReflectionTestUtils.getField(service, "router");
        when(router.route(9L,"TEXT")).thenReturn(mock(com.antshorttv.ai.AiModelRoute.class));
        when(configs.resolveModelId(7L, null, "TEXT")).thenReturn(9L);
        var versions = (AiModelPointPriceVersionMapper) org.springframework.test.util.ReflectionTestUtils.getField(service, "pointVersions");
        var version = new AiModelPointPriceVersionEntity(); version.id = 20L;
        when(versions.selectEffective(eq(9L), any())).thenReturn(version);
        var components = (AiModelPointPriceComponentMapper) org.springframework.test.util.ReflectionTestUtils.getField(service, "components");
        var component = new AiModelPointPriceComponentEntity(); component.metric = "CALL";
        component.unitSize = BigDecimal.ONE; component.pointRate = BigDecimal.ONE;
        when(components.selectByVersion(20L)).thenReturn(List.of(component));
        var agent = mock(com.antshorttv.workflowagent.agent.WorkflowAgentRecord.class);
        when(agent.maxTokens()).thenReturn(1024); when(agent.systemPrompt()).thenReturn("system");
        var runner = (WorkflowAgentRunner) org.springframework.test.util.ReflectionTestUtils.getField(service, "agents");
        when(runner.freezeFormal(anyString(), eq(7L))).thenReturn(
            new com.antshorttv.workflowagent.run.WorkflowAgentExecutionPlan(agent, List.of()));
        when(calculator.calculate(eq(7L), any(),any(AiPointEstimateCalculator.Context.class))).thenAnswer(call -> {
            AiPointEstimatePlan plan = call.getArgument(1);
            return new AiPointEstimateCalculator.Line(plan.label(), plan.modelId(), 20L,
                new BigDecimal("800"), true, true);
        });
        var data = json.createObjectNode().put("initialScriptContent", "第一集：测试剧本");
        var result = service.estimate(7L, List.of(new AiPointEstimateService.Operation("/api/projects", data),
            new AiPointEstimateService.Operation("/api/projects", data)));
        assertThat(result.estimatedPoints()).isEqualByComparingTo("2000");
        assertThat(result.lines().stream().map(AiPointEstimateCalculator.Line::points).reduce(BigDecimal.ZERO, BigDecimal::add))
            .isEqualByComparingTo("2000");
        verify(versions,times(1)).selectEffective(eq(9L),any());
        verify(components,times(1)).selectByVersion(20L);
        verify(router,times(1)).route(9L,"TEXT");
        verify(runner,times(1)).freezeFormal("short-drama-global-understanding",7L);
        when(jdbc.queryForList(contains("team_point_account"),eq(7L))).thenReturn(List.of(
            Map.of("balance",new BigDecimal("3000"),"trial_balance",BigDecimal.ZERO)));
        var next=service.estimate(7L,List.of(new AiPointEstimateService.Operation("/api/projects",data)));
        assertThat(next.sufficient()).isTrue();
        verify(versions,times(2)).selectEffective(eq(9L),any());
        verify(components,times(2)).selectByVersion(20L);
        verify(router,times(2)).route(9L,"TEXT");
        verify(jdbc,times(2)).queryForList(contains("team_point_account"),eq(7L));
    }

    AiPointEstimatePlan plan(String label, String points) {
        return new AiPointEstimatePlan(label,9L,Map.of(AiUsageMetric.CALL,new BigDecimal(points)),Map.of(),false);
    }

    @Test
    void characterSimilarityQuotesCanonicalPromptsWithoutStartingAnExecution() {
        var configs = (ProjectAiConfigService) org.springframework.test.util.ReflectionTestUtils.getField(service,"configs");
        when(configs.resolveModelId(7L,3L,"TEXT")).thenReturn(9L);
        var router = (AiModelRouter) org.springframework.test.util.ReflectionTestUtils.getField(service,"router");
        when(router.route(9L,"TEXT")).thenReturn(mock(AiModelRoute.class));
        var versions = (AiModelPointPriceVersionMapper) org.springframework.test.util.ReflectionTestUtils.getField(service,"pointVersions");
        var version = new AiModelPointPriceVersionEntity(); version.id=20L;
        when(versions.selectEffective(eq(9L),any())).thenReturn(version);
        var input = new AiModelPointPriceComponentEntity(); input.metric="INPUT_TOKEN"; input.pointRate=BigDecimal.ONE; input.unitSize=BigDecimal.ONE;
        var output = new AiModelPointPriceComponentEntity(); output.metric="OUTPUT_TOKEN"; output.pointRate=BigDecimal.ONE; output.unitSize=BigDecimal.ONE;
        var components = (AiModelPointPriceComponentMapper) org.springframework.test.util.ReflectionTestUtils.getField(service,"components");
        when(components.selectByVersion(20L)).thenReturn(List.of(input,output));
        when(jdbc.queryForList(contains("select c.id,c.name,c.prompt"),eq(7L),eq(3L),eq(7L),eq(3L)))
            .thenReturn(List.of(Map.of("id",11L,"name","甲","prompt","长窄脸"),Map.of("id",12L,"name","乙","prompt","方脸")));

        var result=service.estimate(7L,List.of(new AiPointEstimateService.Operation("/api/projects/3/character-prompt-similarity",null)));

        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().get(0).label()).isEqualTo("角色提示词相似度检查");
        var captured=org.mockito.ArgumentCaptor.forClass(AiPointEstimatePlan.class);
        verify(calculator).calculate(eq(7L),captured.capture(),any(AiPointEstimateCalculator.Context.class));
        assertThat(captured.getValue().usage().get(AiUsageMetric.OUTPUT_TOKEN)).isEqualByComparingTo("16384");
        verify(permissions).require(7L,3L,"ELEMENT:AI_EXTRACT");
        verifyNoInteractions(executions);

        var task=new AiExecutionTaskEntity();
        task.id=51L; task.tenantId=7L; task.projectId=3L; task.requestedModelId=9L;
        task.businessId=41L; task.businessType="SCRIPT_AI_OPERATION";
        when(executions.requireTask(51L)).thenReturn(task);
        when(jdbc.queryForList(contains("select operation_type"),eq(7L),eq(41L)))
            .thenReturn(List.of(Map.of("operation_type","CHARACTER_PROMPT_SIMILARITY","redacted_input_json","{}")));
        when(jdbc.queryForList(contains("select input_json from character_prompt_similarity_check"),eq(7L),eq(3L),eq(51L)))
            .thenReturn(List.of(Map.of("input_json","[{\"id\":11,\"prompt\":\"frozen face\"}]")));
        service.estimate(7L,List.of(new AiPointEstimateService.Operation("/api/tenants/7/ai-executions/51/retry",null)));
        verify(jdbc).queryForList(contains("select input_json from character_prompt_similarity_check"),eq(7L),eq(3L),eq(51L));
        verify(jdbc,times(1)).queryForList(contains("select c.id,c.name,c.prompt"),eq(7L),eq(3L),eq(7L),eq(3L));
    }

    @Test
    void propSimplificationQuotesOneAnalysisCallWithoutCreatingATask() {
        var configs=(ProjectAiConfigService)org.springframework.test.util.ReflectionTestUtils.getField(service,"configs");
        when(configs.resolveModelId(7L,3L,"TEXT")).thenReturn(9L);
        var router=(AiModelRouter)org.springframework.test.util.ReflectionTestUtils.getField(service,"router");
        when(router.route(9L,"TEXT")).thenReturn(mock(AiModelRoute.class));
        var versions=(AiModelPointPriceVersionMapper)org.springframework.test.util.ReflectionTestUtils.getField(service,"pointVersions");
        var version=new AiModelPointPriceVersionEntity();version.id=20L;when(versions.selectEffective(eq(9L),any())).thenReturn(version);
        var components=(AiModelPointPriceComponentMapper)org.springframework.test.util.ReflectionTestUtils.getField(service,"components");
        var component=new AiModelPointPriceComponentEntity();component.metric="CALL";component.unitSize=BigDecimal.ONE;component.pointRate=BigDecimal.ONE;
        when(components.selectByVersion(20L)).thenReturn(List.of(component));
        var agent=mock(com.antshorttv.workflowagent.agent.WorkflowAgentRecord.class);when(agent.maxTokens()).thenReturn(32768);when(agent.systemPrompt()).thenReturn("保留关键版本");
        var agents=(WorkflowAgentRunner)org.springframework.test.util.ReflectionTestUtils.getField(service,"agents");
        when(agents.freezeFormal("short-drama-prop-simplification",7L)).thenReturn(new com.antshorttv.workflowagent.run.WorkflowAgentExecutionPlan(agent,List.of()));
        var result=service.estimate(7L,List.of(new AiPointEstimateService.Operation("/api/projects/3/prop-simplification",null)));
        assertThat(result.lines()).hasSize(1);assertThat(result.lines().get(0).label()).isEqualTo("道具精简方案分析");
        verify(agents).freezeFormal("short-drama-prop-simplification",7L);verifyNoInteractions(executions);
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
        verify(calculator).calculate(eq(7L),captured.capture(),any(AiPointEstimateCalculator.Context.class));
        assertThat(captured.getValue().modelId()).isEqualTo(9L);
        assertThat(captured.getValue().frozenPointPriceVersionId()).isEqualTo(30L);
        assertThat(captured.getValue().frozenDiscountRate()).isEqualByComparingTo("0.5");
        assertThat(captured.getValue().workbench()).isTrue();
        assertThat(captured.getValue().projectId()).isEqualTo(3L);
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

    @Test
    void twentyEpisodesReuseConfigurationAndOneStoryboardQueryWithoutDroppingAnyCharges() {
        var configs=(ProjectAiConfigService)org.springframework.test.util.ReflectionTestUtils.getField(service,"configs");
        var router=(AiModelRouter)org.springframework.test.util.ReflectionTestUtils.getField(service,"router");
        var versions=(AiModelPointPriceVersionMapper)org.springframework.test.util.ReflectionTestUtils.getField(service,"pointVersions");
        var components=(AiModelPointPriceComponentMapper)org.springframework.test.util.ReflectionTestUtils.getField(service,"components");
        var runner=(WorkflowAgentRunner)org.springframework.test.util.ReflectionTestUtils.getField(service,"agents");
        when(configs.resolveModelId(7L,3L,"TEXT")).thenReturn(9L);
        when(router.route(9L,"TEXT")).thenReturn(mock(com.antshorttv.ai.AiModelRoute.class));
        var version=new AiModelPointPriceVersionEntity();version.id=20L;
        when(versions.selectEffective(eq(9L),any())).thenReturn(version);
        var component=new AiModelPointPriceComponentEntity();component.metric="CALL";component.pointRate=BigDecimal.ONE;component.unitSize=BigDecimal.ONE;
        when(components.selectByVersion(20L)).thenReturn(List.of(component));
        var agent=mock(com.antshorttv.workflowagent.agent.WorkflowAgentRecord.class);
        when(agent.maxTokens()).thenReturn(1024);when(agent.systemPrompt()).thenReturn("system");
        when(runner.freezeFormal(anyString(),eq(7L))).thenReturn(new com.antshorttv.workflowagent.run.WorkflowAgentExecutionPlan(agent,List.of()));
        when(jdbc.queryForList(contains("select content from script where"),eq(7L),eq(3L))).thenReturn(List.of(Map.of("content","完整剧本")));
        var episodes=java.util.stream.LongStream.rangeClosed(1,20).mapToObj(id->Map.<String,Object>of("id",id,"content","第"+id+"集剧情")).toList();
        when(jdbc.queryForList(contains("select id, content from script_episode"),eq(7L),eq(3L))).thenReturn(episodes);
        when(jdbc.queryForList(contains("select distinct episode_id"),eq(Long.class),eq(7L),eq(3L))).thenReturn(List.of(1L));
        var result=service.estimate(7L,List.of(new AiPointEstimateService.Operation("/api/projects/3/asset-reextraction",json.createObjectNode())));
        assertThat(result.lines()).hasSize(40);
        assertThat(result.estimatedPoints()).isEqualByComparingTo("101");
        verify(router,times(1)).route(9L,"TEXT");
        verify(versions,times(1)).selectEffective(eq(9L),any());
        verify(components,times(1)).selectByVersion(20L);
        verify(runner,times(1)).freezeFormal("short-drama-asset-recognition",7L);
        verify(runner,times(1)).freezeFormal("short-drama-storyboard",7L);
        verify(jdbc,times(1)).queryForList(contains("select distinct episode_id"),eq(Long.class),eq(7L),eq(3L));
        verify(jdbc,times(1)).queryForList(contains("select content from script where"),eq(7L),eq(3L));
    }
}
