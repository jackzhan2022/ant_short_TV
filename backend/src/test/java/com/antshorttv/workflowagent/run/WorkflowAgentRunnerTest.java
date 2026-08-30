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
import com.antshorttv.workflowagent.WorkflowAgentProperties;
import com.antshorttv.workflowagent.agent.WorkflowAgentRecord;
import com.antshorttv.workflowagent.agent.WorkflowAgentCommand;
import com.antshorttv.workflowagent.agent.WorkflowAgentService;
import com.antshorttv.workflowagent.skill.WorkflowSkillService;
import com.antshorttv.workflowagent.skill.WorkflowSkillView;
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
}
