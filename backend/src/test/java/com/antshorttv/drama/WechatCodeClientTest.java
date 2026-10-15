package com.antshorttv.drama;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.http.client.methods.*;
import org.apache.http.entity.*;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.HttpVersion;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WechatCodeClientTest {
    CloseableHttpClient transport;
    CloseableHttpResponse response;
    WechatComponentClient client;
    @BeforeEach void setup() throws Exception {
        transport = mock(CloseableHttpClient.class);
        response = mock(CloseableHttpResponse.class);
        when(response.getStatusLine()).thenReturn(new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"));
        when(transport.execute(any(HttpUriRequest.class))).thenReturn(response);
        client = new WechatComponentClient(new ObjectMapper(), transport);
    }
    void response(String type, byte[] data) {
        var entity = new ByteArrayEntity(data); entity.setContentType(type);
        when(response.getEntity()).thenReturn(entity);
    }
    @Test void templatesUseGetComponentTokenAndOrdinaryType() throws Exception {
        response("application/json", "{\"errcode\":0,\"template_list\":[]}".getBytes(StandardCharsets.UTF_8));
        client.getTemplates("fixture-token");
        var request = org.mockito.ArgumentCaptor.forClass(HttpUriRequest.class);
        verify(transport).execute(request.capture());
        assertEquals("GET", request.getValue().getMethod());
        assertEquals("/wxa/gettemplatelist", request.getValue().getURI().getPath());
        assertEquals("access_token=fixture-token&template_type=0", request.getValue().getURI().getQuery());
    }
    @Test void successfulCommitCallsTransportOnce() throws Exception {
        response("application/json", "{\"errcode\":0}".getBytes(StandardCharsets.UTF_8));
        assertEquals(0, client.commitCode("fixture-token", Map.of("template_id", 0)).path("errcode").asInt());
        verify(transport, times(1)).execute(any(HttpUriRequest.class));
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "null", "html", "{\"errcode\":\"0\"}", "{\"errcode\":null}", "{\"errcode\":0} trailing"})
    void invalidCommitResultIsUnknown(String body) {
        response("application/json", body.getBytes(StandardCharsets.UTF_8));
        assertEquals("WECHAT_COMMIT_UNKNOWN", assertThrows(WechatCodeModels.CodeException.class,
            () -> client.commitCode("fixture-token", Map.of())).code());
    }
    @Test void timeoutIsUnknownWithoutRetryOrTokenLeak() throws Exception {
        when(transport.execute(any(HttpUriRequest.class))).thenThrow(new java.net.SocketTimeoutException("https://api.weixin.qq.com/?access_token=fixture-token"));
        var error = assertThrows(WechatCodeModels.CodeException.class, () -> client.commitCode("fixture-token", Map.of()));
        assertEquals("WECHAT_COMMIT_UNKNOWN", error.code());
        assertNull(error.getCause()); assertFalse(error.toString().contains("fixture-token"));
        verify(transport, times(1)).execute(any(HttpUriRequest.class));
    }
    @Test void pngAndJpegSucceedWithSingleEncodedPath() throws Exception {
        for (String format : new String[]{"png", "jpeg"}) {
            var output = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), format, output);
            response("image/" + format, output.toByteArray());
            assertArrayEquals(output.toByteArray(), client.trialQrCode("fixture-token", "pages/index?a=1").bytes());
        }
        verify(transport, times(2)).execute(argThat((HttpUriRequest r) -> r.getURI().getRawQuery().endsWith("path=pages%2Findex%3Fa%3D1")));
    }
    @ParameterizedTest @ValueSource(strings = {"image/svg+xml", "text/html", "image/png", "image/jpeg"})
    void activeContentOrFakeImageRejected(String type) {
        response(type, "<svg><script>bad</script></svg>".getBytes(StandardCharsets.UTF_8));
        assertThrows(WechatCodeModels.CodeException.class, () -> client.trialQrCode("fixture-token", null));
    }
    @Test void jsonErrorIsMappedWithoutRawMessage() {
        response("application/json", "{\"errcode\":40001,\"errmsg\":\"access_token=fixture-token\"}".getBytes(StandardCharsets.UTF_8));
        var error = assertThrows(WechatCodeModels.CodeException.class, () -> client.trialQrCode("fixture-token", null));
        assertEquals("WECHAT_CODE_REJECTED", error.code()); assertFalse(error.toString().contains("fixture-token"));
    }
    @Test void declaredAndStreamingOversizeRejected() throws Exception {
        response("image/png", new byte[WechatComponentClient.MAX_QRCODE + 1]);
        assertThrows(WechatCodeModels.CodeException.class, () -> client.trialQrCode("fixture-token", null));
        var active = new java.util.concurrent.atomic.AtomicReference<HttpUriRequest>();
        when(transport.execute(any(HttpUriRequest.class))).thenAnswer(i -> { active.set(i.getArgument(0)); return response; });
        var stream = new ByteArrayInputStream(new byte[WechatComponentClient.MAX_QRCODE + 1]) {
            @Override public void close() throws IOException {
                assertTrue(active.get().isAborted(), "超限后必须先中止连接再关闭流");
                super.close();
            }
        };
        var entity = new InputStreamEntity(stream, -1);
        entity.setContentType("image/png"); when(response.getEntity()).thenReturn(entity);
        assertThrows(WechatCodeModels.CodeException.class, () -> client.trialQrCode("fixture-token", null));
        verify(transport, times(2)).execute(any(HttpUriRequest.class));
    }
    @Test void mismatchedImageMimeAndJsonSuccessAreNotQrCodes() throws Exception {
        var output = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", output);
        response("image/jpeg", output.toByteArray());
        assertThrows(WechatCodeModels.CodeException.class, () -> client.trialQrCode("fixture-token", null));
        response("application/json", "{\"errcode\":0}".getBytes(StandardCharsets.UTF_8));
        assertThrows(WechatCodeModels.CodeException.class, () -> client.trialQrCode("fixture-token", null));
    }
    @Test void boundedReadConsumesAtMostLimitPlusOne() throws Exception {
        var stream = new ByteArrayInputStream(new byte[100]);
        assertThrows(IOException.class, () -> WechatComponentClient.readBounded(stream, 10));
        assertEquals(89, stream.available());
    }
    @Test void httpErrorDoesNotLeakOrRetryCommit() throws Exception {
        when(response.getStatusLine()).thenReturn(new BasicStatusLine(HttpVersion.HTTP_1_1, 502, "access_token=fixture-token"));
        var error = assertThrows(WechatCodeModels.CodeException.class, () -> client.commitCode("fixture-token", Map.of()));
        assertEquals("WECHAT_COMMIT_UNKNOWN", error.code()); assertFalse(error.toString().contains("fixture-token"));
        verify(transport, times(1)).execute(any(HttpUriRequest.class));
    }
}
