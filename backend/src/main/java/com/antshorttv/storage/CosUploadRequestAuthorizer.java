package com.antshorttv.storage;

import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.auth.COSCredentialsProvider;
import com.qcloud.cos.auth.COSSessionCredentials;
import com.qcloud.cos.auth.COSSigner;
import com.qcloud.cos.http.HttpMethodName;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CosUploadRequestAuthorizer {
    private static final Set<String> SIGNABLE_HEADERS = Set.of(
        "host", "cache-control", "content-type", "content-length", "content-md5",
        "content-disposition", "content-encoding", "expires", "x-cos-content-sha1",
        "x-cos-meta-md5", "x-cos-sdk-retry", "x-cos-storage-class"
    );
    private static final Set<String> LIST_PART_QUERY = Set.of(
        "uploadId", "encoding-type", "max-parts", "part-number-marker"
    );
    private static final Set<String> LIST_UPLOAD_QUERY = Set.of(
        "uploads", "prefix", "encoding-type", "max-uploads", "key-marker", "upload-id-marker"
    );

    private final COSCredentialsProvider credentialsProvider;
    private final ObjectStorageProperties properties;
    private final Clock clock;

    @Autowired
    public CosUploadRequestAuthorizer(
        COSCredentialsProvider credentialsProvider,
        ObjectStorageProperties properties
    ) {
        this(credentialsProvider, properties, Clock.systemUTC());
    }

    CosUploadRequestAuthorizer(
        COSCredentialsProvider credentialsProvider,
        ObjectStorageProperties properties,
        Clock clock
    ) {
        this.credentialsProvider = credentialsProvider;
        this.properties = properties;
        this.clock = clock;
    }

    public CosUploadAuthorization authorize(
        String expectedObjectKey,
        CosUploadAuthorizationRequest request
    ) {
        HttpMethodName method = validate(expectedObjectKey, request);
        COSCredentials credentials = credentialsProvider.getCredentials();
        if (!(credentials instanceof COSSessionCredentials sessionCredentials)) {
            throw new IllegalStateException("COS 上传签名必须使用 CVM 实例角色会话凭证。");
        }
        Instant now = clock.instant();
        Instant expiresAt = now.plusSeconds(properties.getUploadSignatureSeconds());
        String authorization = new COSSigner().buildAuthorizationStr(
            method,
            request.pathname(),
            normalizedHeaders(request.headers()),
            safe(request.query()),
            credentials,
            Date.from(now.minusSeconds(30)),
            Date.from(expiresAt),
            true
        );
        return new CosUploadAuthorization(
            authorization,
            sessionCredentials.getSessionToken(),
            expiresAt.getEpochSecond()
        );
    }

    private HttpMethodName validate(
        String expectedObjectKey,
        CosUploadAuthorizationRequest request
    ) {
        if (expectedObjectKey == null || expectedObjectKey.isBlank() || request == null
            || request.method() == null || request.pathname() == null) {
            throw new IllegalArgumentException("COS 上传签名请求路径不合法。");
        }
        HttpMethodName method;
        try {
            method = HttpMethodName.valueOf(request.method().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("COS 上传签名请求方法不合法。", exception);
        }
        Map<String, String> query = safe(request.query());
        // The SDK looks up resumable uploads at the bucket root before initiating multipart upload.
        boolean multipartLookup = method == HttpMethodName.GET && "/".equals(request.pathname())
            && validMultipartLookup(expectedObjectKey, query);
        if (!("/" + expectedObjectKey).equals(request.pathname()) && !multipartLookup) {
            throw new IllegalArgumentException("COS 上传签名请求路径不合法。");
        }
        if (!multipartLookup && !validOperation(method, query)) {
            throw new IllegalArgumentException("COS 上传签名请求操作不合法。");
        }
        Map<String, String> headers = normalizedHeaders(request.headers());
        for (String header : headers.keySet()) {
            String normalized = header.toLowerCase(Locale.ROOT);
            if (!SIGNABLE_HEADERS.contains(normalized)) {
                throw new IllegalArgumentException("COS 上传签名请求头不合法。");
            }
        }
        String expectedHost = "%s.cos.%s.myqcloud.com".formatted(
            properties.getBucket(), properties.getRegion()
        );
        if (!expectedHost.equalsIgnoreCase(headers.get("host"))) {
            throw new IllegalArgumentException("COS 上传签名请求主机不合法。");
        }
        if (createsObject(method, query)) {
            String storageClass = headers.get("x-cos-storage-class");
            if (storageClass == null
                || !properties.getStorageClass().equalsIgnoreCase(storageClass.trim())) {
                throw new IllegalArgumentException("COS 上传存储类型不合法。");
            }
        }
        return method;
    }

    private boolean validMultipartLookup(String expectedObjectKey, Map<String, String> query) {
        return "".equals(query.get("uploads")) && expectedObjectKey.equals(query.get("prefix"))
            && LIST_UPLOAD_QUERY.containsAll(query.keySet())
            && (!query.containsKey("key-marker") || expectedObjectKey.equals(query.get("key-marker")));
    }

    private boolean createsObject(HttpMethodName method, Map<String, String> query) {
        return (method == HttpMethodName.PUT && query.isEmpty())
            || (method == HttpMethodName.POST && query.keySet().equals(Set.of("uploads")));
    }

    private boolean validOperation(HttpMethodName method, Map<String, String> query) {
        return switch (method) {
            case PUT -> query.isEmpty()
                || query.keySet().equals(Set.of("partNumber", "uploadId"));
            case POST -> query.keySet().equals(Set.of("uploads"))
                || query.keySet().equals(Set.of("uploadId"));
            case GET -> query.containsKey("uploadId") && LIST_PART_QUERY.containsAll(query.keySet());
            case DELETE -> query.keySet().equals(Set.of("uploadId"));
            case HEAD -> query.isEmpty();
            default -> false;
        };
    }

    private Map<String, String> safe(Map<String, String> values) {
        return values == null ? Map.of() : Map.copyOf(values);
    }

    private Map<String, String> normalizedHeaders(Map<String, String> headers) {
        Map<String, String> normalized = new LinkedHashMap<>();
        safe(headers).forEach((key, value) ->
            normalized.put(key.toLowerCase(Locale.ROOT), value)
        );
        return Map.copyOf(normalized);
    }
}
