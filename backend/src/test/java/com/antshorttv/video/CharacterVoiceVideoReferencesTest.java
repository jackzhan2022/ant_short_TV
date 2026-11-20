package com.antshorttv.video;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import com.antshorttv.common.BusinessException;
import com.antshorttv.material.ProjectAudioService;
import com.antshorttv.material.StoryboardCharacterVoiceService;
import com.antshorttv.script.StoryboardPromptCompiler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CharacterVoiceVideoReferencesTest {
    private final StoryboardCharacterVoiceService voices = mock(StoryboardCharacterVoiceService.class);
    private final ProjectAudioService audio = mock(ProjectAudioService.class);
    private final ModelAccessibleVideoUrlResolver urls = mock(ModelAccessibleVideoUrlResolver.class);
    private final CharacterVoiceVideoReferences service = new CharacterVoiceVideoReferences(voices, audio, urls);

    private StoryboardCharacterVoiceService.VoiceChoice choice(long character, long variant, long resource) {
        return new StoryboardCharacterVoiceService.VoiceChoice(character, "角色" + character, variant, "形态" + variant,
            "MAIN", 0, 3L, "PROJECT_AUDIO_ASSET", resource, false);
    }
    private void media(long id) {
        when(audio.trustedMedia(1L, 2L + 1, "PROJECT_AUDIO_ASSET", id)).thenReturn(new ProjectAudioService.TrustedAudio(
            "声音" + id, "materials/1/3/" + id + ".wav", "wav", 1000L, new BigDecimal("3"), "/playback/" + id));
        when(urls.resolve(anyString())).thenAnswer(call -> "https://provider.example/" + call.getArgument(0));
    }

    @Test
    void v3LabelsOnlyRealBoundVoicesAsVoiceAssets() {
        when(voices.choicesForVideo(1L, 2L, 20L)).thenReturn(List.of(choice(10, 100, 50)));
        media(50);
        var result = service.append(1L, 2L, 20L, List.of());
        assertThat(result.prompt("分镜3.0", result.references(), true))
            .contains("• 音色资产：", "角色10的声音是音频1", "形态100");
        assertThat(result.prompt("旧版", result.references())).contains("角色音色：").doesNotContain("• 音色资产：");
        assertThat(new CharacterVoiceVideoReferences.Augmentation(List.of(), List.of()).prompt("无绑定", List.of(), true))
            .isEqualTo("无绑定");
    }

    @Test
    void v3FillsVoiceAssetsInTheOpeningLockModuleWithoutDuplicatingIt() {
        when(voices.choicesForVideo(1L, 2L, 20L)).thenReturn(List.of(choice(10, 100, 50)));
        media(50);
        var result = service.append(1L, 2L, 20L, List.of());
        String template = "🎬 本镜资产锁定：\n• 人物资产：角色10为图片1；\n"
            + "• 音色资产：平台已绑定的角色参考音频由视频生成阶段注入。\n\n人物站位图是，根据造型匹配人物资产。\n完整画面描述：对峙。";
        String prompt = result.prompt(template, result.references(), true);
        assertThat(prompt).containsOnlyOnce("• 音色资产：").contains("角色10的声音是音频1")
            .doesNotContain("由视频生成阶段注入");
        assertThat(prompt.indexOf("角色10的声音是音频1")).isLessThan(prompt.indexOf("人物站位图是"));
        assertThat(new CharacterVoiceVideoReferences.Augmentation(List.of(), List.of()).prompt(template, List.of(), true))
            .contains("• 音色资产：本镜未绑定角色参考音频。")
            .doesNotContain("角色10的声音是", "由视频生成阶段注入");
    }

    @Test
    void deduplicatesSharedVoiceButKeepsEveryCharacterAndShapeAssociation() {
        when(voices.choicesForVideo(1L, 2L, 20L)).thenReturn(List.of(choice(10, 100, 50), choice(11, 101, 50)));
        media(50);
        var result = service.append(1L, 2L, 20L, List.of());
        assertThat(result.references()).hasSize(1);
        assertThat(result.assignments()).hasSize(2);
        assertThat(result.prompt("对白", result.references())).contains("角色10", "角色11", "形态100", "形态101", "音频1");
        verify(audio, times(1)).trustedMedia(1L, 3L, "PROJECT_AUDIO_ASSET", 50L);
    }

    @Test
    void preservesExplicitAudioNumberingWhenSubsetPriorityPlacesCharacterVoicesFirst() throws Exception {
        when(voices.choicesForVideo(1L, 2L, 20L)).thenReturn(List.of(choice(10, 100, 50)));
        media(50);
        var explicit = new VideoTaskReferenceResolver.ResolvedReference(new StoryboardPromptCompiler.Reference(
            "AUDIO", 1, "音频1", "reference_audio", "AUDIO_MATERIAL", 9L, null, null, null, "环境音", 0),
            "环境音", "materials/1/2/background.wav", "https://provider.example/background.wav", "wav", 1000L, null, null, new BigDecimal("3"), null);
        var result = service.append(1L, 2L, 20L, List.of(explicit));
        var selection = new VideoReferenceSubsetSelector().select(result.references(), new ObjectMapper().readTree("""
            {"audio":{"maxCount":3,"maxTotalDurationSeconds":15}}
            """));
        assertThat(selection.kept().get(0).reference().sourceType()).isEqualTo("PROJECT_AUDIO_ASSET");
        var ordered = result.selectedReferences(selection.kept());
        assertThat(ordered).extracting(ref -> ref.reference().compiledLabel()).containsExactly("音频1", "音频2");
        assertThat(ordered.get(0).providerUrl()).endsWith("background.wav");
        assertThat(result.prompt("参考音频1", ordered)).contains("角色10", "使用音频2作为音色参考");
    }

    @Test
    void rejectsSilentVoiceOmissionAndDoesNotAccessUnconfiguredAudio() throws Exception {
        when(voices.choicesForVideo(1L, 2L, 20L)).thenReturn(List.of(choice(10, 100, 50), choice(11, 101, 51)));
        media(50); media(51);
        var result = service.append(1L, 2L, 20L, List.of());
        var selection = new VideoReferenceSubsetSelector().select(result.references(), new ObjectMapper().readTree("""
            {"audio":{"maxCount":1,"maxTotalDurationSeconds":15}}
            """));
        assertThatThrownBy(() -> result.selectedReferences(selection.kept())).isInstanceOf(BusinessException.class).hasMessageContaining("音频数量或总时长限制");
        when(voices.choicesForVideo(1L, 2L, 20L)).thenReturn(List.of());
        clearInvocations(audio, urls);
        assertThat(service.append(1L, 2L, 20L, List.of()).assignments()).isEmpty();
        verifyNoInteractions(audio, urls);
    }
}
