package com.antshorttv.aiimage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AiImageResponsesTest {

    @ParameterizedTest
    @ValueSource(strings = {"SETTLING", "RENDERING"})
    void exposesInternalProcessingPhasesAsRunningWithoutMutatingTask(String phase) {
        AiImageTaskEntity task = new AiImageTaskEntity();
        task.setStatus(phase);

        AiImageTaskResponse response = AiImageTaskResponse.from(task, List.of());

        assertThat(response.status()).isEqualTo("RUNNING");
        assertThat(task.getStatus()).isEqualTo(phase);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "RUNNING", "SUCCESS", "FAILED", "CANCELED"})
    void preservesPublicStatuses(String status) {
        AiImageTaskEntity task = new AiImageTaskEntity();
        task.setStatus(status);

        assertThat(AiImageTaskResponse.from(task, List.of()).status()).isEqualTo(status);
    }
}
