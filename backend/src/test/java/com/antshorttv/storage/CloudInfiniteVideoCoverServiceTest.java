package com.antshorttv.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.qcloud.cos.COS;
import com.qcloud.cos.model.ciModel.snapshot.CosSnapshotRequest;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CloudInfiniteVideoCoverServiceTest {

    @Test
    void persistsOneSecondSnapshotAndReturnsWebpCoverKey() {
        COS cos = mock(COS.class);
        ObjectStorageService storage = mock(ObjectStorageService.class);
        when(cos.getSnapshot(any())).thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        CloudInfiniteVideoCoverService service = new CloudInfiniteVideoCoverService(
            cos, storage, new ObjectStorageKeyFactory(), "antv-1418200553"
        );

        String cover = service.create(
            "platform/inspiration/abc/source/original.mp4",
            "platform/inspiration/abc/cover/original.jpg"
        );

        ArgumentCaptor<CosSnapshotRequest> request = ArgumentCaptor.forClass(CosSnapshotRequest.class);
        verify(cos).getSnapshot(request.capture());
        assertThat(request.getValue().getTime()).isEqualTo("1");
        assertThat(request.getValue().getFormat()).isEqualTo("jpg");
        verify(storage).upload(
            eq("platform/inspiration/abc/cover/original.jpg"),
            eq(new byte[] {1, 2, 3}),
            eq("image/jpeg")
        );
        assertThat(cover).isEqualTo("platform/inspiration/abc/cover/derived/display.webp");
    }

    @Test
    void fallsBackToFirstDecodableFrameForShortVideo() {
        COS cos = mock(COS.class);
        ObjectStorageService storage = mock(ObjectStorageService.class);
        when(cos.getSnapshot(any()))
            .thenThrow(new IllegalArgumentException("time exceeds duration"))
            .thenReturn(new ByteArrayInputStream(new byte[] {4, 5}));
        CloudInfiniteVideoCoverService service = new CloudInfiniteVideoCoverService(
            cos, storage, new ObjectStorageKeyFactory(), "antv-1418200553"
        );

        service.create("videos/short/original.mp4", "videos/short/cover/original.jpg");

        ArgumentCaptor<CosSnapshotRequest> requests = ArgumentCaptor.forClass(CosSnapshotRequest.class);
        verify(cos, times(2)).getSnapshot(requests.capture());
        assertThat(requests.getAllValues()).extracting(CosSnapshotRequest::getTime)
            .containsExactly("1", "0");
        verify(storage).upload(
            eq("videos/short/cover/original.jpg"), eq(new byte[] {4, 5}), eq("image/jpeg")
        );
    }
}
