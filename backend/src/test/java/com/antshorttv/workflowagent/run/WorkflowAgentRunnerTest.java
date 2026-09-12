package com.antshorttv.workflowagent.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.ai.AiCapability;
import com.antshorttv.ai.AiChatMessage;
import com.antshorttv.ai.AiChatRole;
import com.antshorttv.ai.AiInvocationResult;
import com.antshorttv.ai.AiInvocationService;
import com.antshorttv.ai.AiTextResponse;
import com.antshorttv.ai.AiToolCall;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.workflowagent.WorkflowAgentProperties;
import com.antshorttv.workflowagent.agent.WorkflowAgentRecord;
import com.antshorttv.workflowagent.agent.WorkflowAgentCommand;
import com.antshorttv.workflowagent.agent.WorkflowAgentService;
import com.antshorttv.workflowagent.skill.WorkflowSkillService;
import com.antshorttv.workflowagent.skill.WorkflowSkillView;
import com.antshorttv.workflowagent.tool.ReviewToolScope;
import com.antshorttv.workflowagent.tool.ToolFailurePolicy;
import com.antshorttv.workflowagent.tool.ToolRiskLevel;
import com.antshorttv.workflowagent.tool.WorkflowToolDefinition;
import com.antshorttv.workflowagent.tool.WorkflowToolExecutor;
import com.antshorttv.workflowagent.tool.WorkflowToolRegistry;
import com.antshorttv.workflowagent.tool.WorkflowToolSchemaValidator;
import com.antshorttv.workflowagent.tool.WorkflowToolValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WorkflowAgentRunnerTest {
    private final WorkflowAgentService agents = mock(WorkflowAgentService.class);
    private final WorkflowSkillService skills = mock(WorkflowSkillService.class);
    private final AiInvocationService invocation = mock(AiInvocationService.class);
    private final WorkflowAgentRunRepository runs = mock(WorkflowAgentRunRepository.class);
    private final WorkflowAgentScopeGuard scopeGuard = mock(WorkflowAgentScopeGuard.class);
    private final ObjectMapper json = new ObjectMapper();
    private final AtomicInteger toolExecutions = new AtomicInteger();
    private WorkflowAgentRunner runner;

    @BeforeEach
    void setUp() throws Exception {
        WorkflowToolDefinition tool = new WorkflowToolDefinition(
            "read_episode_script", "读取剧集", "读取当前剧集",
            json.readTree("{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}"),
            json.readTree("{\"type\":\"object\"}"), ToolRiskLevel.READ_ONLY,
            ToolFailurePolicy.TERMINAL,
            new WorkflowToolExecutor() {
                @Override
                public com.fasterxml.jackson.databind.JsonNode execute(
                    com.antshorttv.workflowagent.tool.ToolExecutionContext context,
                    com.fasterxml.jackson.databind.JsonNode arguments
                ) {
                    toolExecutions.incrementAndGet();
                    try {
                        return json.readTree("{\"content\":\"第一集\"}");
                    } catch (java.io.IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                }
            }
        );
        WorkflowAgentProperties properties = new WorkflowAgentProperties();
        properties.setRunTimeoutSeconds(30);
        runner = new WorkflowAgentRunner(
            agents, skills, new WorkflowToolRegistry(List.of(tool)), new WorkflowToolSchemaValidator(),
            invocation, runs, scopeGuard, properties, json
        );
        when(runs.start(any())).thenReturn(101L);
        when(skills.detail("rewrite-guide")).thenReturn(new WorkflowSkillView(
            "rewrite-guide", "改写指南", "规范", "---\nname: rewrite-guide\ndescription: 规范\n---\n先保留情节。",
            "hash-1", List.of("screenplay-agent")
        ));
        when(agents.loadForRun("screenplay-agent")).thenReturn(agent(3, List.of("rewrite-guide"),
            List.of("read_episode_script")));
    }

    @Test
    void composesSkillsAndCompletesAValidatedMultiStepToolLoop() {
        when(invocation.invokeText(any()))
            .thenReturn(result(null, List.of(new AiToolCall("call-1", "read_episode_script", "{}")), 501L))
            .thenReturn(result("改写完成", List.of(), 502L));

        WorkflowAgentRunResult result = runner.runFormal(new WorkflowAgentRunInput(
            "screenplay-agent", "改写当前剧集", 7L, 25L, 91L, null, 9L
        ));

        assertThat(result.runId()).isEqualTo(101L);
        assertThat(result.output()).isEqualTo("改写完成");
        assertThat(toolExecutions).hasValue(1);
        verify(runs).complete(101L, "改写完成");
        verify(runs).recordModelStep(101L, 1, 501L, List.of(new AiToolCall(
            "call-1", "read_episode_script", "{}")), null);
        verify(runs).recordToolStep(101L, 2, "read_episode_script", "{}", "{\"content\":\"第一集\"}");

        var firstRequest = org.mockito.ArgumentCaptor.forClass(com.antshorttv.ai.AiInvocationRequest.class);
        verify(invocation, org.mockito.Mockito.atLeastOnce()).invokeText(firstRequest.capture());
        assertThat(firstRequest.getAllValues().get(0).textRequest().messages())
            .extracting(AiChatMessage::content)
            .contains("改写当前剧集");
        assertThat(firstRequest.getAllValues().get(0).textRequest().messages().get(0).content())
            .contains("screenplay-agent", "rewrite-guide", "先保留情节");
    }

    @Test
    void placesStableContextBeforeDynamicInstructionAndForwardsCacheIdentity() {
        when(invocation.invokeText(any())).thenReturn(result("完成", List.of(), 503L));

        runner.runFormal(new WorkflowAgentRunInput(
            "screenplay-agent", "仅检查时间线", 7L, 25L, 91L, null, null, null, 9L,
            null, null, null, null, null,
            "公共规则\n结构索引\n冻结剧本", "review-cache-v1", Map.of("retention", "short")
        ));

        var requests = org.mockito.ArgumentCaptor.forClass(com.antshorttv.ai.AiInvocationRequest.class);
        verify(invocation).invokeText(requests.capture());
        assertThat(requests.getValue().textRequest().messages())
            .extracting(AiChatMessage::content)
            .containsExactly(
                "公共规则\n结构索引\n冻结剧本",
                requests.getValue().textRequest().messages().get(1).content(),
                "仅检查时间线"
            );
        assertThat(requests.getValue().textRequest().messages().get(1).content())
            .contains("screenplay-agent", "rewrite-guide");
        assertThat(requests.getValue().textRequest().messages())
            .extracting(AiChatMessage::role)
            .containsExactly(AiChatRole.SYSTEM, AiChatRole.SYSTEM, AiChatRole.USER);
        assertThat(requests.getValue().textRequest().promptCacheKey()).isEqualTo("review-cache-v1");
        assertThat(requests.getValue().textRequest().promptCacheOptions())
            .containsEntry("retention", "short");
    }

    @Test
    void episodeSummaryHostPreloadsTrustedEpisodeBeforeTheFirstModelRequest() {
        List<String> executed = new ArrayList<>();
        WorkflowToolDefinition read = storyboardRead("read_current_episode", executed,
            json.createObjectNode().put("content", "CURRENT_EPISODE").put("fingerprint", "fp"));
        WorkflowToolDefinition save = tool("save_episode_summary");
        runner = runnerWith(List.of(read, save), 30);
        when(agents.loadForRun("short-drama-episode-summary")).thenReturn(new WorkflowAgentRecord(
            6L, "short-drama-episode-summary", "概要", "", "概要阶段规则", 8L,
            new BigDecimal("0.2"), 4096, 4, "ENABLED", 0L, 9L, 9L,
            LocalDateTime.now(), LocalDateTime.now(), List.of(),
            List.of("read_current_episode", "save_episode_summary")));
        when(invocation.invokeText(any())).thenAnswer(ignored -> {
            assertThat(executed).containsExactly("read_current_episode");
            return result(null, List.of(new AiToolCall("save", "save_episode_summary", "{}")), 504L);
        });

        runner.runFormal(new WorkflowAgentRunInput(
            "short-drama-episode-summary", "直接保存", 7L, 25L, 91L, 77L,
            null, null, 9L, null, null, null, 8L, null,
            "公共剧集上下文\nCURRENT_EPISODE", "episode-cache", Map.of()));

        var requests = org.mockito.ArgumentCaptor.forClass(com.antshorttv.ai.AiInvocationRequest.class);
        verify(invocation).invokeText(requests.capture());
        assertThat(requests.getValue().textRequest().tools())
            .extracting(com.antshorttv.ai.AiToolDefinition::code)
            .containsExactly("save_episode_summary");
        assertThat(requests.getValue().textRequest().messages())
            .extracting(AiChatMessage::content)
            .satisfiesExactly(
                content -> assertThat(content).isEqualTo("公共剧集上下文\nCURRENT_EPISODE"),
                content -> assertThat(content).contains("概要阶段规则"),
                content -> assertThat(content).isEqualTo("直接保存"),
                content -> assertThat(content).contains("已完成 read_current_episode", "不要再次读取")
                    .doesNotContain("CURRENT_EPISODE"));
        verify(runs).recordToolStep(101L, 1, "read_current_episode", "{}",
            "{\"content\":\"CURRENT_EPISODE\",\"fingerprint\":\"fp\"}");
    }

    @Test
    void episodeSummaryDoesNotExposeOrAllowCrossStageTools() {
        WorkflowToolDefinition read = storyboardRead("read_current_episode", new ArrayList<>(),
            json.createObjectNode().put("content", "CURRENT_EPISODE").put("fingerprint", "fp"));
        runner = runnerWith(List.of(read, tool("save_episode_summary"), tool("save_episode_assets")), 30);
        when(agents.loadForRun("short-drama-episode-summary")).thenReturn(new WorkflowAgentRecord(
            6L, "short-drama-episode-summary", "概要", "", "概要阶段规则", 8L,
            new BigDecimal("0.2"), 4096, 4, "ENABLED", 0L, 9L, 9L,
            LocalDateTime.now(), LocalDateTime.now(), List.of(),
            List.of("read_current_episode", "save_episode_summary")));
        when(invocation.invokeText(any())).thenReturn(result(null,
            List.of(new AiToolCall("cross-stage", "save_episode_assets", "{}")), 505L));

        assertThatThrownBy(() -> runner.runFormal(new WorkflowAgentRunInput(
            "short-drama-episode-summary", "直接保存", 7L, 25L, 91L, 77L,
            null, null, 9L))).isInstanceOf(BusinessException.class).hasMessageContaining("未授权");

        var request = org.mockito.ArgumentCaptor.forClass(com.antshorttv.ai.AiInvocationRequest.class);
        verify(invocation).invokeText(request.capture());
        assertThat(request.getValue().textRequest().tools())
            .extracting(com.antshorttv.ai.AiToolDefinition::code)
            .containsExactly("save_episode_summary");
    }

    @ParameterizedTest
    @CsvSource({
        "short-drama-episode-summary,save_episode_summary,true",
        "short-drama-episode-summary,save_episode_summary,false",
        "short-drama-asset-recognition,save_episode_assets,true",
        "short-drama-asset-recognition,save_episode_assets,false"
    })
    void preloadedEpisodeStagesExposeOnlyTheirSaveAndAnnounceReadCompletion(
        String agentCode, String saveCode, boolean stableContext
    ) {
        List<String> executed = new ArrayList<>();
        var read = storyboardRead("read_current_episode", executed,
            json.createObjectNode().put("content", "CURRENT_EPISODE").put("assetCatalog", "CURRENT_ASSETS"));
        WorkflowToolDefinition save = new WorkflowToolDefinition(
            saveCode, "保存当前阶段", "保存当前阶段",
            json.createObjectNode().put("type", "object"), json.createObjectNode().put("type", "object"),
            ToolRiskLevel.WRITE, ToolFailurePolicy.TERMINAL, tool(saveCode).executor());
        runner = runnerWith(List.of(read, save), 30);
        when(agents.loadForRun(agentCode)).thenReturn(new WorkflowAgentRecord(
            6L, agentCode, "当前阶段", "", "先读取当前剧集，再保存", 8L,
            new BigDecimal("0.2"), 4096, 4, "ENABLED", 0L, 9L, 9L,
            LocalDateTime.now(), LocalDateTime.now(), List.of(),
            List.of("read_current_episode", saveCode)));
        when(invocation.invokeText(any())).thenAnswer(call -> {
            assertThat(executed).containsExactly("read_current_episode");
            com.antshorttv.ai.AiInvocationRequest request = call.getArgument(0);
            assertThat(request.textRequest().tools()).extracting(com.antshorttv.ai.AiToolDefinition::code)
                .containsExactly(saveCode);
            var messages = request.textRequest().messages();
            assertThat(messages.get(messages.size() - 1).content())
                .contains("已完成 read_current_episode", "不要再次读取", saveCode);
            if (stableContext) {
                assertThat(messages.get(0).content()).isEqualTo("冻结上下文 CURRENT_EPISODE");
                assertThat(request.textRequest().promptCacheKey()).isEqualTo("stable-cache-key");
            }
            if (!stableContext || agentCode.equals("short-drama-asset-recognition")) {
                assertThat(messages.get(messages.size() - 1).content()).contains("CURRENT_EPISODE", "CURRENT_ASSETS");
            }
            String payload = saveCode.equals("save_episode_assets")
                ? "{\"schemaVersion\":1,\"characters\":[],\"characterLooks\":[],\"scenes\":[],\"props\":[],\"propVariants\":[]}"
                : "{}";
            return result(null, List.of(new AiToolCall("save", saveCode, payload)), 506L);
        });
        runner.runFormal(new WorkflowAgentRunInput(
            agentCode, "分析当前剧集", 7L, 25L, 91L, 77L, null, null, 9L,
            null, null, null, 8L, null,
            stableContext ? "冻结上下文 CURRENT_EPISODE" : null, "stable-cache-key", Map.of()));
        assertThat(executed).containsExactly("read_current_episode");
        verify(invocation).invokeText(any());
        verify(runs).complete(org.mockito.ArgumentMatchers.eq(101L), any());
    }

    @Test
    void assetRecognitionRejectsTruncatedOutputBeforeExecutingAnySave() throws Exception {
        AtomicInteger saves = new AtomicInteger();
        WorkflowToolDefinition save = new WorkflowToolDefinition(
            "save_episode_assets", "保存资产", "保存", json.readTree("{\"type\":\"object\"}"),
            json.readTree("{\"type\":\"object\"}"), ToolRiskLevel.WRITE,
            ToolFailurePolicy.TERMINAL, new WorkflowToolExecutor() {
                @Override
                public JsonNode execute(
                    com.antshorttv.workflowagent.tool.ToolExecutionContext context,
                    JsonNode arguments
                ) {
                    saves.incrementAndGet();
                    return json.createObjectNode().put("saved", true);
                }
            });
        runner = runnerWith(List.of(tool("read_current_episode"), save), 30);
        when(agents.loadForRun("short-drama-asset-recognition")).thenReturn(new WorkflowAgentRecord(
            6L, "short-drama-asset-recognition", "资产识别", "", "执行", 8L,
            new BigDecimal("0.2"), 16384, 6, "ENABLED", 0L, 9L, 9L,
            LocalDateTime.now(), LocalDateTime.now(), List.of(), List.of(
                "read_current_episode", "save_episode_assets")));
        when(invocation.invokeText(any())).thenReturn(truncatedWithCalls(507L, List.of(
            new AiToolCall("save", "save_episode_assets", "{}"))));

        assertThatThrownBy(() -> runner.runFormal(new WorkflowAgentRunInput(
            "short-drama-asset-recognition", "执行", 7L, 25L, 91L, 77L,
            null, null, 9L)))
            .isInstanceOf(WorkflowAgentTruncatedOutputException.class);

        assertThat(saves).hasValue(0);
    }

    @Test
    void rejectsAnUnassociatedToolBeforeExecution() {
        when(invocation.invokeText(any())).thenReturn(result(null,
            List.of(new AiToolCall("call-x", "save_episode_script", "{\"content\":\"x\"}")), 503L));

        assertThatThrownBy(() -> runner.runFormal(new WorkflowAgentRunInput(
            "screenplay-agent", "保存", 7L, 25L, 91L, null, 9L
        ))).isInstanceOf(BusinessException.class).hasMessageContaining("未授权");

        assertThat(toolExecutions).hasValue(0);
        verify(runs).fail(org.mockito.ArgumentMatchers.eq(101L), any(), any());
    }

    @Test
    void rejectsModelSuppliedScopeAndHonorsStepLimit() {
        when(invocation.invokeText(any())).thenReturn(result(null,
            List.of(new AiToolCall("call-x", "read_episode_script", "{\"projectId\":999}")), 504L));

        assertThatThrownBy(() -> runner.runFormal(new WorkflowAgentRunInput(
            "screenplay-agent", "读取", 7L, 25L, 91L, null, 9L
        ))).isInstanceOf(BusinessException.class).hasMessageContaining("作用域");
        assertThat(toolExecutions).hasValue(0);

        toolExecutions.set(0);
        when(agents.loadForRun("screenplay-agent")).thenReturn(agent(1, List.of(),
            List.of("read_episode_script")));
        when(invocation.invokeText(any())).thenReturn(result(null,
            List.of(new AiToolCall("call-y", "read_episode_script", "{}")), 505L));
        assertThatThrownBy(() -> runner.runFormal(new WorkflowAgentRunInput(
            "screenplay-agent", "循环", 7L, 25L, 91L, null, 9L
        ))).isInstanceOf(BusinessException.class).hasMessageContaining("步数");
    }

    @Test
    void countsEveryToolCallAgainstTheSharedStepBudget() {
        when(agents.loadForRun("screenplay-agent")).thenReturn(agent(2, List.of(),
            List.of("read_episode_script")));
        when(invocation.invokeText(any())).thenReturn(result(null, List.of(
            new AiToolCall("call-1", "read_episode_script", "{}"),
            new AiToolCall("call-2", "read_episode_script", "{}")
        ), 506L));

        assertThatThrownBy(() -> runner.runFormal(new WorkflowAgentRunInput(
            "screenplay-agent", "读取两次", 7L, 25L, 91L, null, 9L
        ))).isInstanceOf(BusinessException.class).hasMessageContaining("步数");

        assertThat(toolExecutions).hasValue(1);
    }

    @Test
    void disabledAgentFailsBeforeStartingAnAuditOrProviderCall() {
        when(agents.loadForRun("screenplay-agent")).thenThrow(new BusinessException(
            com.antshorttv.common.ErrorCode.WORKFLOW_AGENT_DISABLED, "Agent 未启用。"));

        assertThatThrownBy(() -> runner.runFormal(new WorkflowAgentRunInput(
            "screenplay-agent", "执行", 7L, 25L, 91L, null, 9L
        ))).isInstanceOf(BusinessException.class);
        verify(runs, never()).start(any());
        verify(invocation, never()).invokeText(any());
    }

    @Test
    void rejectsUnauthorizedScopeBeforeAuditOrProviderContact() {
        doThrow(new BusinessException(com.antshorttv.common.ErrorCode.FORBIDDEN, "无权访问"))
            .when(scopeGuard).requireAuthorized(any(), any());

        assertThatThrownBy(() -> runner.runFormal(new WorkflowAgentRunInput(
            "screenplay-agent", "读取", 7L, 25L, 91L, null, 9L
        ))).isInstanceOf(BusinessException.class);

        verify(runs, never()).start(any());
        verify(invocation, never()).invokeText(any());
    }

    @Test
    void returnsNonTerminalToolFailureToModelAndAcceptsFinalResponse() throws Exception {
        WorkflowToolDefinition recoverable = new WorkflowToolDefinition(
            "recoverable_read", "可恢复读取", "用于验证失败策略",
            json.readTree("{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}"),
            json.readTree("{\"type\":\"object\"}"), ToolRiskLevel.READ_ONLY,
            ToolFailurePolicy.RETURN_TO_MODEL,
            new WorkflowToolExecutor() {
                @Override
                public com.fasterxml.jackson.databind.JsonNode execute(
                    com.antshorttv.workflowagent.tool.ToolExecutionContext context,
                    com.fasterxml.jackson.databind.JsonNode arguments
                ) {
                    throw new IllegalStateException("暂时不可用");
                }
            }
        );
        runner = runnerWith(List.of(recoverable), 30);
        when(agents.loadForRun("screenplay-agent")).thenReturn(agent(3, List.of(), List.of("recoverable_read")));
        when(invocation.invokeText(any()))
            .thenReturn(result(null, List.of(new AiToolCall("recover-1", "recoverable_read", "{}")), 601L))
            .thenReturn(result("已采用替代方案", List.of(), 602L));

        WorkflowAgentRunResult result = runner.runFormal(new WorkflowAgentRunInput(
            "screenplay-agent", "读取", 7L, 25L, 91L, null, 9L
        ));

        assertThat(result.output()).isEqualTo("已采用替代方案");
        verify(runs).recordFailedToolStep(org.mockito.ArgumentMatchers.eq(101L),
            org.mockito.ArgumentMatchers.eq(2), org.mockito.ArgumentMatchers.eq("recoverable_read"),
            org.mockito.ArgumentMatchers.eq("{}"), any(), any());
    }

    @Test
    void returnsRecoverableReviewReadOrderFailureToModelForCorrection() throws Exception {
        configureMarkdownReview("MARKDOWN_DEEP_CHILD");
        when(invocation.invokeText(any()))
            .thenReturn(result(null, List.of(new AiToolCall(
                "premature-content", "read_review_content", "{}")), 611L))
            .thenReturn(result(null, List.of(
                new AiToolCall("context", "read_review_context", "{}"),
                new AiToolCall("content", "read_review_content", "{}")), 612L))
            .thenReturn(result("## 审核发现", List.of(), 613L));

        WorkflowAgentRunResult result = runner.runFormal(reviewInput("MARKDOWN_DEEP_CHILD"));

        assertThat(result.output()).isEqualTo("## 审核发现");
        verify(runs).recordFailedToolStep(101L, 2, "read_review_content", "{}",
            ErrorCode.REQUIRED_TOOL_NOT_CALLED.name(),
            "必须先读取审核上下文，再读取可信正文：read_review_context -> read_review_content");
        verify(invocation, org.mockito.Mockito.times(3)).invokeText(any());
    }

    @Test
    void markdownDeepChildUsesDeepBudgetAndReadOnlyToolsWithoutThinking() throws Exception {
        configureMarkdownReview("MARKDOWN_DEEP_CHILD");
        when(invocation.invokeText(any()))
            .thenReturn(result(null, List.of(new AiToolCall("context", "read_review_context", "{}")), 617L))
            .thenReturn(result(null, List.of(new AiToolCall("content", "read_review_content", "{}")), 618L))
            .thenReturn(result("## 当前维度报告", List.of(), 619L));

        WorkflowAgentRunResult result = runner.runFormal(reviewInput("MARKDOWN_DEEP_CHILD"));

        assertThat(result.output()).isEqualTo("## 当前维度报告");
        var requests = org.mockito.ArgumentCaptor.forClass(com.antshorttv.ai.AiInvocationRequest.class);
        verify(invocation, org.mockito.Mockito.times(3)).invokeText(requests.capture());
        assertThat(requests.getAllValues()).allSatisfy(request -> {
            assertThat(request.textRequest().thinkingMode()).isEqualTo("disabled");
            assertThat(request.textRequest().tools()).extracting(com.antshorttv.ai.AiToolDefinition::code)
                .containsExactly("read_review_context", "read_review_content");
        });
        assertThat(requests.getAllValues().get(0).textRequest().timeoutSeconds()).isGreaterThan(300);
        var start = org.mockito.ArgumentCaptor.forClass(WorkflowAgentRunStart.class);
        verify(runs).start(start.capture());
        assertThat(start.getValue().maxSteps()).isEqualTo(96);
    }

    @ParameterizedTest
    @CsvSource({
        "MARKDOWN_QUICK,false", "MARKDOWN_QUICK,true",
        "MARKDOWN_DEEP_CHILD,false", "MARKDOWN_DEEP_CHILD,true",
        "MARKDOWN_DEEP_AGGREGATION,false", "MARKDOWN_DEEP_AGGREGATION,true"
    })
    void markdownReviewRejectsTruncatedOutputWithoutAnotherProviderRound(String phase, boolean empty)
        throws Exception {
        configureMarkdownReview(phase);
        when(invocation.invokeText(any())).thenReturn(empty ? truncatedEmpty(623L) : truncated(623L));

        assertThatThrownBy(() -> runner.runFormal(reviewInput(phase)))
            .isInstanceOf(WorkflowAgentTruncatedOutputException.class)
            .satisfies(error -> {
                WorkflowAgentTruncatedOutputException truncated = (WorkflowAgentTruncatedOutputException) error;
                assertThat(truncated.runId()).isEqualTo(101L);
                assertThat(truncated.partialContent()).isEqualTo(empty ? "" : "partial");
                assertThat(truncated.modelCalls()).extracting(WorkflowAgentModelCall::callLogId)
                    .containsExactly(623L);
            });

        verify(invocation, org.mockito.Mockito.times(1)).invokeText(any());
        verify(runs, never()).complete(org.mockito.ArgumentMatchers.eq(101L), any());
    }

    @Test
    void markdownReviewRejectsTruncationBeforeParsingPartialReadArguments() throws Exception {
        configureMarkdownReview("MARKDOWN_DEEP_CHILD");
        AiToolCall partial = new AiToolCall("partial", "read_review_content", "{\"offset\":");
        when(invocation.invokeText(any())).thenReturn(truncatedWithCalls(641L, List.of(partial)));

        assertThatThrownBy(() -> runner.runFormal(reviewInput("MARKDOWN_DEEP_CHILD")))
            .isInstanceOf(WorkflowAgentTruncatedOutputException.class);
        verify(runs, never()).recordFailedToolStep(
            org.mockito.ArgumentMatchers.eq(101L), org.mockito.ArgumentMatchers.anyInt(),
            any(), any(), any(), any());
        verify(invocation, org.mockito.Mockito.times(1)).invokeText(any());
    }

    @Test
    void markdownQuickCompletesAfterTrustedReadsWithoutTerminalSave() throws Exception {
        configureMarkdownReview("MARKDOWN_QUICK");
        when(invocation.invokeText(any()))
            .thenReturn(result(null, List.of(
                new AiToolCall("context", "read_review_context", "{}"),
                new AiToolCall("content", "read_review_content", "{}")), 651L))
            .thenReturn(result("# 审核报告\n\n原样保存。", List.of(), 652L));

        WorkflowAgentRunResult result = runner.runFormal(reviewInput("MARKDOWN_QUICK"));

        assertThat(result.output()).isEqualTo("# 审核报告\n\n原样保存。");
        assertThat(result.modelCalls()).extracting(WorkflowAgentModelCall::callLogId)
            .containsExactly(651L, 652L);
        verify(runs).complete(101L, result.output());
    }

    @Test
    void markdownAggregationCompletesWithoutTools() throws Exception {
        configureMarkdownReview("MARKDOWN_DEEP_AGGREGATION");
        when(invocation.invokeText(any())).thenReturn(result("# 合并报告", List.of(), 661L));

        WorkflowAgentRunResult result = runner.runFormal(reviewInput("MARKDOWN_DEEP_AGGREGATION"));

        assertThat(result.output()).isEqualTo("# 合并报告");
        var request = org.mockito.ArgumentCaptor.forClass(com.antshorttv.ai.AiInvocationRequest.class);
        verify(invocation).invokeText(request.capture());
        assertThat(request.getValue().textRequest().tools()).isEmpty();
    }

    @Test
    void markdownReviewRejectsRetiredSaveToolFromModel() throws Exception {
        configureMarkdownReview("MARKDOWN_QUICK");
        when(invocation.invokeText(any())).thenReturn(result(null,
            List.of(new AiToolCall("save", "save_review_result", "{}")), 671L));

        assertThatThrownBy(() -> runner.runFormal(reviewInput("MARKDOWN_QUICK")))
            .isInstanceOf(BusinessException.class).hasMessageContaining("未授权工具");
        verify(invocation, org.mockito.Mockito.times(1)).invokeText(any());
        verify(runs, never()).complete(org.mockito.ArgumentMatchers.eq(101L), any());
    }

    private void configureMarkdownReview(String phase) throws Exception {
        List<String> codes = "MARKDOWN_DEEP_AGGREGATION".equals(phase) ? List.of()
            : List.of("read_review_context", "read_review_content");
        List<WorkflowToolDefinition> definitions = codes.isEmpty() ? List.of() : List.of(
            reviewTool("read_review_context", executorReturning("{\"mode\":\"DEEP\"}")),
            reviewTool("read_review_content", executorReturning("{\"content\":\"可信正文\"}")));
        runner = runnerWith(definitions, 30);
        when(agents.loadForRun("script-review")).thenReturn(new WorkflowAgentRecord(
            6L, "script-review", "剧本审核", "", "执行审核", 8L,
            new BigDecimal("0.2"), 16384, 8, "ENABLED", 0L, 9L, 9L,
            LocalDateTime.now(), LocalDateTime.now(), List.of(), codes));
    }

    private WorkflowAgentRunInput reviewInput(String phase) {
        boolean quick = "MARKDOWN_QUICK".equals(phase);
        return new WorkflowAgentRunInput(
            "script-review", "审核当前冻结范围", 7L, 25L, null, null, 91L, null, 9L,
            700L, 701L, 1, 8L,
            new ReviewToolScope(25L, 77L, quick ? null : 88L,
                "MARKDOWN_DEEP_CHILD".equals(phase) ? 99L : null, 1, phase, List.of("台词合理性")));
    }

    @Test
    void enforcesTotalTimeoutBeforeProviderContact() {
        runner = runnerWith(List.of(tool("read_episode_script")), 0);

        assertThatThrownBy(() -> runner.runFormal(new WorkflowAgentRunInput(
            "screenplay-agent", "读取", 7L, 25L, 91L, null, 9L
        ))).isInstanceOf(BusinessException.class).hasMessageContaining("超时");
        verify(invocation, never()).invokeText(any());
    }

    @Test
    void validatesAndRunsTemporaryConfigurationWithoutLoadingSavedAgent() {
        when(invocation.invokeText(any())).thenReturn(result("测试完成", List.of(), 701L));
        WorkflowAgentCommand temporary = new WorkflowAgentCommand(
            "draft-agent", "草稿", "", "临时提示词", 8L, new BigDecimal("0.3"), 1024, 2,
            "ENABLED", List.of(), List.of("read_episode_script")
        );

        WorkflowAgentRunResult result = runner.runTest(temporary, new WorkflowAgentRunInput(
            "draft-agent", "测试", 7L, 25L, 91L, null, 9L
        ));

        assertThat(result.output()).isEqualTo("测试完成");
        verify(agents).validate(temporary, false);
        verify(agents, never()).loadForRun(any());
        var start = org.mockito.ArgumentCaptor.forClass(WorkflowAgentRunStart.class);
        verify(runs).start(start.capture());
        assertThat(start.getValue().runType()).isEqualTo("TEST");
        assertThat(start.getValue().agentId()).isNull();
    }

    @Test
    void globalUnderstandingRequiresOrderedReadAndTerminalSave() throws Exception {
        WorkflowToolDefinition read = new WorkflowToolDefinition(
            "read_current_script", "读取当前剧本", "读取当前剧本",
            json.readTree("{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}"),
            json.readTree("{\"type\":\"object\"}"), ToolRiskLevel.READ_ONLY,
            ToolFailurePolicy.TERMINAL, executorReturning("{\"content\":\"正文\"}"));
        WorkflowToolDefinition save = new WorkflowToolDefinition(
            "save_global_understanding", "保存全局理解", "保存全局理解",
            json.readTree("{\"type\":\"object\"}"), json.readTree("{\"type\":\"object\"}"),
            ToolRiskLevel.WRITE, ToolFailurePolicy.TERMINAL,
            executorReturning("{\"saved\":true,\"globalUnderstandingId\":88}"));
        runner = runnerWith(List.of(read, save), 30);
        when(skills.detail("short-drama-analysis-foundation")).thenReturn(new WorkflowSkillView(
            "short-drama-analysis-foundation", "基础", "基础约束", "当前稿规则", "foundation-v1", List.of()));
        when(skills.detail("short-drama-global-understanding-framework")).thenReturn(new WorkflowSkillView(
            "short-drama-global-understanding-framework", "框架", "字段框架", "全局字段规则", "framework-v1", List.of()));
        when(agents.loadForRun("short-drama-global-understanding")).thenReturn(new WorkflowAgentRecord(
            4L, "short-drama-global-understanding", "剧情全局理解", "", "执行", 8L,
            new BigDecimal("0.2"), 4096, 4, "ENABLED", 0L, 9L, 9L,
            LocalDateTime.now(), LocalDateTime.now(), List.of(
                "short-drama-analysis-foundation",
                "short-drama-global-understanding-framework"),
            List.of("read_current_script", "save_global_understanding")));
        when(invocation.invokeText(any()))
            .thenReturn(result(null, List.of(new AiToolCall("read", "read_current_script", "{}")), 801L))
            .thenReturn(result(null, List.of(new AiToolCall(
                "save", "save_global_understanding", "{\"schemaVersion\":1,\"content\":{}}")), 802L));

        WorkflowAgentRunResult completed = runner.runFormal(new WorkflowAgentRunInput(
            "short-drama-global-understanding", "执行", 7L, 25L, null, 77L, null, null, 9L,
            501L, 502L, 3, 18L));

        assertThat(completed.output()).contains("\"saved\":true");
        assertThat(completed.modelCalls()).hasSize(2);
        var start = org.mockito.ArgumentCaptor.forClass(WorkflowAgentRunStart.class);
        verify(runs).start(start.capture());
        assertThat(start.getValue().skillSnapshots())
            .extracting(WorkflowAgentSkillSnapshot::code)
            .containsExactly("short-drama-analysis-foundation", "short-drama-global-understanding-framework");
        assertThat(start.getValue().modelId()).isEqualTo(18L);
        verify(agents).requireToolCallingModel(18L);
        var requests = org.mockito.ArgumentCaptor.forClass(com.antshorttv.ai.AiInvocationRequest.class);
        verify(invocation, org.mockito.Mockito.times(2)).invokeText(requests.capture());
        assertThat(requests.getAllValues()).allSatisfy(request -> {
            assertThat(request.executionId()).isEqualTo(501L);
            assertThat(request.attemptId()).isEqualTo(502L);
            assertThat(request.executionVersion()).isEqualTo(3);
            assertThat(request.modelId()).isEqualTo(18L);
        });
        verify(runs).complete(101L, "{\"saved\":true,\"globalUnderstandingId\":88}");
    }

    @Test
    void globalUnderstandingCannotFinishWithoutRequiredSave() {
        runner = runnerWith(List.of(
            tool("read_current_script"), tool("save_global_understanding")), 30);
        when(agents.loadForRun("short-drama-global-understanding")).thenReturn(new WorkflowAgentRecord(
            4L, "short-drama-global-understanding", "剧情全局理解", "", "执行", 8L,
            new BigDecimal("0.2"), 4096, 4, "ENABLED", 0L, 9L, 9L,
            LocalDateTime.now(), LocalDateTime.now(), List.of(),
            List.of("read_current_script", "save_global_understanding")));
        when(invocation.invokeText(any())).thenReturn(result("已完成", List.of(), 803L));

        assertThatThrownBy(() -> runner.runFormal(new WorkflowAgentRunInput(
            "short-drama-global-understanding", "执行", 7L, 25L, null, 77L, null, null, 9L)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("必须调用");
    }

    @Test
    void globalUnderstandingRejectsSaveBeforeRead() {
        runner = runnerWith(List.of(
            tool("read_current_script"), tool("save_global_understanding")), 30);
        when(agents.loadForRun("short-drama-global-understanding")).thenReturn(globalAgent());
        when(invocation.invokeText(any())).thenReturn(result(null, List.of(new AiToolCall(
            "save", "save_global_understanding", "{\"schemaVersion\":1,\"content\":{}}")), 804L));

        assertThatThrownBy(() -> runner.runFormal(new WorkflowAgentRunInput(
            "short-drama-global-understanding", "执行", 7L, 25L, null, 77L, null, null, 9L)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("按顺序");
    }

    @Test
    void globalUnderstandingStopsAtTheFirstTerminalSave() throws Exception {
        AtomicInteger saves = new AtomicInteger();
        WorkflowToolDefinition save = new WorkflowToolDefinition(
            "save_global_understanding", "保存全局理解", "保存全局理解",
            json.readTree("{\"type\":\"object\"}"), json.readTree("{\"type\":\"object\"}"),
            ToolRiskLevel.WRITE, ToolFailurePolicy.TERMINAL, new WorkflowToolExecutor() {
                @Override
                public com.fasterxml.jackson.databind.JsonNode execute(
                    com.antshorttv.workflowagent.tool.ToolExecutionContext context,
                    com.fasterxml.jackson.databind.JsonNode arguments
                ) {
                    saves.incrementAndGet();
                    try {
                        return json.readTree("{\"saved\":true}");
                    } catch (java.io.IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                }
            });
        runner = runnerWith(List.of(tool("read_current_script"), tool("read_script_structure"),
            tool("analyze_script_chunks"), save), 30);
        when(agents.loadForRun("short-drama-global-understanding")).thenReturn(globalAgent());
        when(invocation.invokeText(any())).thenReturn(result(null, List.of(
            new AiToolCall("read", "read_current_script", "{}"),
            new AiToolCall("save-1", "save_global_understanding", "{\"schemaVersion\":1,\"content\":{}}"),
            new AiToolCall("save-2", "save_global_understanding", "{\"schemaVersion\":1,\"content\":{}}")
        ), 805L));

        runner.runFormal(new WorkflowAgentRunInput(
            "short-drama-global-understanding", "执行", 7L, 25L, null, 77L, null, null, 9L));

        assertThat(saves).hasValue(1);
    }

    @Test
    void globalUnderstandingRejectsAnOversizedSavePayloadBeforeExecution() throws Exception {
        WorkflowAgentProperties properties = new WorkflowAgentProperties();
        properties.setRunTimeoutSeconds(30);
        properties.setMaxLogPayloadBytes(80);
        runner = new WorkflowAgentRunner(
            agents, skills, new WorkflowToolRegistry(List.of(
                tool("read_current_script"), tool("save_global_understanding"))),
            new WorkflowToolSchemaValidator(), invocation, runs, scopeGuard, properties, json);
        when(agents.loadForRun("short-drama-global-understanding")).thenReturn(globalAgent());
        when(invocation.invokeText(any()))
            .thenReturn(result(null, List.of(new AiToolCall("read", "read_current_script", "{}")), 806L))
            .thenReturn(result(null, List.of(new AiToolCall(
                "save", "save_global_understanding",
                "{\"schemaVersion\":1,\"content\":{\"synopsis\":\"" + "x".repeat(200) + "\"}}")), 807L));

        assertThatThrownBy(() -> runner.runFormal(new WorkflowAgentRunInput(
            "short-drama-global-understanding", "执行", 7L, 25L, null, 77L, null, null, 9L)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("负载");
    }

    @Test
    void splittingClearsFullContextAndCompletesTheFallbackSequenceAfterTruncation() {
        WorkflowAgentProperties properties = new WorkflowAgentProperties();
        properties.setRunTimeoutSeconds(30);
        EpisodeSplittingRunPolicy policy = new EpisodeSplittingRunPolicy(properties, input -> "small");
        runner = new WorkflowAgentRunner(
            agents, skills, new WorkflowToolRegistry(List.of(
                tool("read_current_script"), tool("read_script_structure"),
                tool("analyze_script_chunks"), tool("save_episode_splitting"))),
            new WorkflowToolSchemaValidator(), invocation, runs, scopeGuard, properties, json, policy);
        when(agents.loadForRun("short-drama-episode-splitting")).thenReturn(splitAgent());
        when(invocation.invokeText(any()))
            .thenReturn(result(null, List.of(new AiToolCall("read", "read_current_script", "{}")), 901L))
            .thenReturn(truncated(902L))
            .thenReturn(result(null, List.of(new AiToolCall("structure", "read_script_structure", "{}")), 903L))
            .thenReturn(result(null, List.of(new AiToolCall("analyze", "analyze_script_chunks", "{}")), 904L))
            .thenReturn(result("候选分析完成", List.of(), 905L))
            .thenReturn(result(null, List.of(new AiToolCall("save", "save_episode_splitting", "{}")), 906L));

        runner.runFormal(new WorkflowAgentRunInput(
            "short-drama-episode-splitting", "FULL_INPUT", 7L, 25L, null, 77L,
            null, null, 9L));

        var requests = org.mockito.ArgumentCaptor.forClass(com.antshorttv.ai.AiInvocationRequest.class);
        verify(invocation, org.mockito.Mockito.times(6)).invokeText(requests.capture());
        assertThat(requests.getAllValues().get(2).textRequest().messages())
            .extracting(AiChatMessage::content).doesNotContain("FULL_INPUT");
        assertThat(requests.getAllValues().get(2).textRequest().messages().get(1).content())
            .contains("read_script_structure", "OUTPUT_TRUNCATED");
        assertThat(requests.getAllValues().get(0).textRequest().tools())
            .extracting(com.antshorttv.ai.AiToolDefinition::code)
            .containsExactly("read_current_script", "save_episode_splitting");
        assertThat(requests.getAllValues().get(2).textRequest().tools())
            .extracting(com.antshorttv.ai.AiToolDefinition::code)
            .containsExactly("read_script_structure", "analyze_script_chunks", "save_episode_splitting");
        assertThat(requests.getAllValues()).allSatisfy(request ->
            assertThat(request.textRequest().messages().get(0).content())
                .contains("startSegmentId", "schemaVersion=2"));
        assertThat(requests.getAllValues()).allSatisfy(request ->
            assertThat(request.textRequest().thinkingMode()).isEqualTo("disabled"));
    }

    @Test
    void splittingReturnsBoundaryValidationErrorsToTheModelForOneCorrectiveSave() throws Exception {
        AtomicInteger saves = new AtomicInteger();
        WorkflowToolDefinition save = new WorkflowToolDefinition(
            "save_episode_splitting", "保存分集", "保存分集",
            json.readTree("{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}"),
            json.readTree("{\"type\":\"object\"}"), ToolRiskLevel.WRITE,
            ToolFailurePolicy.TERMINAL,
            new WorkflowToolExecutor() {
                @Override
                public com.fasterxml.jackson.databind.JsonNode execute(
                    com.antshorttv.workflowagent.tool.ToolExecutionContext context,
                    com.fasterxml.jackson.databind.JsonNode arguments
                ) {
                    if (saves.incrementAndGet() == 1) {
                        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "边界标记无法唯一定位");
                    }
                    return json.createObjectNode().put("saved", true);
                }
            });
        runner = runnerWith(List.of(tool("read_current_script"), tool("read_script_structure"),
            tool("analyze_script_chunks"), save), 30);
        when(agents.loadForRun("short-drama-episode-splitting")).thenReturn(splitAgent());
        when(invocation.invokeText(any()))
            .thenReturn(result(null, List.of(new AiToolCall("read", "read_current_script", "{}")), 911L))
            .thenReturn(result(null, List.of(new AiToolCall("bad", "save_episode_splitting", "{}")), 912L))
            .thenReturn(result(null, List.of(new AiToolCall("fixed", "save_episode_splitting", "{}")), 913L));

        WorkflowAgentRunResult result = runner.runFormal(new WorkflowAgentRunInput(
            "short-drama-episode-splitting", "执行", 7L, 25L, null, 77L,
            null, null, 9L));

        assertThat(result.runId()).isEqualTo(101L);
        assertThat(saves).hasValue(2);
        verify(invocation, org.mockito.Mockito.times(3)).invokeText(any());
    }

    @ParameterizedTest
    @CsvSource({"8, 3, evidence invalid", "5, 2, 最大执行步数"})
    void assetRecognitionBoundsCorrectiveSavesByAttemptsAndSteps(
        int maxSteps, int expectedSaves, String errorMessage
    ) throws Exception {
        AtomicInteger saves = new AtomicInteger();
        WorkflowToolDefinition save = new WorkflowToolDefinition(
            "save_episode_assets", "保存资产", "保存资产",
            json.readTree("{\"type\":\"object\"}"), json.readTree("{\"type\":\"object\"}"),
            ToolRiskLevel.WRITE, ToolFailurePolicy.RETURN_TO_MODEL,
            new WorkflowToolExecutor() {
                @Override
                public com.fasterxml.jackson.databind.JsonNode execute(
                    com.antshorttv.workflowagent.tool.ToolExecutionContext context,
                    com.fasterxml.jackson.databind.JsonNode arguments
                ) {
                    saves.incrementAndGet();
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "evidence invalid");
                }
            });
        runner = runnerWith(List.of(tool("read_current_episode"), save), 30);
        when(agents.loadForRun("short-drama-asset-recognition")).thenReturn(new WorkflowAgentRecord(
            6L, "short-drama-asset-recognition", "资产识别", "", "执行", 8L,
            new BigDecimal("0.2"), 4096, maxSteps, "ENABLED", 0L, 9L, 9L,
            LocalDateTime.now(), LocalDateTime.now(), List.of(),
            List.of("read_current_episode", "save_episode_assets")));
        when(invocation.invokeText(any()))
            .thenReturn(result(null, List.of(new AiToolCall("bad-1", "save_episode_assets", "{}")), 932L))
            .thenReturn(result(null, List.of(new AiToolCall("bad-2", "save_episode_assets", "{}")), 933L))
            .thenReturn(result(null, List.of(new AiToolCall("bad-3", "save_episode_assets", "{}")), 934L));

        assertThatThrownBy(() -> runner.runFormal(new WorkflowAgentRunInput(
            "short-drama-asset-recognition", "执行", 7L, 25L, 91L, 77L,
            null, null, 9L)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining(errorMessage);

        assertThat(saves).hasValue(expectedSaves);
        var requests = org.mockito.ArgumentCaptor.forClass(com.antshorttv.ai.AiInvocationRequest.class);
        verify(invocation, org.mockito.Mockito.times(expectedSaves)).invokeText(requests.capture());
        assertThat(requests.getAllValues()).allSatisfy(request ->
            assertThat(request.textRequest().tools())
                .extracting(com.antshorttv.ai.AiToolDefinition::code)
                .containsExactly("save_episode_assets"));
        assertThat(requests.getAllValues()).allSatisfy(request -> {
            assertThat(request.textRequest().maxTokens()).isEqualTo(4096);
            assertThat(request.textRequest().retryCount()).isEqualTo(0);
        });
    }

    @Test
    void storyboardAgentHostPreparesAllReadsBeforeOnePlanningModelCall() {
        List<String> codes = List.of("read_current_episode", "read_adjacent_episodes",
            "read_script_analysis", "read_project_context", "read_script_assets",
            "save_episode_storyboards");
        List<String> executed = new ArrayList<>();
        List<WorkflowToolDefinition> definitions = new ArrayList<>();
        codes.subList(0, 5).forEach(code -> definitions.add(storyboardRead(code, executed)));
        definitions.add(tool("save_episode_storyboards"));
        runner = runnerWith(definitions, 30);
        when(agents.loadForRun("short-drama-storyboard")).thenReturn(new WorkflowAgentRecord(
            7L, "short-drama-storyboard", "分镜规划", "", "执行", 8L,
            new BigDecimal("0.2"), 8192, 12, "ENABLED", 0L, 9L, 9L,
            LocalDateTime.now(), LocalDateTime.now(), List.of(), codes));
        when(invocation.invokeText(any())).thenAnswer(ignored -> {
            assertThat(executed).containsExactlyElementsOf(codes.subList(0, 5));
            return result(null, List.of(
                new AiToolCall("save", "save_episode_storyboards", "{}")), 950L);
        });

        runner.runFormal(new WorkflowAgentRunInput(
            "short-drama-storyboard", "执行", 7L, 25L, 91L, 77L,
            null, null, 9L, 700L, 701L, 1, 8L));

        var requests = org.mockito.ArgumentCaptor.forClass(com.antshorttv.ai.AiInvocationRequest.class);
        verify(invocation, org.mockito.Mockito.times(1)).invokeText(requests.capture());
        assertThat(requests.getValue().textRequest().tools())
            .extracting(com.antshorttv.ai.AiToolDefinition::code)
            .containsExactlyElementsOf(codes);
        assertThat(requests.getValue().textRequest().messages())
            .extracting(AiChatMessage::content)
            .anySatisfy(content -> assertThat(content)
                .contains("read_current_episode", "read_adjacent_episodes", "read_script_analysis",
                    "read_project_context", "read_script_assets")
                .doesNotContain("old storyboard"));
        for (int index = 0; index < 5; index++) {
            verify(runs).recordToolStep(101L, index + 1, codes.get(index), "{}",
                "{\"tool\":\"" + codes.get(index) + "\"}");
        }
        verify(runs).recordModelStep(101L, 6, 950L,
            List.of(new AiToolCall("save", "save_episode_storyboards", "{}")), null);
        verify(runs).recordToolStep(101L, 7, "save_episode_storyboards", "{}", "{}");
    }

    @Test
    void storyboardPlanningMessageExcludesFullProjectAnalysisContent() throws Exception {
        List<String> codes = List.of("read_current_episode", "read_adjacent_episodes",
            "read_script_analysis", "read_project_context", "read_script_assets",
            "save_episode_storyboards");
        List<String> executed = new ArrayList<>();
        var current = json.readTree("""
            {"episodeKey":"episode-1","episodeNo":1,"content":"CURRENT_EPISODE_TEXT",
             "contentFingerprint":"fingerprint","sourceSegments":[
               {"id":"S0001","type":"ACTION","text":"CURRENT_EPISODE_TEXT",
                "requiredCoverage":true}],"assetCatalog":{"characters":[],"scenes":[],"props":[]}}
            """);
        var analysis = json.createObjectNode();
        analysis.putNull("globalUnderstanding");
        analysis.putArray("stages").addObject()
            .put("stage_code", "EPISODE_SPLITTING")
            .put("normalized_json", "{\"episodes\":[{\"episodeNo\":1,"
                + "\"content\":\"FULL_PROJECT_SCRIPT\"}]}")
            .put("raw_response", "RAW_MODEL_RESPONSE");
        List<WorkflowToolDefinition> definitions = new ArrayList<>();
        definitions.add(storyboardRead("read_current_episode", executed, current));
        definitions.add(storyboardRead("read_adjacent_episodes", executed,
            json.readTree("{\"previous\":null,\"next\":null}")));
        definitions.add(storyboardRead("read_script_analysis", executed, analysis));
        definitions.add(storyboardRead("read_project_context", executed,
            json.readTree("{\"projectId\":26}")));
        definitions.add(storyboardRead("read_script_assets", executed,
            json.readTree("{\"characters\":[],\"scenes\":[],\"props\":[]}")));
        definitions.add(tool("save_episode_storyboards"));
        runner = runnerWith(definitions, 30);
        when(agents.loadForRun("short-drama-storyboard")).thenReturn(new WorkflowAgentRecord(
            7L, "short-drama-storyboard", "分镜规划", "", "执行", 8L,
            new BigDecimal("0.2"), 8192, 12, "ENABLED", 0L, 9L, 9L,
            LocalDateTime.now(), LocalDateTime.now(), List.of(), codes));
        when(invocation.invokeText(any())).thenReturn(result(null, List.of(
            new AiToolCall("save", "save_episode_storyboards", "{}")), 951L));

        runner.runFormal(new WorkflowAgentRunInput(
            "short-drama-storyboard", "执行", 7L, 25L, 91L, 77L,
            null, null, 9L, 700L, 701L, 1, 8L));

        var requests = org.mockito.ArgumentCaptor.forClass(com.antshorttv.ai.AiInvocationRequest.class);
        verify(invocation).invokeText(requests.capture());
        String planningMessage = requests.getValue().textRequest().messages().stream()
            .map(AiChatMessage::content)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.joining("\n"));
        assertThat(planningMessage)
            .contains("CURRENT_EPISODE_TEXT")
            .doesNotContain("FULL_PROJECT_SCRIPT", "raw_response", "RAW_MODEL_RESPONSE");
        assertThat(executed).containsExactlyElementsOf(codes.subList(0, 5));
        verify(runs).recordToolStep(101L, 3, "read_script_analysis", "{}",
            json.writeValueAsString(analysis));
    }

    @Test
    void storyboardPreparationFailureStopsBeforeTheModel() throws Exception {
        List<String> codes = List.of("read_current_episode", "read_adjacent_episodes",
            "read_script_analysis", "read_project_context", "read_script_assets",
            "save_episode_storyboards");
        List<String> executed = new ArrayList<>();
        List<WorkflowToolDefinition> definitions = new ArrayList<>();
        definitions.add(storyboardRead(codes.get(0), executed));
        definitions.add(storyboardRead(codes.get(1), executed));
        definitions.add(new WorkflowToolDefinition(
            codes.get(2), codes.get(2), codes.get(2),
            json.readTree("{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}"),
            json.readTree("{\"type\":\"object\"}"), ToolRiskLevel.READ_ONLY,
            ToolFailurePolicy.TERMINAL, new WorkflowToolExecutor() {
                @Override
                public com.fasterxml.jackson.databind.JsonNode execute(
                    com.antshorttv.workflowagent.tool.ToolExecutionContext context,
                    com.fasterxml.jackson.databind.JsonNode arguments
                ) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "analysis unavailable");
                }
            }));
        definitions.add(storyboardRead(codes.get(3), executed));
        definitions.add(storyboardRead(codes.get(4), executed));
        definitions.add(tool(codes.get(5)));
        runner = runnerWith(definitions, 30);
        when(agents.loadForRun("short-drama-storyboard")).thenReturn(new WorkflowAgentRecord(
            7L, "short-drama-storyboard", "分镜规划", "", "执行", 8L,
            new BigDecimal("0.2"), 8192, 12, "ENABLED", 0L, 9L, 9L,
            LocalDateTime.now(), LocalDateTime.now(), List.of(), codes));

        assertThatThrownBy(() -> runner.runFormal(new WorkflowAgentRunInput(
            "short-drama-storyboard", "执行", 7L, 25L, 91L, 77L,
            null, null, 9L, 700L, 701L, 1, 8L)))
            .isInstanceOf(BusinessException.class).hasMessageContaining("analysis unavailable");

        assertThat(executed).containsExactly(codes.get(0), codes.get(1));
        verify(invocation, never()).invokeText(any());
        verify(runs).recordFailedToolStep(101L, 3, codes.get(2), "{}",
            ErrorCode.VALIDATION_ERROR.name(), "analysis unavailable");
    }

    @Test
    void storyboardHardValidationStopsAfterOneBusinessModelCall() throws Exception {
        List<String> codes = List.of("read_current_episode", "read_adjacent_episodes",
            "read_script_analysis", "read_project_context", "read_script_assets",
            "save_episode_storyboards");
        List<WorkflowToolDefinition> definitions = new ArrayList<>();
        codes.subList(0, 5).forEach(code -> definitions.add(storyboardRead(code, new ArrayList<>())));
        AtomicInteger saves = new AtomicInteger();
        definitions.add(storyboardSave(saves, false));
        runner = runnerWith(definitions, 30);
        when(agents.loadForRun("short-drama-storyboard")).thenReturn(storyboardAgent(codes));
        when(invocation.invokeText(any()))
            .thenReturn(result(null, List.of(new AiToolCall("bad", "save_episode_storyboards", "{}")), 960L));

        assertThatThrownBy(() -> runner.runFormal(new WorkflowAgentRunInput(
            "short-drama-storyboard", "执行", 7L, 25L, 91L, 77L,
            null, null, 9L, 700L, 701L, 1, 8L)))
            .isInstanceOf(WorkflowToolValidationException.class);

        assertThat(saves).hasValue(1);
        verify(invocation, org.mockito.Mockito.times(1)).invokeText(any());
    }

    @Test
    void storyboardTerminalPolicyDoesNotRetryARepeatableFailure() throws Exception {
        List<String> codes = List.of("read_current_episode", "read_adjacent_episodes",
            "read_script_analysis", "read_project_context", "read_script_assets",
            "save_episode_storyboards");
        List<WorkflowToolDefinition> definitions = new ArrayList<>();
        codes.subList(0, 5).forEach(code -> definitions.add(storyboardRead(code, new ArrayList<>())));
        AtomicInteger saves = new AtomicInteger();
        definitions.add(storyboardSave(saves, true));
        runner = runnerWith(definitions, 30);
        when(agents.loadForRun("short-drama-storyboard")).thenReturn(storyboardAgent(codes));
        when(invocation.invokeText(any()))
            .thenReturn(result(null, List.of(new AiToolCall("bad-1", "save_episode_storyboards", "{}")), 970L));

        assertThatThrownBy(() -> runner.runFormal(new WorkflowAgentRunInput(
            "short-drama-storyboard", "执行", 7L, 25L, 91L, 77L,
            null, null, 9L, 700L, 701L, 1, 8L)))
            .isInstanceOf(WorkflowToolValidationException.class);
        assertThat(saves).hasValue(1);
        verify(invocation, org.mockito.Mockito.times(1)).invokeText(any());
    }

    @Test
    void assetRecognitionCanCorrectSuccessiveEvidenceErrorsAndThenSucceed() throws Exception {
        AtomicInteger saves = new AtomicInteger();
        WorkflowToolDefinition save = new WorkflowToolDefinition(
            "save_episode_assets", "保存资产", "保存资产",
            json.readTree("{\"type\":\"object\"}"), json.readTree("{\"type\":\"object\"}"),
            ToolRiskLevel.WRITE, ToolFailurePolicy.RETURN_TO_MODEL,
            new WorkflowToolExecutor() {
                @Override
                public com.fasterxml.jackson.databind.JsonNode execute(
                    com.antshorttv.workflowagent.tool.ToolExecutionContext context,
                    com.fasterxml.jackson.databind.JsonNode arguments
                ) {
                    if (saves.incrementAndGet() <= 2) {
                        throw WorkflowToolValidationException.aggregate(java.util.stream.IntStream.range(0, 100)
                            .mapToObj(i -> "$.scenes[" + i + "].evidence is required").toList());
                    }
                    return json.createObjectNode().put("saved", true);
                }
            });
        runner = runnerWith(List.of(tool("read_current_episode"), save), 30);
        when(agents.loadForRun("short-drama-asset-recognition")).thenReturn(new WorkflowAgentRecord(
            6L, "short-drama-asset-recognition", "资产识别", "", "执行", 8L,
            new BigDecimal("0.2"), 4096, 8, "ENABLED", 0L, 9L, 9L,
            LocalDateTime.now(), LocalDateTime.now(), List.of(),
            List.of("read_current_episode", "save_episode_assets")));
        when(invocation.invokeText(any()))
            .thenReturn(result(null, List.of(new AiToolCall("bad", "save_episode_assets", "{}")), 942L))
            .thenReturn(result(null, List.of(new AiToolCall("fixed", "save_episode_assets", "{}")), 943L))
            .thenReturn(result(null, List.of(new AiToolCall("fixed-again", "save_episode_assets", "{}")), 944L));

        WorkflowAgentRunResult result = runner.runFormal(new WorkflowAgentRunInput(
            "short-drama-asset-recognition", "执行", 7L, 25L, 91L, 77L,
            null, null, 9L));

        assertThat(result.runId()).isEqualTo(101L);
        assertThat(saves).hasValue(3);
        var requests = org.mockito.ArgumentCaptor.forClass(com.antshorttv.ai.AiInvocationRequest.class);
        verify(invocation, org.mockito.Mockito.times(3)).invokeText(requests.capture());
        assertThat(requests.getAllValues().get(1).textRequest().messages())
            .extracting(AiChatMessage::content)
            .anySatisfy(message -> assertThat(message)
                .contains("$.scenes[1].evidence is required", "validationErrors", "$.scenes[99].evidence is required"));
        assertThat(requests.getAllValues().get(2).textRequest().messages())
            .extracting(AiChatMessage::content)
            .anySatisfy(message -> assertThat(message).contains("evidenceRef", "usageEvidenceRef", "sourceSegments", "不得再次读取", "不得删除"));
    }

    @ParameterizedTest
    @CsvSource({"WORKFLOW_AGENT_TIMEOUT", "WORKFLOW_AGENT_TOOL_UNAUTHORIZED"})
    void assetRecognitionDoesNotCorrectNonValidationFailures(String code) throws Exception {
        AtomicInteger saves = new AtomicInteger();
        WorkflowToolDefinition save = new WorkflowToolDefinition(
            "save_episode_assets", "保存", "保存", json.readTree("{\"type\":\"object\"}"),
            json.readTree("{\"type\":\"object\"}"), ToolRiskLevel.WRITE,
            ToolFailurePolicy.RETURN_TO_MODEL, new WorkflowToolExecutor() {
                @Override
                public JsonNode execute(com.antshorttv.workflowagent.tool.ToolExecutionContext context,
                    JsonNode arguments) {
                    saves.incrementAndGet();
                    throw new BusinessException(ErrorCode.valueOf(code), "noncorrectable failure");
                }
            });
        runner = runnerWith(List.of(tool("read_current_episode"), save), 30);
        when(agents.loadForRun("short-drama-asset-recognition")).thenReturn(new WorkflowAgentRecord(
            6L, "short-drama-asset-recognition", "资产识别", "", "执行", 8L,
            new BigDecimal("0.2"), 4096, 8, "ENABLED", 0L, 9L, 9L,
            LocalDateTime.now(), LocalDateTime.now(), List.of(),
            List.of("read_current_episode", "save_episode_assets")));
        when(invocation.invokeText(any())).thenReturn(result(null,
            List.of(new AiToolCall("save", "save_episode_assets", "{}")), 944L));
        assertThatThrownBy(() -> runner.runFormal(new WorkflowAgentRunInput(
            "short-drama-asset-recognition", "执行", 7L, 25L, 91L, 77L,
            null, null, 9L))).isInstanceOf(BusinessException.class)
            .hasMessageContaining("noncorrectable failure");
        assertThat(saves).hasValue(1);
        verify(invocation).invokeText(any());
    }

    @ParameterizedTest
    @CsvSource({"database", "state"})
    void assetRecognitionDoesNotCorrectInfrastructureFailures(String kind) throws Exception {
        AtomicInteger saves = new AtomicInteger();
        WorkflowToolDefinition save = new WorkflowToolDefinition(
            "save_episode_assets", "保存", "保存", json.readTree("{\"type\":\"object\"}"),
            json.readTree("{\"type\":\"object\"}"), ToolRiskLevel.WRITE,
            ToolFailurePolicy.RETURN_TO_MODEL, new WorkflowToolExecutor() {
                @Override
                public JsonNode execute(com.antshorttv.workflowagent.tool.ToolExecutionContext context,
                    JsonNode arguments) {
                    saves.incrementAndGet();
                    if ("database".equals(kind)) {
                        throw new org.springframework.dao.DataAccessResourceFailureException("storage unavailable");
                    }
                    throw new IllegalStateException("storage unavailable");
                }
            });
        runner = runnerWith(List.of(tool("read_current_episode"), save), 30);
        when(agents.loadForRun("short-drama-asset-recognition")).thenReturn(new WorkflowAgentRecord(
            6L, "short-drama-asset-recognition", "资产识别", "", "执行", 8L,
            new BigDecimal("0.2"), 4096, 8, "ENABLED", 0L, 9L, 9L,
            LocalDateTime.now(), LocalDateTime.now(), List.of(),
            List.of("read_current_episode", "save_episode_assets")));
        when(invocation.invokeText(any())).thenReturn(result(null,
            List.of(new AiToolCall("save", "save_episode_assets", "{}")), 944L));
        assertThatThrownBy(() -> runner.runFormal(new WorkflowAgentRunInput(
            "short-drama-asset-recognition", "执行", 7L, 25L, 91L, 77L,
            null, null, 9L))).isInstanceOf(BusinessException.class)
            .hasMessageContaining("storage unavailable");
        assertThat(saves).hasValue(1);
        verify(invocation).invokeText(any());
    }

    @Test
    void assetRecognitionDefaultsAllowTwoBoundedCorrections() {
        WorkflowAgentProperties defaults = new WorkflowAgentProperties();
        assertThat(defaults.getAssetRecognitionRunTimeoutSeconds()).isEqualTo(600);
        assertThat(defaults.getAssetRecognitionRequestTimeoutSeconds()).isEqualTo(300);
    }

    @Test
    void episodeStagesUseIndependentRequestAndRunBudgets() throws Exception {
        WorkflowAgentProperties stageProperties = new WorkflowAgentProperties();
        stageProperties.setRunTimeoutSeconds(30);
        stageProperties.setAssetRecognitionRunTimeoutSeconds(40);
        stageProperties.setAssetRecognitionRequestTimeoutSeconds(7);
        WorkflowToolDefinition save = new WorkflowToolDefinition(
            "save_episode_assets", "保存", "保存", json.readTree("{\"type\":\"object\"}"),
            json.readTree("{\"type\":\"object\"}"), ToolRiskLevel.WRITE,
            ToolFailurePolicy.TERMINAL, new WorkflowToolExecutor() {
                @Override
                public JsonNode execute(
                    com.antshorttv.workflowagent.tool.ToolExecutionContext context,
                    JsonNode arguments
                ) {
                    return json.createObjectNode();
                }
            });
        runner = new WorkflowAgentRunner(
            agents, skills, new WorkflowToolRegistry(List.of(tool("read_current_episode"), save)),
            new WorkflowToolSchemaValidator(), invocation, runs, scopeGuard, stageProperties, json
        );
        when(agents.loadForRun("short-drama-asset-recognition")).thenReturn(new WorkflowAgentRecord(
            6L, "short-drama-asset-recognition", "资产识别", "", "执行", 8L,
            new BigDecimal("0.2"), 4096, 6, "ENABLED", 0L, 9L, 9L,
            LocalDateTime.now(), LocalDateTime.now(), List.of(),
            List.of("read_current_episode", "save_episode_assets")));
        when(invocation.invokeText(any())).thenReturn(result(null,
            List.of(new AiToolCall("save", "save_episode_assets", "{}")), 944L));

        runner.runFormal(new WorkflowAgentRunInput(
            "short-drama-asset-recognition", "执行", 7L, 25L, 91L, 77L,
            null, null, 9L));

        var request = org.mockito.ArgumentCaptor.forClass(com.antshorttv.ai.AiInvocationRequest.class);
        verify(invocation).invokeText(request.capture());
        assertThat(request.getValue().textRequest().timeoutSeconds()).isEqualTo(7);
    }

    @Test
    void stopsBeforeAnotherProviderRoundWhenTheUnifiedExecutionIsCanceled() {
        doNothing().doNothing().doNothing()
            .doThrow(new BusinessException(ErrorCode.VALIDATION_ERROR, "执行已取消"))
            .when(scopeGuard).requireExecutionActive(any());
        when(invocation.invokeText(any())).thenReturn(result(null,
            List.of(new AiToolCall("read", "read_episode_script", "{}")), 934L));

        assertThatThrownBy(() -> runner.runFormal(new WorkflowAgentRunInput(
            "screenplay-agent", "读取", 7L, 25L, 91L, 77L, null, null, 9L,
            700L, 701L, 1, 8L)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("取消");

        verify(invocation, org.mockito.Mockito.times(1)).invokeText(any());
    }

    @Test
    void splittingReturnsAnOutOfSequenceFallbackToolToTheModelForCorrection() {
        WorkflowAgentProperties properties = new WorkflowAgentProperties();
        properties.setRunTimeoutSeconds(30);
        properties.setSplitSafeContextTokens(1);
        EpisodeSplittingRunPolicy policy = new EpisodeSplittingRunPolicy(properties, input -> "long script");
        runner = new WorkflowAgentRunner(
            agents, skills, new WorkflowToolRegistry(List.of(
                tool("read_current_script"), tool("read_script_structure"),
                tool("analyze_script_chunks"), tool("save_episode_splitting"))),
            new WorkflowToolSchemaValidator(), invocation, runs, scopeGuard, properties, json, policy);
        when(agents.loadForRun("short-drama-episode-splitting")).thenReturn(splitAgent());
        when(invocation.invokeText(any()))
            .thenReturn(result(null, List.of(new AiToolCall("wrong", "read_current_script", "{}")), 921L))
            .thenReturn(result(null, List.of(new AiToolCall("structure", "read_script_structure", "{}")), 922L))
            .thenReturn(result(null, List.of(new AiToolCall("analyze", "analyze_script_chunks", "{}")), 923L))
            .thenReturn(result(null, List.of(new AiToolCall("save", "save_episode_splitting", "{}")), 924L));

        WorkflowAgentRunResult result = runner.runFormal(new WorkflowAgentRunInput(
            "short-drama-episode-splitting", "执行", 7L, 25L, null, 77L,
            null, null, 9L));

        assertThat(result.runId()).isEqualTo(101L);
        verify(invocation, org.mockito.Mockito.times(4)).invokeText(any());
        verify(runs).recordFailedToolStep(org.mockito.ArgumentMatchers.eq(101L),
            org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.eq("read_current_script"),
            org.mockito.ArgumentMatchers.eq("{}"),
            org.mockito.ArgumentMatchers.eq(ErrorCode.REQUIRED_TOOL_NOT_CALLED.name()),
            org.mockito.ArgumentMatchers.anyString());
    }

    private WorkflowAgentRecord agent(int maxSteps, List<String> skillCodes, List<String> toolCodes) {
        return new WorkflowAgentRecord(3L, "screenplay-agent", "编剧", "", "遵循工作流。", 8L,
            new BigDecimal("0.2"), 2048, maxSteps, "ENABLED", 0L, 9L, 9L,
            LocalDateTime.now(), LocalDateTime.now(), skillCodes, toolCodes);
    }

    private AiInvocationResult<AiTextResponse> result(String content, List<AiToolCall> calls, Long logId) {
        AiTextResponse response = new AiTextResponse(content, "provider-1", 1, 1, 2, 10L,
            Map.of(), calls.isEmpty() ? "stop" : "tool_calls", false, calls);
        return new AiInvocationResult<>(AiCapability.TEXT, "workflow_agent", response, content, logId,
            "provider-1", 8L, 2L, "OpenAI", 1, 1, 2, 10L, "SUCCESS", null, null);
    }

    private WorkflowAgentRunner runnerWith(List<WorkflowToolDefinition> definitions, long timeoutSeconds) {
        WorkflowAgentProperties properties = new WorkflowAgentProperties();
        properties.setRunTimeoutSeconds(timeoutSeconds);
        return new WorkflowAgentRunner(
            agents, skills, new WorkflowToolRegistry(definitions), new WorkflowToolSchemaValidator(),
            invocation, runs, scopeGuard, properties, json
        );
    }

    private WorkflowToolDefinition tool(String code) {
        try {
            return new WorkflowToolDefinition(
                code, "读取剧集", "读取当前剧集",
                json.readTree("{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}"),
                json.readTree("{\"type\":\"object\"}"), ToolRiskLevel.READ_ONLY,
                ToolFailurePolicy.TERMINAL,
                new WorkflowToolExecutor() {
                    @Override
                    public com.fasterxml.jackson.databind.JsonNode execute(
                        com.antshorttv.workflowagent.tool.ToolExecutionContext context,
                        com.fasterxml.jackson.databind.JsonNode arguments
                    ) {
                        return json.createObjectNode();
                    }
                }
            );
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private WorkflowToolDefinition storyboardRead(String code, List<String> executed) {
        try {
            return new WorkflowToolDefinition(
                code, code, code,
                json.readTree("{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}"),
                json.readTree("{\"type\":\"object\"}"), ToolRiskLevel.READ_ONLY,
                ToolFailurePolicy.TERMINAL, new WorkflowToolExecutor() {
                    @Override
                    public com.fasterxml.jackson.databind.JsonNode execute(
                        com.antshorttv.workflowagent.tool.ToolExecutionContext context,
                        com.fasterxml.jackson.databind.JsonNode arguments
                    ) {
                        executed.add(code);
                        return json.createObjectNode().put("tool", code);
                    }
                });
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private WorkflowToolDefinition storyboardRead(
        String code,
        List<String> executed,
        JsonNode output
    ) {
        try {
            return new WorkflowToolDefinition(
                code, code, code,
                json.readTree("{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}"),
                json.readTree("{\"type\":\"object\"}"), ToolRiskLevel.READ_ONLY,
                ToolFailurePolicy.TERMINAL, new WorkflowToolExecutor() {
                    @Override
                    public com.fasterxml.jackson.databind.JsonNode execute(
                        com.antshorttv.workflowagent.tool.ToolExecutionContext context,
                        com.fasterxml.jackson.databind.JsonNode arguments
                    ) {
                        executed.add(code);
                        return output.deepCopy();
                    }
                });
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private WorkflowToolDefinition storyboardSave(AtomicInteger saves, boolean alwaysFail) throws Exception {
        return new WorkflowToolDefinition(
            "save_episode_storyboards", "save", "save", json.readTree("{\"type\":\"object\"}"),
            json.readTree("{\"type\":\"object\"}"), ToolRiskLevel.WRITE,
            ToolFailurePolicy.TERMINAL, new WorkflowToolExecutor() {
                @Override
                public com.fasterxml.jackson.databind.JsonNode execute(
                    com.antshorttv.workflowagent.tool.ToolExecutionContext context,
                    com.fasterxml.jackson.databind.JsonNode arguments
                ) {
                    int attempt = saves.incrementAndGet();
                    if (attempt == 1 || alwaysFail) {
                        throw new WorkflowToolValidationException("segment gap", Map.of(
                            "validationCode", "SOURCE_SEGMENT_GAP",
                            "storyboardNo", 2,
                            "expectedSegmentId", "S0002",
                            "actualSegmentId", "S0003"));
                    }
                    return json.createObjectNode().put("saved", true);
                }
            });
    }

    private WorkflowAgentRecord storyboardAgent(List<String> codes) {
        return new WorkflowAgentRecord(
            7L, "short-drama-storyboard", "分镜规划", "", "执行", 8L,
            new BigDecimal("0.2"), 8192, 12, "ENABLED", 0L, 9L, 9L,
            LocalDateTime.now(), LocalDateTime.now(), List.of(), codes);
    }

    private WorkflowToolDefinition reviewTool(String code, WorkflowToolExecutor executor)
        throws Exception {
        return new WorkflowToolDefinition(
            code, code, code,
            json.readTree("{\"type\":\"object\"}"), json.readTree("{\"type\":\"object\"}"),
            ToolRiskLevel.READ_ONLY,
            ToolFailurePolicy.RETURN_TO_MODEL, executor);
    }

    private WorkflowAgentRecord globalAgent() {
        return new WorkflowAgentRecord(
            4L, "short-drama-global-understanding", "剧情全局理解", "", "执行", 8L,
            new BigDecimal("0.2"), 4096, 4, "ENABLED", 0L, 9L, 9L,
            LocalDateTime.now(), LocalDateTime.now(), List.of(),
            List.of("read_current_script", "save_global_understanding"));
    }

    private WorkflowAgentRecord splitAgent() {
        return new WorkflowAgentRecord(
            5L, "short-drama-episode-splitting", "剧集智能拆分", "", "执行", 8L,
            new BigDecimal("0.2"), 16384, 16, "ENABLED", 0L, 9L, 9L,
            LocalDateTime.now(), LocalDateTime.now(), List.of(), List.of(
                "read_current_script", "read_script_structure", "analyze_script_chunks",
                "save_episode_splitting"));
    }

    private AiInvocationResult<AiTextResponse> truncated(Long logId) {
        AiTextResponse response = new AiTextResponse(
            "partial", "provider-1", 1, 1, 2, 10L, Map.of(), "length", true, List.of());
        return new AiInvocationResult<>(AiCapability.TEXT, "workflow_agent", response, "partial", logId,
            "provider-1", 8L, 2L, "DeepSeek", 1, 1, 2, 10L, "SUCCESS", null, null);
    }

    private AiInvocationResult<AiTextResponse> truncatedEmpty(Long logId) {
        AiTextResponse response = new AiTextResponse(
            "", "provider-1", 17131, 16384, 33515, 152789L,
            Map.of(), "length", true, List.of());
        return new AiInvocationResult<>(AiCapability.TEXT, "workflow_agent", response, "", logId,
            "provider-1", 8L, 2L, "DeepSeek", 17131, 16384, 33515, 152789L,
            "SUCCESS", null, null);
    }

    private AiInvocationResult<AiTextResponse> truncatedWithCalls(Long logId, List<AiToolCall> calls) {
        AiTextResponse response = new AiTextResponse(
            "partial", "provider-1", 1, 16384, 2, 10L,
            Map.of(), "length", true, calls);
        return new AiInvocationResult<>(AiCapability.TEXT, "workflow_agent", response, "partial", logId,
            "provider-1", 8L, 2L, "DeepSeek", 1, 16384, 2, 10L,
            "SUCCESS", null, null);
    }

    private WorkflowToolExecutor executorReturning(String value) {
        return new WorkflowToolExecutor() {
            @Override
            public com.fasterxml.jackson.databind.JsonNode execute(
                com.antshorttv.workflowagent.tool.ToolExecutionContext context,
                com.fasterxml.jackson.databind.JsonNode arguments
            ) {
                try {
                    return json.readTree(value);
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException(exception);
                }
            }
        };
    }
}
