package com.antshorttv.aiimage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AiImageTaskMapperTest {
    @Autowired private AiImageTaskMapper mapper;

    @Test
    void runningFilterIncludesInternalProcessingPhasesWithinProjectAndTaskType() {
        AiImageTaskEntity running = insert(11L, 22L, "CHARACTER", "RUNNING");
        AiImageTaskEntity settling = insert(11L, 22L, "CHARACTER", "SETTLING");
        AiImageTaskEntity rendering = insert(11L, 22L, "CHARACTER", "RENDERING");
        insert(11L, 22L, "CHARACTER", "SUCCESS");
        insert(11L, 22L, "CHARACTER", "PENDING");
        insert(12L, 22L, "CHARACTER", "RENDERING");
        insert(11L, 23L, "CHARACTER", "SETTLING");
        insert(11L, 22L, "SCENE", "RENDERING");
        AiImageTaskEntity deleted = insert(11L, 22L, "CHARACTER", "RENDERING");
        deleted.setDeletedAt(LocalDateTime.now());
        mapper.updateById(deleted);

        assertThat(mapper.selectByProject(11L, 22L, "CHARACTER", "RUNNING"))
            .extracting(AiImageTaskEntity::getId)
            .containsExactlyInAnyOrder(running.getId(), settling.getId(), rendering.getId());
    }

    @Test
    void terminalFilterStillMatchesOnlyRequestedStatus() {
        insert(11L, 22L, "CHARACTER", "RENDERING");
        insert(11L, 22L, "CHARACTER", "FAILED");
        AiImageTaskEntity success = insert(11L, 22L, "CHARACTER", "SUCCESS");

        assertThat(mapper.selectByProject(11L, 22L, null, "SUCCESS"))
            .extracting(AiImageTaskEntity::getId).containsExactly(success.getId());
    }

    private AiImageTaskEntity insert(Long tenantId, Long projectId, String taskType, String status) {
        AiImageTaskEntity task = new AiImageTaskEntity();
        task.setTenantId(tenantId);
        task.setProjectId(projectId);
        task.setTaskType(taskType);
        task.setTargetType("CHARACTER");
        task.setTargetId(77L);
        task.setProviderCode("mock");
        task.setModel("image-test");
        task.setPrompt("portrait");
        task.setAspectRatio("1:1");
        task.setImageCount(1);
        task.setStatus(status);
        task.setCreatedBy(66L);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(task.getCreatedAt());
        mapper.insert(task);
        return task;
    }
}
