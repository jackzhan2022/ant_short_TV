package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.antshorttv.ai.*;
import com.antshorttv.execution.*;
import com.antshorttv.project.ProjectMapper;
import com.antshorttv.style.AssetArtStyleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ArtStylePromptBackfillTest {
    @Test void v2AllSeparatesAssetRulesFromStoryboardAndUsesDistinctInvocationKeys() throws Exception {
        var service = mock(ScriptWorkflowService.class, CALLS_REAL_METHODS);
        var jdbc = mock(JdbcTemplate.class);
        var json = new ObjectMapper();
        var styles = new AssetArtStyleService(mock(ProjectMapper.class), json);
        var ai = mock(AiInvocationService.class);
        var attempts = mock(AiExecutionAttemptMapper.class);
        var tasks = mock(AiExecutionTaskMapper.class);
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(service, "objectMapper", json);
        ReflectionTestUtils.setField(service, "artStyles", styles);
        ReflectionTestUtils.setField(service, "aiInvocationService", ai);
        ReflectionTestUtils.setField(service, "executionAttemptMapper", attempts);
        ReflectionTestUtils.setField(service, "executionTaskMapper", tasks);
        ReflectionTestUtils.setField(service, "scriptAiOperationService", mock(ScriptAiOperationService.class));
        ReflectionTestUtils.setField(service, "transactionTemplate", new TransactionTemplate(mock(PlatformTransactionManager.class)));
        when(jdbc.queryForList(anyString(), eq(7L), eq(8L))).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.contains("from character_asset")) return List.of(Map.of("id", 101L, "name", "厨师"));
            if (sql.contains("from storyboard")) return List.of(Map.of("id", 201L, "visualDescription", "厨房对话"));
            return List.of();
        });
        var task = new AiExecutionTaskEntity();
        task.id = 10L; task.businessId = 20L; task.tenantId = 7L; task.projectId = 8L;
        task.status = "RUNNING"; task.claimToken = "claim";
        when(tasks.selectById(10L)).thenReturn(task);
        var attempt = new AiExecutionAttemptEntity(); attempt.idempotencyKey = "test-call";
        when(attempts.selectById(11L)).thenReturn(attempt);
        @SuppressWarnings("unchecked") AiInvocationResult<AiTextResponse> result = mock(AiInvocationResult.class);
        when(result.content()).thenReturn("{}");
        when(ai.invokeText(any())).thenReturn(result);
        var operation = new ScriptAiOperationEntity();
        operation.id = 20L; operation.tenantId = 7L; operation.projectId = 8L;
        operation.artStyleSnapshot = json.writeValueAsString(new AssetArtStyleService.Snapshot("V2", "r1", styles.catalog().get(0)));
        service.executePromptOperation(operation, new GeneratePromptRequest("ALL", null),
            new AiExecutionContext(task, new AiExecutionClaim(10L, 11L, "claim", 1, "SUBMIT")));
        var requests = ArgumentCaptor.forClass(AiInvocationRequest.class);
        verify(ai, times(2)).invokeText(requests.capture());
        var asset = requests.getAllValues().get(0);
        var storyboard = requests.getAllValues().get(1);
        assertThat(asset.textRequest().userPrompt()).contains("厨师", "资产画风 2.0").doesNotContain("厨房对话");
        assertThat(storyboard.textRequest().userPrompt()).contains("厨房对话").doesNotContain("资产画风", "厨师", "赛璐璐");
        assertThat(asset.idempotencyKey()).isNotEqualTo(storyboard.idempotencyKey());
        assertThat(operation.resultType).isEqualTo("SCRIPT_PROMPTS");
    }
}
