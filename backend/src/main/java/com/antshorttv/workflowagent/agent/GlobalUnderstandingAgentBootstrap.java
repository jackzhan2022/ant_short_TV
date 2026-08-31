package com.antshorttv.workflowagent.agent;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GlobalUnderstandingAgentBootstrap implements ApplicationRunner {
    public static final String AGENT_CODE = "short-drama-global-understanding";
    private static final Logger LOG = LoggerFactory.getLogger(GlobalUnderstandingAgentBootstrap.class);

    private final WorkflowAgentRepository repository;
    private final WorkflowAgentService service;
    private final JdbcTemplate jdbc;

    public GlobalUnderstandingAgentBootstrap(
        WorkflowAgentRepository repository,
        WorkflowAgentService service,
        JdbcTemplate jdbc
    ) {
        this.repository = repository;
        this.service = service;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (alreadyExists()) {
            return;
        }
        Long modelId = findCompatibleModel();
        if (modelId == null) {
            LOG.warn("Skipped {} bootstrap: no enabled text tool-calling model is available.", AGENT_CODE);
            return;
        }
        WorkflowAgentCommand command = definition(modelId);
        service.validate(command, true);
        try {
            repository.create(command, null);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() != ErrorCode.WORKFLOW_AGENT_CONFLICT || !alreadyExists()) {
                throw exception;
            }
            // Another application instance created the immutable seed first.
        }
    }

    private boolean alreadyExists() {
        try {
            repository.get(AGENT_CODE);
            return true;
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.WORKFLOW_AGENT_NOT_FOUND) {
                return false;
            }
            throw exception;
        }
    }

    private Long findCompatibleModel() {
        List<Long> ids = jdbc.queryForList("""
            select model.id
              from ai_model model
              join ai_model_capability capability on capability.model_id = model.id
             where model.service_type = 'TEXT'
               and model.status = 'ENABLED'
               and capability.capability = 'TOOL_CALLING'
               and capability.status = 'ENABLED'
             order by model.is_default desc, model.sort asc, model.id asc
             limit 1
            """, Long.class);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private WorkflowAgentCommand definition(Long modelId) {
        return new WorkflowAgentCommand(
            AGENT_CODE,
            "剧情全局理解",
            "独立读取当前剧本并生成可编辑的正式剧情全局理解数据。",
            "严格按已加载 Skill 执行：先读取当前剧本，再分析，最后调用保存工具；保存成功前不得声称完成。",
            modelId,
            new BigDecimal("0.200"),
            16384,
            4,
            "ENABLED",
            List.of(
                "short-drama-analysis-foundation",
                "short-drama-global-understanding-framework"),
            List.of("read_current_script", "save_global_understanding")
        );
    }
}
