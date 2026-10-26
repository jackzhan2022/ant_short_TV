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

}
