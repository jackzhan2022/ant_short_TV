package com.antshorttv.script;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class CharacterVariantSimilarityAnalysisTest {
    private final ObjectMapper json = new ObjectMapper();

    private ObjectNode report() {
        ObjectNode result = json.createObjectNode().put("variantsHasMore", false);
        result.putArray("variantPairs");
        return result;
    }

    private ArrayNode input(String leftName, String rightName, String leftAppearance, String rightAppearance) {
        ArrayNode input = json.createArrayNode();
        ArrayNode variants = input.addObject().put("id", 11).putArray("variants");
        variants.addObject().put("id", 101).put("name", leftName).put("appearance", leftAppearance).put("prompt", "");
        variants.addObject().put("id", 102).put("name", rightName).put("appearance", rightAppearance).put("prompt", "");
        return input;
    }

    @Test void catchesColorSuffixWhitespaceAndPunctuationDifferencesInOutfitDescriptions() {
        var input = input("墨蓝色工装外套", "墨蓝工装外套", "墨蓝色外套，黑色工装裤", "墨蓝外套 黑工装裤");
        var report = report();
        CharacterVariantSimilarityAnalysis.addEquivalentOutfits(json, input, report);
        assertThat(report.path("variantPairs")).hasSize(1);
        CharacterVariantSimilarityAnalysis.validate(input, report);
    }

    @Test void retainsExplicitColorWetnessDamageAndPromptDifferences() {
        for (var input : java.util.List.of(
            input("墨蓝工装外套", "炭灰工装外套", "", ""),
            input("墨蓝工装外套", "湿透墨蓝工装外套", "", ""),
            input("墨蓝工装外套", "墨蓝工装外套", "完整衣服", "袖口破损"),
            input("默认形态", "默认形态", "", ""))) {
            var report = report();
            CharacterVariantSimilarityAnalysis.addEquivalentOutfits(json, input, report);
            assertThat(report.path("variantPairs")).isEmpty();
        }
        var input = input("墨蓝工装外套", "墨蓝工装外套", "", "");
        ((ObjectNode) input.get(0).path("variants").get(0)).put("prompt", "佩戴手表");
        ((ObjectNode) input.get(0).path("variants").get(1)).put("prompt", "未佩戴手表");
        var report = report();
        CharacterVariantSimilarityAnalysis.addEquivalentOutfits(json, input, report);
        assertThat(report.path("variantPairs")).isEmpty();
    }

    @Test void keepsSemanticModelFindingsWithoutDuplicatingEquivalentOutfitPairs() throws Exception {
        var input = input("墨蓝色工装外套", "墨蓝工装外套", "", "");
        var report = (ObjectNode) json.readTree("""
            {"variantsHasMore":false,"variantPairs":[{"characterId":11,"leftVariantId":102,"rightVariantId":101,
            "score":98,"sharedFeatures":["同款外套"],"differences":[],"suggestions":["核实重复"]}]}
            """);
        CharacterVariantSimilarityAnalysis.validate(input, report);
        CharacterVariantSimilarityAnalysis.addEquivalentOutfits(json, input, report);
        assertThat(report.path("variantPairs")).hasSize(1);
        assertThat(report.path("variantPairs").get(0).path("score").asInt()).isEqualTo(98);
    }

    @Test void capsLargeDuplicateGroupsAndSignalsMoreResults() {
        ArrayNode input = json.createArrayNode();
        ArrayNode variants = input.addObject().put("id", 11).putArray("variants");
        for (int i = 1; i <= 100; i++) variants.addObject().put("id", i).put("name", "墨蓝工装外套");
        var report = report();
        CharacterVariantSimilarityAnalysis.addEquivalentOutfits(json, input, report);
        assertThat(report.path("variantPairs")).hasSize(30);
        assertThat(report.path("variantsHasMore").asBoolean()).isTrue();
        CharacterVariantSimilarityAnalysis.validate(input, report);
    }
}
