package com.antshorttv.script;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class PropSimplificationDecisionTest {
    private final ObjectMapper json=new ObjectMapper();
    private JsonNode input() throws Exception {return json.readTree("""
        {"props":[{"id":1},{"id":2}],"scenes":[{"id":5}],
         "variants":[{"id":11,"asset_id":1},{"id":12,"asset_id":2}],
         "episodes":[{"id":8,"content":"王大山与李明各拿起一把炒勺。"}],"bindings":[{"asset_id":1,"episode_id":8},{"asset_id":2,"episode_id":8}]}
        """);}
    private ObjectNode decision() throws Exception {return (ObjectNode)json.readTree("""
        {"groups":[{"name":"普通炒勺","mode":"SHARED","targetId":1,"memberIds":[1,2],"sceneIds":[],"variants":[],
        "reason":"外观相同","evidence":[{"episodeId":8,"quote":"各拿起一把炒勺"}]}]}
        """);}
    @Test void acceptsVisualReuseWithoutIdentityMerge() throws Exception {assertThatCode(()->PropSimplificationDecision.validate(input(),decision())).doesNotThrowAnyException();}
    @Test void rejectsCrossProjectAndMissingMembers() throws Exception {
        var value=decision();((ObjectNode)value.path("groups").get(0)).putArray("memberIds").add(1).add(99);
        assertThatThrownBy(()->PropSimplificationDecision.validate(input(),value)).hasMessageContaining("跨项目");
    }
    @Test void rejectsFabricatedEvidence() throws Exception {
        var value=decision();((ObjectNode)value.path("groups").get(0).path("evidence").get(0)).put("quote","原文不存在的文字");
        assertThatThrownBy(()->PropSimplificationDecision.validate(input(),value)).hasMessageContaining("逐字原文");
    }
    @Test void requiresRealSceneAndKeepsOriginalCoverage() throws Exception {
        var value=decision();((ObjectNode)value.path("groups").get(0)).put("mode","SCENE");
        assertThatThrownBy(()->PropSimplificationDecision.validate(input(),value)).hasMessageContaining("有效场景");
        ((ObjectNode)value.path("groups").get(0)).putArray("sceneIds").add(5);
        assertThatCode(()->PropSimplificationDecision.validate(input(),value)).doesNotThrowAnyException();
    }
    @Test void doesNotFlattenMultipleExistingStates() throws Exception {
        var inventory=(ObjectNode)input();((com.fasterxml.jackson.databind.node.ArrayNode)inventory.path("variants")).addObject().put("id",13).put("asset_id",1);
        assertThatThrownBy(()->PropSimplificationDecision.validate(inventory,decision())).hasMessageContaining("多个形态");
    }
    @Test void requiresAllExistingVariantMappings() throws Exception {
        var value=decision();var group=(ObjectNode)value.path("groups").get(0);group.put("mode","VARIANTS");
        group.putArray("variants").addObject().put("name","普通外观").put("prompt","完整绘图提示词").putArray("sourceVariantIds").add(11);
        assertThatThrownBy(()->PropSimplificationDecision.validate(input(),value)).hasMessageContaining("所有已有形态");
    }
}
