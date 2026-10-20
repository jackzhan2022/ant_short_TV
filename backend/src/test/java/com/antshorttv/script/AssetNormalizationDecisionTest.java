package com.antshorttv.script;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

class AssetNormalizationDecisionTest {
    private final ObjectMapper json = new ObjectMapper();
    private JsonNode tree(String value) throws Exception { return json.readTree(value); }
    private JsonNode inventory() throws Exception {
        return tree("""
            {"assets":[{"assetType":"CHARACTER","id":747,"name":"林嘉欣母亲"},
                       {"assetType":"CHARACTER","id":757,"name":"林嘉欣妈妈"},
                       {"assetType":"PROP","id":1511,"name":"挎包"}],
             "bindings":[{"id":1,"asset_type":"CHARACTER","asset_id":747,"episode_id":7},
                         {"id":2,"asset_type":"CHARACTER","asset_id":757,"episode_id":30},
                         {"id":3,"asset_type":"PROP","asset_id":1511,"episode_id":1},
                         {"id":4,"asset_type":"PROP","asset_id":1511,"episode_id":11}],
             "episodes":[{"id":7,"content":"林嘉欣母亲每月要七千元。"},
                         {"id":30,"content":"林嘉欣妈妈打电话要七千。"},
                         {"id":1,"content":"林嘉欣拿起自己的挎包。"},
                         {"id":11,"content":"陈志国背着自己的挎包。"}]}
            """);
    }
    private String decision() {
        return """
            {"groups":[{"assetType":"CHARACTER","retainId":747,"memberIds":[747,757],
              "evidence":[{"episodeId":7,"quote":"林嘉欣母亲每月要七千元"},
                          {"episodeId":30,"quote":"林嘉欣妈妈打电话要七千"}]},
              {"assetType":"PROP","retainId":1511,"memberIds":[1511],"evidence":[]}],
             "splits":[{"assetType":"PROP","sourceId":1511,"newName":"陈志国的挎包",
              "bindingIds":[4],"evidence":[{"episodeId":11,"quote":"陈志国背着自己的挎包"}]}]}
            """;
    }
    @Test void acceptsMotherAliasesAndSeparatesDifferentOwners() throws Exception {
        assertThat(AssetNormalizationDecision.validate(inventory(), tree(decision())).groups()).hasSize(2);
    }
    @Test void rejectsMissingIdentityCoverage() throws Exception {
        assertThatThrownBy(() -> AssetNormalizationDecision.validate(inventory(),
            tree(decision().replace("747,757", "747")))).hasMessageContaining("覆盖");
    }
    @Test void rejectsDuplicateIdentityAndCrossType() throws Exception {
        assertThatThrownBy(() -> AssetNormalizationDecision.validate(inventory(),
            tree(decision().replace("747,757", "747,757,747")))).hasMessageContaining("重复");
        assertThatThrownBy(() -> AssetNormalizationDecision.validate(inventory(),
            tree(decision().replace("747,757", "747,1511")))).hasMessageContaining("资产");
    }
    @Test void rejectsInventedEvidenceAndForeignBinding() throws Exception {
        assertThatThrownBy(() -> AssetNormalizationDecision.validate(inventory(),
            tree(decision().replace("陈志国背着自己的挎包", "陈志国拿公文包"))))
            .hasMessageContaining("原文");
        assertThatThrownBy(() -> AssetNormalizationDecision.validate(inventory(),
            tree(decision().replace("\"bindingIds\":[4]", "\"bindingIds\":[2]"))))
            .hasMessageContaining("绑定");
    }
}
