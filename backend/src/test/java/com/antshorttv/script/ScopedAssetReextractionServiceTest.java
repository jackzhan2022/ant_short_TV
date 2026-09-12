package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.antshorttv.workflowagent.run.WorkflowAgentRunner;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ScopedAssetReextractionServiceTest {
    @Test
    void preflightCountsOnlyTheRequestedAssetScope() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), eq(Integer.class), any(Object[].class)))
            .thenReturn(2, 3, 5, 7);
        ScopedAssetReextractionService service = new ScopedAssetReextractionService(
            jdbc, mock(WorkflowAgentRunner.class), mock(AssetRecognitionAgentAdapter.class));

        var result = service.preflight(1L, 2L, 3L, AssetRecognitionScope.SCENE);

        assertThat(result.targetType()).isEqualTo("SCENE");
        assertThat(result.existingAssets()).isEqualTo(2);
        assertThat(result.existingVariants()).isEqualTo(3);
        assertThat(result.existingPrompts()).isEqualTo(12);
        assertThat(result.requiresConfirmation()).isTrue();
    }
}
