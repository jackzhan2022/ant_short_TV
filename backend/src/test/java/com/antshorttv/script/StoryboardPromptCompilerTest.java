package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class StoryboardPromptCompilerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StoryboardPromptCompiler compiler = new StoryboardPromptCompiler();

    @Test
    void compilesIndependentMediaNumbersInFirstMentionOrderAndDeduplicatesSources() throws Exception {
        var document = objectMapper.readTree("""
            {"version":2,"nodes":[
              {"type":"text","text":"首帧使用"},
              {"type":"mention","mediaType":"IMAGE","sourceType":"ASSET_VISUAL_VARIANT","sourceId":101,"variantId":101,"displayName":"苹果首帧"},
              {"type":"text","text":"，运镜参考"},
              {"type":"mention","mediaType":"VIDEO","sourceType":"VIDEO_MATERIAL","sourceId":201,"displayName":"第一视角"},
              {"type":"text","text":"，再次使用"},
              {"type":"mention","mediaType":"IMAGE","sourceType":"ASSET_VISUAL_VARIANT","sourceId":101,"variantId":101,"displayName":"苹果首帧"},
              {"type":"text","text":"，结尾参考"},
              {"type":"mention","mediaType":"IMAGE","sourceType":"STORYBOARD_FIRST_FRAME","sourceId":102,"displayName":"成品果茶"},
              {"type":"text","text":"，背景音乐使用"},
              {"type":"mention","mediaType":"AUDIO","sourceType":"AUDIO_MATERIAL","sourceId":301,"displayName":"轻快鼓点"}
            ]}
            """);

        StoryboardPromptCompiler.CompiledPrompt result = compiler.compile(document);

        assertThat(result.text()).isEqualTo("首帧使用图片1，运镜参考视频1，再次使用图片1，结尾参考图片2，背景音乐使用音频1");
        assertThat(result.references())
            .extracting(
                StoryboardPromptCompiler.Reference::mediaType,
                StoryboardPromptCompiler.Reference::mediaIndex,
                StoryboardPromptCompiler.Reference::compiledLabel,
                StoryboardPromptCompiler.Reference::providerRole,
                StoryboardPromptCompiler.Reference::sourceId)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("IMAGE", 1, "图片1", "reference_image", 101L),
                org.assertj.core.groups.Tuple.tuple("VIDEO", 1, "视频1", "reference_video", 201L),
                org.assertj.core.groups.Tuple.tuple("IMAGE", 2, "图片2", "reference_image", 102L),
                org.assertj.core.groups.Tuple.tuple("AUDIO", 1, "音频1", "reference_audio", 301L)
            );
    }

    @Test
    void preservesPlainTextExactly() throws Exception {
        var document = objectMapper.readTree("""
            {"version":2,"nodes":[
              {"type":"text","text":"第一行\\n"},
              {"type":"text","text":"第二行，保留标点。"}
            ]}
            """);

        StoryboardPromptCompiler.CompiledPrompt result = compiler.compile(document);

        assertThat(result.text()).isEqualTo("第一行\n第二行，保留标点。");
        assertThat(result.references()).isEmpty();
    }
}
