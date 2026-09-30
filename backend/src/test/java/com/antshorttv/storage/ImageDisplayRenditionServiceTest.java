package com.antshorttv.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ImageDisplayRenditionServiceTest {

    @Test
    void registersVerifiedOriginalAndSubmitsOnePersistentDisplayJob() {
        MediaObjectRegistry registry = mock(MediaObjectRegistry.class);
        CloudInfiniteProcessingService processing = mock(CloudInfiniteProcessingService.class);
        ImageDisplayRenditionService service = new ImageDisplayRenditionService(registry, processing);
        MediaObjectIdentity identity = new MediaObjectIdentity(
            11L, 22L, "AI_IMAGE_RESULT", 44L, "result-44"
        );
        StoredObject original = new StoredObject(
            "materials/11/22/images/202609/44/result-44/original.jpg",
            321L,
            "image/jpeg",
            "etag-original",
            "INTELLIGENT_TIERING"
        );
        when(registry.registerOriginal(identity, original, null, 800, 400)).thenReturn(
            new RegisteredMediaObject(71L, identity, "ORIGINAL", original.key(), "READY")
        );
        when(processing.submitImageDisplay(org.mockito.ArgumentMatchers.any())).thenReturn(
            new SubmittedMediaProcessingJob(
                "job-1", "SUBMITTED",
                "materials/11/22/images/202609/44/result-44/derived/display.jpg"
            )
        );

        RegisteredImageDisplay registered = service.registerOriginalAndSubmit(
            identity, original, 800, 400, "ai-image-result:44"
        );

        assertThat(registered.originalKey()).isEqualTo(original.key());
        assertThat(registered.displayKey())
            .isEqualTo("materials/11/22/images/202609/44/result-44/derived/display.jpg");
        assertThat(registered.status()).isEqualTo("SUBMITTED");
        verify(registry).registerOriginal(identity, original, null, 800, 400);
        ArgumentCaptor<SubmitImageDisplayJob> command =
            ArgumentCaptor.forClass(SubmitImageDisplayJob.class);
        verify(processing).submitImageDisplay(command.capture());
        assertThat(command.getValue().identity()).isEqualTo(identity);
        assertThat(command.getValue().inputKey()).isEqualTo(original.key());
        assertThat(command.getValue().sourceMimeType()).isEqualTo("image/jpeg");
        assertThat(command.getValue().storageClass()).isEqualTo("INTELLIGENT_TIERING");
        assertThat(command.getValue().correlationData()).isEqualTo("ai-image-result:44");
    }

    @Test
    void exposesThePersistedDisplayRenditionStateForDomainReconciliation() {
        MediaObjectRegistry registry = mock(MediaObjectRegistry.class);
        CloudInfiniteProcessingService processing = mock(CloudInfiniteProcessingService.class);
        ImageDisplayRenditionService service = new ImageDisplayRenditionService(registry, processing);
        MediaObjectIdentity identity = new MediaObjectIdentity(
            11L, 22L, "AI_IMAGE_RESULT", 44L, "result-44"
        );
        RegisteredMediaObject ready = new RegisteredMediaObject(
            72L,
            identity,
            "DISPLAY_IMAGE_SLIM",
            "materials/11/22/images/202609/44/result-44/derived/display.jpg",
            "READY"
        );
        when(registry.find(identity, "DISPLAY_IMAGE_SLIM")).thenReturn(ready);

        assertThat(service.display(identity)).isEqualTo(ready);
    }

    @Test
    void retriesFailedDisplayFromTheRegisteredOriginal() {
        MediaObjectRegistry registry = mock(MediaObjectRegistry.class);
        CloudInfiniteProcessingService processing = mock(CloudInfiniteProcessingService.class);
        ImageDisplayRenditionService service = new ImageDisplayRenditionService(registry, processing);
        MediaObjectIdentity identity = new MediaObjectIdentity(
            11L, 22L, "AI_IMAGE_RESULT", 44L, "result-44"
        );
        RegisteredImageOriginal original = new RegisteredImageOriginal(
            "materials/11/22/images/202609/44/result-44/original.jpg",
            "image/jpeg",
            "INTELLIGENT_TIERING",
            "READY"
        );
        when(registry.original(identity)).thenReturn(original);
        when(processing.submitImageDisplay(org.mockito.ArgumentMatchers.any())).thenReturn(
            new SubmittedMediaProcessingJob(
                "job-2", "SUBMITTED",
                "materials/11/22/images/202609/44/result-44/derived/display.jpg"
            )
        );

        RegisteredImageDisplay retried = service.retryFailedDisplay(
            identity, "ai-image-result:44"
        );

        assertThat(retried.status()).isEqualTo("SUBMITTED");
        ArgumentCaptor<SubmitImageDisplayJob> command =
            ArgumentCaptor.forClass(SubmitImageDisplayJob.class);
        verify(processing).submitImageDisplay(command.capture());
        assertThat(command.getValue().inputKey()).isEqualTo(original.objectKey());
        assertThat(command.getValue().sourceMimeType()).isEqualTo(original.mimeType());
        assertThat(command.getValue().storageClass()).isEqualTo(original.storageClass());
    }
}
