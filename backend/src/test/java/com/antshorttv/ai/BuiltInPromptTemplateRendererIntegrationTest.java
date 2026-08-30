package com.antshorttv.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class BuiltInPromptTemplateRendererIntegrationTest {
    @Autowired private BuiltInPromptTemplateRenderer renderer;

    @Test
    void rendersPublishedSchemaAndOrderedSkillContentForRecognitionAgent() {
        String prompt = renderer.render("script-character-scene-recognition", Map.of(
            "scriptContent", "第1集\n林夏拿着录音笔走上天台。",
            "globalUnderstanding", "{}",
            "episodes", "{\"episodes\":[]}"
        ));

        assertThat(prompt)
            .contains("输出 Schema", "characters", "aliases", "roleType", "gender", "ageRange",
                "identity", "personality", "appearance", "scenes", "sceneType", "visualStyle",
                "props", "propType", "plotFunction", "relatedCharacter", "maxItems")
            .contains("技能约束", "strict-json-output", "no-invention", "short-drama-structure")
            .doesNotContain("AI_SECRET_KEY", "provider_config", "encrypted");
        assertThat(prompt.indexOf("strict-json-output")).isLessThan(prompt.indexOf("no-invention"));
    }

    @Test
    void rendersPersistedEpisodeSplitMarkerContract() {
        String prompt = renderer.render("script-episode-split", Map.of(
            "globalUnderstanding", "{}", "scriptContent", "第1场正文"
        ));

        assertThat(prompt)
            .contains("startMarker", "endMarker", "必须覆盖原剧本全部正文")
            .doesNotContain("\"content\":\"\"");
    }

    @Test
    void rendersPersistedDirectVideoScreenplayContract() {
        String prompt = renderer.render("video-understanding", Map.of("episodeNo", 3));

        assertThat(prompt)
            .contains("# 第3集：标题", "## 3-1 夜 内 地点", "出场人物：", "（OS）", "（VO）", "——本集完")
            .contains("{\"script\":\"完整剧本文本\"}")
            .doesNotContain("结尾钩子：", "【字幕：", "双引号包裹的原声台词");
    }
}
