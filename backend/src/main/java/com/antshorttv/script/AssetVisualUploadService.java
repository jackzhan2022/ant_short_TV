package com.antshorttv.script;

import com.antshorttv.storage.LocalImageUploadService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetVisualUploadService {
    private final AssetVisualVariantService variants;
    private final AssetNormalizationGuard normalization;
    private final LocalImageUploadService uploads;

    public AssetVisualUploadService(AssetVisualVariantService variants, AssetNormalizationGuard normalization,
        LocalImageUploadService uploads) {
        this.variants = variants;
        this.normalization = normalization;
        this.uploads = uploads;
    }

    @Transactional
    public AssetVisualVariantService.VariantResponse bind(
        Long tenantId, Long projectId, Long variantId, Long userId, String sessionToken
    ) {
        normalization.requireReady(tenantId, projectId);
        variants.requireUploadTarget(tenantId, projectId, variantId);
        var image = uploads.storeUpload(tenantId, projectId, "VISUAL_VARIANT", variantId, userId, sessionToken);
        return variants.bindUploadedImage(tenantId, projectId, variantId, image.getId(), image.getImageUrl());
    }
}
