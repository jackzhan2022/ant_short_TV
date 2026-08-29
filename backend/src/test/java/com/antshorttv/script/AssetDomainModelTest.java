package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AssetDomainModelTest {

    @Test
    void exposesTypedEntitiesAndMappersForStableAssetWorkflow() {
        List<String> requiredTypes = List.of(
            "ScriptEpisodeEntity",
            "ScriptAssetNormalizationRunEntity",
            "ScriptAssetCandidateEntity",
            "ScriptAssetCandidateAliasEntity",
            "ScriptAssetPromotionDecisionEntity",
            "AssetVisualVariantEntity",
            "AssetVisualVariantEpisodeEntity",
            "ScriptEpisodeMapper",
            "ScriptAssetNormalizationRunMapper",
            "ScriptAssetCandidateMapper",
            "ScriptAssetCandidateAliasMapper",
            "ScriptAssetPromotionDecisionMapper",
            "AssetVisualVariantMapper",
            "AssetVisualVariantEpisodeMapper"
        );

        assertThat(requiredTypes)
            .allSatisfy(type -> assertThat(load("com.antshorttv.script." + type)).isNotNull());
    }

    @Test
    void assetTypeAcceptsOnlySupportedCanonicalOwners() throws Exception {
        Class<?> assetType = load("com.antshorttv.script.AssetType");

        assertThat(assetType).isNotNull();
        Object character = assetType.getMethod("fromStorageValue", String.class).invoke(null, "character");
        assertThat(character.toString()).isEqualTo("CHARACTER");
    }

    private Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            return null;
        }
    }
}
