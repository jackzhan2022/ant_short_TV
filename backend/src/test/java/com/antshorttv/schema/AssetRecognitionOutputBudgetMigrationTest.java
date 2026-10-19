package com.antshorttv.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class AssetRecognitionOutputBudgetMigrationTest {
    @ParameterizedTest
    @ValueSource(ints = {4096, 16384, 65536})
    void upgradesOnlyTheBuiltInLegacyBudgetAndKeepsHistoricalRuns(int originalBudget) {
        var source = new DriverManagerDataSource("jdbc:h2:mem:asset_budget_" + originalBudget
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("create table ai_workflow_agent(code varchar(128),max_tokens int,revision bigint,updated_at timestamp)");
        jdbc.execute("create table ai_workflow_agent_run(max_tokens int)");
        jdbc.update("insert into ai_workflow_agent values ('short-drama-asset-recognition',?,7,now()),('custom-agent',4096,2,now()),('short-drama-episode-summary',16384,3,now())", originalBudget);
        jdbc.update("insert into ai_workflow_agent_run values (4096)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V140__increase_asset_recognition_output_budget.sql")).execute(source);
        boolean changed = originalBudget == 4096 || originalBudget == 16384;
        assertThat(jdbc.queryForObject("select max_tokens from ai_workflow_agent where code='short-drama-asset-recognition'", Integer.class))
            .isEqualTo(changed ? 32768 : originalBudget);
        assertThat(jdbc.queryForObject("select revision from ai_workflow_agent where code='short-drama-asset-recognition'", Long.class))
            .isEqualTo(changed ? 8L : 7L);
        assertThat(jdbc.queryForObject("select max_tokens from ai_workflow_agent where code='custom-agent'", Integer.class)).isEqualTo(4096);
        assertThat(jdbc.queryForObject("select max_tokens from ai_workflow_agent where code='short-drama-episode-summary'", Integer.class)).isEqualTo(16384);
        assertThat(jdbc.queryForObject("select max_tokens from ai_workflow_agent_run", Integer.class)).isEqualTo(4096);
    }
}
