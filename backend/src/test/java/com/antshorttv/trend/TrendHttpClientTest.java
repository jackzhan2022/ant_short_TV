package com.antshorttv.trend;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

class TrendHttpClientTest {
    @Test void refusesArbitraryUrlsBeforeIssuingNetworkRequests() {
        var client = new TrendHttpClient();
        for (String url : List.of("http://hongguoduanju.com/rank/hot-drama", "https://example.com/rank/hot-drama", "https://hongguoduanju.com/player/1/2", "https://user@hongguoduanju.com/rank/hot-drama", "https://reelpulse.net/pro/", "https://reelpulse.net/api/", "https://reelpulse.net/trends?url=http://127.0.0.1")) {
            assertThatThrownBy(() -> client.fetch(URI.create(url))).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void limitsTheStreamingBodyBeforeAllocatingAnUnboundedResponse() {
        var body = new TrendHttpClient.LimitedBody(3);
        var subscription = mock(Flow.Subscription.class);
        body.onSubscribe(subscription);
        body.onNext(List.of(ByteBuffer.wrap(new byte[]{1,2}), ByteBuffer.wrap(new byte[]{3,4})));
        verify(subscription).cancel();
        assertThat(body.getBody().toCompletableFuture()).isCompletedExceptionally();
    }

    @Test void preservesCompleteBodyAndPropagatesTransportErrors() throws Exception {
        var body = new TrendHttpClient.LimitedBody(10);
        body.onSubscribe(mock(Flow.Subscription.class));
        body.onNext(List.of(ByteBuffer.wrap(new byte[]{1,2,3})));
        body.onComplete();
        assertThat(body.getBody().toCompletableFuture().get()).containsExactly(1,2,3);
        var failed = new TrendHttpClient.LimitedBody(10);
        failed.onError(new java.io.IOException("connection reset"));
        assertThat(failed.getBody().toCompletableFuture()).isCompletedExceptionally();
    }
}
