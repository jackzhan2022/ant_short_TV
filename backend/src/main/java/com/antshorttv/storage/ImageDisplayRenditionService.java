package com.antshorttv.storage;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
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

    @Transactional
    public RegisteredMediaObject display(MediaObjectIdentity identity) {
        return registry.find(identity, "DISPLAY_IMAGE_SLIM");
    }
}
