package com.antshorttv.video;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.ai.AiModelEntity;
import com.antshorttv.common.BusinessException;
import com.antshorttv.script.StoryboardPromptCompiler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SeedanceVideoRequestValidatorTest {
    private final SeedanceVideoRequestValidator validator = new SeedanceVideoRequestValidator(new ObjectMapper());

    @Test
    void acceptsMiniBoundaryValuesAndIntelligentDuration() {
        List<VideoTaskReferenceResolver.ResolvedReference> references = List.of(
            image("png", 29_000_000L, 300, 750),
            video("mp4", 200_000_000L, 614, 664, "15", "24"),
            audio("wav", 15_000_000L, "15")
        );

        assertThatCode(() -> validator.validate(mini(), 4, "480p", references)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(mini(), 15, "720p", references)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(mini(), -1, "720p", references)).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnsupportedDurationResolutionAndImageCount() {
        assertInvalid(() -> validator.validate(mini(), 3, "720p", List.of(image("png", 1000L, 720, 1280))));
        assertInvalid(() -> validator.validate(mini(), 16, "720p", List.of(image("png", 1000L, 720, 1280))));
        assertInvalid(() -> validator.validate(mini(), 5, "1080p", List.of(image("png", 1000L, 720, 1280))));
        assertInvalid(() -> validator.validate(mini(), 5, "720p", List.of()));
        List<VideoTaskReferenceResolver.ResolvedReference> images = new ArrayList<>();
        for (int index = 0; index < 10; index++) images.add(image("png", 1000L, 720, 1280));
        assertInvalid(() -> validator.validate(mini(), 5, "720p", images));
    }

    @Test
    void rejectsInvalidImageMetadata() {
        assertInvalid(() -> validateSingle(image("svg", 1000L, 720, 1280)));
        assertInvalid(() -> validateSingle(image("png", 30_000_000L, 720, 1280)));
        assertInvalid(() -> validateSingle(image("png", 1000L, 299, 720)));
        assertInvalid(() -> validateSingle(image("png", 1000L, 6001, 3000)));
        assertInvalid(() -> validateSingle(image("png", 1000L, 300, 1000)));
    }

    @Test
    void rejectsInvalidVideoMetadataCountAndTotalDuration() {
        assertInvalid(() -> validateWithImage(video("avi", 1000L, 1280, 720, "5", "24")));
        assertInvalid(() -> validateWithImage(video("mp4", 200_000_001L, 1280, 720, "5", "24")));
        assertInvalid(() -> validateWithImage(video("mp4", 1000L, 300, 300, "5", "24")));
        assertInvalid(() -> validateWithImage(video("mp4", 1000L, 1280, 720, "1.9", "24")));
        assertInvalid(() -> validateWithImage(video("mp4", 1000L, 1280, 720, "5", "23.9")));
        assertInvalid(() -> validateWithImage(video("mp4", 1000L, 1280, 720, "5", "60.1")));
        assertInvalid(() -> validator.validate(mini(), 5, "720p", List.of(
            image("png", 1000L, 720, 1280),
            video("mp4", 1000L, 1280, 720, "6", "24"),
            video("mp4", 1000L, 1280, 720, "6", "24"),
            video("mp4", 1000L, 1280, 720, "4", "24")
        )));
    }

    @Test
    void rejectsInvalidAudioMetadataCountAndTotalDuration() {
        assertInvalid(() -> validateWithImage(audio("aac", 1000L, "5")));
        assertInvalid(() -> validateWithImage(audio("mp3", 15_000_001L, "5")));
        assertInvalid(() -> validateWithImage(audio("mp3", 1000L, "1.9")));
        assertInvalid(() -> validator.validate(mini(), 5, "720p", List.of(
            image("png", 1000L, 720, 1280),
            audio("mp3", 1000L, "6"),
            audio("wav", 1000L, "6"),
            audio("mp3", 1000L, "4")
        )));
    }

    @Test
    void acceptsStandard4kAndSeedance25LargerReferenceSets() {
        assertThatCode(() -> validator.validate(standard(), 15, "4k", List.of(
            image("heic", 1000L, 2160, 3840)))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(seedance25(), 30, "1080p", List.of(
            image("webp", 1000L, 1080, 1920),
            video("mov", 1000L, 1920, 1080, "30", "60"),
            audio("mp3", 1000L, "30")
        ))).doesNotThrowAnyException();
    }

    private void validateSingle(VideoTaskReferenceResolver.ResolvedReference reference) {
        validator.validate(mini(), 5, "720p", List.of(reference));
    }

    private void validateWithImage(VideoTaskReferenceResolver.ResolvedReference reference) {
        validator.validate(mini(), 5, "720p", List.of(image("png", 1000L, 720, 1280), reference));
    }

    private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOf(BusinessException.class);
    }

    private VideoTaskReferenceResolver.ResolvedReference image(String format, Long size, int width, int height) {
        return resolved("IMAGE", "reference_image", format, size, width, height, null, null);
    }

    private VideoTaskReferenceResolver.ResolvedReference video(
        String format, Long size, int width, int height, String duration, String fps
    ) {
        return resolved("VIDEO", "reference_video", format, size, width, height,
            new BigDecimal(duration), new BigDecimal(fps));
    }

    private VideoTaskReferenceResolver.ResolvedReference audio(String format, Long size, String duration) {
        return resolved("AUDIO", "reference_audio", format, size, null, null,
            new BigDecimal(duration), null);
    }

    private VideoTaskReferenceResolver.ResolvedReference resolved(
        String type, String role, String format, Long size, Integer width, Integer height,
        BigDecimal duration, BigDecimal fps
    ) {
        var reference = new StoryboardPromptCompiler.Reference(
            type, 1, type + "1", role, type + "_MATERIAL", 1L, null, null, null, type, 0);
        return new VideoTaskReferenceResolver.ResolvedReference(
            reference, type, "/materials/source." + format, "https://cdn.example/source." + format,
            format, size, width, height, duration, fps);
    }

    private AiModelEntity mini() {
        return model("SEEDANCE_2_0_MINI", 4, 15, "[\"480p\",\"720p\"]", 9, 3, 15, 3, 15);
    }

    private AiModelEntity standard() {
        return model("SEEDANCE_2_0_STANDARD", 4, 15,
            "[\"480p\",\"720p\",\"1080p\",\"4k\"]", 9, 3, 15, 3, 15);
    }

    private AiModelEntity seedance25() {
        return model("SEEDANCE_2_5", 4, 30, "[\"480p\",\"720p\",\"1080p\"]", 30, 10, 30, 10, 30);
    }

    private AiModelEntity model(
        String code, int minDuration, int maxDuration, String resolutions,
        int maxImages, int maxVideos, int maxVideoDuration, int maxAudio, int maxAudioDuration
    ) {
        AiModelEntity model = new AiModelEntity();
        model.setCode(code);
        model.setConfigJson("""
            {"videoGeneration":{
              "duration":{"min":%d,"max":%d,"intelligent":true},
              "resolutions":%s,
              "image":{"formats":["jpeg","jpg","png","webp","bmp","tiff","gif","heic","heif"],"minCount":1,"maxCount":%d,"maxBytes":29999999,"minAspectRatio":0.4,"maxAspectRatio":2.5,"minDimension":300,"maxDimension":6000},
              "video":{"formats":["mp4","mov"],"maxCount":%d,"maxTotalDurationSeconds":%d,"minDurationSeconds":2,"maxDurationSeconds":%d,"maxBytes":200000000,"minAspectRatio":0.4,"maxAspectRatio":2.5,"minDimension":300,"maxDimension":6000,"minPixels":407696,"maxPixels":8295044,"minFps":24,"maxFps":60},
              "audio":{"formats":["wav","mp3"],"maxCount":%d,"maxTotalDurationSeconds":%d,"minDurationSeconds":2,"maxDurationSeconds":%d,"maxBytes":15000000}
            }}
            """.formatted(minDuration, maxDuration, resolutions, maxImages, maxVideos,
            maxVideoDuration, maxVideoDuration, maxAudio, maxAudioDuration, maxAudioDuration));
        return model;
    }
}
