package com.antshorttv.script;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Scoped evidence returned to the extraction agent for one automatic identity review. */
public final class AssetIdentityAmbiguityException extends BusinessException {
    private final JsonNode conflicts;

    public AssetIdentityAmbiguityException(JsonNode conflicts) {
        super(ErrorCode.ENTITY_MATCH_AMBIGUOUS,
            "资产身份需要自动核对，候选安全 key：" + conflicts.findValuesAsText("assetKey").stream().limit(12).toList()
                + "；完整上下文见 identityConflicts，证据不足时使用 deferIdentity=true 独立保存。");
        this.conflicts = boundedConflicts(conflicts);
    }

    public JsonNode conflicts() { return conflicts.deepCopy(); }

    private static JsonNode boundedConflicts(JsonNode input) {
        ArrayNode output = ((ArrayNode) input).arrayNode();
        ObjectNode omitted = output.objectNode().put("detailsTruncated", true);
        ObjectNode identities = omitted.putObject("deferIdentities");
        int remaining = 24_000;
        for (JsonNode conflict : input) {
            int length = conflict.toString().length();
            if (length <= remaining) {
                output.add(conflict.deepCopy());
                remaining -= length;
            } else {
                identities.withArray(conflict.path("assetType").asText()).add(conflict.path("localKey").asText());
            }
        }
        if (!identities.isEmpty()) output.add(omitted);
        // Unusually long/escaped local keys must not defeat the overall feedback limit.
        if (output.toString().length() > 64_000) {
            output.removeAll();
            output.addObject().put("detailsTruncated", true).put("deferAllUnresolved", true);
        }
        return output;
    }
}
