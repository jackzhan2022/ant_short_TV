package com.antshorttv.aiimage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.execution.AiExecutionClaim;
import com.antshorttv.execution.AiExecutionContext;
import com.antshorttv.execution.AiExecutionStatus;
import com.antshorttv.execution.AiExecutionTaskEntity;
import com.antshorttv.execution.AiExecutionTaskMapper;
import com.antshorttv.script.AssetVisualVariantService;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiImageRenditionPublicationServiceTest {

    @Test
    void activationCountMismatchPublishesNeitherPrefixNorDomainState() {
        AiImageTaskMapper tasks = mock(AiImageTaskMapper.class);
        AiImageResultMapper results = mock(AiImageResultMapper.class);
        AiExecutionTaskMapper executions = mock(AiExecutionTaskMapper.class);
        AssetVisualVariantService variants = mock(AssetVisualVariantService.class);
        AiImageRenditionPublicationService service = new AiImageRenditionPublicationService(
            tasks, results, executions, variants
        );
        AiImageTaskEntity task = task();
        AiExecutionContext context = context();
        List<AiImageResultEntity> expected = List.of(result(44L), result(45L));
        when(executions.lockActiveClaim(99L, "claim-1")).thenReturn(context.task());
        when(results.activateForPublication(33L, 99L)).thenReturn(1);

        assertThatThrownBy(() -> service.publish(context, task, expected))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("完整激活");

        assertThat(expected).extracting(AiImageResultEntity::getStatus)
            .containsExactly("PROCESSING", "PROCESSING");
        assertThat(task.getStatus()).isEqualTo("RENDERING");
        verify(variants, never()).generationSucceededIfClaimActive(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        );
        verify(tasks, never()).updateById(task);
    }

    private AiImageTaskEntity task() {
        AiImageTaskEntity task = new AiImageTaskEntity();
        task.setId(33L);
        task.setTenantId(11L);
        task.setProjectId(22L);
        task.setExecutionId(99L);
        task.setTargetType("VISUAL_VARIANT");
        task.setTargetId(77L);
        task.setImageCount(2);
        task.setStatus("RENDERING");
        return task;
    }

    private AiImageResultEntity result(Long id) {
        AiImageResultEntity result = new AiImageResultEntity();
        result.setId(id);
        result.setTaskId(33L);
        result.setExecutionId(99L);
        result.setImageUrl("/api/projects/22/ai-image-results/" + id + "/display");
        result.setStatus("PROCESSING");
        return result;
    }

    private AiExecutionContext context() {
        AiExecutionTaskEntity execution = new AiExecutionTaskEntity();
        execution.id = 99L;
        execution.status = AiExecutionStatus.RUNNING.name();
        execution.claimToken = "claim-1";
        return new AiExecutionContext(
            execution, new AiExecutionClaim(99L, 100L, "claim-1", 1, "SUBMIT")
        );
    }
}
