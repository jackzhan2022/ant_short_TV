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
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import java.util.stream.Stream;

class CloudInfiniteProcessingServiceTest {
    private COS cos;
    private InMemoryJobStore jobs;
    private InMemoryMediaObjectStore media;
    private MediaProcessingJobCoordinator coordinator;
    private CloudInfiniteProcessingService service;
    private final ObjectStorageKeyFactory keys = new ObjectStorageKeyFactory();

    @BeforeEach
    void setUp() {
        cos = mock(COS.class);
        jobs = new InMemoryJobStore();
        media = new InMemoryMediaObjectStore();
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setBucket("antv-1418200553");
        properties.setRegion("ap-guangzhou");
        properties.setCdnTypeDKey("test-key");
        properties.setCiCallbackUrl("https://app.example/api/media-processing/callbacks/tencent-ci");
        MediaJobResponse response = mock(MediaJobResponse.class);
        MediaJobObject detail = mock(MediaJobObject.class);
        when(detail.getJobId()).thenReturn("job-1");
        when(detail.getState()).thenReturn("Submitted");
        when(detail.getQueueId()).thenReturn("queue-1");
        when(response.getJobsDetail()).thenReturn(detail);
        when(cos.createPicProcessJob(any())).thenReturn(response);
        MediaObjectRegistry registry = new MediaObjectRegistry(media, keys);
        coordinator = new MediaProcessingJobCoordinator(
            jobs, media, registry
        );
        service = new CloudInfiniteProcessingService(
            cos,
            properties,
            keys,
            coordinator,
            new ImageDisplayRenditionPlanner(keys)
        );
    }

    @Test
    void submitsEachOutputOperationOnlyOnce() {
        SubmitImageDisplayJob command = command("image/png");

        SubmittedMediaProcessingJob first = service.submitImageDisplay(command);
        SubmittedMediaProcessingJob second = service.submitImageDisplay(command);

        assertThat(second.providerJobId()).isEqualTo(first.providerJobId()).isEqualTo("job-1");
        verify(cos, times(1)).createPicProcessJob(any());
        assertThat(jobs.current.status).isEqualTo("SUBMITTED");
        assertThat(jobs.current.queueId).isEqualTo("queue-1");
        ArgumentCaptor<MediaJobsRequest> request = ArgumentCaptor.forClass(MediaJobsRequest.class);
        verify(cos).createPicProcessJob(request.capture());
        assertThat(request.getValue().getTag()).isEqualTo("PicProcess");
        assertThat(request.getValue().getInput().getObject()).isEqualTo(command.inputKey());
        assertThat(request.getValue().getOperation().getOutput().getObject())
            .isEqualTo("materials/11/22/images/202609/42/v1/derived/display.png");
        assertThat(request.getValue().getOperation().getPicProcess().getProcessRule())
            .isEqualTo("imageSlim");
        assertThat(request.getValue().getCallBack()).startsWith(
            "https://app.example/api/media-processing/callbacks/tencent-ci/"
        );
    }

    @Test
    void authenticatesCorrelatesAndIdempotentlyCompletesCallback() {
        service.submitImageDisplay(command("image/png"));
        ArgumentCaptor<MediaJobsRequest> request = ArgumentCaptor.forClass(MediaJobsRequest.class);
        verify(cos).createPicProcessJob(request.capture());
        String token = request.getValue().getCallBack().substring(
            request.getValue().getCallBack().lastIndexOf('/') + 1
        );
        TencentCiTaskCallback callback = TencentCiTaskCallback.success(
            "job-1",
            command("image/png").inputKey(),
            "materials/11/22/images/202609/42/v1/derived/display.png",
            "asset-42-display",
            321L,
            1200,
            800,
            "etag-derived",
            "PNG"
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
        assertThat(media.mimeType).isEqualTo("image/png");
        assertThat(media.current.status).isEqualTo("READY");
    }

    @Test
    void appliesFailedCallbackOnlyOnce() {
        service.submitImageDisplay(command("image/png"));
        String token = callbackToken();
        TencentCiTaskCallback callback = failedCallback("ImageSlimFailed", "imageSlim failed");

        service.handleCallback(token, callback);
        service.handleCallback(token, callback);

        assertThat(jobs.current.status).isEqualTo("FAILED");
        assertThat(jobs.current.errorCode).isEqualTo("ImageSlimFailed");
        assertThat(media.failedCalls).isEqualTo(1);
        assertThat(media.readyCalls).isZero();
        assertThat(media.current.status).isEqualTo("FAILED");
    }

    @Test
    void resubmitsFailedJobAndReturnsRenditionToPending() {
        SubmitImageDisplayJob command = command("image/png");
        service.submitImageDisplay(command);
        service.handleCallback(
            callbackToken(), failedCallback("ImageSlimFailed", "imageSlim failed")
        );

        service.submitImageDisplay(command);

        assertThat(jobs.current.status).isEqualTo("SUBMITTED");
        assertThat(jobs.current.attemptNo).isEqualTo(2);
        assertThat(media.current.status).isEqualTo("PENDING");
        verify(cos, times(2)).createPicProcessJob(any());
    }

    @ParameterizedTest
    @MethodSource("invalidSuccessMetadata")
    void persistsMalformedSuccessCallbackAsRetryableFailure(
        long size,
        int width,
        int height,
        String eTag,
        String format
    ) {
        service.submitImageDisplay(command("image/png"));
        String token = callbackToken();

        service.handleCallback(token, TencentCiTaskCallback.success(
            "job-1",
            command("image/png").inputKey(),
            "materials/11/22/images/202609/42/v1/derived/display.png",
            "asset-42-display",
            size,
            width,
            height,
            eTag,
            format
        ));

        assertThat(jobs.current.status).isEqualTo("FAILED");
        assertThat(jobs.current.errorCode).isEqualTo("INVALID_RESULT_METADATA");
        assertThat(jobs.current.errorMessage).contains("元数据");
        assertThat(media.current.status).isEqualTo("FAILED");
        assertThat(media.failedCalls).isEqualTo(1);
        assertThat(media.readyCalls).isZero();
    }

    private static Stream<Arguments> invalidSuccessMetadata() {
        return Stream.of(
            Arguments.of(0L, 1200, 800, "etag-derived", "PNG"),
            Arguments.of(321L, 0, 800, "etag-derived", "PNG"),
            Arguments.of(321L, 1200, 0, "etag-derived", "PNG"),
            Arguments.of(321L, 1200, 800, " ", "PNG"),
            Arguments.of(321L, 1200, 800, "etag-derived", "BMP")
        );
    }

    @Test
    void keepsFailedJobTerminalWhenSuccessCallbackArrivesLater() {
        service.submitImageDisplay(command("image/png"));
        String token = callbackToken();

        service.handleCallback(token, failedCallback("ImageSlimFailed", "imageSlim failed"));
        service.handleCallback(token, TencentCiTaskCallback.success(
            "job-1",
            command("image/png").inputKey(),
            "materials/11/22/images/202609/42/v1/derived/display.png",
            "asset-42-display",
            321L,
            1200,
            800,
            "etag-derived",
            "PNG"
        ));

        assertThat(jobs.current.status).isEqualTo("FAILED");
        assertThat(media.failedCalls).isEqualTo(1);
        assertThat(media.readyCalls).isZero();
    }

    @Test
    void convertsUnsupportedImageToPngBeforeImageSlim() {
        service.submitImageDisplay(command("image/webp"));

        ArgumentCaptor<MediaJobsRequest> request = ArgumentCaptor.forClass(MediaJobsRequest.class);
        verify(cos).createPicProcessJob(request.capture());
        assertThat(request.getValue().getOperation().getOutput().getObject())
            .isEqualTo("materials/11/22/images/202609/42/v1/derived/display.png");
        assertThat(request.getValue().getOperation().getPicProcess().getProcessRule())
            .isEqualTo("imageMogr2/format/png|imageSlim");
    }

    @Test
    void acceptsCorrelatedCallbackBeforeProviderSubmissionFinalizes() {
        when(cos.createPicProcessJob(any())).thenAnswer(invocation -> {
            MediaJobsRequest request = invocation.getArgument(0);
            String token = request.getCallBack().substring(request.getCallBack().lastIndexOf('/') + 1);
            service.handleCallback(token, TencentCiTaskCallback.success(
                "job-1",
                command("image/png").inputKey(),
                "materials/11/22/images/202609/42/v1/derived/display.png",
                "asset-42-display",
                321L, 1200, 800, "etag-derived", "PNG"
            ));
            return submittedResponse("job-1");
        });

        SubmittedMediaProcessingJob submitted = service.submitImageDisplay(command("image/png"));

        assertThat(submitted.status()).isEqualTo("SUCCEEDED");
        assertThat(jobs.current.providerJobId).isEqualTo("job-1");
        assertThat(media.current.status).isEqualTo("READY");
    }

    @Test
    void providerFinalizeDoesNotOverwriteEarlyTerminalFailure() {
        when(cos.createPicProcessJob(any())).thenAnswer(invocation -> {
            MediaJobsRequest request = invocation.getArgument(0);
            String token = request.getCallBack().substring(request.getCallBack().lastIndexOf('/') + 1);
            service.handleCallback(token, failedCallback("ImageSlimFailed", "imageSlim failed"));
            return submittedResponse("job-1");
        });

        SubmittedMediaProcessingJob submitted = service.submitImageDisplay(command("image/png"));

        assertThat(submitted.status()).isEqualTo("FAILED");
        assertThat(jobs.current.errorCode).isEqualTo("ImageSlimFailed");
        assertThat(media.current.status).isEqualTo("FAILED");
    }

    @Test
    void submissionFailurePersistsRetryableJobAndRenditionFailure() {
        when(cos.createPicProcessJob(any())).thenThrow(new IllegalStateException("cos unavailable"));

        assertThatThrownBy(() -> service.submitImageDisplay(command("image/png")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cos unavailable");

        assertThat(jobs.current.status).isEqualTo("FAILED");
        assertThat(jobs.current.errorCode).isEqualTo("SUBMIT_FAILED");
        assertThat(media.current.status).isEqualTo("FAILED");
    }

    @Test
    void staleSubmittingJobStartsANewAttempt() {
        service.submitImageDisplay(command("image/png"));
        jobs.current.status = "SUBMITTING";
        jobs.current.providerJobId = null;
        jobs.current.updatedAt = LocalDateTime.now().minusMinutes(6);

        service.submitImageDisplay(command("image/png"));

        assertThat(jobs.current.attemptNo).isEqualTo(2);
        ArgumentCaptor<MediaJobsRequest> requests = ArgumentCaptor.forClass(MediaJobsRequest.class);
        verify(cos, times(2)).createPicProcessJob(requests.capture());
        assertThat(requests.getAllValues().get(1).getCallBack())
            .isEqualTo(requests.getAllValues().get(0).getCallBack());
    }

    @Test
    void oldCallbackWinsDuringStaleReplacementFinalize() {
        service.submitImageDisplay(command("image/png"));
        String oldToken = callbackToken();
        jobs.current.status = "SUBMITTING";
        jobs.current.providerJobId = null;
        jobs.current.updatedAt = LocalDateTime.now().minusMinutes(6);
        when(cos.createPicProcessJob(any())).thenAnswer(invocation -> {
            service.handleCallback(oldToken, TencentCiTaskCallback.success(
                "job-1", command("image/png").inputKey(),
                "materials/11/22/images/202609/42/v1/derived/display.png",
                "asset-42-display", 321L, 1200, 800, "etag-old", "PNG"
            ));
            return submittedResponse("job-2");
        });

        SubmittedMediaProcessingJob submitted = service.submitImageDisplay(command("image/png"));

        assertThat(submitted.status()).isEqualTo("SUCCEEDED");
        assertThat(jobs.current.providerJobId).isEqualTo("job-1");
        assertThat(jobs.current.errorCode).isNull();
        assertThat(media.current.status).isEqualTo("READY");
    }

    @Test
    void oldCallbackWinsWhenStaleReplacementSubmissionThrows() {
        service.submitImageDisplay(command("image/png"));
        String oldToken = callbackToken();
        jobs.current.status = "SUBMITTING";
        jobs.current.providerJobId = null;
        jobs.current.updatedAt = LocalDateTime.now().minusMinutes(6);
        when(cos.createPicProcessJob(any())).thenAnswer(invocation -> {
            service.handleCallback(oldToken, TencentCiTaskCallback.success(
                "job-1", command("image/png").inputKey(),
                "materials/11/22/images/202609/42/v1/derived/display.png",
                "asset-42-display", 321L, 1200, 800, "etag-old", "PNG"
            ));
            throw new IllegalStateException("replacement failed");
        });

        assertThatThrownBy(() -> service.submitImageDisplay(command("image/png")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("replacement failed");

        assertThat(jobs.current.status).isEqualTo("SUCCEEDED");
        assertThat(jobs.current.providerJobId).isEqualTo("job-1");
        assertThat(jobs.current.errorCode).isNull();
        assertThat(media.current.status).isEqualTo("READY");
    }

    @Test
    void lateCallbackCannotReviveRetiredMediaGraph() {
        service.submitImageDisplay(command("image/png"));
        String token = callbackToken();

        coordinator.retireImage(command("image/png").identity());
        service.handleCallback(token, TencentCiTaskCallback.success(
            "job-1", command("image/png").inputKey(),
            "materials/11/22/images/202609/42/v1/derived/display.png",
            "asset-42-display", 321L, 1200, 800, "etag-late", "PNG"
        ));

        assertThat(jobs.current.status).isEqualTo("CANCELED");
        assertThat(media.current.status).isEqualTo("RETIRED");
        assertThat(media.readyCalls).isZero();
    }

    private MediaJobResponse submittedResponse(String jobId) {
        MediaJobResponse response = mock(MediaJobResponse.class);
        MediaJobObject detail = mock(MediaJobObject.class);
        when(detail.getJobId()).thenReturn(jobId);
        when(detail.getState()).thenReturn("Submitted");
        when(detail.getQueueId()).thenReturn("queue-1");
        when(response.getJobsDetail()).thenReturn(detail);
        return response;
    }

    private SubmitImageDisplayJob command(String sourceMimeType) {
        return new SubmitImageDisplayJob(
            new MediaObjectIdentity(11L, 22L, "images", 42L, "v1"),
            "materials/11/22/images/202609/42/v1/original.png",
            sourceMimeType,
            "INTELLIGENT_TIERING",
            "asset-42-display"
        );
    }

    private String callbackToken() {
        ArgumentCaptor<MediaJobsRequest> request = ArgumentCaptor.forClass(MediaJobsRequest.class);
        verify(cos).createPicProcessJob(request.capture());
        String callback = request.getValue().getCallBack();
        return callback.substring(callback.lastIndexOf('/') + 1);
    }

    private TencentCiTaskCallback failedCallback(String code, String message) {
        return new TencentCiTaskCallback("TaskFinish", List.of(new TencentCiJobDetail(
            code,
            message,
            "job-1",
            "Failed",
            new TencentCiInput(command("image/png").inputKey()),
            new TencentCiOperation(
                new TencentCiOutput("materials/11/22/images/202609/42/v1/derived/display.png"),
                "asset-42-display",
                null
            )
        )));
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
        @Override void update(MediaProcessingJobEntity entity) {
            current = entity;
            byToken.put(entity.callbackTokenHash, entity);
        }
        @Override void resetForSubmission(MediaProcessingJobEntity entity) {
            update(entity);
        }
    }

    private static final class InMemoryMediaObjectStore extends MediaObjectStore {
        int readyCalls;
        int failedCalls;
        long size;
        int width;
        int height;
        String mimeType;
        long nextId = 1;
        MediaObjectEntity current;

        @Override MediaObjectEntity find(MediaObjectIdentity identity, String renditionType) {
            return current != null && current.identity().equals(identity)
                && current.renditionType.equals(renditionType) ? current : null;
        }
        @Override void insert(MediaObjectEntity entity) {
            entity.id = nextId++;
            current = entity;
        }
        @Override boolean retryFailed(Long id) {
            if (current == null || !current.id.equals(id) || !"FAILED".equals(current.status)) {
                return false;
            }
            current.status = "PENDING";
            current.errorMessage = null;
            return true;
        }
        @Override void retire(MediaObjectIdentity identity) {
            if (current != null && current.identity().equals(identity)) {
                current.status = "RETIRED";
                current.errorMessage = "Business result discarded";
            }
        }
        @Override void ready(Long id, long size, String eTag, String mimeType, int width, int height) {
            readyCalls++;
            current.status = "READY";
            this.size = size;
            this.mimeType = mimeType;
            this.width = width;
            this.height = height;
        }
        @Override void failed(Long id, String message) {
            failedCalls++;
            current.status = "FAILED";
        }
    }
}
