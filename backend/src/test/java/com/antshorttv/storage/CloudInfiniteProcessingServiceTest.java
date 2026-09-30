package com.antshorttv.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.qcloud.cos.COS;
import com.qcloud.cos.model.ciModel.job.MediaJobObject;
import com.qcloud.cos.model.ciModel.job.MediaJobResponse;
import com.qcloud.cos.model.ciModel.job.MediaJobsRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CloudInfiniteProcessingServiceTest {
    private COS cos;
    private InMemoryJobStore jobs;
    private InMemoryMediaObjectStore media;
    private CloudInfiniteProcessingService service;

    @BeforeEach
    void setUp() {
        cos = mock(COS.class);
        jobs = new InMemoryJobStore();
        media = new InMemoryMediaObjectStore();
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setBucket("antv-1418200553");
        properties.setRegion("ap-guangzhou");
        properties.setCiCallbackUrl("https://app.example/api/media-processing/callbacks/tencent-ci");
        MediaJobResponse response = mock(MediaJobResponse.class);
        MediaJobObject detail = mock(MediaJobObject.class);
        when(detail.getJobId()).thenReturn("job-1");
        when(detail.getState()).thenReturn("Submitted");
        when(detail.getQueueId()).thenReturn("queue-1");
        when(response.getJobsDetail()).thenReturn(detail);
        when(cos.createPicProcessJob(any())).thenReturn(response);
        service = new CloudInfiniteProcessingService(
            cos, properties, new ObjectStorageKeyFactory(), jobs, media
        );
    }

    @Test
    void submitsEachOutputOperationOnlyOnce() {
        SubmitMediaProcessingJob command = command();

        SubmittedMediaProcessingJob first = service.submit(command);
        SubmittedMediaProcessingJob second = service.submit(command);

        assertThat(second.providerJobId()).isEqualTo(first.providerJobId()).isEqualTo("job-1");
        verify(cos, times(1)).createPicProcessJob(any());
        assertThat(jobs.current.status).isEqualTo("SUBMITTED");
        assertThat(jobs.current.queueId).isEqualTo("queue-1");
        ArgumentCaptor<MediaJobsRequest> request = ArgumentCaptor.forClass(MediaJobsRequest.class);
        verify(cos).createPicProcessJob(request.capture());
        assertThat(request.getValue().getTag()).isEqualTo("PicProcess");
        assertThat(request.getValue().getInput().getObject()).isEqualTo(command.inputKey());
        assertThat(request.getValue().getOperation().getOutput().getObject()).isEqualTo(command.outputKey());
        assertThat(request.getValue().getCallBack()).startsWith(
            "https://app.example/api/media-processing/callbacks/tencent-ci/"
        );
    }

    @Test
    void authenticatesCorrelatesAndIdempotentlyCompletesCallback() {
        service.submit(command());
        ArgumentCaptor<MediaJobsRequest> request = ArgumentCaptor.forClass(MediaJobsRequest.class);
        verify(cos).createPicProcessJob(request.capture());
        String token = request.getValue().getCallBack().substring(
            request.getValue().getCallBack().lastIndexOf('/') + 1
        );
        TencentCiTaskCallback callback = TencentCiTaskCallback.success(
            "job-1", command().inputKey(), command().outputKey(), "asset-42-display",
            321L, 1200, 800, "etag-derived", "WEBP"
        );

        assertThatThrownBy(() -> service.handleCallback("wrong-token", callback))
            .isInstanceOf(IllegalArgumentException.class);
        service.handleCallback(token, callback);
        service.handleCallback(token, callback);

        assertThat(jobs.current.status).isEqualTo("SUCCEEDED");
        assertThat(media.readyCalls).isEqualTo(1);
        assertThat(media.size).isEqualTo(321L);
        assertThat(media.width).isEqualTo(1200);
        assertThat(media.height).isEqualTo(800);
    }

    private SubmitMediaProcessingJob command() {
        return new SubmitMediaProcessingJob(
            11L, 22L, 99L, "DISPLAY_WEBP",
            "materials/11/22/images/202609/42/v1/original.png",
            "materials/11/22/images/202609/42/v1/derived/display.webp",
            "imageMogr2/format/webp/quality/80", "asset-42-display"
        );
    }

    private static final class InMemoryJobStore extends MediaProcessingJobStore {
        private final Map<String, MediaProcessingJobEntity> byToken = new HashMap<>();
        private MediaProcessingJobEntity current;

        @Override MediaProcessingJobEntity find(String outputKey, String operation) {
            return current != null && current.outputKey.equals(outputKey)
                && current.operation.equals(operation) ? current : null;
        }
        @Override MediaProcessingJobEntity findByTokenHash(String tokenHash) {
            return byToken.get(tokenHash);
        }
        @Override void insert(MediaProcessingJobEntity entity) {
            current = entity;
            byToken.put(entity.callbackTokenHash, entity);
        }
        @Override void update(MediaProcessingJobEntity entity) { current = entity; }
    }

    private static final class InMemoryMediaObjectStore extends MediaObjectStore {
        int readyCalls;
        long size;
        int width;
        int height;

        @Override void ready(Long id, long size, String eTag, String mimeType, int width, int height) {
            readyCalls++;
            this.size = size;
            this.width = width;
            this.height = height;
        }
        @Override void failed(Long id, String message) { }
    }
}
