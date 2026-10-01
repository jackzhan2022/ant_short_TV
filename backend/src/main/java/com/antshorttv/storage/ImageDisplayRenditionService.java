package com.antshorttv.storage;

import org.springframework.stereotype.Service;

@Service
public class ImageDisplayRenditionService {
    private final MediaObjectRegistry registry;
    private final CloudInfiniteProcessingService processing;

    public ImageDisplayRenditionService(
        MediaObjectRegistry registry,
        CloudInfiniteProcessingService processing
    ) {
        this.registry = registry;
        this.processing = processing;
    }

    public RegisteredImageDisplay registerOriginalAndSubmit(
        MediaObjectIdentity identity,
        StoredObject original,
        int width,
        int height,
        String correlationData
    ) {
        registry.registerOriginal(identity, original, null, width, height);
        SubmittedMediaProcessingJob job = processing.submitImageDisplay(new SubmitImageDisplayJob(
            identity,
            original.key(),
            original.contentType(),
            original.storageClass(),
            correlationData
        ));
        return new RegisteredImageDisplay(original.key(), job.outputKey(), job.status());
    }

    public RegisteredMediaObject display(MediaObjectIdentity identity) {
        return registry.find(identity, "DISPLAY_IMAGE_SLIM");
    }

    public RegisteredMediaDetails displayDetails(MediaObjectIdentity identity) {
        return registry.details(identity, "DISPLAY_IMAGE_SLIM");
    }

    public RegisteredMediaDetails originalDetails(MediaObjectIdentity identity) {
        return registry.details(identity, "ORIGINAL");
    }

    public RegisteredImageDisplay retryFailedDisplay(
        MediaObjectIdentity identity,
        String correlationData
    ) {
        RegisteredImageOriginal original = registry.original(identity);
        if (original == null || !"READY".equals(original.status())) {
            throw new IllegalStateException("AI 图片原图尚未就绪，无法重试展示版本。");
        }
        SubmittedMediaProcessingJob job = processing.submitImageDisplay(new SubmitImageDisplayJob(
            identity,
            original.objectKey(),
            original.mimeType(),
            original.storageClass(),
            correlationData
        ));
        return new RegisteredImageDisplay(original.objectKey(), job.outputKey(), job.status());
    }

    public void retire(MediaObjectIdentity identity) {
        processing.retireImage(identity);
    }
}
