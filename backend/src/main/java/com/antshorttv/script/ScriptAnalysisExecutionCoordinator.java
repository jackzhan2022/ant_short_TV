package com.antshorttv.script;

import com.antshorttv.accounting.AiUsageMetric;
import com.antshorttv.ai.ProjectAiConfigService;
import com.antshorttv.common.BusinessException;
import com.antshorttv.execution.AiExecutionCreateCommand;
import com.antshorttv.execution.AiExecutionResponse;
import com.antshorttv.execution.AiExecutionResponseMapper;
import com.antshorttv.execution.AiExecutionService;
import com.antshorttv.execution.AiExecutionTaskEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScriptAnalysisExecutionCoordinator {
    static final String SCENE = "script_analysis";

    private final ScriptAnalysisTaskMapper taskMapper;
    private final ScriptVersionMapper versionMapper;
    private final ProjectAiConfigService projectAiConfigService;
    private final AiExecutionService executionService;
    private final AiExecutionResponseMapper responseMapper;
    @org.springframework.beans.factory.annotation.Autowired
    private com.antshorttv.workflowagent.agent.WorkflowAgentModelLookup workflowAgentModelLookup;
    @org.springframework.beans.factory.annotation.Value("${ai.workflow-agent.global-understanding-enabled:false}")
    private boolean globalUnderstandingAgentEnabled;

    public ScriptAnalysisExecutionCoordinator(
        ScriptAnalysisTaskMapper taskMapper,
        ScriptVersionMapper versionMapper,
        ProjectAiConfigService projectAiConfigService,
        AiExecutionService executionService,
        AiExecutionResponseMapper responseMapper
    ) {
        this.taskMapper = taskMapper;
        this.versionMapper = versionMapper;
        this.projectAiConfigService = projectAiConfigService;
        this.executionService = executionService;
        this.responseMapper = responseMapper;
    }

    @Scheduled(fixedDelayString = "${ai.script-analysis.scheduler.fixed-delay-ms:8000}")
    public void scheduleAutomaticAnalysis() {
        for (ScriptAnalysisTaskEntity task : taskMapper.selectRunnableWithoutExecution()) {
            try {
                createExecution(task);
            } catch (BusinessException exception) {
                task.setCurrentAction("积分不足，充值后将自动继续分析");
                task.setErrorCode(exception.getErrorCode().name());
                task.setErrorMessage(exception.getMessage());
                task.setUpdatedAt(LocalDateTime.now());
                taskMapper.updateById(task);
            } catch (RuntimeException exception) {
                task.setCurrentAction("等待分析执行条件就绪");
                task.setUpdatedAt(LocalDateTime.now());
                taskMapper.updateById(task);
            }
        }
    }

    @Transactional
    public AiExecutionResponse submitManual(ScriptAnalysisTaskEntity task) {
        return responseMapper.toResponse(createExecution(task));
    }

    @Transactional
    public AiExecutionResponse retry(ScriptAnalysisTaskEntity task) {
        if (task.getExecutionId() == null) {
            return responseMapper.toResponse(createExecution(task));
        }
        AiExecutionTaskEntity execution = executionService.requireTask(task.getExecutionId());
        if ("FAILED".equals(execution.status) || "TIMED_OUT".equals(execution.status)) {
            execution = executionService.retry(execution.id);
        }
        return responseMapper.toResponse(execution);
    }

    @Transactional
    public AiExecutionTaskEntity createExecution(ScriptAnalysisTaskEntity task) {
        ScriptAnalysisTaskEntity current = taskMapper.selectById(task.getId());
        if (current.getExecutionId() != null) {
            return executionService.requireTask(current.getExecutionId());
        }
        ScriptVersionEntity version = versionMapper.selectById(current.getScriptVersionId());
        if (version == null || version.getContent() == null || version.getContent().isBlank()) {
            throw new IllegalStateException("Script analysis version is unavailable.");
        }
        Long modelId = projectAiConfigService.resolveModelId(current.getTenantId(), current.getProjectId(), "TEXT");
        if (globalUnderstandingAgentEnabled) {
            workflowAgentModelLookup.requireEnabledTextModel(modelId);
        }
        int maximumCalls = maximumCallCount(version.getContent());
        AiExecutionTaskEntity execution = executionService.createWithReservation(
            new AiExecutionCreateCommand(
                current.getTenantId(), current.getCreatedBy(), current.getProjectId(), SCENE, "TEXT",
                "SCRIPT_ANALYSIS_TASK", current.getId(), modelId, "ANALYSIS",
                current.getIdempotencyKey(), "script-analysis-" + current.getId(), true,
                "{\"scriptVersionId\":" + current.getScriptVersionId() + "}"
            ),
            Map.of(AiUsageMetric.CALL, BigDecimal.valueOf(maximumCalls)),
            Map.of()
        );
        current.setExecutionId(execution.id);
        current.setErrorCode(null);
        current.setErrorMessage(null);
        current.setCurrentAction("分析任务已排队");
        current.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(current);
        return execution;
    }

    int maximumCallCount(String content) {
        // The Agent path uses two model rounds (read, then terminal save), one more
        // than the legacy global-understanding stage.
        return globalUnderstandingAgentEnabled ? 5 : 4;
    }
}
