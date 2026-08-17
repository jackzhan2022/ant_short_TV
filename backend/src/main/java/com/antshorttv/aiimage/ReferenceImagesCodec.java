package com.antshorttv.aiimage;

import java.util.Arrays;
import java.util.List;

final class ReferenceImagesCodec {
    static String encode(List<String> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        return String.join("\n", items.stream().map(String::trim).filter(item -> !item.isBlank()).limit(4).toList());
    }

    static List<String> decode(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\\R")).filter(item -> !item.isBlank()).toList();
    }

    private ReferenceImagesCodec() {
    }
}
