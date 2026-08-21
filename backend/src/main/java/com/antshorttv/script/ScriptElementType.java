package com.antshorttv.script;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.util.List;
import java.util.Locale;

enum ScriptElementType {
    ALL(null),
    CHARACTER("character_asset"),
    SCENE("scene_asset"),
    PROP("prop_asset");

    private final String tableName;

    ScriptElementType(String tableName) {
        this.tableName = tableName;
    }

    static ScriptElementType from(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ALL", "CHARACTER", "SCENE", "PROP").contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择元素类型。");
        }
        return ScriptElementType.valueOf(normalized);
    }

    String tableName() {
        if (tableName == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择元素类型。");
        }
        return tableName;
    }
}
