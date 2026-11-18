package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class ScriptTitleRulesTest {
    @Test void extractsExplicitTitleBeforeGenerating() {
        assertThat(ScriptTitleRules.explicitTitle("# 剧名：《归途》\n第一集\n雨夜回家")).isEqualTo("归途");
        assertThat(ScriptTitleRules.explicitTitle("《逆风而行》\n第一集：归来")).isEqualTo("逆风而行");
        assertThat(ScriptTitleRules.explicitTitle("主角翻开《归途》，开始读书。\n第一集")).isNull();
        assertThat(ScriptTitleRules.explicitTitle("第一集：归来\n雨夜回家")).isNull();
        assertThat(ScriptTitleRules.explicitTitle("AI短剧剧本：《第37次测试》\n类型：科幻")).isEqualTo("第37次测试");
        assertThat(ScriptTitleRules.explicitTitle("# 古风探案短剧《玉镯奇案》第一集\n人物表")).isEqualTo("玉镯奇案");
    }
    @Test void protectsManualNamesAndRecognizesOnlyPlaceholders() {
        assertThat(ScriptTitleRules.unnamed(" 未命名短剧 ")).isTrue();
        assertThat(ScriptTitleRules.unnamed(null)).isTrue();
        assertThat(ScriptTitleRules.unnamed("未命名剧本")).isTrue();
        assertThat(ScriptTitleRules.unnamed("未命名短剧的春天")).isFalse();
    }
    @Test void limitsInputTo5000UnicodeCharacters() {
        String text = "😀".repeat(5001);
        assertThat(ScriptTitleRules.excerpt(text)).isEqualTo("😀".repeat(5000));
        assertThat(ScriptTitleRules.excerpt("剧情")).isEqualTo("剧情");
    }
    @Test void rejectsExplanationsAndPlaceholderOutputs() {
        assertThat(ScriptTitleRules.cleanGenerated("《雨夜归途》")).isEqualTo("雨夜归途");
        assertThat(ScriptTitleRules.cleanGenerated("未命名短剧")).isNull();
        assertThat(ScriptTitleRules.cleanGenerated("无题")).isNull();
        assertThat(ScriptTitleRules.cleanGenerated("归途\n这个名字体现了剧情")).isNull();
        assertThat(ScriptTitleRules.cleanGenerated("字".repeat(51))).isNull();
    }
}
