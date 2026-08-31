package com.antshorttv.workflowagent.agent;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;

abstract class AbstractAnalysisAgentBootstrap implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractAnalysisAgentBootstrap.class);

    private final WorkflowAgentRepository repository;
    private final WorkflowAgentService service;
    private final JdbcTemplate jdbc;
    private final boolean enabled;

    AbstractAnalysisAgentBootstrap(
        WorkflowAgentRepository repository,
        WorkflowAgentService service,
        JdbcTemplate jdbc,
        boolean enabled
    ) {
        this.repository = repository;
        this.service = service;
        this.jdbc = jdbc;
        this.enabled = enabled;
    }

    protected abstract String agentCode();

    protected abstract WorkflowAgentCommand definition(Long modelId);

    @Override
    public final void run(ApplicationArguments arguments) {
        if (!enabled || alreadyExists()) {
            return;
        }
        Long modelId = findCompatibleModel();
        if (modelId == null) {
            LOG.warn("Skipped {} bootstrap: no enabled text tool-calling model is available.", agentCode());
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
        }
    }

    private boolean alreadyExists() {
        try {
            repository.get(agentCode());
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
}
