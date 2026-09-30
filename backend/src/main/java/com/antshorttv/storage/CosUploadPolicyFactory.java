package com.antshorttv.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class CosUploadPolicyFactory {
    private static final String[] ACTIONS = {
        "name/cos:PutObject",
        "name/cos:InitiateMultipartUpload",
        "name/cos:UploadPart",
        "name/cos:ListParts",
        "name/cos:CompleteMultipartUpload",
        "name/cos:AbortMultipartUpload",
        "name/cos:HeadObject"
    };

    private final ObjectStorageProperties properties;
    private final ObjectMapper objectMapper;
    private final ObjectStorageKeyFactory keys = new ObjectStorageKeyFactory();

    public CosUploadPolicyFactory(ObjectStorageProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String policyFor(String objectKey) {
        String key = keys.objectKey(objectKey);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("version", "2.0");
        ObjectNode statement = root.putArray("statement").addObject();
        statement.put("effect", "allow");
        ArrayNode actions = statement.putArray("action");
        for (String action : ACTIONS) {
            actions.add(action);
        }
        statement.putArray("resource").add(resource(key));
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成 COS 临时授权策略。", exception);
        }
    }

    private String resource(String objectKey) {
        String bucket = properties.getBucket();
        int separator = bucket.lastIndexOf('-');
        if (separator < 0 || separator == bucket.length() - 1) {
            throw new IllegalStateException("COS 存储桶名称必须包含 APPID 后缀。");
        }
        String appId = bucket.substring(separator + 1);
        return "qcs::cos:%s:uid/%s:%s/%s".formatted(
            properties.getRegion(), appId, bucket, objectKey
        );
    }
}
