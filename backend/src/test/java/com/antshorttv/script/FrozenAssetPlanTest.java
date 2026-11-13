package com.antshorttv.script;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.antshorttv.workflowagent.agent.WorkflowAgentRecord;
import com.antshorttv.workflowagent.run.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class FrozenAssetPlanTest {
    @Test void restoresOriginalContentAndTimestampsAfterARevisionChange() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:frozen-assets;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("create table script_analysis_fanout_snapshot(id bigint primary key, asset_plan_json clob)");
        jdbc.update("insert into script_analysis_fanout_snapshot values(1,null)");
        var store = new JdbcEpisodeFanoutStore(jdbc, mock(WorkflowAgentRunRepository.class));
        var original = plan("original", 1L);
        assertThat(store.freezePlan(1L, original)).isEqualTo(original);
        assertThat(store.freezePlan(1L, plan("edited after start", 2L))).isEqualTo(original);
    }

    private WorkflowAgentExecutionPlan plan(String content, Long revision) {
        return new WorkflowAgentExecutionPlan(new WorkflowAgentRecord(1L,"short-drama-asset-recognition","资产",null,"rules",9L,
            BigDecimal.ZERO,32768,20,"ENABLED",revision,7L,7L,LocalDateTime.of(2026,11,13,10,0),null,List.of(),List.of()),
            List.of(new WorkflowAgentSkillSnapshot("strategy","方案",revision.toString(),content)),7L);
    }
}
