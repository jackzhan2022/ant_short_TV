package com.antshorttv.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.antshorttv.ai.AiSecretCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class AiVideoProviderDownloadTest {

    @Test
    void streamsProviderVideoToFile() throws Exception {
        byte[] body = new byte[1024 * 128];
        java.util.Arrays.fill(body, (byte) 7);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/video.mp4", exchange -> {
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        var target = Files.createTempFile("provider-video-test-", ".mp4");
        try {
            AiVideoProviderAdapter adapter = new AiVideoProviderAdapter(
                mock(AiSecretCodec.class), new ObjectMapper()
            );

            long size = adapter.downloadTo(
                "http://127.0.0.1:%d/video.mp4".formatted(server.getAddress().getPort()), target
            );

            assertThat(size).isEqualTo(body.length);
            assertThat(Files.readAllBytes(target)).isEqualTo(body);
        } finally {
            server.stop(0);
            Files.deleteIfExists(target);
        }
    }
}
