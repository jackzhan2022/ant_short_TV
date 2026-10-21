package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class StoryboardBatchServiceTest {

    @Test
    void mapsExecutionOutcomesWithoutTreatingWarningsAsFailures() {
        assertThat(StoryboardBatchService.itemStatus("PENDING", 0)).isEqualTo("PENDING");
        assertThat(StoryboardBatchService.itemStatus("RUNNING", 0)).isEqualTo("RUNNING");
        assertThat(StoryboardBatchService.itemStatus("SUCCEEDED", 0)).isEqualTo("SUCCESS");
        assertThat(StoryboardBatchService.itemStatus("SUCCEEDED", 2))
            .isEqualTo("SUCCESS_WITH_WARNING");
        assertThat(StoryboardBatchService.itemStatus("FAILED", 0)).isEqualTo("FAILED");
        assertThat(StoryboardBatchService.itemStatus("CANCELED", 0)).isEqualTo("FAILED");
        assertThat(StoryboardBatchService.itemStatus("TIMED_OUT", 0)).isEqualTo("FAILED");
    }

    @Test
    void reportsMixedTerminalBatchSeparatelyFromCompleteSuccess() {
        assertThat(StoryboardBatchService.batchStatus(3, 0, 0, 1, 1, 1))
            .isEqualTo("COMPLETED_WITH_FAILURES");
        assertThat(StoryboardBatchService.batchStatus(2, 0, 0, 1, 1, 0))
            .isEqualTo("SUCCEEDED_WITH_WARNING");
        assertThat(StoryboardBatchService.batchStatus(2, 0, 0, 2, 0, 0))
            .isEqualTo("SUCCEEDED");
        assertThat(StoryboardBatchService.batchStatus(2, 1, 1, 0, 0, 0))
            .isEqualTo("RUNNING");
    }

    @Test
    void mysqlProjectsOnlySummariesAndAggregatesGuardedArrayWarningsWithoutJvmJsonParsing() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet items = mock(ResultSet.class);
        ResultSet warnings = mock(ResultSet.class);
        ObjectMapper json = mock(ObjectMapper.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("MySQL");
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(items, warnings);
        when(items.next()).thenReturn(true, true, false);
        when(items.getObject("found_execution_id")).thenReturn(2001L, 2002L);
        when(items.getLong("id")).thenReturn(1L, 2L);
        when(items.getLong("episode_id")).thenReturn(1001L, 1002L);
        when(items.getLong("execution_id")).thenReturn(2001L, 2002L);
        when(items.getString("status")).thenReturn("SUCCEEDED", "RUNNING");
        when(warnings.next()).thenReturn(true, true, false);
        when(warnings.getLong("id")).thenReturn(1L, 2L);
        when(warnings.getInt("warning_count")).thenReturn(7);
        StoryboardBatchEntity batch = new StoryboardBatchEntity();
        batch.id = 90L;
        batch.tenantId = 1L;
        batch.projectId = 2L;
        var result = new StoryboardBatchQuery(new JdbcTemplate(source), json).selectItems(batch);
        assertThat(result).hasSize(2).first().satisfies(item -> {
            assertThat(item.warningCount()).isEqualTo(7);
            assertThat(item.status()).isEqualTo("SUCCESS_WITH_WARNING");
            assertThat(item.businessCallCount()).isZero();
            assertThat(item.technicalRetryCount()).isZero();
            assertThat(item.settledPoints()).isEqualByComparingTo(BigDecimal.ZERO);
        });
        // The second execution may have completed between summary and warning queries.
        assertThat(result.get(1).status()).isEqualTo("RUNNING");
        assertThat(result.get(1).warningCount()).isZero();
        var sql = ArgumentCaptor.forClass(String.class);
        verify(connection, times(2)).prepareStatement(sql.capture());
        assertThat(sql.getAllValues().get(0)).doesNotContain("select *", "redacted_input_json", "provider_cost_summary_json")
            .contains("left join ai_execution_task", "order by item.episode_no, item.id");
        assertThat(sql.getAllValues().get(1)).contains("sum(", "json_valid(storyboard.shot_plan_json)",
            "json_type(json_extract(if(", "= 'ARRAY'", "json_length(if(", "'{}'", "$.warnings",
            "$.diagnostics.classificationWarnings", "group by item.id", "and exists (",
            "call_log.execution_id = item.execution_id", "step.run_id = storyboard.generated_by_run_id",
            "item.tenant_id = ? and item.project_id = ? and item.batch_id = ?")
            .doesNotContain("select item.id, storyboard.shot_plan_json");
        verifyNoInteractions(json);
        verify(warnings, never()).getString("shot_plan_json");
    }
}
