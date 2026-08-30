package com.antshorttv.workflowagent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.common.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class WorkflowAgentRepositoryTest {
    @Autowired
    private WorkflowAgentRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createsAndUpdatesAgentWithOrderedSkillsToolsAndOptimisticLocking() {
        long modelId = model("repo-model");
        String code = "repo-agent-" + UUID.randomUUID();
        WorkflowAgentRecord created = repository.create(command(code, "First", modelId,
            List.of("foundation", "screenplay"), List.of("read_episode_script", "save_episode_script")), 41L);

        assertThat(created.code()).isEqualTo(code);
        assertThat(created.revision()).isZero();
        assertThat(created.skillCodes()).containsExactly("foundation", "screenplay");
        assertThat(created.toolCodes()).containsExactly("read_episode_script", "save_episode_script");
        assertThat(created.createdBy()).isEqualTo(41L);

        WorkflowAgentRecord updated = repository.update(code, created.revision(),
            command(code, "Second", modelId, List.of("screenplay", "foundation"),
                List.of("save_episode_script")), 42L);
        assertThat(updated.name()).isEqualTo("Second");
        assertThat(updated.code()).isEqualTo(code);
        assertThat(updated.revision()).isEqualTo(1L);
        assertThat(updated.skillCodes()).containsExactly("screenplay", "foundation");
        assertThat(updated.toolCodes()).containsExactly("save_episode_script");
        assertThat(updated.updatedBy()).isEqualTo(42L);

        assertThatThrownBy(() -> repository.update(code, 0L,
            command(code, "Stale", modelId, List.of(), List.of()), 43L))
            .isInstanceOf(BusinessException.class);
        assertThat(repository.get(code).name()).isEqualTo("Second");
    }

    @Test
    void uniqueCodeIsImmutableAndCopyGetsIndependentAssociations() {
        long modelId = model("copy-model");
        String source = "copy-source-" + UUID.randomUUID();
        String target = source + "-copy";
        repository.create(command(source, "Source", modelId, List.of("screenplay"),
            List.of("read_episode_script")), 51L);

        assertThatThrownBy(() -> repository.create(command(source, "Duplicate", modelId,
            List.of(), List.of()), 51L)).isInstanceOf(BusinessException.class);

        WorkflowAgentRecord copied = repository.copy(source, target, 52L);
        assertThat(copied.code()).isEqualTo(target);
        assertThat(copied.skillCodes()).containsExactly("screenplay");
        assertThat(copied.toolCodes()).containsExactly("read_episode_script");
        assertThat(copied.createdBy()).isEqualTo(52L);
    }

    @Test
    void associationFailureRollsBackScalarUpdateAndAllReplacementRows() {
        long modelId = model("rollback-model");
        String code = "rollback-agent-" + UUID.randomUUID();
        WorkflowAgentRecord created = repository.create(command(code, "Before", modelId,
            List.of("foundation"), List.of("read_episode_script")), 61L);

        assertThatThrownBy(() -> repository.update(code, created.revision(),
            command(code, "Must Roll Back", modelId,
                List.of("duplicate", "duplicate"), List.of("read_episode_script")), 62L))
            .isInstanceOf(DataIntegrityViolationException.class);

        WorkflowAgentRecord unchanged = repository.get(code);
        assertThat(unchanged.name()).isEqualTo("Before");
        assertThat(unchanged.revision()).isZero();
        assertThat(unchanged.skillCodes()).containsExactly("foundation");
    }

    private WorkflowAgentCommand command(
        String code,
        String name,
        long modelId,
        List<String> skillCodes,
        List<String> toolCodes
    ) {
        return new WorkflowAgentCommand(code, name, "description", "Use read_episode_script",
            modelId, new BigDecimal("0.700"), 4096, 10, "ENABLED", skillCodes, toolCodes);
    }

    private long model(String prefix) {
        Long providerId = jdbc.queryForObject("select min(id) from ai_provider", Long.class);
        String code = prefix + "-" + UUID.randomUUID();
        jdbc.update("""
            insert into ai_model
              (provider_id, code, name, model_code, service_type, status, is_default, sort, created_at, updated_at)
            values (?, ?, ?, ?, 'TEXT', 'ENABLED', false, 999, now(), now())
            """, providerId, code, code, code);
        return jdbc.queryForObject("select id from ai_model where code = ?", Long.class, code);
    }
}
