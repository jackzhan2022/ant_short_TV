package com.antshorttv.style;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.storage.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/** Public V2 samples use the shared media pipeline, separate from the legacy style catalog. */
@Service
public class AssetArtStyleImageService {
    private final ObjectStorageService storage;
    private final ImageDisplayRenditionService renditions;
    private final ObjectStorageKeyFactory keys;
    private final MediaDeliveryGrantService grants;
    private final List<Sample> samples;

    public AssetArtStyleImageService(ObjectStorageService storage, ImageDisplayRenditionService renditions,
        ObjectStorageKeyFactory keys, MediaDeliveryGrantService grants, ObjectMapper json) {
        this.storage = storage;
        this.renditions = renditions;
        this.keys = keys;
        this.grants = grants;
        try (var input = new ClassPathResource("art-styles/preview-images-v2.json").getInputStream()) {
            samples = List.of(json.readValue(input, Sample[].class));
            if (samples.size() != 11 || samples.stream().map(Sample::code).distinct().count() != 11
                || samples.stream().map(Sample::assetId).distinct().count() != 11
                || samples.stream().anyMatch(s -> s.assetId() <= 0 || !s.code().matches("[A-Za-z0-9_]+")
                    || !s.sha256().matches("[a-f0-9]{64}"))) throw new IllegalArgumentException("Invalid sample manifest");
        } catch (Exception e) {
            throw new IllegalStateException("画风 2.0 样图清单加载失败。", e);
        }
    }

    public List<Sample> samples() { return samples; }

    public String imageUrl(String code) {
        return "/api/style-library/v2/images/" + require(code).code();
    }

    public MediaObjectIdentity identity(String code) {
        Sample sample = require(code);
        return new MediaObjectIdentity(0L, null, "STYLE_LIBRARY_V2", sample.assetId(), sample.sha256());
    }

    public String deliveryUrl(String code) {
        MediaObjectIdentity identity = identity(code);
        RegisteredMediaDetails display = renditions.displayDetails(identity);
        if (display == null || !"READY".equals(display.status()) || display.fileSize() <= 0
            || !"image/png".equals(display.mimeType())
            || !displayKey(code).equals(display.objectKey())) throw unavailable();
        // Fixed, reviewed sample catalog is public. Never accept an arbitrary object key from callers.
        return grants.issue(new DeliveryGrantRequest(0L, null, 0L, identity.assetType(), identity.assetId(),
            identity.versionId(), "DISPLAY", display.objectKey(), false)).url();
    }

    /** Maintenance-only entry point: no upload route or startup side effect. */
    public String importImage(String code, byte[] bytes) {
        validateSource(code, bytes);
        MediaObjectIdentity identity = identity(code);
        RegisteredMediaDetails original = renditions.originalDetails(identity);
        if (original == null) {
            try {
                var image = ImageIO.read(new ByteArrayInputStream(bytes));
                if (image == null) throw new IllegalArgumentException("无法读取样图。");
                StoredObject uploaded = storage.uploadOriginal(originalKey(code), bytes, "image/png");
                return renditions.registerOriginalAndSubmit(identity, uploaded, image.getWidth(), image.getHeight(),
                    "art-style-v2:" + code).displayKey();
            } catch (java.io.IOException e) {
                throw new IllegalArgumentException("无法读取样图。", e);
            }
        }
        if (!"READY".equals(original.status()) || original.fileSize() != bytes.length
            || !originalKey(code).equals(original.objectKey()) || !"image/png".equals(original.mimeType())) {
            throw new IllegalStateException("已登记的画风原图与清单不一致。");
        }
        var display = renditions.displayDetails(identity);
        if (display == null || "FAILED".equals(display.status())) {
            return renditions.retryFailedDisplay(identity, "art-style-v2:" + code).displayKey();
        }
        if (!List.of("PENDING", "READY").contains(display.status()) || !displayKey(code).equals(display.objectKey()))
            throw new IllegalStateException("画风展示图不可用。");
        return display.objectKey();
    }

    public void validateSource(String code, byte[] bytes) {
        try {
            if (!require(code).sha256().equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))))
                throw new IllegalArgumentException("样图与已确认清单的 SHA-256 不一致：" + code);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String originalKey(String code) {
        var identity = identity(code);
        return keys.tenantOriginal(0L, "style_library_v2", identity.assetId(), identity.versionId(),
            LocalDate.of(2026, 11, 4), "png");
    }

    private String displayKey(String code) {
        return new ImageDisplayRenditionPlanner(keys).plan(originalKey(code), "image/png").objectKey();
    }

    private Sample require(String code) {
        return samples.stream().filter(s -> s.code().equals(code)).findFirst().orElseThrow(this::unavailable);
    }

    private BusinessException unavailable() { return new BusinessException(ErrorCode.NOT_FOUND, "画风示例图尚未就绪。"); }
    public record Sample(String code, Long assetId, String sha256) {}
}
