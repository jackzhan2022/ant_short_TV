package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class EpisodeSplitBoundaryResolverTest {
    private final EpisodeSplitBoundaryResolver resolver = new EpisodeSplitBoundaryResolver();

    @Test
    void resolvesCompleteCoverageAndPreservesPreambleWhitespaceTailAndFormatting() {
        String source = "简介：不能丢。\r\n\r\n第1集\r\nA  动作\r\n\r\n第2集\r\nB对白\r\n  ";

        List<ScriptEpisodeResponse> result = resolver.resolve(source, List.of(
            draft("雨夜", "第1集", "A  动作"),
            draft("重逢", "第2集", "B对白")));

        assertThat(result).extracting(ScriptEpisodeResponse::episodeNo).containsExactly(1, 2);
        assertThat(result).extracting(ScriptEpisodeResponse::title).containsExactly("雨夜", "重逢");
        assertThat(result.get(0).content()).isEqualTo("简介：不能丢。\r\n\r\n第1集\r\nA  动作\r\n\r\n");
        assertThat(result.get(1).content()).isEqualTo("第2集\r\nB对白\r\n  ");
        assertThat(result).extracting(ScriptEpisodeResponse::content).asString()
            .doesNotContain("简介：不能丢。简介：不能丢。");
        assertThat(result.stream().map(ScriptEpisodeResponse::content).reduce("", String::concat))
            .isEqualTo(source);
    }

    @Test
    void rejectsRepeatedMissingReversedOverlapAndNonWhitespaceGapMarkers() {
        assertInvalid("第1集\n同句\n第2集\n同句", List.of(
            draft("1", "第1集", "同句"), draft("2", "第2集", "同句")), "重复");
        assertInvalid("第1集\n正文", List.of(draft("1", "不存在", "正文")), "找不到");
        assertInvalid("开始\n结束", List.of(draft("1", "结束", "开始")), "顺序");
        assertInvalid("第1集\n结束跨界\n第2集\n正文", List.of(
            draft("1", "第1集", "结束跨界\n第2集\n正文"),
            draft("2", "第2集\n正文", "正文")), "重叠");
        assertInvalid("第1集\n完一\n遗漏正文\n第2集\n完二", List.of(
            draft("1", "第1集", "完一"), draft("2", "第2集", "完二")), "缺口");
    }

    @Test
    void resolvesUniqueMarkersWhenTheModelOnlyChangesPunctuationOrWhitespace() {
        String source = "前言\r\n第一集\r\nSerena os: \"What is this place...?\"（这是什么地方……？）\r\n\r\n---\r\n第四集\r\n正文。";

        List<ScriptEpisodeResponse> result = resolver.resolve(source, List.of(
            draft("坠落", "第一 集", "Serena os: \"What is this place...?\"（这是什么地方……）"),
            draft("觉醒", "第四 集", "正文。")));

        assertThat(result.stream().map(ScriptEpisodeResponse::content).reduce("", String::concat))
            .isEqualTo(source);
        assertThat(result.get(1).content()).startsWith("第四集");
    }

    @Test
    void rejectsPunctuationTolerantMarkerWhenItIsNotUnique() {
        assertInvalid("第一集\n正文。\n第二集\n正文！", List.of(
            draft("1", "第一 集", "正文")), "重复");
    }

    @Test
    void acceptsTheNextEpisodeStartAsThePreviousEpisodeEndMarker() {
        String source = "第一集\n正文一\n第二集\n正文二";

        List<ScriptEpisodeResponse> result = resolver.resolve(source, List.of(
            draft("一", "第一集", "第二集"),
            draft("二", "第二集", "正文二")));

        assertThat(result).extracting(ScriptEpisodeResponse::content)
            .containsExactly("第一集\n正文一\n", "第二集\n正文二");
    }

    private void assertInvalid(String source, List<EpisodeSplitBoundaryResolver.Boundary> boundaries,
                               String message) {
        assertThatThrownBy(() -> resolver.resolve(source, boundaries))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(message);
    }

    private EpisodeSplitBoundaryResolver.Boundary draft(String title, String start, String end) {
        return new EpisodeSplitBoundaryResolver.Boundary(title, start, end);
    }
}
