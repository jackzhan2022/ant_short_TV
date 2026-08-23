package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ScriptEpisodeParserTest {

    @Test
    void parsesArabicChineseAndEnglishEpisodeHeadings() {
        List<ScriptEpisodeResponse> episodes = ScriptEpisodeParser.parse("""
            故事简介：主角回到故乡。

            第1集：雨夜
            第一场，主角走进院子。

            第十二集 决战
            最终对峙开始。

            EP13: Aftermath
            众人离开。
            """);

        assertThat(episodes).extracting(ScriptEpisodeResponse::episodeNo)
            .containsExactly(1, 12, 13);
        assertThat(episodes).extracting(ScriptEpisodeResponse::title)
            .containsExactly("第1集：雨夜", "第十二集 决战", "EP13: Aftermath");
        assertThat(episodes.get(0).content())
            .contains("故事简介：主角回到故乡。")
            .contains("第一场，主角走进院子。")
            .doesNotContain("第1集：雨夜");
    }

    @Test
    void acceptsWhitespaceAndEnglishOrChineseColon() {
        List<ScriptEpisodeResponse> episodes = ScriptEpisodeParser.parse("""
            第 2 集 ： 第二集
            内容二

            EP 03 - Third
            内容三
            """);

        assertThat(episodes).extracting(ScriptEpisodeResponse::episodeNo)
            .containsExactly(2, 3);
        assertThat(episodes.get(0).title()).isEqualTo("第 2 集 ： 第二集");
        assertThat(episodes.get(1).title()).isEqualTo("EP 03 - Third");
    }

    @Test
    void preservesOrderAndMergesDuplicateEpisodeNumbers() {
        List<ScriptEpisodeResponse> episodes = ScriptEpisodeParser.parse("""
            第2集
            后半段。

            第1集
            前半段。

            EP02
            补充段落。
            """);

        assertThat(episodes).extracting(ScriptEpisodeResponse::episodeNo)
            .containsExactly(1, 2);
        assertThat(episodes.get(1).content())
            .contains("后半段。")
            .contains("补充段落。");
    }

    @Test
    void fallsBackToOneEpisodeForUnstructuredContentAndNoneForBlankContent() {
        List<ScriptEpisodeResponse> fallback = ScriptEpisodeParser.parse("一段没有集标题的剧本。");
        assertThat(fallback).singleElement().satisfies(episode -> {
            assertThat(episode.episodeNo()).isEqualTo(1);
            assertThat(episode.title()).isEqualTo("第1集");
            assertThat(episode.content()).isEqualTo("一段没有集标题的剧本。");
        });

        assertThat(ScriptEpisodeParser.parse(" \n\t ")).isEmpty();
        assertThat(ScriptEpisodeParser.parse(null)).isEmpty();
    }
}
