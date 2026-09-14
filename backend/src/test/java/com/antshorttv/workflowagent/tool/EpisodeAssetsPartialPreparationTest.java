package com.antshorttv.workflowagent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class EpisodeAssetsPartialPreparationTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void preservesInputAndAcceptsValidCategoryWhenAnotherHasWrongType() throws Exception {
        var input = json.readTree("""
            {"schemaVersion":1,"characters":"bad","scenes":[
             {"localKey":"s1","name":"仓库","evidenceRef":{"segmentId":"S0001"}}]}
            """);
        var original = input.deepCopy();
        var prepared = EpisodeAssetsPartialPreparation.prepare(input,
            new ScreenplayToolConfiguration().episodeAssetsInput(json), "仓库");
        assertThat(input).isEqualTo(original);
        assertThat(prepared.payload().at("/scenes/0/evidence").asText()).isEqualTo("仓库");
        assertThat(prepared.warnings().toString()).contains("INVALID_CATEGORY");
    }

    @Test
    void acceptsAnEmptyEpisodeAndRejectsUnknownRootFields() throws Exception {
        var schema = new ScreenplayToolConfiguration().episodeAssetsInput(json);
        var empty = EpisodeAssetsPartialPreparation.prepare(
            json.readTree("{\"schemaVersion\":1}"), schema, "仓库");
        assertThat(empty.payload().path("characters")).isEmpty();
        assertThat(empty.warnings()).isEmpty();
        assertThatThrownBy(() -> EpisodeAssetsPartialPreparation.prepare(
            json.readTree("{\"schemaVersion\":1,\"tenantId\":42}"), schema, "仓库"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tenantId");
    }

    @Test
    void duplicateKeysAndOrphanVariantsAreIndividuallyQuarantined() throws Exception {
        var prepared = EpisodeAssetsPartialPreparation.prepare(json.readTree("""
            {"schemaVersion":1,"props":[
             {"localKey":"p1","name":"钥匙","evidence":"钥匙"},
             {"localKey":"p1","name":"钥匙","evidence":"钥匙"}],
             "propVariants":[{"localKey":"v1","propLocalKey":"missing","name":"金色","evidence":"金色"}]}
            """), new ScreenplayToolConfiguration().episodeAssetsInput(json), "金色钥匙");
        assertThat(prepared.payload().path("props").size()).isEqualTo(1);
        assertThat(prepared.payload().path("propVariants").size()).isZero();
        assertThat(prepared.warnings().size()).isEqualTo(2);
        assertThat(prepared.warnings().get(1).path("item").path("propLocalKey").asText()).isEqualTo("missing");
    }
}
