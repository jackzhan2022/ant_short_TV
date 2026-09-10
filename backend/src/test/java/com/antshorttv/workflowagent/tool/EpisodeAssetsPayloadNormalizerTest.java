package com.antshorttv.workflowagent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class EpisodeAssetsPayloadNormalizerTest {
    private final ObjectMapper json = new ObjectMapper();
    private final JsonNode schema = new ScreenplayToolConfiguration().saveEpisodeAssetsTool(null, json).inputSchema();

    private JsonNode prepare(String payload, String content) throws Exception {
        return EpisodeAssetsPayloadNormalizer.prepare(json.readTree(payload), schema, content);
    }

    @Test
    void removesSchemaMetadataAndConvertsOnlyVerifiableStringAliases() throws Exception {
        JsonNode result = prepare("""
            {"schemaVersion":1,"maxItems":200,"scenes":[
              {"localKey":"s1","name":"仓库","evidence":"仓库","aliases":["旧仓库"],"maxItems":30}]}
            """, "小满走进旧仓库。");
        assertThat(result.has("maxItems")).isFalse();
        assertThat(result.at("/scenes/0/maxItems").isMissingNode()).isTrue();
        assertThat(result.at("/scenes/0/aliases/0/evidence").asText()).isEqualTo("旧仓库");
    }

    @Test
    void resolvesSegmentReferencesToExactSourceAndPreservesInput() throws Exception {
        JsonNode input = json.readTree("""
            {"schemaVersion":1,"scenes":[{"localKey":"s1","name":"仓库","aliases":[],
             "evidenceRef":{"segmentId":"S0002"},"usageEvidenceRef":{"segmentId":"S0002","start":2,"end":4}}]}
            """);
        JsonNode result = EpisodeAssetsPayloadNormalizer.prepare(input, schema, "第一集\r\n走进仓库。\n");
        assertThat(result.at("/scenes/0/evidence").asText()).isEqualTo("走进仓库。");
        assertThat(result.at("/scenes/0/usageEvidence").asText()).isEqualTo("仓库");
        assertThat(input.at("/scenes/0/evidence").isMissingNode()).isTrue();
    }

    @Test
    void reportsSchemaEvidenceOwnerAndPreferredErrorsTogether() {
        assertThatThrownBy(() -> prepare("""
            {"schemaVersion":1,"unknown":true,
             "characters":[{"localKey":"c1","name":"小满","evidence":"小满"}],
             "scenes":[{"localKey":"s1","name":"仓库","usageEvidence":"海边"}],
             "props":[{"localKey":"p1","name":"钥匙","evidence":"钥匙","ownerCharacterLocalKey":"missing"}],
             "characterLooks":[
               {"localKey":"l1","characterLocalKey":"c1","name":"红裙","evidence":"红裙","preferred":true},
               {"localKey":"l2","characterLocalKey":"c1","name":"白裙","evidence":"白裙","preferred":true}]}
            """, "小满穿红裙，后来穿白裙，带着钥匙。"))
            .hasMessageContaining("$.unknown")
            .hasMessageContaining("$.scenes[0].evidence")
            .hasMessageContaining("$.scenes[0].usageEvidence")
            .hasMessageContaining("$.props[0].ownerCharacterLocalKey")
            .hasMessageContaining("$.characterLooks[1].preferred");
    }

    @Test
    void rejectsUnknownSegmentAndInvalidRangesEvenWhenLegacyEvidenceIsValid() {
        for (String ref : new String[]{"{\"segmentId\":\"S9999\"}",
                "{\"segmentId\":\"S0001\",\"start\":-1,\"end\":2}",
                "{\"segmentId\":\"S0001\",\"start\":1}",
                "{\"segmentId\":\"S0001\",\"start\":0,\"end\":100}"}) {
            assertThatThrownBy(() -> prepare("{\"schemaVersion\":1,\"characters\":[{\"localKey\":\"c1\","
                + "\"name\":\"小满\",\"evidence\":\"小满\",\"evidenceRef\":" + ref + "}]}", "小满"))
                .hasMessageContaining("evidenceRef");
        }
    }

    @Test
    void doesNotInventEvidenceOrSilentlyDropMalformedArrays() {
        assertThatThrownBy(() -> prepare("""
            {"schemaVersion":1,"scenes":[{"localKey":"s1","name":"仓库","aliases":["异界"]}],"props":"wrong"}
            """, "仓库"))
            .hasMessageContaining("$.scenes[0].evidence")
            .hasMessageContaining("$.scenes[0].aliases[0]")
            .hasMessageContaining("$.props");
    }

    @Test
    void rejectsLongSegmentsAndSplitUnicodeButAllowsExplicitSourceSlice() throws Exception {
        String longLine = "小满" + "走".repeat(1001);
        String payload = """
            {"schemaVersion":1,"characters":[{"localKey":"c1","name":"小满",
             "evidenceRef":{"segmentId":"S0001"}}]}
            """;
        assertThatThrownBy(() -> prepare(payload, longLine)).hasMessageContaining("1000");
        JsonNode slice = json.readTree(payload);
        ((com.fasterxml.jackson.databind.node.ObjectNode) slice.at("/characters/0/evidenceRef"))
            .put("start", 0).put("end", 2);
        assertThat(EpisodeAssetsPayloadNormalizer.prepare(slice, schema, longLine)
            .at("/characters/0/evidence").asText()).isEqualTo("小满");
        assertThatThrownBy(() -> EpisodeAssetsPayloadNormalizer.prepare(slice, schema, "人😀"))
            .hasMessageContaining("范围无效");
    }

    @Test
    void acceptsAliasReferenceWithoutInventingMissingEvidence() throws Exception {
        JsonNode result = prepare("""
            {"schemaVersion":1,"characters":[{"localKey":"c1","name":"林小满","evidence":"林小满",
             "aliases":[{"name":"小满","evidenceRef":{"segmentId":"S0001"}}]}]}
            """, "林小满又名小满。");
        assertThat(result.at("/characters/0/aliases/0/evidence").asText()).isEqualTo("林小满又名小满。");
    }

    @Test
    void keepsAuditMessageBoundedWhileReturningEveryValidationErrorToModel() {
        var payload = json.createObjectNode().put("schemaVersion", 1);
        var scenes = payload.putArray("scenes");
        for (int i = 0; i < 100; i++) scenes.addObject().put("localKey", "s" + i).put("name", "仓库");
        assertThatThrownBy(() -> EpisodeAssetsPayloadNormalizer.prepare(payload, schema, "仓库"))
            .isInstanceOfSatisfying(WorkflowToolValidationException.class, error -> {
                assertThat(error.getMessage().length()).isLessThanOrEqualTo(1800);
                assertThat((java.util.List<?>) error.details().get("validationErrors")).hasSize(100);
                assertThat(error.details().toString()).contains("$.scenes[99].evidence");
            });
    }
}
