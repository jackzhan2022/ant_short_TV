package com.antshorttv.inspiration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class InspirationManagementServiceTest {
    @Autowired private InspirationManagementService service;
    @Autowired private InspirationCreationMapper mapper;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mapper.delete(null);
    }

    @Test
    void filtersEditsPublishesReordersAndSoftDeletes() throws Exception {
        InspirationCreationEntity image = insert("都市逆袭", "IMAGE", "PUBLISHED", 10);
        InspirationCreationEntity video = insert("古装重生", "VIDEO", "UNPUBLISHED", 20);

        InspirationManagementPageResponse filtered = service.list(
            1, 20, "重生", "UNPUBLISHED", "VIDEO"
        );
        assertThat(filtered.total()).isEqualTo(1);
        assertThat(filtered.records()).extracting(InspirationManagementItemResponse::id)
            .containsExactly(video.getId());

        service.update(video.getId(), new InspirationMetadataRequest(
            "古装复仇", List.of("古装", "复仇"), "完整提示词"
        ));
        service.updatePublishStatus(video.getId(), new InspirationPublishStatusRequest("PUBLISHED"));
        InspirationCreationEntity updated = mapper.selectById(video.getId());
        assertThat(updated.getTitle()).isEqualTo("古装复仇");
        assertThat(updated.getPromptText()).isEqualTo("完整提示词");
        assertThat(objectMapper.readTree(updated.getTagsJson()).get(1).asText()).isEqualTo("复仇");
        assertThat(updated.getPublishStatus()).isEqualTo("PUBLISHED");

        service.reorder(new InspirationReorderRequest(List.of(video.getId(), image.getId())));
        assertThat(mapper.selectById(video.getId()).getSortOrder()).isLessThan(
            mapper.selectById(image.getId()).getSortOrder()
        );

        service.delete(video.getId());
        assertThat(mapper.selectById(video.getId()).getDeletedAt()).isNotNull();
        assertThat(service.list(1, 20, null, null, null).records())
            .extracting(InspirationManagementItemResponse::id)
            .containsExactly(image.getId());
    }

    @Test
    void invalidReorderLeavesPreviousOrderUntouched() {
        InspirationCreationEntity first = insert("A", "IMAGE", "PUBLISHED", 10);
        InspirationCreationEntity second = insert("B", "VIDEO", "PUBLISHED", 20);

        assertThatThrownBy(() -> service.reorder(
            new InspirationReorderRequest(List.of(first.getId(), first.getId()))
        )).isInstanceOf(BusinessException.class);

        assertThat(mapper.selectById(first.getId()).getSortOrder()).isEqualTo(10);
        assertThat(mapper.selectById(second.getId()).getSortOrder()).isEqualTo(20);
    }

    private InspirationCreationEntity insert(String title, String type, String publishStatus, int sortOrder) {
        InspirationCreationEntity entity = new InspirationCreationEntity();
        entity.setExternalId("manual-" + title);
        entity.setCreationType(type);
        entity.setTaskType("MANUAL_UPLOAD");
        entity.setTitle(title);
        entity.setAuthorName("管理员");
        entity.setUrl("/api/inspiration-creations/0/file");
        entity.setStoragePath("inspiration/creations/manual/original.jpg");
        entity.setMimeType("IMAGE".equals(type) ? "image/jpeg" : "video/mp4");
        entity.setImportStatus("IMPORTED");
        entity.setPublishStatus(publishStatus);
        entity.setSourceType("MANUAL");
        entity.setSortOrder(sortOrder);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.insert(entity);
        return entity;
    }
}
