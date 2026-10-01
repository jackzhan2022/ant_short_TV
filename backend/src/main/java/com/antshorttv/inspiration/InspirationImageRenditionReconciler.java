package com.antshorttv.inspiration;

import com.antshorttv.storage.ImageDisplayRenditionService;
import com.antshorttv.storage.MediaObjectIdentity;
import com.antshorttv.storage.RegisteredMediaDetails;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class InspirationImageRenditionReconciler {
    private static final int DEFAULT_BATCH_SIZE = 20;

    private final InspirationCreationMapper mapper;
    private final ImageDisplayRenditionService renditions;
    private long afterId;

    public InspirationImageRenditionReconciler(
        InspirationCreationMapper mapper,
        ImageDisplayRenditionService renditions
    ) {
        this.mapper = mapper;
        this.renditions = renditions;
    }

    @Scheduled(fixedDelayString = "${inspiration.image-rendition.scheduler.fixed-delay-ms:2000}")
    void tick() {
        reconcilePending(DEFAULT_BATCH_SIZE);
    }

    public synchronized int reconcilePending(int limit) {
        List<InspirationCreationEntity> candidates = mapper.selectImageRenditionCandidatesAfter(afterId, limit);
        if (candidates.isEmpty() && afterId != 0L) {
            afterId = 0L;
            candidates = mapper.selectImageRenditionCandidatesAfter(afterId, limit);
        }
        int reconciled = 0;
        for (InspirationCreationEntity entity : candidates) {
            afterId = entity.getId();
            RegisteredMediaDetails display = renditions.displayDetails(identity(entity));
            if (display == null) continue;
            if ("READY".equals(display.status())) {
                if (validReady(display)) {
                    reconciled += mapper.markImageRenditionReady(
                        entity.getId(), display.objectKey(), display.mimeType(), display.fileSize()
                    );
                }
            } else if ("FAILED".equals(display.status())) {
                reconciled += mapper.markImageRenditionFailed(
                    entity.getId(), error(display.errorMessage())
                );
            } else if ("PENDING".equals(display.status()) || "SUBMITTED".equals(display.status())) {
                reconciled += mapper.markImageRenditionPending(entity.getId(), display.objectKey());
            }
        }
        return reconciled;
    }

    static MediaObjectIdentity identity(InspirationCreationEntity entity) {
        return new MediaObjectIdentity(
            0L,
            null,
            "INSPIRATION_CREATION",
            entity.getId(),
            entity.getExternalId()
        );
    }

    private boolean validReady(RegisteredMediaDetails display) {
        return display.fileSize() > 0
            && display.objectKey() != null && !display.objectKey().isBlank()
            && display.mimeType() != null && display.mimeType().startsWith("image/");
    }

    private String error(String value) {
        String message = value == null || value.isBlank()
            ? "万象展示图处理失败，请通过存储任务重试。" : value;
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
