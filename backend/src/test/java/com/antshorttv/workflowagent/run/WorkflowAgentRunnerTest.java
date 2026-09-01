package com.antshorttv.workflowagent.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.ai.AiCapability;
import com.antshorttv.ai.AiChatMessage;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    void returnsRecoverableReviewContractOrderFailureToModelForCorrection() throws Exception {
        AtomicInteger saves = new AtomicInteger();
        WorkflowToolDefinition context = reviewTool("read_review_context",
            executorReturning("{\"projectId\":25}"));
        WorkflowToolDefinition content = reviewTool("read_review_content",
            executorReturning("{\"content\":\"正文\"}"));
        WorkflowToolDefinition history = reviewTool("read_review_issue_history",
            executorReturning("{\"issues\":[]}"));
        WorkflowToolDefinition save = reviewTool("save_review_unit_result", new WorkflowToolExecutor() {
            @Override
            public com.fasterxml.jackson.databind.JsonNode execute(
                com.antshorttv.workflowagent.tool.ToolExecutionContext toolContext,
                com.fasterxml.jackson.databind.JsonNode arguments
            ) {
                saves.incrementAndGet();
                return json.createObjectNode().put("saved", true);
            }
        });
        runner = runnerWith(List.of(context, content, history, save), 30);
        when(agents.loadForRun("script-review")).thenReturn(new WorkflowAgentRecord(
            6L, "script-review", "剧本审核", "", "执行审核", 8L,
            new BigDecimal("0.2"), 16384, 8, "ENABLED", 0L, 9L, 9L,
            LocalDateTime.now(), LocalDateTime.now(), List.of(), List.of(
                "read_review_context", "read_review_content", "read_review_issue_history",
                "save_review_unit_result")));
        when(invocation.invokeText(any()))
            .thenReturn(result(null, List.of(new AiToolCall(
                "premature-save", "save_review_unit_result", "{}")), 611L))
            .thenReturn(result(null, List.of(
                new AiToolCall("context", "read_review_context", "{}"),
                new AiToolCall("content", "read_review_content", "{}"),
                new AiToolCall("history", "read_review_issue_history", "{}")), 612L))
            .thenReturn(result(null, List.of(new AiToolCall(
                "corrected-save", "save_review_unit_result", "{}")), 613L));

        WorkflowAgentRunResult result = runner.runFormal(new WorkflowAgentRunInput(
            "script-review", "执行", 7L, 25L, null, null, 91L, null, 9L,
            null, null, null, null,
            new ReviewToolScope(25L, 77L, 88L, 99L, 1, "DEEP_CHILD", List.of("台词合理性"))));

        assertThat(result.output()).contains("\"saved\":true");
        assertThat(saves).hasValue(1);
        verify(runs).recordFailedToolStep(101L, 2, "save_review_unit_result", "{}",
            ErrorCode.REQUIRED_TOOL_NOT_CALLED.name(),
            "必须先读取审核上下文，并完成全部可信读取后再保存：read_review_context -> "
                + "read_review_content -> read_review_issue_history -> save_review_unit_result");
        verify(invocation, org.mockito.Mockito.times(3)).invokeText(any());
    }

    @Test
    void scriptReviewDisablesThinkingAndRecoversTruncatedEmptyResponse() throws Exception {
        AtomicInteger saves = new AtomicInteger();
        WorkflowToolDefinition context = reviewTool("read_review_context",
            executorReturning("{\"projectId\":25}"));
        WorkflowToolDefinition content = reviewTool("read_review_content",
            executorReturning("{\"content\":\"正文\"}"));
        WorkflowToolDefinition history = reviewTool("read_review_issue_history",
            executorReturning("{\"issues\":[]}"));
        WorkflowToolDefinition save = reviewTool("save_review_unit_result", new WorkflowToolExecutor() {
            @Override
            public com.fasterxml.jackson.databind.JsonNode execute(
                com.antshorttv.workflowagent.tool.ToolExecutionContext toolContext,
                com.fasterxml.jackson.databind.JsonNode arguments
            ) {
                saves.incrementAndGet();
                return json.createObjectNode().put("saved", true);
            }
        });
        runner = runnerWith(List.of(context, content, history, save), 30);
        when(agents.loadForRun("script-review")).thenReturn(new WorkflowAgentRecord(
            6L, "script-review", "剧本审核", "", "执行审核", 8L,
            new BigDecimal("0.2"), 16384, 10, "ENABLED", 0L, 9L, 9L,
            LocalDateTime.now(), LocalDateTime.now(), List.of(), List.of(
                "read_review_context", "read_review_content", "read_review_issue_history",
                "save_review_unit_result")));
        when(invocation.invokeText(any()))
            .thenReturn(result(null, List.of(new AiToolCall(
                "context", "read_review_context", "{}")), 621L))
            .thenReturn(result(null, List.of(new AiToolCall(
                "content", "read_review_content", "{}")), 622L))
            .thenReturn(truncatedEmpty(623L))
            .thenReturn(result(null, List.of(new AiToolCall(
                "history", "read_review_issue_history", "{}")), 624L))
            .thenReturn(result(null, List.of(new AiToolCall(
                "save", "save_review_unit_result", "{}")), 625L));

        WorkflowAgentRunResult result = runner.runFormal(new WorkflowAgentRunInput(
            "script-review", "执行", 7L, 25L, null, null, 91L, null, 9L,
            null, null, null, null,
            new ReviewToolScope(25L, 77L, 88L, 99L, 1, "DEEP_CHILD", List.of("台词合理性"))));

        assertThat(result.output()).contains("\"saved\":true");
        assertThat(saves).hasValue(1);
        var requests = org.mockito.ArgumentCaptor.forClass(com.antshorttv.ai.AiInvocationRequest.class);
        verify(invocation, org.mockito.Mockito.times(5)).invokeText(requests.capture());
        assertThat(requests.getAllValues()).allSatisfy(request ->
            assertThat(request.textRequest().thinkingMode()).isEqualTo("disabled"));
        assertThat(requests.getAllValues().get(3).textRequest().messages())
            .extracting(AiChatMessage::content)
            .anySatisfy(message -> assertThat(message).contains("输出已截断", "不得输出普通文本"));
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

    private WorkflowToolDefinition reviewTool(String code, WorkflowToolExecutor executor)
        throws Exception {
        return new WorkflowToolDefinition(
            code, code, code,
            json.readTree("{\"type\":\"object\"}"), json.readTree("{\"type\":\"object\"}"),
            code.startsWith("save_") ? ToolRiskLevel.WRITE : ToolRiskLevel.READ_ONLY,
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
