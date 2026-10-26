package com.antshorttv.video;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.script.StoryboardPromptCompiler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class AvailableVideoReferencesTest {
    private final ObjectMapper json = new ObjectMapper();
    private final VideoTaskReferenceResolver resolver = mock(VideoTaskReferenceResolver.class);
    private final StoryboardPromptCompiler compiler = new StoryboardPromptCompiler();

    @Test
    void skipsOnlyUnavailableImagesAndRenumbersTheRemainingReferences() throws Exception {
        when(resolver.resolve(eq(1L), eq(2L), anyList())).thenAnswer(call -> {
            StoryboardPromptCompiler.Reference ref = ((List<StoryboardPromptCompiler.Reference>) call.getArgument(2)).get(0);
            if (ref.sourceId() == 10L) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "图片已删除");
            return List.of();
        });
        var document = json.readTree("""
            {"version":2,"nodes":[
              {"type":"mention","mediaType":"IMAGE","sourceType":"ASSET_VISUAL_VARIANT","sourceId":10,"variantId":10,"displayName":"缺图角色"},
              {"type":"text","text":"走进"},
              {"type":"mention","mediaType":"IMAGE","sourceType":"ASSET_VISUAL_VARIANT","sourceId":11,"variantId":11,"displayName":"餐厅"},
              {"type":"mention","mediaType":"VIDEO","sourceType":"VIDEO_MATERIAL","sourceId":12,"displayName":"动作参考"}]}
            """);
        var result = new AvailableVideoReferences(compiler, resolver).compile(1L, 2L, document, List.of());
        assertThat(result.compiled().text()).isEqualTo("缺图角色走进图片1视频1");
        assertThat(result.compiled().references()).extracting(StoryboardPromptCompiler.Reference::sourceId)
            .containsExactly(11L, 12L);
        assertThat(result.omitted()).singleElement().satisfies(item -> {
            assertThat(item.displayName()).isEqualTo("缺图角色");
            assertThat(item.reason()).isEqualTo("IMAGE_UNAVAILABLE");
        });
        assertThat(document.toString()).contains("\"sourceId\":10");
    }

    @Test
    void dropsUnavailableFormalBindingsWhileKeepingTheirNamesAndTheOtherBindings() throws Exception {
        when(resolver.resolve(eq(1L), eq(2L), anyList())).thenAnswer(call -> {
            StoryboardPromptCompiler.Reference ref = ((List<StoryboardPromptCompiler.Reference>) call.getArgument(2)).get(0);
            if (ref.sourceId() == 10L) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未生成");
            return List.of();
        });
        var document = json.readTree("""
            {"version":2,"nodes":[{"type":"mention","mediaType":"IMAGE","sourceType":"ASSET_VISUAL_VARIANT", "sourceId":10,"variantId":10,"displayName":"角色","bindingSource":"FORMAL"}]}
            """);
        var bindings = List.of(new StoryboardPromptCompiler.BindingReference("CHARACTER", 1L, 10L, "角色", "VISIBLE", 0),
            new StoryboardPromptCompiler.BindingReference("SCENE", 2L, 11L, "餐厅", "MAIN", 1));
        var result = new AvailableVideoReferences(compiler, resolver).compile(1L, 2L, document, bindings);
        assertThat(result.compiled().text()).isEqualTo("素材参考：图片1。\n角色");
        assertThat(result.compiled().references()).extracting(StoryboardPromptCompiler.Reference::sourceId).containsExactly(11L);
        assertThat(bindings).hasSize(2);
    }

    @Test
    void allImagesMissingBecomesTextWhileUnexpectedFailuresStillPropagate() throws Exception {
        var document = json.readTree("""
            {"version":2,"nodes":[{"type":"mention","mediaType":"IMAGE","sourceType":"IMAGE_MATERIAL", "sourceId":10,"displayName":"人物"}]}
            """);
        when(resolver.resolve(eq(1L), eq(2L), anyList())).thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR, "不可用"));
        var helper = new AvailableVideoReferences(compiler, resolver);
        assertThat(helper.compile(1L, 2L, document, List.of()).compiled().references()).isEmpty();
        assertThat(helper.compile(1L, 2L, document, List.of()).compiled().text()).isEqualTo("人物");
        when(resolver.resolve(eq(1L), eq(2L), anyList())).thenThrow(new IllegalStateException("数据库不可用"));
        assertThatThrownBy(() -> helper.compile(1L, 2L, document, List.of())).isInstanceOf(IllegalStateException.class);
    }
}
