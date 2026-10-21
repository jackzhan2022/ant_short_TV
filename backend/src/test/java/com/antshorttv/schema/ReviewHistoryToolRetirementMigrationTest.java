package com.antshorttv.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.antshorttv.common.BusinessException;
import com.antshorttv.review.ReviewAgentExecutionPlanFactory;
import com.antshorttv.review.ReviewDimension;
import com.antshorttv.security.CurrentPrincipal;
import com.antshorttv.workflowagent.WorkflowAgentProperties;
import com.antshorttv.workflowagent.agent.WorkflowAgentBusinessReferenceLookup;
import com.antshorttv.workflowagent.agent.WorkflowAgentCommand;
import com.antshorttv.workflowagent.agent.WorkflowAgentModelLookup;
import com.antshorttv.workflowagent.agent.WorkflowAgentRepository;
import com.antshorttv.workflowagent.agent.WorkflowAgentService;
import com.antshorttv.workflowagent.skill.FileSkillRepository;
import com.antshorttv.workflowagent.skill.SkillDocumentParser;
import com.antshorttv.workflowagent.skill.SkillReferenceLookup;
import com.antshorttv.workflowagent.skill.WorkflowSkillService;
import com.antshorttv.workflowagent.tool.WorkflowToolRegistry;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ReviewHistoryToolRetirementMigrationTest {
    @Test
    void upgradesLegacyReviewGrantsAndRestoresPlanValidationWithoutOverwritingAgentConfiguration() {
        DataSource source = new DriverManagerDataSource(
            "jdbc:h2:mem:review_history_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        migrate(source, "112");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        WorkflowAgentRepository repository = new WorkflowAgentRepository(jdbc);
        List<String> skills = ReviewDimension.skillCodes(Arrays.asList(ReviewDimension.values()), true);
        List<String> legacySkills = new ArrayList<>(skills);
        legacySkills.add("script-review-semantic-quality");
        repository.create(new WorkflowAgentCommand("script-review", "Administrator review", "Custom description",
            "Administrator prompt", 1L, new BigDecimal("0.300"), 12000, 18, "ENABLED", legacySkills,
            List.of("read_review_context", "read_review_content", "read_review_issue_history",
                "save_review_unit_result", "read_review_unit_results", "read_review_candidates",
                "save_review_semantic_decisions", "save_review_result")), null);
        repository.create(new WorkflowAgentCommand("custom-review", "Custom review", "Custom description",
            "Custom prompt", 1L, BigDecimal.ZERO, 6000, 10, "DISABLED", skills,
            List.of("read_review_context", "read_review_issue_history", "search_script_assets", "unknown_tool")), null);
        migrate(source, "145");
        var agentBefore = jdbc.queryForMap("select * from ai_workflow_agent where code='script-review'");
        var customBefore = jdbc.queryForMap("select * from ai_workflow_agent where code='custom-review'");
        var skillsBefore = jdbc.queryForList("select * from ai_workflow_agent_skill order by agent_id, load_order");

        WorkflowToolRegistry tools = mock(WorkflowToolRegistry.class);
        when(tools.contains("read_review_context")).thenReturn(true);
        when(tools.contains("read_review_content")).thenReturn(true);
        WorkflowSkillService skillService = new WorkflowSkillService(
            new FileSkillRepository(Path.of("skills"), 1024 * 1024, new SkillDocumentParser()),
            mock(SkillReferenceLookup.class));
        WorkflowAgentService service = new WorkflowAgentService(repository, mock(WorkflowAgentModelLookup.class),
            skillService, tools, mock(WorkflowAgentBusinessReferenceLookup.class), mock(CurrentPrincipal.class),
            new WorkflowAgentProperties());
        assertThatThrownBy(() -> service.loadForRun("script-review"))
            .isInstanceOf(BusinessException.class).hasMessageContaining("read_review_issue_history");

        migrate(source, null);

        assertThat(service.loadForRun("script-review").toolCodes())
            .containsExactly("read_review_content", "read_review_context");
        ReviewAgentExecutionPlanFactory plans = new ReviewAgentExecutionPlanFactory(service, skillService);
        List<String> dimensions = Arrays.stream(ReviewDimension.values()).map(ReviewDimension::label).toList();
        for (String phase : List.of("MARKDOWN_QUICK", "MARKDOWN_DEEP_CHILD")) {
            assertThat(plans.freeze(dimensions, phase).agent().toolCodes())
                .containsExactly("read_review_context", "read_review_content");
        }
        assertThat(plans.freeze(dimensions, "MARKDOWN_DEEP_AGGREGATION").agent().toolCodes()).isEmpty();
        assertThat(repository.get("custom-review").toolCodes())
            .containsExactly("read_review_context", "search_script_assets", "unknown_tool");
        assertThat(jdbc.queryForMap("select * from ai_workflow_agent where code='script-review'"))
            .isEqualTo(agentBefore);
        assertThat(jdbc.queryForMap("select * from ai_workflow_agent where code='custom-review'"))
            .isEqualTo(customBefore);
        assertThat(jdbc.queryForList("select * from ai_workflow_agent_skill order by agent_id, load_order"))
            .isEqualTo(skillsBefore);
        assertThat(jdbc.queryForObject(
            "select count(*) from ai_workflow_agent_tool where tool_code='read_review_issue_history'", Integer.class))
            .isZero();
    }

    private void migrate(DataSource source, String target) {
        var configuration = Flyway.configure().dataSource(source).locations("classpath:db/migration");
        if (target != null) configuration.target(MigrationVersion.fromVersion(target));
        configuration.load().migrate();
    }
}
