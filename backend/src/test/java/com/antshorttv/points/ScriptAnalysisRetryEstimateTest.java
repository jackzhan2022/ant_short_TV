package com.antshorttv.points;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.antshorttv.accounting.*;
import com.antshorttv.ai.*;
import com.antshorttv.aiimage.*;
import com.antshorttv.execution.*;
import com.antshorttv.productiontask.ProductionTaskService;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.review.ReviewWorkbenchService;
import com.antshorttv.security.*;
import com.antshorttv.video.*;
import com.antshorttv.workflowagent.agent.WorkflowAgentRecord;
import com.antshorttv.workflowagent.run.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

class ScriptAnalysisRetryEstimateTest {
    JdbcTemplate jdbc;
    AiPointEstimateService service;
    final AiExecutionService executions = mock(AiExecutionService.class);
    final AiPointReservationMapper reservations = mock(AiPointReservationMapper.class);
    final AiPointEstimateCalculator calculator = mock(AiPointEstimateCalculator.class);
    final ProjectAiConfigService configs = mock(ProjectAiConfigService.class);
    final AiModelPointPriceVersionMapper versions = mock(AiModelPointPriceVersionMapper.class);

    @BeforeEach
    void setUp() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:retry_estimate_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("create table team_point_account(tenant_id bigint, balance decimal, trial_balance decimal)");
        jdbc.execute("create table script(id bigint, tenant_id bigint, project_id bigint, content varchar, deleted_at timestamp)");
        jdbc.execute("create table script_version(id bigint, content varchar)");
        jdbc.execute("create table script_analysis_task(id bigint, tenant_id bigint, project_id bigint, script_id bigint, script_version_id bigint, execution_id bigint, workflow_code varchar, created_at timestamp)");
        jdbc.execute("create table script_analysis_config_snapshot(task_id bigint, snapshot_json varchar)");
        jdbc.execute("create table script_analysis_stage(id bigint, task_id bigint, stage_code varchar, stage_order int, status varchar)");
        jdbc.execute("create table script_episode(id bigint, tenant_id bigint, project_id bigint, script_id bigint, episode_no int, stable_key varchar, content_fingerprint varchar, content varchar, status varchar, retired_at timestamp)");
        jdbc.execute("create table script_analysis_fanout_snapshot(id bigint, tenant_id bigint, project_id bigint, script_id bigint, task_id bigint, stage_id bigint, agent_code varchar, agent_revision bigint, model_id bigint, episode_set_hash varchar, status varchar)");
        jdbc.execute("create table script_analysis_fanout_unit(snapshot_id bigint, episode_id bigint, status varchar)");
        jdbc.execute("create table storyboard(tenant_id bigint, project_id bigint, script_id bigint, episode_id bigint, episode_no int, deleted_at timestamp)");
        for (String table : List.of("character_asset", "scene_asset", "prop_asset")) {
            jdbc.execute("create table " + table + "(id bigint, tenant_id bigint, project_id bigint, name varchar, content_json varchar, deleted_at timestamp)");
        }
        jdbc.update("insert into team_point_account values(7,10000,0)");
        jdbc.update("insert into script values(62,7,3,'完整剧本',null)");
        jdbc.update("insert into script_version values(63,'完整剧本')");
        jdbc.update("insert into script_analysis_task values(69,7,3,62,63,51,'SCRIPT_INITIAL_ANALYSIS',now())");
        jdbc.update("insert into script_analysis_config_snapshot values(69,'{\"modelId\":9}')");
        String[] codes = {"GLOBAL_UNDERSTANDING", "EPISODE_SPLITTING", "EPISODE_SUMMARY", "CHARACTER_SCENE_RECOGNITION"};
        for (int i = 0; i < codes.length; i++) {
            jdbc.update("insert into script_analysis_stage values(?,69,?,?,?)", 273 + i, codes[i], i + 1, i == 3 ? "FAILED" : "SUCCEEDED");
        }
        StringBuilder hashInput = new StringBuilder();
        for (int i = 1; i <= 70; i++) {
            jdbc.update("insert into script_episode values(?,7,3,62,?,?,?,?,'ACTIVE',null)", i, i, "ep" + i, "fp" + i, "第" + i + "集剧情");
            jdbc.update("insert into script_analysis_fanout_unit values(84,?,?)", i, i == 68 ? "FAILED" : "SUCCEEDED");
            hashInput.append('|').append(i).append(":ep").append(i).append(":fp").append(i);
        }
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(hashInput.toString().getBytes(StandardCharsets.UTF_8)));
        jdbc.update("insert into script_analysis_fanout_snapshot values(84,7,3,62,69,276,'short-drama-asset-recognition',2,9,?,'PARTIAL_FAILED')", hash);

        var tenants = mock(TenantContextResolver.class);
        when(tenants.requireActiveMember(7L)).thenReturn(new TenantContext(1L,7L,2L,"OWNER"));
        when(configs.resolveModelId(7L,3L,"TEXT")).thenReturn(99L);
        var router = mock(AiModelRouter.class);
        when(router.route(anyLong(),eq("TEXT"))).thenReturn(mock(AiModelRoute.class));
        var version = new AiModelPointPriceVersionEntity(); version.id = 20L;
        when(versions.selectEffective(anyLong(),any())).thenReturn(version);
        var components = mock(AiModelPointPriceComponentMapper.class);
        var component = new AiModelPointPriceComponentEntity();
        component.metric = "CALL"; component.pointRate = BigDecimal.ONE; component.unitSize = BigDecimal.ONE;
        when(components.selectByVersion(anyLong())).thenReturn(List.of(component));
        var agents = mock(WorkflowAgentRunner.class);
        var agent = mock(WorkflowAgentRecord.class);
        when(agent.revision()).thenReturn(2L);
        when(agent.maxTokens()).thenReturn(1024);
        when(agent.systemPrompt()).thenReturn("system");
        when(agents.freezeFormal(anyString(),eq(7L))).thenReturn(new WorkflowAgentExecutionPlan(agent,List.of()));
        var task = new AiExecutionTaskEntity();
        task.id = 51L; task.tenantId = 7L; task.projectId = 3L; task.businessId = 69L;
        task.businessType = "SCRIPT_ANALYSIS_TASK"; task.requestedModelId = 9L; task.status = "FAILED";
        when(executions.requireTask(51L)).thenReturn(task);
        var reservation = new AiPointReservationEntity();
        reservation.pointPriceVersionId = 30L; reservation.discountRate = new BigDecimal("0.5"); reservation.trialEligible = true;
        when(reservations.selectByExecutionId(51L)).thenReturn(reservation);
        when(calculator.calculate(eq(7L),any(),any(AiPointEstimateCalculator.Context.class))).thenAnswer(call -> {
            AiPointEstimatePlan plan = call.getArgument(1);
            return new AiPointEstimateCalculator.Line(plan.label(),plan.modelId(),20L,plan.usage().get(AiUsageMetric.CALL),true,true);
        });
        var policy = mock(WorkbenchPointPolicy.class);
        when(policy.estimateRemaining(7L,3L)).thenReturn(new BigDecimal("1000"));
        service = new AiPointEstimateService(jdbc,tenants,mock(ProjectPermissionGuard.class),configs,router,
            mock(AiImageTaskService.class),mock(AiVideoTaskService.class),mock(AssetImageBatchService.class),
            mock(ReviewWorkbenchService.class),mock(VideoDecompositionService.class),mock(ProductionTaskService.class),
            mock(AiExecutionAccessService.class),executions,reservations,versions,components,agents,calculator,new ObjectMapper(),policy);
    }

    @Test
    void assetRetryExcludesAllSuccessfulEpisodesAndCompletedEarlierStages() {
        existingStoryboards();
        var result = quote("/api/projects/3/script-analysis/current/retry/CHARACTER_SCENE_RECOGNITION");
        assertThat(result.lines()).extracting(AiPointEstimateCalculator.Line::label)
            .containsExactly("分集资产识别", "全剧资产自动归一");
        assertThat(result.estimatedPoints()).isEqualByComparingTo("6");
        assertThat(result.lines()).allMatch(line -> line.modelId().equals(9L));
        var plans = org.mockito.ArgumentCaptor.forClass(AiPointEstimatePlan.class);
        verify(calculator,times(2)).calculate(eq(7L),plans.capture(),any(AiPointEstimateCalculator.Context.class));
        assertThat(plans.getAllValues()).allMatch(p -> p.frozenPointPriceVersionId().equals(30L)
            && p.frozenDiscountRate().compareTo(new BigDecimal("0.5")) == 0);
        verify(executions,never()).retry(anyLong());
        assertThat(jdbc.queryForObject("select count(*) from script_analysis_fanout_unit where status='SUCCEEDED'",Integer.class)).isEqualTo(69);
    }

    @Test
    void retryStillIncludesDownstreamStoryboardsForAllEpisodesWithoutResults() {
        var result = quote("/api/projects/3/script-analysis/current/retry/CHARACTER_SCENE_RECOGNITION");
        assertThat(count(result,"分集资产识别")).isEqualTo(1);
        assertThat(count(result,"生成分镜（含自动分镜）")).isEqualTo(70);
        assertThat(count(result,"分集概要")).isZero();
        assertThat(result.estimatedPoints()).isEqualByComparingTo("146");
        assertThat(result.lines().stream().filter(l -> l.label().startsWith("生成分镜")))
            .allMatch(line -> line.modelId().equals(99L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"agent_revision", "model_id", "episode_set_hash", "stage_id", "task_id"})
    void incompatibleSnapshotsCannotHideWorkThatWillRunAgain(String mismatch) {
        existingStoryboards();
        if (mismatch.equals("episode_set_hash")) jdbc.update("update script_analysis_fanout_snapshot set episode_set_hash='old'");
        else jdbc.update("update script_analysis_fanout_snapshot set " + mismatch + "=999");
        var result = quote("/api/projects/3/script-analysis/current/retry/CHARACTER_SCENE_RECOGNITION");
        assertThat(count(result,"分集资产识别")).isEqualTo(70);
        assertThat(count(result,"分集概要")).isZero();
    }

    @Test
    void executionRetryUsesTheSameRemainingWorkAsTheStageButton() {
        existingStoryboards();
        var result = quote("/api/tenants/7/ai-executions/51/retry");
        assertThat(result.lines()).extracting(AiPointEstimateCalculator.Line::label)
            .containsExactly("分集资产识别", "全剧资产自动归一");
    }

    @Test
    void summaryRetryIncludesSummaryWorkAndReusesTheSuccessfulAssetUnits() {
        existingStoryboards();
        var result = quote("/api/projects/3/script-analysis/current/retry/EPISODE_SUMMARY");
        assertThat(count(result,"分集概要")).isEqualTo(70);
        assertThat(count(result,"分集资产识别")).isEqualTo(1);
        assertThat(count(result,"剧本全局理解")).isZero();
        assertThat(count(result,"剧集智能拆分")).isZero();
    }

    @Test
    void normalizationOnlyRetryDoesNotRequoteSuccessfulAssetCalls() {
        existingStoryboards();
        jdbc.update("update script_analysis_fanout_unit set status='SUCCEEDED'");
        var result = quote("/api/projects/3/script-analysis/current/retry/CHARACTER_SCENE_RECOGNITION");
        assertThat(result.lines()).extracting(AiPointEstimateCalculator.Line::label).containsExactly("全剧资产自动归一");
    }

    @Test
    void fullReanalysisRetainsItsFullWorkflowEstimate() {
        existingStoryboards();
        var result = quote("/api/projects/3/script-analysis/current/reanalyze");
        assertThat(count(result,"剧本全局理解")).isEqualTo(1);
        assertThat(count(result,"剧集智能拆分")).isEqualTo(1);
        assertThat(count(result,"分集概要")).isEqualTo(70);
        assertThat(count(result,"分集资产识别")).isEqualTo(70);
    }

    void existingStoryboards() {
        jdbc.update("insert into storyboard select tenant_id,project_id,script_id,id,episode_no,null from script_episode");
    }

    AiPointEstimateService.Estimate quote(String path) {
        return service.estimate(7L,List.of(new AiPointEstimateService.Operation(path,null)));
    }

    long count(AiPointEstimateService.Estimate result,String label) {
        return result.lines().stream().filter(line -> line.label().equals(label)).count();
    }
}
