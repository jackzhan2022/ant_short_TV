package com.antshorttv.storage;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;

record SubmitMediaProcessingJob(
    Long tenantId,
    Long projectId,
    Long mediaObjectId,
    String operation,
    String inputKey,
    String outputKey,
    String processRule,
    String correlationData
) { }

record SubmitImageDisplayJob(
    MediaObjectIdentity identity,
    String inputKey,
    String sourceMimeType,
    String storageClass,
    String correlationData
) { }

record SubmittedMediaProcessingJob(String providerJobId, String status, String outputKey) { }

record TencentCiTaskCallback(
    @JsonProperty("EventName") String eventName,
    @JsonProperty("JobsDetail")
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    List<TencentCiJobDetail> jobsDetail
) {
    static TencentCiTaskCallback success(
        String jobId, String input, String output, String userData,
        long size, int width, int height, String eTag, String format
    ) {
        return new TencentCiTaskCallback("TaskFinish", List.of(new TencentCiJobDetail(
            "Success", null, jobId, "Success",
            new TencentCiInput(input),
            new TencentCiOperation(
                new TencentCiOutput(output), userData,
                List.of(new TencentCiPicProcessResult(new TencentCiProcessResult(size, width, height, eTag, format)))
            )
        )));
    }
}

record TencentCiJobDetail(
    @JsonProperty("Code") String code,
    @JsonProperty("Message") String message,
    @JsonProperty("JobId") String jobId,
    @JsonProperty("State") String state,
    @JsonProperty("Input") TencentCiInput input,
    @JsonProperty("Operation") TencentCiOperation operation
) { }
record TencentCiInput(@JsonProperty("Object") String object) { }
record TencentCiOperation(
    @JsonProperty("Output") TencentCiOutput output,
    @JsonProperty("UserData") String userData,
    @JsonProperty("PicProcessResult")
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    List<TencentCiPicProcessResult> picProcessResults
) {
    TencentCiOperation {
        if (picProcessResults != null && picProcessResults.size() != 1) {
            throw new IllegalArgumentException("A picture callback must contain exactly one process result.");
        }
    }

    TencentCiPicProcessResult picProcessResult() {
        return picProcessResults == null ? null : picProcessResults.get(0);
    }
}
record TencentCiOutput(@JsonProperty("Object") String object) { }
record TencentCiPicProcessResult(
    @JsonProperty("ProcessResult") TencentCiProcessResult processResult
) { }
record TencentCiProcessResult(
    @JsonProperty("Size") long size,
    @JsonProperty("Width") int width,
    @JsonProperty("Height") int height,
    @JsonProperty("Etag") String eTag,
    @JsonProperty("Format") String format
) { }
