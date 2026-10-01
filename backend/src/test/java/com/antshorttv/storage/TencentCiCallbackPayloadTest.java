package com.antshorttv.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class TencentCiCallbackPayloadTest {
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
}
