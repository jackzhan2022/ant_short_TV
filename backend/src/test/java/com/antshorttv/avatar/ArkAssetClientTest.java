package com.antshorttv.avatar;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArkAssetClientTest {
    @Test void signsCreateAndPollsOfficialEnvelope() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).startsWith("HMAC-SHA256 Credential=test-ak/");
            var body=new ObjectMapper().readTree(exchange.getRequestBody());
            assertThat(body.path("ProjectName").asText()).isEqualTo("default");
            String response;
            if(exchange.getRequestURI().getQuery().contains("Action=CreateAsset&")) {
                assertThat(body.path("GroupId").asText()).isEqualTo("group-1");
                assertThat(body.path("AssetType").asText()).isEqualTo("Image");
                response="{\"Result\":{\"Id\":\"asset-1\"}}";
            } else response="{\"Result\":{\"Id\":\"asset-1\",\"Status\":\"Active\",\"URL\":\"https://example.com/image.png\"}}";
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.start();
        try {
            var client=new ArkAssetClient(new ObjectMapper(),"test-ak","test-sk","cn-beijing","default","http://127.0.0.1:"+server.getAddress().getPort());
            assertThat(client.createAsset("group-1","pose","https://example.com/source.png")).isEqualTo("asset-1");
            assertThat(client.getAsset("asset-1").status()).isEqualTo("Active");
        } finally {server.stop(0);}
    }
    @Test void mapsFailureEvenOnHttp200AndRejectsUnknownStatus() throws Exception {
        var mapper=new ObjectMapper();
        var failed=ArkAssetClient.asset(mapper.readTree("{\"Status\":\"Failed\",\"Error\":{\"Code\":\"DownloadFailed\",\"Message\":\"download failed\"}}"));
        assertThat(failed.errorCode()).isEqualTo("DownloadFailed");
        assertThatThrownBy(()->ArkAssetClient.asset(mapper.readTree("{\"Status\":\"Surprise\"}"))).isInstanceOf(IllegalStateException.class);
    }
    @Test void requiresAssetAccessKeysInsteadOfSilentlyUsingInferenceKeys() {
        var client=new ArkAssetClient(new ObjectMapper(),"","","cn-beijing","default","https://ark.cn-beijing.volcengineapi.com");
        assertThatThrownBy(client::requireConfigured).isInstanceOf(com.antshorttv.common.BusinessException.class)
            .hasMessageContaining("ARK_ASSET_ACCESS_KEY").hasMessageContaining("ARK_ASSET_SECRET_KEY");
    }

    @Test void listsAllPagesInTheRequestedProjectAndDeletesWithThatScope() throws Exception {
        var mapper = new ObjectMapper();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var calls = new java.util.ArrayList<String>();
        server.createContext("/", exchange -> {
            var body = mapper.readTree(exchange.getRequestBody());
            assertThat(body.path("ProjectName").asText()).isEqualTo("another-project");
            String action = exchange.getRequestURI().getQuery();
            calls.add(action);
            String response;
            if (action.contains("Action=ListAssets&")) {
                assertThat(body.path("Filter").path("GroupType").asText()).isIn("AIGC","LivenessFace");
                int page = body.path("PageNumber").asInt();
                response = "LivenessFace".equals(body.path("Filter").path("GroupType").asText())
                    ? "{\"Result\":{\"Items\":[{\"Id\":\"face-asset\"}],\"TotalCount\":1}}"
                    : "{\"Result\":{\"Items\":[{\"Id\":\"asset-"+page+"\"}],\"TotalCount\":2}}";
            } else response = "{\"Result\":{}}";
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.start();
        try {
            var client=new ArkAssetClient(mapper,"test-ak","test-sk","cn-beijing","default","http://127.0.0.1:"+server.getAddress().getPort());
            assertThat(client.listAssets("another-project")).extracting(n -> n.path("Id").asText()).containsExactly("asset-1","asset-2","face-asset");
            client.deleteAsset("another-project","asset-1");
            client.deleteGroup("another-project","group-1");
            assertThat(calls).hasSize(5);
        } finally {server.stop(0);}
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={200,400,503})
    void retainsSafeProviderDiagnosticsWithoutLeakingSignedUrlsOrCredentials(int status) throws Exception {
        var mapper = new ObjectMapper();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            var error = Map.of("ResponseMetadata", Map.of("RequestId", "req-download-123", "Error", Map.of(
                "Code", "InvalidParameter.DownloadFailed",
                "Message", "Download returned HTTP 403: https://cos.example/original.jpg?q-signature=private-sign&x-cos-security-token=private-token AK=test-ak SK=test-sk\nretry later")));
            byte[] bytes = mapper.writeValueAsBytes(error);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var client = new ArkAssetClient(mapper, "test-ak", "test-sk", "cn-beijing", "default", "http://127.0.0.1:" + server.getAddress().getPort());
            assertThatThrownBy(() -> client.createAsset("group-1", "pose", "https://cos.example/source.png"))
                .isInstanceOfSatisfying(ArkAssetClient.ProviderException.class, error -> {
                    assertThat(error.code()).isEqualTo("InvalidParameter.DownloadFailed");
                    assertThat(error.definitive()).isEqualTo(status<500);
                    assertThat(error.getMessage()).contains("HTTP 403", "req-download-123")
                        .doesNotContain("https://", "private-sign", "private-token", "test-ak", "test-sk", "\n");
                });
        } finally { server.stop(0); }
    }

}
