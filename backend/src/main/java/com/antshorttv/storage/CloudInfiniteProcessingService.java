package com.antshorttv.storage;

import com.qcloud.cos.COS;
import com.qcloud.cos.model.ciModel.common.MediaInputObject;
import com.qcloud.cos.model.ciModel.common.MediaOutputObject;
import com.qcloud.cos.model.ciModel.job.MediaJobObject;
import com.qcloud.cos.model.ciModel.job.MediaJobOperation;
import com.qcloud.cos.model.ciModel.job.MediaJobResponse;
import com.qcloud.cos.model.ciModel.job.MediaJobsRequest;
import com.qcloud.cos.model.ciModel.job.MediaPicProcessTemplateObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class CloudInfiniteProcessingService {
    private final COS cos;
    private final ObjectStorageProperties properties;
    private final ObjectStorageKeyFactory keys;
    private final MediaProcessingJobCoordinator coordinator;
    private final ImageDisplayRenditionPlanner imageRenditions;

    public CloudInfiniteProcessingService(
        COS cos,
        ObjectStorageProperties properties,
        ObjectStorageKeyFactory keys,
        MediaProcessingJobCoordinator coordinator,
        ImageDisplayRenditionPlanner imageRenditions
    ) {
        this.cos = cos;
        this.properties = properties;
        this.keys = keys;
        this.coordinator = coordinator;
        this.imageRenditions = imageRenditions;
    }

    public SubmittedMediaProcessingJob submitImageDisplay(SubmitImageDisplayJob command) {
        if (command == null || command.identity() == null || blank(command.inputKey())
            || blank(command.sourceMimeType()) || blank(command.storageClass())
            || blank(command.correlationData())) {
            throw new IllegalArgumentException("万象图片展示任务参数不完整。");
        }
        String inputKey = keys.objectKey(command.inputKey());
        ImageDisplayRenditionPlan plan = imageRenditions.plan(inputKey, command.sourceMimeType());
        SubmitImageDisplayJob normalized = new SubmitImageDisplayJob(
            command.identity(), inputKey, command.sourceMimeType(), command.storageClass(),
            command.correlationData()
        );
        String token = token(plan.objectKey(), "DISPLAY_IMAGE_SLIM");
        PreparedMediaProcessingJob prepared = coordinator.prepareImageDisplay(
            normalized, plan, hash(token)
        );
        if (!prepared.shouldSubmit()) return prepared.current();

        try {
            MediaJobObject detail = submitToTencent(prepared.command(), token);
            return coordinator.finalizeSubmission(prepared, detail);
        } catch (RuntimeException exception) {
            coordinator.failSubmission(
                prepared, SensitiveValueRedactor.redact(exception.getMessage())
            );
            throw exception;
        }
    }

    public void handleCallback(String token, TencentCiTaskCallback callback) {
        coordinator.handleCallback(hash(token), callback);
    }

    public void retireImage(MediaObjectIdentity identity) {
        coordinator.retireImage(identity);
    }

    private MediaJobObject submitToTencent(SubmitMediaProcessingJob command, String token) {
        MediaInputObject input = new MediaInputObject();
        input.setObject(command.inputKey());
        MediaOutputObject output = new MediaOutputObject();
        output.setBucket(properties.getBucket());
        output.setRegion(properties.getRegion());
        output.setObject(command.outputKey());
        MediaPicProcessTemplateObject process = new MediaPicProcessTemplateObject();
        process.setIsPicInfo("true");
        process.setProcessRule(command.processRule());
        MediaJobOperation operation = new MediaJobOperation();
        operation.setPicProcess(process);
        operation.setOutput(output);
        operation.setUserData(command.correlationData());
        MediaJobsRequest request = new MediaJobsRequest();
        request.setBucketName(properties.getBucket());
        request.setTag("PicProcess");
        request.setInput(input);
        request.setOperation(operation);
        request.setCallBack(callbackBase() + "/" + token);
        request.setCallBackFormat("JSON");
        request.setCallBackType("Url");
        MediaJobResponse response = cos.createPicProcessJob(request);
        return response == null ? null : response.getJobsDetail();
    }

    private String callbackBase() {
        String value = properties.getCiCallbackUrl();
        if (blank(value)) throw new IllegalStateException("object-storage.ci-callback-url 未配置。");
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String token(String outputKey, String operation) {
        String secret = properties.getCdnTypeDKey();
        if (blank(secret)) throw new IllegalStateException("object-storage.cdn-type-d-key 未配置。");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] value = mac.doFinal(
                ("tencent-ci-callback\n" + operation + "\n" + outputKey)
                    .getBytes(StandardCharsets.UTF_8)
            );
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成万象回调令牌。", exception);
        }
    }

    static String hash(String value) {
        if (blank(value)) return "";
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算回调令牌摘要。", exception);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
