package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class EpisodeGranularityValidatorTest {
    private final EpisodeGranularityValidator validator = new EpisodeGranularityValidator();

    @Test
    void rejectsFiftyNineExplicitEpisodesMergedIntoEightNarrativeGroups() {
        String source = fiftyNineEpisodeSource();
        List<ScriptEpisodeResponse> merged = mergeIntoEight(source);

        assertThatThrownBy(() -> validator.validate(source, merged))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("59")
            .hasMessageContaining("8")
            .hasMessageContaining("单集边界");
    }

    @Test
    void acceptsEveryExplicitEpisodeAndKeepsTitlelessInferenceAvailable() {
        String source = fiftyNineEpisodeSource();
        List<EpisodeHeadingClassifier.Heading> headings = new EpisodeHeadingClassifier().scan(source)
            .stream().filter(item -> item.type() == EpisodeHeadingClassifier.Type.SINGLE).toList();
        List<ScriptEpisodeResponse> episodes = new ArrayList<>();
        for (int index = 0; index < headings.size(); index++) {
            int start = index == 0 ? 0 : headings.get(index).startOffset();
            int end = index + 1 < headings.size() ? headings.get(index + 1).startOffset() : source.length();
            episodes.add(new ScriptEpisodeResponse(index + 1, headings.get(index).marker(),
                source.substring(start, end)));
        }

        assertThatCode(() -> validator.validate(source, episodes)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate("无明确集标题的剧本", List.of(
            new ScriptEpisodeResponse(1, "AI 推断集", "无明确集标题的剧本"))))
            .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingOrDuplicateExplicitEpisodeNumbersAsAmbiguous() {
        String source = "第1集\nA\n第2集\nB\n第2集\nC\n第4集\nD";

        assertThatThrownBy(() -> validator.validate(source, List.of(
            new ScriptEpisodeResponse(1, "1", source))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("重复或缺号");
    }

    private String fiftyNineEpisodeSource() {
        StringBuilder source = new StringBuilder("剧情分组仅作参考\n");
        int[][] groups = {{1, 19}, {20, 24}, {25, 28}, {29, 34},
            {35, 40}, {41, 45}, {46, 51}, {52, 59}};
        for (int[] group : groups) {
            source.append("第").append(group[0]).append("集到第")
                .append(group[1]).append("集剧情\n");
            IntStream.rangeClosed(group[0], group[1]).forEach(number -> source
                .append("第").append(number).append("集：标题").append(number).append('\n')
                .append("正文").append(number).append('\n'));
        }
        return source.toString();
    }

    private List<ScriptEpisodeResponse> mergeIntoEight(String source) {
        int[] starts = {1, 20, 25, 29, 35, 41, 46, 52};
        List<ScriptEpisodeResponse> result = new ArrayList<>();
        for (int index = 0; index < starts.length; index++) {
            int start = source.indexOf("第" + starts[index] + "集到第");
            int end = index + 1 < starts.length
                ? source.indexOf("第" + starts[index + 1] + "集到第")
                : source.length();
            if (index == 0) start = 0;
            result.add(new ScriptEpisodeResponse(index + 1, "剧情组" + (index + 1),
                source.substring(start, end)));
        }
        return result;
    }
}
