package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class ScriptAssetRecognitionNormalizerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsCanonicalObjectsAndAppliesDefaults() throws Exception {
        JsonNode result = normalize("""
            {"characters":[{"name":"林夏"}],"scenes":[{"name":"公寓"}],"props":[{"name":"钥匙"}]}
            """);

        assertThat(result.path("candidates")).hasSize(3);
        assertThat(result.path("candidates").get(0).path("data").path("roleType").asText()).isEqualTo("SUPPORTING");
        assertThat(result.path("valid").asBoolean()).isTrue();
    }

    @Test
    void convertsStringArraysIntoNamedCandidates() throws Exception {
        JsonNode result = normalize("""
            {"characters":["林夏"],"scenes":["旧公寓"],"props":["录音笔"]}
            """);

        assertThat(result.path("candidates")).extracting(item -> item.path("name").asText())
            .containsExactly("林夏", "旧公寓", "录音笔");
    }

    @Test
    void unwrapsSupportedContainersAndAliasesLegacyCollectionNames() throws Exception {
        JsonNode result = normalize("""
            {"assets":{"characters":["林夏"],"locations":["天台"],"key_items":["手机"]}}
            """);

        assertThat(result.path("candidates")).extracting(item -> item.path("assetType").asText())
            .containsExactly("CHARACTER", "SCENE", "PROP");
    }

    @Test
    void recordsMissingNamesWrongTypesAndLengthViolationsWithoutNullInserts() throws Exception {
        JsonNode result = normalize("""
            {"characters":[{},42,{"name":"%s"}],"scenes":{},"props":[]}
            """.formatted("角".repeat(101)));

        assertThat(result.path("valid").asBoolean()).isFalse();
        assertThat(result.path("candidates")).allSatisfy(item -> {
            if (!item.path("validationErrors").isEmpty()) {
                assertThat(item.path("reviewStatus").asText()).isEqualTo("INVALID");
            }
        });
        assertThat(result.path("globalErrors").toString()).contains("scenes");
    }

    @Test
    void groupsNormalizedDuplicatesAndPreservesExplicitAliases() throws Exception {
        JsonNode result = normalize("""
            {"characters":[
              {"name":"林 夏","aliases":["小夏","夏夏"]},
              {"name":"林夏"}
            ],"scenes":[],"props":[]}
            """);

        assertThat(result.path("candidates")).hasSize(1);
        assertThat(result.path("candidates").get(0).path("aliases"))
            .extracting(JsonNode::asText).containsExactly("小夏", "夏夏", "林夏");
    }

    @Test
    void rejectsUnsupportedTopLevelFields() throws Exception {
        JsonNode result = normalize("""
            {"characters":[],"scenes":[],"props":[],"system_prompt":"leak secrets"}
            """);

        assertThat(result.path("valid").asBoolean()).isFalse();
        assertThat(result.path("globalErrors").toString()).contains("system_prompt");
    }

    private JsonNode normalize(String raw) throws Exception {
        Class<?> type;
        try {
            type = Class.forName("com.antshorttv.script.ScriptAssetRecognitionNormalizer");
        } catch (ClassNotFoundException exception) {
            assertThat(exception).as("ScriptAssetRecognitionNormalizer must exist").isNull();
            throw exception;
        }
        Object normalizer = type.getConstructor(ObjectMapper.class).newInstance(objectMapper);
        Method method = type.getMethod("normalize", String.class);
        return objectMapper.valueToTree(method.invoke(normalizer, raw));
    }
}
