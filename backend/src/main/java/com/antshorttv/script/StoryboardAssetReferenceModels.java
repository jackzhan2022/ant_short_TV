package com.antshorttv.script;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;

enum StoryboardReferenceAssetType {
    CHARACTER, SCENE, PROP;

    static StoryboardReferenceAssetType parse(String value) {
        return parseEnum(StoryboardReferenceAssetType.class, value, "素材类型必须为 CHARACTER、SCENE 或 PROP。");
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> type, String value, String message) {
        try {
            return Enum.valueOf(type, value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw StoryboardAssetReferenceValidation.invalid(message);
        }
    }
}

enum StoryboardReferenceRole {
    VISIBLE, MAIN, SUPPORTING, TRANSITION;

    static StoryboardReferenceRole parse(String value) {
        try {
            return valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw StoryboardAssetReferenceValidation.invalid(
                "引用角色必须为 VISIBLE、MAIN、SUPPORTING 或 TRANSITION。");
        }
    }
}

enum StoryboardReferenceResolutionStatus {
    RESOLVED, ASSET_PENDING, UNRESOLVED
}

enum StoryboardReferenceSourceType {
    AI, MANUAL, LEGACY
}

record StoryboardAssetReferenceCommand(
    @NotNull String assetType,
    Long assetId,
    Long variantId,
    @NotNull String referenceRole,
    @NotNull Integer sortOrder,
    @Size(max = 200) String sourceName
) {
}

record ReplaceStoryboardAssetReferencesRequest(
    @NotNull List<@Valid StoryboardAssetReferenceCommand> references
) {
}

record StoryboardAssetReferenceResponse(
    Long id,
    String assetType,
    Long assetId,
    String assetName,
    Long variantId,
    String variantName,
    String imageUrl,
    String referenceRole,
    Integer sortOrder,
    String resolutionStatus,
    String sourceType,
    String sourceName,
    boolean lockedByUser
) {
}

final class StoryboardAssetReferenceValidation {
    private StoryboardAssetReferenceValidation() {
    }

    static com.antshorttv.common.BusinessException invalid(String message) {
        return new com.antshorttv.common.BusinessException(
            com.antshorttv.common.ErrorCode.VALIDATION_ERROR, message);
    }
}
