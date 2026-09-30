package com.antshorttv.storage;

import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ImageDisplayRenditionPlanner {
    private final ObjectStorageKeyFactory keys;

    public ImageDisplayRenditionPlanner(ObjectStorageKeyFactory keys) {
        this.keys = keys;
    }

    public ImageDisplayRenditionPlan plan(String originalKey, String sourceMimeType) {
        String mimeType = sourceMimeType == null
            ? "" : sourceMimeType.toLowerCase(Locale.ROOT).trim();
        return switch (mimeType) {
            case "image/jpeg", "image/jpg" -> direct(originalKey, "jpg", "image/jpeg");
            case "image/png" -> direct(originalKey, "png", "image/png");
            case "image/gif" -> direct(originalKey, "gif", "image/gif");
            default -> {
                if (!mimeType.startsWith("image/")) {
                    throw new IllegalArgumentException("极智压缩仅支持图片对象。");
                }
                yield new ImageDisplayRenditionPlan(
                    keys.rendition(originalKey, "display", "png"),
                    "image/png",
                    "imageMogr2/format/png|imageSlim"
                );
            }
        };
    }

    private ImageDisplayRenditionPlan direct(
        String originalKey,
        String extension,
        String mimeType
    ) {
        return new ImageDisplayRenditionPlan(
            keys.rendition(originalKey, "display", extension), mimeType, "imageSlim"
        );
    }
}
