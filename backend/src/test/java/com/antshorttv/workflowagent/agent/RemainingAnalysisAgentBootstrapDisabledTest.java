package com.antshorttv.workflowagent.agent;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

class RemainingAnalysisAgentBootstrapDisabledTest {
    @Test
    void disabledMigrationFlagsDoNotInspectModelsOrCreatePartialDefinitions() throws Exception {
        WorkflowAgentRepository repository = mock(WorkflowAgentRepository.class);
        WorkflowAgentService service = mock(WorkflowAgentService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var arguments = new DefaultApplicationArguments(new String[0]);

        new EpisodeSplittingAgentBootstrap(repository, service, jdbc, false).run(arguments);
        new EpisodeSummaryAgentBootstrap(repository, service, jdbc, false).run(arguments);
        new AssetRecognitionAgentBootstrap(repository, service, jdbc, false).run(arguments);

        verifyNoInteractions(repository, service, jdbc);
    }
}
