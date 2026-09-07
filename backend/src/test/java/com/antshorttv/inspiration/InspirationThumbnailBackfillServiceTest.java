package com.antshorttv.inspiration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.storage.ObjectStorageService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;

@SpringBootTest
class InspirationThumbnailBackfillServiceTest {
    @Autowired
    private InspirationThumbnailBackfillService service;

    @Autowired
    private InspirationCreationMapper mapper;

    @MockBean
    private ObjectStorageService objectStorageService;

    @BeforeEach
    void setUp() {
        mapper.delete(null);
    }

    @Test
    void generatesThumbnailForImportedRecordAndSkipsReadyRows() throws Exception {
        InspirationCreationEntity pending = creation("pending", null);
        mapper.insert(pending);
        InspirationCreationEntity ready = creation("ready", "READY");
        ready.setThumbnailPath("inspiration/creations/ready/thumbnail.jpg");
        mapper.insert(ready);
        when(objectStorageService.resource(pending.getStoragePath()))
            .thenReturn(new ByteArrayResource(png()));

        InspirationThumbnailBackfillResult result = service.backfill(10);

        InspirationCreationEntity updated = mapper.selectById(pending.getId());
        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(updated.getThumbnailStatus()).isEqualTo("READY");
        assertThat(updated.getThumbnailUrl()).isEqualTo(
            "/api/inspiration-creations/%d/thumbnail".formatted(pending.getId())
        );
        verify(objectStorageService).upload(
            eq("inspiration/creations/pending/thumbnail.jpg"),
            any(byte[].class),
            eq("image/jpeg")
        );
    }

    @Test
    void keepsProcessingWhenOneThumbnailCannotBeRead() throws Exception {
        InspirationCreationEntity failed = creation("failed", null);
        mapper.insert(failed);
        InspirationCreationEntity pending = creation("pending", null);
        mapper.insert(pending);
        when(objectStorageService.resource(failed.getStoragePath()))
            .thenThrow(new IllegalStateException("对象不存在"));
        when(objectStorageService.resource(pending.getStoragePath()))
            .thenReturn(new ByteArrayResource(png()));

        InspirationThumbnailBackfillResult result = service.backfill(10);

        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(mapper.selectById(failed.getId()).getThumbnailStatus()).isEqualTo("FAILED");
        assertThat(mapper.selectById(pending.getId()).getThumbnailStatus()).isEqualTo("READY");
    }

    private InspirationCreationEntity creation(String externalId, String thumbnailStatus) {
        InspirationCreationEntity entity = new InspirationCreationEntity();
        entity.setExternalId(externalId);
        entity.setCreationType("IMAGE");
        entity.setTaskType("TEXT_TO_IMAGE");
        entity.setTitle(externalId);
        entity.setAuthorName("管理员");
        entity.setUrl("/api/inspiration-creations/file");
        entity.setStoragePath("inspiration/creations/%s/original.png".formatted(externalId));
        entity.setMimeType("image/png");
        entity.setImportStatus("IMPORTED");
        entity.setThumbnailStatus(thumbnailStatus);
        entity.setSortOrder(1);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    private byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
