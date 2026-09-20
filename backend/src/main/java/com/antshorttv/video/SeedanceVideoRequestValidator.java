package com.antshorttv.video;

import com.antshorttv.ai.AiModelEntity;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SeedanceVideoRequestValidator {
    private final ObjectMapper objectMapper;

    public SeedanceVideoRequestValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void validate(
        AiModelEntity model,
        Integer durationSeconds,
        String resolution,
        List<VideoTaskReferenceResolver.ResolvedReference> references
    ) {
        JsonNode constraints = constraints(model);
        validateOutput(durationSeconds, resolution, constraints);
        validateImages(media(references, "IMAGE"), constraints.path("image"));
        validateVideos(media(references, "VIDEO"), constraints.path("video"));
        validateAudio(media(references, "AUDIO"), constraints.path("audio"));
    }

    public JsonNode constraints(AiModelEntity model) {
        if (model == null || model.getConfigJson() == null || model.getConfigJson().isBlank()) {
            throw invalid("所选视频模型缺少生成约束配置。");
        }
        try {
            JsonNode constraints = objectMapper.readTree(model.getConfigJson()).path("videoGeneration");
            if (!constraints.isObject()) throw invalid("所选视频模型生成约束配置不正确。");
            return constraints;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid("所选视频模型生成约束配置不正确。");
        }
    }

    private List<VideoTaskReferenceResolver.ResolvedReference> media(
        List<VideoTaskReferenceResolver.ResolvedReference> references,
        String mediaType
    ) {
        return references.stream().filter(item -> mediaType.equals(item.reference().mediaType())).toList();
    }

    private void validateOutput(Integer durationSeconds, String resolution, JsonNode constraints) {
        JsonNode duration = constraints.path("duration");
        if (durationSeconds == null
            || (durationSeconds != -1
                && (durationSeconds < duration.path("min").asInt()
                    || durationSeconds > duration.path("max").asInt()))) {
            throw invalid("视频时长不受所选模型支持。");
        }
        boolean supported = false;
        for (JsonNode candidate : constraints.path("resolutions")) {
            if (candidate.asText().equalsIgnoreCase(resolution == null ? "" : resolution.trim())) {
                supported = true;
                break;
            }
        }
        if (!supported) throw invalid("视频分辨率不受所选模型支持。");
    }

    private void validateImages(List<VideoTaskReferenceResolver.ResolvedReference> images, JsonNode rules) {
        validateCount(images.size(), rules, "图片");
        for (var image : images) {
            validateFormatAndSize(image, rules, "图片");
            validateDimensions(image, rules, false);
        }
    }

    private void validateVideos(List<VideoTaskReferenceResolver.ResolvedReference> videos, JsonNode rules) {
        validateCount(videos.size(), rules, "视频");
        BigDecimal total = BigDecimal.ZERO;
        for (var video : videos) {
            validateFormatAndSize(video, rules, "视频");
            validateDimensions(video, rules, true);
            BigDecimal duration = required(video.durationSeconds(), video, "视频时长元数据缺失。");
            range(duration, rules.path("minDurationSeconds").decimalValue(),
                rules.path("maxDurationSeconds").decimalValue(), video, "视频时长不符合要求。");
            BigDecimal fps = required(video.fps(), video, "视频帧率元数据缺失。");
            range(fps, rules.path("minFps").decimalValue(), rules.path("maxFps").decimalValue(),
                video, "视频帧率不符合要求。");
            total = total.add(duration);
        }
        if (total.compareTo(rules.path("maxTotalDurationSeconds").decimalValue()) > 0) {
            throw invalid("参考视频总时长超过所选模型限制。");
        }
    }

    private void validateAudio(List<VideoTaskReferenceResolver.ResolvedReference> audioFiles, JsonNode rules) {
        validateCount(audioFiles.size(), rules, "音频");
        BigDecimal total = BigDecimal.ZERO;
        for (var audio : audioFiles) {
            validateFormatAndSize(audio, rules, "音频");
            BigDecimal duration = required(audio.durationSeconds(), audio, "音频时长元数据缺失。");
            range(duration, rules.path("minDurationSeconds").decimalValue(),
                rules.path("maxDurationSeconds").decimalValue(), audio, "音频时长不符合要求。");
            total = total.add(duration);
        }
        if (total.compareTo(rules.path("maxTotalDurationSeconds").decimalValue()) > 0) {
            throw invalid("参考音频总时长超过所选模型限制。");
        }
    }

    private void validateCount(int count, JsonNode rules, String type) {
        int min = rules.path("minCount").asInt(0);
        int max = rules.path("maxCount").asInt(0);
        if (count < min || count > max) {
            throw invalid("参考%s数量不符合所选模型限制。".formatted(type));
        }
    }

    private void validateFormatAndSize(
        VideoTaskReferenceResolver.ResolvedReference reference,
        JsonNode rules,
        String type
    ) {
        boolean supported = false;
        for (JsonNode candidate : rules.path("formats")) {
            if (candidate.asText().equalsIgnoreCase(reference.format())) {
                supported = true;
                break;
            }
        }
        if (!supported) throw invalid(reference, type + "格式不符合要求。");
        if (reference.fileSize() == null || reference.fileSize() < 0
            || reference.fileSize() > rules.path("maxBytes").asLong()) {
            throw invalid(reference, type + "文件大小不符合要求。");
        }
    }

    private void validateDimensions(
        VideoTaskReferenceResolver.ResolvedReference reference,
        JsonNode rules,
        boolean validatePixels
    ) {
        Integer width = reference.width();
        Integer height = reference.height();
        if (width == null || height == null
            || width < rules.path("minDimension").asInt() || width > rules.path("maxDimension").asInt()
            || height < rules.path("minDimension").asInt() || height > rules.path("maxDimension").asInt()) {
            throw invalid(reference, "素材尺寸不符合要求。");
        }
        double aspectRatio = (double) width / height;
        if (aspectRatio < rules.path("minAspectRatio").asDouble()
            || aspectRatio > rules.path("maxAspectRatio").asDouble()) {
            throw invalid(reference, "素材宽高比不符合要求。");
        }
        long pixels = (long) width * height;
        if (validatePixels
            && (pixels < rules.path("minPixels").asLong() || pixels > rules.path("maxPixels").asLong())) {
            throw invalid(reference, "视频总像素数不符合要求。");
        }
    }

    private BigDecimal required(
        BigDecimal value,
        VideoTaskReferenceResolver.ResolvedReference reference,
        String message
    ) {
        if (value == null) throw invalid(reference, message);
        return value;
    }

    private void range(
        BigDecimal value,
        BigDecimal min,
        BigDecimal max,
        VideoTaskReferenceResolver.ResolvedReference reference,
        String message
    ) {
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) throw invalid(reference, message);
    }

    private BusinessException invalid(VideoTaskReferenceResolver.ResolvedReference reference, String message) {
        return invalid("%s（%s）：%s".formatted(
            reference.displayName(), reference.reference().compiledLabel(), message));
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}
