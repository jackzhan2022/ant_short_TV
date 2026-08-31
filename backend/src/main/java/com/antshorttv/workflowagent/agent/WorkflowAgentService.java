package com.antshorttv.workflowagent.agent;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.security.CurrentPrincipal;
import com.antshorttv.workflowagent.WorkflowAgentProperties;
import com.antshorttv.workflowagent.skill.WorkflowSkillService;
import com.antshorttv.workflowagent.tool.WorkflowToolRegistry;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class WorkflowAgentService {
    private static final Pattern CODE = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Set<String> STATUSES = Set.of("ENABLED", "DISABLED");
    private static final BigDecimal MIN_TEMPERATURE = BigDecimal.ZERO;
    private static final BigDecimal MAX_TEMPERATURE = new BigDecimal("2.000");

    private final WorkflowAgentRepository repository;
    private final WorkflowAgentModelLookup models;
    private final WorkflowSkillService skills;
    private final WorkflowToolRegistry tools;
    private final WorkflowAgentBusinessReferenceLookup references;
    private final CurrentPrincipal principal;
    private final WorkflowAgentProperties properties;

    public WorkflowAgentService(
        WorkflowAgentRepository repository,
        WorkflowAgentModelLookup models,
        WorkflowSkillService skills,
        WorkflowToolRegistry tools,
        WorkflowAgentBusinessReferenceLookup references,
        CurrentPrincipal principal,
        WorkflowAgentProperties properties
    ) {
        this.repository = repository;
        this.models = models;
        this.skills = skills;
        this.tools = tools;
        this.references = references;
        this.principal = principal;
        this.properties = properties;
    }

    public List<WorkflowAgentRecord> list(String query) {
        return repository.list(query);
    }

    public WorkflowAgentRecord detail(String code) {
        return repository.get(code);
    }

    public WorkflowAgentModel requireToolCallingModel(Long modelId) {
        return models.requireEnabledTextModel(modelId);
    }

    public WorkflowAgentRecord create(WorkflowAgentCommand command) {
        validate(command, true);
        return repository.create(normalize(command), principal.require().userId());
    }

    public WorkflowAgentRecord update(String code, Long expectedRevision, WorkflowAgentCommand command) {
        validate(command, false);
        return repository.update(code, expectedRevision, normalize(command), principal.require().userId());
    }

    public WorkflowAgentRecord copy(String sourceCode, String targetCode) {
        validateCode(targetCode);
        return repository.copy(sourceCode, targetCode, principal.require().userId());
    }

    public WorkflowAgentRecord enable(String code) {
        WorkflowAgentRecord current = repository.get(code);
        validate(toCommand(current), false);
        return repository.setStatus(code, "ENABLED", principal.require().userId());
    }

    public WorkflowAgentRecord disable(String code) {
        return repository.setStatus(code, "DISABLED", principal.require().userId());
    }

    public void delete(String code) {
        List<String> businessReferences = references.findReferences(code);
        if (!businessReferences.isEmpty()) {
            throw new BusinessException(ErrorCode.WORKFLOW_AGENT_IN_USE,
                "Agent 正被业务配置引用：" + String.join(", ", businessReferences));
        }
        principal.require();
        repository.delete(code);
    }

    public WorkflowAgentRecord loadForRun(String code) {
        WorkflowAgentRecord current = repository.get(code);
        if (!"ENABLED".equals(current.status())) {
            throw new BusinessException(ErrorCode.WORKFLOW_AGENT_DISABLED, "Agent 未启用。");
        }
        validate(toCommand(current), false);
        return current;
    }

    public void validate(WorkflowAgentCommand command, boolean requireCode) {
        if (command == null) {
            throw invalid("Agent 配置不能为空。");
        }
        if (requireCode) {
            validateCode(command.code());
        }
        if (blank(command.name()) || blank(command.systemPrompt())) {
            throw invalid("Agent 名称和系统提示词不能为空。");
        }
        if (requireCode && command.code().length() > 128) {
            throw invalid("Agent code 不能超过 128 个字符。");
        }
        if (command.name().length() > 200
            || (command.description() != null && command.description().length() > 1000)) {
            throw invalid("Agent 名称或描述超出允许长度。");
        }
        if (command.temperature() == null
            || command.temperature().compareTo(MIN_TEMPERATURE) < 0
            || command.temperature().compareTo(MAX_TEMPERATURE) > 0) {
            throw invalid("temperature 必须在 0 到 2 之间。");
        }
        if (command.maxTokens() == null || command.maxTokens() < 1 || command.maxTokens() > 1_000_000) {
            throw invalid("maxTokens 超出允许范围。");
        }
        if (command.maxSteps() == null || command.maxSteps() < 1
            || command.maxSteps() > properties.getMaxSteps()) {
            throw invalid("maxSteps 超出允许范围。");
        }
        if (!STATUSES.contains(command.status())) {
            throw invalid("Agent 状态不正确。");
        }
        models.requireEnabledTextModel(command.modelId());
        List<String> skillCodes = safeList(command.skillCodes());
        List<String> toolCodes = safeList(command.toolCodes());
        requireUnique(skillCodes, "Skill");
        requireUnique(toolCodes, "工具");
        for (String skillCode : skillCodes) {
            skills.detail(skillCode);
        }
        for (String toolCode : toolCodes) {
            if (!tools.contains(toolCode)) {
                throw invalid("工具不存在：" + toolCode);
            }
        }
    }

    private WorkflowAgentCommand normalize(WorkflowAgentCommand command) {
        return new WorkflowAgentCommand(command.code(), command.name().trim(),
            command.description() == null ? null : command.description().trim(),
            command.systemPrompt(), command.modelId(), command.temperature(), command.maxTokens(),
            command.maxSteps(), command.status(), List.copyOf(safeList(command.skillCodes())),
            List.copyOf(safeList(command.toolCodes())));
    }

    private WorkflowAgentCommand toCommand(WorkflowAgentRecord record) {
        return new WorkflowAgentCommand(record.code(), record.name(), record.description(),
            record.systemPrompt(), record.modelId(), record.temperature(), record.maxTokens(),
            record.maxSteps(), record.status(), record.skillCodes(), record.toolCodes());
    }

    private void validateCode(String code) {
        if (blank(code) || code.length() > 128 || !CODE.matcher(code).matches()) {
            throw invalid("Agent code 必须使用小写 kebab-case。");
        }
    }

    private void requireUnique(List<String> codes, String label) {
        if (new HashSet<>(codes).size() != codes.size()) {
            throw invalid(label + "不能重复关联。");
        }
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}
