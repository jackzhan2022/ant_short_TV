package com.antshorttv.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antshorttv.storage.ObjectStorageProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qcloud.cos.COS;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentialsProvider;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.model.CopyObjectRequest;
import com.qcloud.cos.model.CopyObjectResult;
import com.qcloud.cos.model.CopyResult;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.UploadResult;
import com.qcloud.cos.model.ciModel.job.MediaJobObject;
import com.qcloud.cos.model.ciModel.job.MediaJobResponse;
import com.qcloud.cos.model.ciModel.job.MediaJobsRequest;
import com.qcloud.cos.transfer.Copy;
import com.qcloud.cos.transfer.TransferManager;
import com.qcloud.cos.transfer.Upload;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

public abstract class OfflineMediaTestSupport {
    @MockBean private COS cos;
    @MockBean private TransferManager transfers;
    @MockBean private COSCredentialsProvider credentials;
    @Autowired private ObjectStorageProperties storageProperties;
    @Autowired private ObjectMapper objectMapper;

    private final Map<String, StoredBytes> objects = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<PendingImageJob> imageJobs = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger JOB_SEQUENCE = new AtomicInteger();

    @BeforeEach
    void configureOfflineMedia() throws Exception {
        storageProperties.setCiCallbackUrl("https://app.example/api/media-processing/callbacks/tencent-ci");
        objects.clear();
        imageJobs.clear();
        when(credentials.getCredentials()).thenReturn(new BasicCOSCredentials("offline-id", "offline-key"));
        when(cos.putObject(any(PutObjectRequest.class))).thenAnswer(call -> upload(call.getArgument(0)));
        when(cos.getObjectMetadata(anyString(), anyString())).thenAnswer(call ->
            object(call.getArgument(1)).metadata());
        when(cos.getObject(anyString(), anyString())).thenAnswer(call -> {
            StoredBytes stored = object(call.getArgument(1));
            COSObject result = new COSObject();
            result.setObjectMetadata(stored.metadata());
            result.setObjectContent(new COSObjectInputStream(
                new ByteArrayInputStream(stored.bytes()), new org.apache.http.client.methods.HttpGet()));
            return result;
        });
        doAnswer(call -> {
            objects.remove(call.getArgument(1));
            return null;
        }).when(cos).deleteObject(anyString(), anyString());
        when(cos.copyObject(any(CopyObjectRequest.class))).thenAnswer(call -> copy(call.getArgument(0)));
        when(cos.generatePresignedUrl(anyString(), anyString(), any(), any())).thenAnswer(call ->
            new URL("https://offline-cos.example/" + call.getArgument(1)));
        when(transfers.upload(any(PutObjectRequest.class))).thenAnswer(call -> {
            upload(call.getArgument(0));
            Upload transfer = mock(Upload.class);
            when(transfer.waitForUploadResult()).thenReturn(new UploadResult());
            return transfer;
        });
        when(transfers.copy(any(CopyObjectRequest.class))).thenAnswer(call -> {
            copy(call.getArgument(0));
            Copy transfer = mock(Copy.class);
            when(transfer.waitForCopyResult()).thenReturn(new CopyResult());
            return transfer;
        });
        when(cos.createPicProcessJob(any(MediaJobsRequest.class))).thenAnswer(call -> {
            MediaJobsRequest request = call.getArgument(0);
            String jobId = "offline-image-" + JOB_SEQUENCE.incrementAndGet();
            imageJobs.add(new PendingImageJob(jobId, request));
            MediaJobObject detail = new MediaJobObject();
            detail.setJobId(jobId);
            detail.setState("Submitted");
            MediaJobResponse response = mock(MediaJobResponse.class);
            when(response.getJobsDetail()).thenReturn(detail);
            return response;
        });
    }

    protected void completePendingImageJobs(MockMvc mockMvc) throws Exception {
        PendingImageJob pending;
        while ((pending = imageJobs.poll()) != null) {
            MediaJobsRequest request = pending.request();
            String input = request.getInput().getObject();
            String output = request.getOperation().getOutput().getObject();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(object(input).bytes()));
            assertThat(image).isNotNull();
            String format = output.substring(output.lastIndexOf('.') + 1);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            assertThat(ImageIO.write(image, format, bytes)).isTrue();
            putStoredObject(output, bytes.toByteArray(), "jpg".equals(format) ? "image/jpeg" : "image/" + format);
            Map<String, Object> payload = Map.of(
                "EventName", "TaskFinish",
                "JobsDetail", Map.of(
                    "Code", "Success", "JobId", pending.jobId(), "State", "Success",
                    "Input", Map.of("Object", input),
                    "Operation", Map.of(
                        "Output", Map.of("Object", output),
                        "UserData", request.getOperation().getUserData(),
                        "PicProcessResult", Map.of("ProcessResult", Map.of(
                            "Size", bytes.size(), "Width", image.getWidth(), "Height", image.getHeight(),
                            "Etag", object(output).metadata().getETag(), "Format", format))
                    )
                )
            );
            String callbackPath = URI.create(request.getCallBack()).getPath();
            mockMvc.perform(post(callbackPath).contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(payload)))
                .andExpect(status().isOk());
        }
    }

    protected byte[] storedObjectBytes(String path) {
        return object(objectKey(path)).bytes();
    }

    protected MvcResult completeMediaUpload(
        MockMvc mockMvc, String credential, Long tenantId,
        String fileName, String contentType, byte[] bytes
    ) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/media-uploads")
                .with(SessionTestSupport.authenticated(credential))
                .header("X-Tenant-Id", tenantId).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                    "fileName", fileName, "contentType", contentType, "fileSize", bytes.length))))
            .andExpect(status().isOk()).andReturn();
        var data = objectMapper.readTree(created.getResponse().getContentAsByteArray()).path("data");
        String sessionToken = data.path("sessionToken").asText();
        putStoredObject(data.path("objectKey").asText(), bytes, contentType);
        return mockMvc.perform(post("/api/media-uploads/{token}/complete", sessionToken)
                .with(SessionTestSupport.authenticated(credential)).header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk()).andReturn();
    }

    protected void putStoredObject(String path, byte[] bytes, String contentType) throws Exception {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(bytes.length);
        metadata.setContentType(contentType);
        metadata.setETag(HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(bytes)));
        metadata.setHeader("x-cos-storage-class", storageProperties.getStorageClass());
        objects.put(objectKey(path), new StoredBytes(bytes, metadata));
    }

    private PutObjectResult upload(PutObjectRequest request) throws Exception {
        requireNewOriginal(request.getKey());
        byte[] bytes = request.getFile() == null
            ? request.getInputStream().readAllBytes() : Files.readAllBytes(request.getFile().toPath());
        putStoredObject(request.getKey(), bytes, request.getMetadata().getContentType());
        PutObjectResult result = new PutObjectResult();
        result.setETag(object(request.getKey()).metadata().getETag());
        return result;
    }

    private CopyObjectResult copy(CopyObjectRequest request) throws Exception {
        StoredBytes source = object(request.getSourceKey());
        requireNewOriginal(request.getDestinationKey());
        putStoredObject(request.getDestinationKey(), source.bytes(), source.metadata().getContentType());
        CopyObjectResult result = new CopyObjectResult();
        result.setETag(object(request.getDestinationKey()).metadata().getETag());
        return result;
    }

    private void requireNewOriginal(String key) {
        if (key.startsWith("materials/") && !key.contains("/derived/")
            && key.substring(key.lastIndexOf('/') + 1).startsWith("original.") && objects.containsKey(key)) {
            CosServiceException conflict = new CosServiceException("Immutable original already exists: " + key);
            conflict.setStatusCode(409);
            throw conflict;
        }
    }

    private StoredBytes object(String key) {
        StoredBytes stored = objects.get(key);
        if (stored == null) {
            CosServiceException missing = new CosServiceException("Offline object does not exist: " + key);
            missing.setStatusCode(404);
            throw missing;
        }
        return stored;
    }

    private String objectKey(String path) {
        String key = URI.create(path).getPath();
        return key.startsWith("/") ? key.substring(1) : key;
    }

    private record StoredBytes(byte[] bytes, ObjectMetadata metadata) { }
    private record PendingImageJob(String jobId, MediaJobsRequest request) { }
}
