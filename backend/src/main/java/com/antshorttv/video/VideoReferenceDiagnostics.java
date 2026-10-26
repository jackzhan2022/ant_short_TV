package com.antshorttv.video;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class VideoReferenceDiagnostics {
    private VideoReferenceDiagnostics() {}

    public static JsonNode from(String providerCode, String snapshotJson, String errorMessage, String firstFrameUrl) {
        ObjectMapper mapper = new ObjectMapper();
        var result = mapper.createObjectNode();
        JsonNode references = mapper.createArrayNode();
        boolean legacy = snapshotJson == null || snapshotJson.isBlank();
        try {
            if (!legacy) {
                JsonNode snapshot = mapper.readTree(snapshotJson);
                references = snapshot.path("references");
                result.set("limits", snapshot.path("referenceLimits").deepCopy());
                result.set("kept", references.deepCopy());
                result.set("omitted", snapshot.path("omittedReferences").deepCopy());
            }
        } catch (Exception ignored) {
            // Older tasks without a valid snapshot expose an empty diagnostic object.
            return result;
        }
        if (!com.antshorttv.ai.VolcengineProviderCodes.isArk(providerCode) || errorMessage == null) return result;
        var failedImages = result.putArray("failedImages");
        var indices = new java.util.LinkedHashSet<Integer>();
        var matcher = java.util.regex.Pattern.compile("content\\[(\\d{1,9})\\]").matcher(errorMessage);
        while (matcher.find()) indices.add(Integer.parseInt(matcher.group(1)));
        for (int index : indices) {
            // content[0] is text; remaining entries follow the frozen snapshot, including video/audio.
            if (index < 1) continue;
            if (legacy) {
                if (index == 1 && firstFrameUrl != null && !firstFrameUrl.isBlank()) {
                    failedImages.addObject().put("contentIndex", index).put("label", "首帧图")
                        .put("displayName", "分镜首帧").put("imageUrl", firstFrameUrl);
                }
                continue;
            }
            if (!references.isArray() || index > references.size()) continue;
            JsonNode item = references.get(index - 1);
            JsonNode reference = item.path("reference");
            if (!"IMAGE".equalsIgnoreCase(reference.path("mediaType").asText())) continue;
            String name = reference.path("displayName").asText("");
            String variant = item.path("displayName").asText("");
            if (name.isBlank()) name = variant;
            else if (!variant.isBlank() && !variant.equals(name)) name += " / " + variant;
            String label = reference.path("compiledLabel").asText("");
            if (label.isBlank()) label = "参考图片（第" + index + "个素材）";
            failedImages.addObject().put("contentIndex", index).put("label", label)
                .put("displayName", name).put("imageUrl", item.path("browserDisplayUrl").asText(""));
        }
        return result;
    }

    public static String describeFailedImages(String message, JsonNode diagnostics) {
        if (message == null) return null;
        for (JsonNode image : diagnostics.path("failedImages")) {
            String name = image.path("displayName").asText("");
            String label = image.path("label").asText();
            message = message.replace("content[" + image.path("contentIndex").asInt() + "]",
                name.isBlank() ? label : label + "（" + name + "）");
        }
        return message;
    }
}
