package com.antshorttv.script;

import static org.assertj.core.api.Assertions.*;
import com.antshorttv.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ManualAssetConfigurationTest {
    private final ManualAssetConfiguration config = new ManualAssetConfiguration(new ObjectMapper());
    static Map<String,String> values(ManualAssetConfiguration config, String type) {
        Map<String,String> values = new LinkedHashMap<>();
        config.definition(type).fields().forEach(f -> values.put(f.key(), f.unknown() == null ? "具体造型" : f.unknown()));
        return values;
    }
    @Test void characterHasExactAiFieldsAndFixedLayout() throws Exception {
        var built=config.build("CHARACTER", values(config,"CHARACTER"));
        assertThat(config.definition("CHARACTER").fields()).hasSize(14);
        assertThat(built.prompt()).startsWith("## 任务：完成角色").contains("40%", "60%", "自然站立", "双手自然下垂", "中性表情");
        for(String label: new String[]{"时代基底","国家/朝代","人种","类型基底","脸型","发型","耳饰","身材","头身比","上身着装","下身着装","鞋子","性别","年龄"})
            assertThat(built.prompt()).contains("- **"+label+"**: ");
        String skill=Files.readString(Path.of("skills/short-drama-asset-recognition-framework/SKILL.md"));
        assertThat(skill).contains(config.definition("CHARACTER").task());
    }
    @Test void scenesAndPropsUseExactExistingTasks() throws Exception {
        String skill=Files.readString(Path.of("skills/short-drama-asset-recognition-framework/SKILL.md"));
        for(String type:new String[]{"SCENE","PROP"}) {
            assertThat(config.definition(type).fields()).hasSize(5);
            assertThat(skill.replace("\r\n","\n")).contains(config.definition(type).task().replace("\r\n","\n"));
            assertThat(config.build(type,values(config,type)).prompt()).contains(config.definition(type).task());
        }
    }
    @Test void rejectsMissingUnknownAndPlaceholderVisuals() {
        assertThatThrownBy(()->config.build("CHARACTER",Map.of())).isInstanceOf(BusinessException.class);
        for(String bad:new String[]{"", "未明确", "未明确（待确认）", "待补充", "{值}", "...", "无依据", "未明确(待确认)"}) {
            var values=values(config,"CHARACTER");values.put("face",bad);
            assertThatThrownBy(()->config.build("CHARACTER",values)).isInstanceOf(BusinessException.class);
        }
    }
    @Test void acceptsExplicitAbsenceAndRejectsExtraOrOversizedFields() {
        var values=values(config,"CHARACTER");values.put("earrings","无");values.put("shoes","赤脚");
        assertThat(config.build("CHARACTER",values).prompt()).contains("耳饰**: 无","鞋子**: 赤脚");
        values.put("unexpected","text");
        assertThatThrownBy(()->config.build("CHARACTER",values)).isInstanceOf(BusinessException.class);
        values.remove("unexpected");values.put("face","长".repeat(201));
        assertThatThrownBy(()->config.build("CHARACTER",values)).isInstanceOf(BusinessException.class);
    }
}
