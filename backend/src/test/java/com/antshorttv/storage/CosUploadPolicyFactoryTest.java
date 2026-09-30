package com.antshorttv.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CosUploadPolicyFactoryTest {

    @Test
    void grantsOnlyMultipartWriteActionsForOneUploadPrefix() throws Exception {
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setBucket("antv-1418200553");
        properties.setRegion("ap-guangzhou");
        CosUploadPolicyFactory factory = new CosUploadPolicyFactory(properties, new ObjectMapper());

        String policy = factory.policyFor("uploads/11/22/session-33/source.mp4");
        JsonNode root = new ObjectMapper().readTree(policy);
        JsonNode statement = root.path("statement").get(0);

        assertThat(root.path("version").asText()).isEqualTo("2.0");
        assertThat(statement.path("resource").get(0).asText()).isEqualTo(
            "qcs::cos:ap-guangzhou:uid/1418200553:antv-1418200553/uploads/11/22/session-33/source.mp4"
        );
        assertThat(statement.path("action").toString())
            .contains("cos:PutObject", "cos:InitiateMultipartUpload", "cos:UploadPart",
                "cos:ListParts", "cos:CompleteMultipartUpload", "cos:AbortMultipartUpload", "cos:HeadObject")
            .doesNotContain("cos:GetObject", "cos:DeleteObject", "cos:ListBucket");
    }
}
