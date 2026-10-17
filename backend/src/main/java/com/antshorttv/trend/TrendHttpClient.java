package com.antshorttv.trend;

import com.antshorttv.trend.TrendModels.HttpPage;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class TrendHttpClient {
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NEVER).build();

    public HttpPage fetch(URI uri) throws IOException, InterruptedException {
        if (!TrendSource.allowedRequest(uri)) throw new IllegalArgumentException("仅支持登记来源的确切公开地址");
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(25))
            .header("User-Agent", "AntShortTVTrendCollector/1.0")
            .header("Accept", "text/html,text/plain;q=0.9").GET().build();
        long started = System.nanoTime();
        var future = client.sendAsync(request, info -> new LimitedBody(MAX_BYTES));
        try {
            // The deadline includes reading the body, not just receiving response headers.
            var response = future.get(30, TimeUnit.SECONDS);
            return new HttpPage(response.statusCode(), response.headers().firstValue("Content-Type").orElse(""),
                new String(response.body(), StandardCharsets.UTF_8), response.headers().map().entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey, e -> e.getValue().isEmpty()?"":e.getValue().get(0))),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new IOException("来源请求超过30秒，已取消", ex);
        } catch (ExecutionException ex) {
            throw new IOException("来源请求失败: " + ex.getCause().getMessage(), ex.getCause());
        } catch (InterruptedException ex) {
            future.cancel(true);
            throw ex;
        }
    }

    static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;

        LimitedBody(int limit) { this.limit = limit; }
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            if (result.isDone()) return;
            for (ByteBuffer buffer : buffers) {
                int count = buffer.remaining();
                if (count > limit - bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(new IOException("来源响应超过大小上限"));
                    return;
                }
                byte[] chunk = new byte[count];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(error); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
