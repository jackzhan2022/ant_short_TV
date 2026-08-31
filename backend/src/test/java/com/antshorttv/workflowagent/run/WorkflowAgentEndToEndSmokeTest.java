package com.antshorttv.workflowagent.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.antshorttv.ai.AiCapability;
import com.antshorttv.ai.AiInvocationResult;
import com.antshorttv.ai.AiInvocationService;
import com.antshorttv.ai.AiTextResponse;
import com.antshorttv.ai.AiToolCall;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.workflowagent.agent.WorkflowAgentCommand;
import com.antshorttv.workflowagent.agent.WorkflowAgentRepository;
import com.antshorttv.workflowagent.skill.WorkflowSkillService;
import com.antshorttv.workflowagent.skill.WorkflowSkillView;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "ai.workflow-agent.skill-root=target/workflow-agent-e2e-skills")
class WorkflowAgentEndToEndSmokeTest {
    private static final String REWRITTEN_SCREENPLAY = """
        ## S01 | 内景 · 客厅 | 深夜

        林夏放下手中的剧本，抬头看向门口。

        林夏：（坚定）这一次，我会自己作出选择。
        """;

    @Autowired
    private WorkflowSkillService skillService;
    @Autowired
    private WorkflowAgentRepository agentRepository;
    @Autowired
    private WorkflowAgentRunner runner;
    @Autowired
    private WorkflowAgentRunRepository runRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @MockBean
    private AiInvocationService invocation;
    @MockBean
    private ProjectPermissionGuard permissionGuard;

    private long tenantId;
    private long userId;
    private long projectId;
    private long scriptId;
    private long episodeId;
    private long modelId;
    private String skillCode;
    private String agentCode;

    @BeforeEach
    void setUp() {
        long seed = Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L) + 1_000_000L;
        tenantId = seed;
        userId = seed + 1;
        projectId = seed + 2;
        skillCode = "smoke-skill-" + seed;
        agentCode = "smoke-agent-" + seed;

        Long providerId = jdbc.queryForObject("select min(id) from ai_provider", Long.class);
        jdbc.update("""
            insert into ai_model
              (provider_id, code, name, model_code, service_type, status, is_default, sort,
               created_at, updated_at)
            values (?, ?, ?, ?, 'TEXT', 'ENABLED', false, 999, now(), now())
            """, providerId, agentCode + "-model", "Smoke Model", agentCode + "-model");
        modelId = jdbc.queryForObject(
            "select id from ai_model where code = ?", Long.class, agentCode + "-model");
        jdbc.update("""
            insert into ai_model_capability
              (model_id, capability, status, created_at, updated_at)
            values (?, 'TOOL_CALLING', 'ENABLED', now(), now())
            """, modelId);

        jdbc.update("""
            insert into project
              (id, tenant_id, name, code, owner_id, status, created_by, created_at, updated_at)
            values (?, ?, 'Agent Smoke Project', ?, ?, 'ACTIVE', ?, now(), now())
            """, projectId, tenantId, "AGENT_SMOKE_" + seed, userId, userId);
        jdbc.update("""
            insert into script
              (tenant_id, project_id, title, source_type, content, status, created_by,
               created_at, updated_at)
            values (?, ?, 'Agent Smoke Script', 'MANUAL_EDIT', 'full script', 'DRAFT', ?,
                    now(), now())
            """, tenantId, projectId, userId);
        scriptId = jdbc.queryForObject(
            "select id from script where project_id = ?", Long.class, projectId);
        jdbc.update("""
            insert into script_version
              (tenant_id, project_id, script_id, version_no, source_type, content, status,
               created_by, created_at)
            values (?, ?, ?, 1, 'MANUAL_EDIT', 'full script', 'DRAFT', ?, now())
            """, tenantId, projectId, scriptId, userId);
        long scriptVersionId = jdbc.queryForObject(
            "select id from script_version where script_id = ?", Long.class, scriptId);
        jdbc.update("""
            insert into script_episode
              (tenant_id, project_id, script_id, script_version_id, stable_key, episode_no,
               title, content, content_fingerprint, reconciliation_status, status,
               created_at, updated_at)
            values (?, ?, ?, ?, 'smoke-episode', 1, 'Episode 1', 'original episode',
                    'smoke-fingerprint', 'MATCHED', 'ACTIVE', now(), now())
            """, tenantId, projectId, scriptId, scriptVersionId);
        episodeId = jdbc.queryForObject(
            "select id from script_episode where project_id = ?", Long.class, projectId);

        WorkflowSkillView createdSkill = skillService.create(skillCode, """
            ---
            name: Smoke Rewrite Guide
            description: Preserve the plot and produce a formatted screenplay.
            ---
            Initial smoke-test instructions.
            """);
        skillService.update(skillCode, """
            ---
            name: Smoke Rewrite Guide
            description: Preserve the plot and produce a formatted screenplay.
            ---
            Read the current episode before saving a rewritten screenplay.
            This edited content must take effect without restarting the application.
            """, createdSkill.revision());
        agentRepository.create(new WorkflowAgentCommand(
            agentCode, "Smoke Screenwriter", "End-to-end smoke agent",
            "Read the episode, rewrite it, and save the completed screenplay.",
            modelId, new BigDecimal("0.200"), 2048, 5, "ENABLED",
            List.of(skillCode), List.of("read_episode_script", "save_episode_script")
        ), userId);
    }

    @Test
    void createsSkillRunsAgentSavesVersionAndPersistsCompleteAuditTrail() {
        when(invocation.invokeText(any()))
            .thenReturn(modelResult(null,
                List.of(new AiToolCall("read-1", "read_episode_script", "{}"))))
            .thenReturn(modelResult(null, List.of(new AiToolCall(
                "save-1", "save_episode_script",
                "{\"content\":" + quote(REWRITTEN_SCREENPLAY) + "}"
            ))))
            .thenReturn(modelResult("剧集改写并保存成功。", List.of()));

        WorkflowAgentRunResult result = runner.runFormal(new WorkflowAgentRunInput(
            agentCode, "改写当前剧集", tenantId, projectId, episodeId,
            scriptId, null, null, userId
        ));

        assertThat(result.output()).isEqualTo("剧集改写并保存成功。");
        assertThat(jdbc.queryForObject("""
            select content from script_episode where id = ?
            """, String.class, episodeId)).isEqualTo(REWRITTEN_SCREENPLAY);
        assertThat(jdbc.queryForObject("""
            select count(*) from script_episode_version
             where episode_id = ? and is_current = true
            """, Integer.class, episodeId)).isEqualTo(1);

        WorkflowAgentRunDetail audit = runRepository.detail(tenantId, result.runId());
        assertThat(audit.status()).isEqualTo("SUCCESS");
        assertThat(audit.agentCode()).isEqualTo(agentCode);
        assertThat(audit.skillSnapshots()).extracting(WorkflowAgentSkillSnapshot::code)
            .containsExactly(skillCode);
        assertThat(audit.skillSnapshots().get(0).content())
            .contains("This edited content must take effect without restarting the application.");
        assertThat(audit.toolCodes())
            .containsExactly("read_episode_script", "save_episode_script");
        assertThat(audit.steps()).extracting(WorkflowAgentRunStepView::stepType)
            .containsExactly("MODEL", "TOOL", "MODEL", "TOOL", "MODEL");
        assertThat(audit.steps()).extracting(WorkflowAgentRunStepView::status)
            .containsOnly("SUCCESS");
    }

    private AiInvocationResult<AiTextResponse> modelResult(
        String content,
        List<AiToolCall> toolCalls
    ) {
        AiTextResponse response = new AiTextResponse(
            content, "smoke-provider", 1, 1, 2, 5L, Map.of(),
            toolCalls.isEmpty() ? "stop" : "tool_calls", false, toolCalls
        );
        return new AiInvocationResult<>(
            AiCapability.TEXT, "workflow_agent", response, content, null,
            "smoke-provider", modelId, null, "Smoke Provider", 1, 1, 2, 5L,
            "SUCCESS", null, null
        );
    }

    private String quote(String value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
