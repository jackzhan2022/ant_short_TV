package com.antshorttv.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DefaultAiGatewayTest {
    @Test
    void preservesCacheEvidenceForTextAccounting() {
        var router=mock(AiModelRouter.class);var writer=mock(AiCallLogWriter.class);
        var limiter=mock(AiProviderConcurrencyLimiter.class);var adapter=mock(AiProviderAdapter.class);
        var model=new AiModelEntity();model.setId(5L);
        var provider=new AiProviderEntity();provider.setId(6L);
        var route=new AiModelRoute(model,provider,new AiProviderConfigEntity(),adapter);
        when(router.route(5L,"TEXT")).thenReturn(route);
        when(limiter.execute(any(),any())).thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
        when(adapter.text(any(),any(),any(),any())).thenReturn(new AiTextResponse(
            "ok","req",100,20,120,1L,Map.of(),"stop",false,List.of(),80,10));
        new DefaultAiGateway(router,writer,limiter).text(new AiContext(1L,2L,3L,4L,5L,"review","trace"),
            new AiTextRequest(null,"prompt",0.2,1024,null));
        var log=ArgumentCaptor.forClass(AiInvocationLogRequest.class);
        verify(writer).record(log.capture());
        assertThat(log.getValue().cachedInputTokens()).isEqualTo(80);
        assertThat(log.getValue().cacheWriteTokens()).isEqualTo(10);
    }
}
