package com.antshorttv.script;

import java.util.Locale;

public enum AssetType {
    CHARACTER,
    SCENE,
    PROP;

    public static AssetType fromStorageValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("asset type is required");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported asset type: " + value, exception);
        }
    }
}
