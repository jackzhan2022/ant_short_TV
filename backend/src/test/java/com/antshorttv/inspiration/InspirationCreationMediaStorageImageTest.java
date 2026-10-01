package com.antshorttv.inspiration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.storage.ImageDisplayRenditionService;
import com.antshorttv.storage.MediaObjectIdentity;
import com.antshorttv.storage.ObjectStorageKeyFactory;
import com.antshorttv.storage.ObjectStorageService;
import com.antshorttv.storage.RegisteredImageDisplay;
import com.antshorttv.storage.StoredObject;
import com.antshorttv.storage.VerifiedMediaUpload;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class InspirationCreationMediaStorageImageTest {

    @Test
    void promotesVerifiedBrowserImageRegistersRealDimensionsAndSubmitsDisplayOnce() throws Exception {
        ObjectStorageService objects = mock(ObjectStorageService.class);
        ImageDisplayRenditionService renditions = mock(ImageDisplayRenditionService.class);
        InspirationCreationMediaStorage storage = new InspirationCreationMediaStorage(
            objects, new ObjectStorageKeyFactory(), renditions
        );
        byte[] bytes = png(12, 8);
        VerifiedMediaUpload upload = new VerifiedMediaUpload(
            "session-1", "uploads/11/session-1/source.png", "image/png", bytes.length,
            "etag-source"
        );
        StoredObject source = new StoredObject(
            upload.objectKey(), bytes.length, "image/png", "etag-source", "INTELLIGENT_TIERING"
        );
        when(objects.metadata(upload.objectKey())).thenReturn(source);
        when(objects.resource(upload.objectKey())).thenReturn(new ByteArrayResource(bytes));
        when(objects.promoteVerifiedUpload(eq(source), any(String.class))).thenAnswer(invocation ->
            new StoredObject(
                invocation.getArgument(1), bytes.length, "image/png", "etag-final",
                "INTELLIGENT_TIERING"
            )
        );
        when(renditions.registerOriginalAndSubmit(
            any(MediaObjectIdentity.class), any(StoredObject.class), eq(12), eq(8), any(String.class)
        )).thenAnswer(invocation -> {
            StoredObject original = invocation.getArgument(1);
            return new RegisteredImageDisplay(
                original.key(), original.key().replace("/original.png", "/derived/display.png"),
                "SUBMITTED"
            );
        });

        InspirationCreationMediaTransfer result = storage.storeUploadedImage(
            44L, "external-44", upload
        );

        assertThat(result.storagePath())
            .isEqualTo("materials/0/inspiration_creation/" + java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"))
                + "/44/external-44/original.png");
        assertThat(result.displayPath()).endsWith("/derived/display.png");
        assertThat(result.displayMimeType()).isEqualTo("image/png");
        assertThat(result.displayStatus()).isEqualTo("PENDING");
        verify(renditions).registerOriginalAndSubmit(
            eq(new MediaObjectIdentity(
                0L, null, "INSPIRATION_CREATION", 44L, "external-44"
            )),
            any(StoredObject.class),
            eq(12),
            eq(8),
            eq("inspiration-creation:44")
        );
    }

    private byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
