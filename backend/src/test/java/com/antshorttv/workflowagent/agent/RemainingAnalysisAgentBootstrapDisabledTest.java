package com.antshorttv.workflowagent.agent;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

class RemainingAnalysisAgentBootstrapDisabledTest {
    @Test
    void initializesCurrentDefinitionsWithoutMigrationOptIn() throws Exception {
        WorkflowAgentRepository repository = mock(WorkflowAgentRepository.class);
        WorkflowAgentService service = mock(WorkflowAgentService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var arguments = new DefaultApplicationArguments(new String[0]);

        new EpisodeSplittingAgentBootstrap(repository, service, jdbc).run(arguments);
        new EpisodeSummaryAgentBootstrap(repository, service, jdbc).run(arguments);
        new AssetRecognitionAgentBootstrap(repository, service, jdbc).run(arguments);

        verify(repository).get(EpisodeSplittingAgentBootstrap.AGENT_CODE);
        verify(repository).get(EpisodeSummaryAgentBootstrap.AGENT_CODE);
        verify(repository).get(AssetRecognitionAgentBootstrap.AGENT_CODE);
    }
}
