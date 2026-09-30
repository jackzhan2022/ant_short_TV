package com.antshorttv.storage;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("JobsDetail") List<TencentCiJobDetail> jobsDetail
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
                new TencentCiPicProcessResult(new TencentCiProcessResult(size, width, height, eTag, format))
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
    @JsonProperty("PicProcessResult") TencentCiPicProcessResult picProcessResult
) { }
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
