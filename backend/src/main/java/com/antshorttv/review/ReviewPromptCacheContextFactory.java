package com.antshorttv.review;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ReviewPromptCacheContextFactory {
    static final String TOOL_CONTRACT_REVISION = "script-review-tools-v1";

    private final ObjectMapper json;

    public ReviewPromptCacheContextFactory(ObjectMapper json) {
        this.json = json;
    }

    public CacheContext build(
        Long tenantId,
        Long modelId,
        Long agentRevision,
        String skillRevisions,
        ReviewContentService.FrozenReview frozen
    ) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("contractRevision", TOOL_CONTRACT_REVISION);
        root.put("tenantId", tenantId);
        root.put("modelId", modelId);
        root.put("agentRevision", agentRevision);
        root.put("skillRevisions", skillRevisions);
        root.put("versionHash", frozen.versionHash());
        root.put("scopeHash", frozen.scopeHash());
        root.put("dimensionsHash", frozen.dimensionsHash());
        root.put("structure", frozen.segments().stream().map(segment -> Map.of(
            "anchor", segment.anchor(),
            "startOffset", segment.startOffset(),
            "endOffset", segment.endOffset(),
            "fingerprint", segment.fingerprint()
        )).toList());
        root.put("content", frozen.content());
        try {
            String serialized = json.writeValueAsString(root);
            String prefix = "公共审核上下文（冻结、只读）\n" + serialized;
            return new CacheContext(prefix, "script-review:" + ReviewContentService.hash(prefix));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化剧本审核缓存上下文。", exception);
        }
    }

    public record CacheContext(String commonPrefix, String cacheKey) {}
}
