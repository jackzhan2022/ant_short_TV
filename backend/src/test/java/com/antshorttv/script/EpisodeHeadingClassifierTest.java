package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class EpisodeHeadingClassifierTest {
    private final EpisodeHeadingClassifier classifier = new EpisodeHeadingClassifier();

    @Test
    void classifiesIndividualGroupDirectoryQuotedAndDuplicateHeadingsWithStableOffsets() {
        String source = """
            目录
            第1集 ........ 1
            第2集 ........ 8

            第一集：雨夜
            正文。
            第二集到第四集剧情
            第2集：追踪
            角色说：“第3集才是真正的开始”
            EP03 - 真相
            第2集：重复草稿
            """;

        var headings = classifier.scan(source);

        assertThat(headings).extracting(EpisodeHeadingClassifier.Heading::type)
            .containsExactly(
                EpisodeHeadingClassifier.Type.DIRECTORY,
                EpisodeHeadingClassifier.Type.DIRECTORY,
                EpisodeHeadingClassifier.Type.SINGLE,
                EpisodeHeadingClassifier.Type.GROUP,
                EpisodeHeadingClassifier.Type.AMBIGUOUS,
                EpisodeHeadingClassifier.Type.QUOTED,
                EpisodeHeadingClassifier.Type.SINGLE,
                EpisodeHeadingClassifier.Type.AMBIGUOUS);
        assertThat(headings).allSatisfy(heading ->
            assertThat(source.substring(heading.startOffset(), heading.endOffset()))
                .isEqualTo(heading.marker()));
    }

    @Test
    void recognizesArabicAndChineseNumbersThroughFiftyNine() {
        String source = IntStream.rangeClosed(1, 59)
            .mapToObj(number -> "第" + number + "集\n正文" + number)
            .reduce((left, right) -> left + "\n" + right).orElseThrow();

        assertThat(classifier.scan(source).stream()
            .filter(heading -> heading.type() == EpisodeHeadingClassifier.Type.SINGLE))
            .hasSize(59)
            .extracting(EpisodeHeadingClassifier.Heading::episodeNo)
            .containsExactlyElementsOf(IntStream.rangeClosed(1, 59).boxed().toList());
    }
}
