package com.antshorttv.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.BasicSessionCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.auth.COSCredentialsProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CosUploadRequestAuthorizerTest {
    private static final String OBJECT_KEY = "uploads/11/session-1/source.mp4";
    private static final String UPLOAD_HOST =
        "antv-1418200553.cos.ap-guangzhou.myqcloud.com";

    @Test
    void signsAnAllowedMultipartRequestWithTheInstanceSecurityToken() {
        CosUploadRequestAuthorizer authorizer = authorizer(sessionCredentials());

        CosUploadAuthorization value = authorizer.authorize(
            OBJECT_KEY,
            new CosUploadAuthorizationRequest(
                "PUT",
                "/" + OBJECT_KEY,
                Map.of("partNumber", "1", "uploadId", "upload-1"),
                Map.of("Host", UPLOAD_HOST)
            )
        );

        assertThat(value.authorization()).startsWith("q-sign-algorithm=sha1");
        assertThat(value.securityToken()).isEqualTo("instance-token");
        assertThat(value.expiresAt()).isEqualTo(1_700_000_300L);
        assertThat(value.toString()).doesNotContain("instance-key");
        assertThat(Arrays.stream(CosUploadAuthorization.class.getRecordComponents())
            .map(component -> component.getName().toLowerCase()))
            .noneMatch(name -> name.contains("secretid") || name.contains("secretkey"));
    }

    @Test
    void signsCosSdkRetryRequests() {
        CosUploadRequestAuthorizer authorizer = authorizer(sessionCredentials());

        CosUploadAuthorization value = authorizer.authorize(
            OBJECT_KEY,
            new CosUploadAuthorizationRequest(
                "PUT",
                "/" + OBJECT_KEY,
                Map.of("partNumber", "1", "uploadId", "upload-1"),
                Map.of(
                    "Host", UPLOAD_HOST,
                    "x-cos-sdk-retry", "true"
                )
            )
        );

        assertThat(value.authorization()).contains("x-cos-sdk-retry");
    }

    @Test
    void signsAllCosSdkMultipartOperationShapes() {
        CosUploadRequestAuthorizer authorizer = authorizer(sessionCredentials());
        Map<String, String> headers = Map.of("Host", UPLOAD_HOST);
        List<CosUploadAuthorizationRequest> requests = List.of(
            new CosUploadAuthorizationRequest(
                "POST", "/" + OBJECT_KEY, Map.of("uploads", ""),
                Map.of("Host", UPLOAD_HOST, "x-cos-storage-class", "INTELLIGENT_TIERING")
            ),
            new CosUploadAuthorizationRequest(
                "PUT", "/" + OBJECT_KEY,
                Map.of("partNumber", "1", "uploadId", "upload-1"), headers
            ),
            new CosUploadAuthorizationRequest(
                "GET", "/" + OBJECT_KEY,
                Map.of("uploadId", "upload-1", "max-parts", "1000"), headers
            ),
            new CosUploadAuthorizationRequest(
                "POST", "/" + OBJECT_KEY, Map.of("uploadId", "upload-1"),
                Map.of(
                    "Host", "antv-1418200553.cos.ap-guangzhou.myqcloud.com",
                    "Content-Type", "application/xml",
                    "Content-MD5", "checksum"
                )
            ),
            new CosUploadAuthorizationRequest(
                "DELETE", "/" + OBJECT_KEY, Map.of("uploadId", "upload-1"), headers
            ),
            new CosUploadAuthorizationRequest("HEAD", "/" + OBJECT_KEY, Map.of(), headers)
        );

        for (CosUploadAuthorizationRequest request : requests) {
            assertThatCode(() -> authorizer.authorize(OBJECT_KEY, request))
                .doesNotThrowAnyException();
        }
    }

    @Test
    void rejectsMissingOrDifferentBucketHost() {
        CosUploadRequestAuthorizer authorizer = authorizer(sessionCredentials());

        assertThatThrownBy(() -> authorizer.authorize(
            OBJECT_KEY,
            new CosUploadAuthorizationRequest(
                "HEAD", "/" + OBJECT_KEY, Map.of(), Map.of()
            )
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("主机");
        assertThatThrownBy(() -> authorizer.authorize(
            OBJECT_KEY,
            new CosUploadAuthorizationRequest(
                "HEAD", "/" + OBJECT_KEY, Map.of(),
                Map.of("host", "other-1418200553.cos.ap-guangzhou.myqcloud.com")
            )
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("主机");
    }

    @Test
    void requiresConfiguredStorageClassWhenCreatingTheObject() {
        CosUploadRequestAuthorizer authorizer = authorizer(sessionCredentials());

        assertThatThrownBy(() -> authorizer.authorize(
            OBJECT_KEY,
            new CosUploadAuthorizationRequest(
                "POST", "/" + OBJECT_KEY, Map.of("uploads", ""),
                Map.of("host", UPLOAD_HOST)
            )
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("存储类型");
        assertThatThrownBy(() -> authorizer.authorize(
            OBJECT_KEY,
            new CosUploadAuthorizationRequest(
                "PUT", "/" + OBJECT_KEY, Map.of(),
                Map.of("host", UPLOAD_HOST, "x-cos-storage-class", "STANDARD")
            )
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("存储类型");
    }

    @Test
    void rejectsRequestsOutsideTheAssignedObjectOrMultipartContract() {
        CosUploadRequestAuthorizer authorizer = authorizer(sessionCredentials());

        assertThatThrownBy(() -> authorizer.authorize(
            OBJECT_KEY,
            new CosUploadAuthorizationRequest(
                "PUT", "/uploads/12/other/source.mp4", Map.of(), Map.of()
            )
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> authorizer.authorize(
            OBJECT_KEY,
            new CosUploadAuthorizationRequest(
                "PUT", "/" + OBJECT_KEY, Map.of("acl", "public-read"), Map.of()
            )
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> authorizer.authorize(
            OBJECT_KEY,
            new CosUploadAuthorizationRequest(
                "PUT", "/" + OBJECT_KEY, Map.of(), Map.of("x-cos-acl", "public-read")
            )
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> authorizer.authorize(
            OBJECT_KEY,
            new CosUploadAuthorizationRequest(
                "PUT", "/" + OBJECT_KEY, Map.of(), Map.of("x-cos-meta-owner", "other")
            )
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesToSignWithoutInstanceRoleSessionCredentials() {
        CosUploadRequestAuthorizer authorizer = authorizer(
            new BasicCOSCredentials("permanent-id", "permanent-key")
        );

        assertThatThrownBy(() -> authorizer.authorize(
            OBJECT_KEY,
            new CosUploadAuthorizationRequest(
                "PUT", "/" + OBJECT_KEY, Map.of(),
                Map.of("host", UPLOAD_HOST, "x-cos-storage-class", "INTELLIGENT_TIERING")
            )
        )).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("实例角色");
    }

    private CosUploadRequestAuthorizer authorizer(COSCredentials credentials) {
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setUploadSignatureSeconds(300);
        return new CosUploadRequestAuthorizer(
            new FixedCredentialsProvider(credentials),
            properties,
            Clock.fixed(Instant.ofEpochSecond(1_700_000_000L), ZoneOffset.UTC)
        );
    }

    private COSCredentials sessionCredentials() {
        return new BasicSessionCredentials("instance-id", "instance-key", "instance-token");
    }

    private record FixedCredentialsProvider(COSCredentials credentials)
        implements COSCredentialsProvider {
        @Override
        public COSCredentials getCredentials() {
            return credentials;
        }

        @Override
        public void refresh() {
        }
    }
}
