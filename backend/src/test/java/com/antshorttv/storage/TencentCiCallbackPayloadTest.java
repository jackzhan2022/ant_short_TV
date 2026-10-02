package com.antshorttv.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;

class TencentCiCallbackPayloadTest {
    private final ObjectMapper mapper = new ObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Test
    void acceptsNativePictureCallbackResultArray() throws Exception {
        TencentCiTaskCallback callback = mapper.readValue(nativePayload(), TencentCiTaskCallback.class);

        assertThat(callback.jobsDetail()).hasSize(1);
        TencentCiOperation operation = callback.jobsDetail().get(0).operation();
        assertThat(operation.userData()).isEqualTo("asset-42-display");
        assertThat(operation.picProcessResult().processResult().size()).isEqualTo(90812L);
        assertThat(operation.picProcessResult().processResult().width()).isEqualTo(512);
        assertThat(operation.picProcessResult().processResult().eTag()).isEmpty();
    }

    @Test
    void rejectsEmptyPictureCallbackResultArray() throws Exception {
        var payload = mapper.readTree(nativePayload());
        ((ArrayNode) payload.at("/JobsDetail/0/Operation/PicProcessResult")).removeAll();

        assertThatThrownBy(() -> mapper.treeToValue(payload, TencentCiTaskCallback.class))
            .isInstanceOf(JsonMappingException.class)
            .hasMessageContaining("exactly one process result");
    }

    @Test
    void rejectsAmbiguousPictureCallbackResultArray() throws Exception {
        var payload = mapper.readTree(nativePayload());
        var results = (ArrayNode) payload.at("/JobsDetail/0/Operation/PicProcessResult");
        results.add(results.get(0).deepCopy());

        assertThatThrownBy(() -> mapper.treeToValue(payload, TencentCiTaskCallback.class))
            .isInstanceOf(JsonMappingException.class)
            .hasMessageContaining("exactly one process result");
    }

    @Test
    void acceptsTencentSingleObjectJobDetail() throws Exception {
        String json = """
            {"EventName":"TaskFinish","JobsDetail":{
              "Code":"Success","JobId":"job-1","State":"Success",
              "Input":{"Object":"materials/11/22/images/202610/42/v1/original.png"},
              "Operation":{
                "Output":{"Object":"materials/11/22/images/202610/42/v1/derived/display.png"},
                "UserData":"asset-42-display",
                "PicProcessResult":{"ProcessResult":{
                  "Size":"798037","Width":"852","Height":"1846","Etag":"","Format":"png"
                }}
              }
            }}
            """;
        ObjectMapper mapper = new ObjectMapper();

        assertThatCode(() -> mapper.readValue(json, TencentCiTaskCallback.class))
            .doesNotThrowAnyException();
        TencentCiTaskCallback callback = mapper.readValue(json, TencentCiTaskCallback.class);
        assertThat(callback.jobsDetail()).hasSize(1);
        assertThat(callback.jobsDetail().get(0).operation().picProcessResult().processResult().size())
            .isEqualTo(798037L);
    }

    private String nativePayload() {
        return """
            {"EventName":"TaskFinish","JobsDetail":[{
              "Code":"Success","CreationTime":"2026-10-02T00:52:25+0800",
              "EndTime":"2026-10-02T00:52:26+0800","JobId":"job-1","Message":"",
              "QueueId":"queue-1","QueueType":"PicProcess","StartTime":"2026-10-02T00:52:25+0800",
              "State":"Success","Tag":"PicProcess",
              "Input":{"BucketId":"test-bucket","Region":"ap-guangzhou",
                "Object":"materials/11/22/images/202610/42/v1/original.png"},
              "Operation":{"JobLevel":"0",
                "Output":{"Bucket":"test-bucket","Region":"ap-guangzhou","Force":false,
                  "Object":"materials/11/22/images/202610/42/v1/derived/display.png"},
                "PicProcess":{"IsPicInfo":true,"ProcessRule":"imageSlim"},
                "UserData":"asset-42-display","PicProcessResult":[{
                  "Code":"Success","Message":"","State":"Success",
                  "InputObjectName":"materials/11/22/images/202610/42/v1/original.png",
                  "InputObjectUrl":"[REDACTED]",
                  "ObjectName":"/materials/11/22/images/202610/42/v1/derived/display.png",
                  "ObjectUrl":"[REDACTED]",
                  "OriginalInfo":{"Etag":"original-etag","ImageInfo":{"Ave":"0x402060",
                    "Format":"png","Height":320,"Width":512,"Orientation":0,"Quality":100}},
                  "ProcessResult":{"Etag":"","Format":"png","Height":320,
                    "Quality":80,"Size":90812,"Width":512}
                }]}
            }]}
            """;
    }
}
