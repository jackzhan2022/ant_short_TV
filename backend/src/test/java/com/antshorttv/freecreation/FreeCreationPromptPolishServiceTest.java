package com.antshorttv.freecreation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.antshorttv.ai.*;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.review.ReviewWorkbenchService;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class FreeCreationPromptPolishServiceTest {
    private final TenantContextResolver tenants = mock(TenantContextResolver.class);
    private final ReviewWorkbenchService reviews = mock(ReviewWorkbenchService.class);
    private final AiInvocationService invocation = mock(AiInvocationService.class);
    private final FreeCreationPromptPolishService service = new FreeCreationPromptPolishService(tenants, reviews, invocation);

    @BeforeEach void setUp() {
        when(tenants.requireActiveMember(1L)).thenReturn(new TenantContext(2L, 1L, 3L, "OWNER"));
        when(reviews.pointEstimateModelId(1L)).thenReturn(42L);
    }

    @Test void usesTheReviewTextModelAndAuthenticatedTenantForVideoPolishing() {
        respond(" 镜头缓慢推进雨夜街景 ", false);
        assertThat(service.polish(1L, "VIDEO", " 雨夜街景 ")).isEqualTo("镜头缓慢推进雨夜街景");
        var captured = ArgumentCaptor.forClass(AiInvocationRequest.class);
        verify(invocation).invokeText(captured.capture());
        var request = captured.getValue();
        assertThat(request.modelId()).isEqualTo(42L);
        assertThat(request.tenantId()).isEqualTo(1L);
        assertThat(request.userId()).isEqualTo(2L);
        assertThat(request.textRequest().userPrompt()).isEqualTo("雨夜街景");
        assertThat(request.textRequest().systemPrompt()).contains("视频", "2000", "原意");
        assertThat(request.textRequest().maxTokens()).isPositive();
        verify(reviews).pointEstimateModelId(1L);
    }

    @Test void supportsImagePrompts() {
        respond("雨夜街景，电影质感", false);
        assertThat(service.polish(1L, "IMAGE", "雨夜街景")).contains("电影质感");
        verify(invocation).invokeText(argThat(request -> request.textRequest().systemPrompt().contains("图片")));
    }

    @Test void failsWithoutAnAvailableReviewModel() {
        when(reviews.pointEstimateModelId(1L)).thenThrow(new BusinessException(ErrorCode.AI_MODEL_NOT_FOUND, "暂无可用的文本模型。"));
        assertThatThrownBy(() -> service.polish(1L, "VIDEO", "雨夜街景")).hasMessageContaining("文本模型");
        verifyNoInteractions(invocation);
    }

    @Test void rejectsUnauthorizedRequestsBeforeModelInvocation() {
        when(tenants.requireActiveMember(1L)).thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "无权访问"));
        assertThatThrownBy(() -> service.polish(1L, "VIDEO", "雨夜街景")).isInstanceOf(BusinessException.class);
        verifyNoInteractions(reviews, invocation);
    }

    @ParameterizedTest @ValueSource(strings = {"", "   "})
    void rejectsEmptyResults(String result) {
        respond(result, false);
        assertThatThrownBy(() -> service.polish(1L, "VIDEO", "雨夜街景")).hasMessageContaining("润色");
    }

    @Test void rejectsTruncatedOrOversizedResults() {
        respond("不完整的文本", true);
        assertThatThrownBy(() -> service.polish(1L, "VIDEO", "雨夜街景")).hasMessageContaining("润色");
        respond("字".repeat(2001), false);
        assertThatThrownBy(() -> service.polish(1L, "VIDEO", "雨夜街景")).hasMessageContaining("润色");
    }

    @Test void validatesInputsBeforeCallingTheProvider() {
        assertThatThrownBy(() -> service.polish(1L, "AUDIO", "声音")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.polish(1L, "VIDEO", " ")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.polish(1L, "VIDEO", "字".repeat(2001))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(invocation);
    }

    @SuppressWarnings("unchecked")
    private void respond(String content, boolean truncated) {
        AiInvocationResult<AiTextResponse> result = mock(AiInvocationResult.class);
        when(result.content()).thenReturn(content);
        when(result.response()).thenReturn(new AiTextResponse(content, "request", 10, 20, 30, 1L, java.util.Map.of(), "stop", truncated));
        when(invocation.invokeText(any())).thenReturn(result);
    }
}
