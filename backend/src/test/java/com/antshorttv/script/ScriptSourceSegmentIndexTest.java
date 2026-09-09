package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ScriptSourceSegmentIndexTest {
    @Test
    void repeatedLinesRemainDistinctAndRangesPreserveAllOriginalWhitespace() {
        String source = "\r\n  【黑场转场】\r\n\r\n  【黑场转场】\r\n  ";
        var index = new ScriptSourceSegmentIndex(source);
        assertThat(index.numberedContent()).contains("[S0001]   【黑场转场】", "[S0002]   【黑场转场】");
        var episodes = index.resolve(List.of(
            new ScriptSourceSegmentIndex.Range("一", "S0001", "S0001"),
            new ScriptSourceSegmentIndex.Range("二", "S0002", "S0002")));
        assertThat(episodes).hasSize(2);
        assertThat(episodes.get(0).content()).isEqualTo("\r\n  【黑场转场】\r\n\r\n");
        assertThat(episodes.get(0).content() + episodes.get(1).content()).isEqualTo(source);
    }

    @Test
    void rejectsUnknownIdsGapsOverlapAndMissingTail() {
        var index = new ScriptSourceSegmentIndex("A\nB\nC");
        for (var ranges : List.of(
            List.of(new ScriptSourceSegmentIndex.Range("一", "S0001", "S9999")),
            List.of(new ScriptSourceSegmentIndex.Range("一", "S0002", "S0003")),
            List.of(new ScriptSourceSegmentIndex.Range("一", "S0001", "S0002")),
            List.of(new ScriptSourceSegmentIndex.Range("一", "S0001", "S0002"),
                new ScriptSourceSegmentIndex.Range("二", "S0002", "S0003")),
            List.of(new ScriptSourceSegmentIndex.Range("一", "S0001", "S0001"),
                new ScriptSourceSegmentIndex.Range("二", "S0003", "S0003")),
            List.of(new ScriptSourceSegmentIndex.Range("一", "S0001", "S0003"),
                new ScriptSourceSegmentIndex.Range("二", "S0003", "S0002")))) {
            assertThatThrownBy(() -> index.resolve(ranges)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void segmentIdsAreDeterministicAndDoNotBreakUnicode() {
        String source = "🧜海\nS0001: 原文中的编号\r尾声";
        var first = new ScriptSourceSegmentIndex(source);
        assertThat(first.numberedContent()).isEqualTo(new ScriptSourceSegmentIndex(source).numberedContent());
        assertThat(first.resolve(List.of(
            new ScriptSourceSegmentIndex.Range("完整", "S0001", "S0003"))).get(0).content())
            .isEqualTo(source);
    }

    @Test
    void acceptsMergedExplicitEpisodesWhenSourceIsFullyCovered() {
        var index = new ScriptSourceSegmentIndex("第1集\nA\n第2集\nB");
        assertThat(index.resolve(List.of(
            new ScriptSourceSegmentIndex.Range("合并", "S0001", "S0004")))).singleElement()
            .extracting(ScriptEpisodeResponse::content).isEqualTo("第1集\nA\n第2集\nB");
    }

    @Test
    void acceptsSkippedRepeatedHeadingsAndSplittingOneOriginalEpisode() {
        String source = "\r\n第六集\r\n甲\r\n乙\r\n第八集\r\n丙\r\n第八集\r\n丁\r\n ";
        var index = new ScriptSourceSegmentIndex(source);
        var episodes = index.resolve(List.of(
            new ScriptSourceSegmentIndex.Range("六上", "S0001", "S0002"),
            new ScriptSourceSegmentIndex.Range("六下", "S0003", "S0003"),
            new ScriptSourceSegmentIndex.Range("八", "S0004", "S0007")));
        assertThat(episodes.stream().map(ScriptEpisodeResponse::content).reduce("", String::concat))
            .isEqualTo(source);
    }

    @Test
    void chunkRenderingKeepsGlobalIdsAndExposesPreviousSegmentForInclusiveRanges() {
        String source = "A\r\n\r\nB\nC";
        var index = new ScriptSourceSegmentIndex(source);
        assertThat(index.numberedContent(source.indexOf("B"), source.length()))
            .isEqualTo("[S0002] B\n[S0003] C");
        assertThat(index.referenceAt(source.indexOf("B")))
            .isEqualTo(new ScriptSourceSegmentIndex.SegmentReference("S0002", "S0001"));
        assertThat(index.referenceAt(source.indexOf("A")).previousSegmentId()).isNull();
        assertThat(index.referenceAt(source.indexOf('\r'))).isNull();
    }
}
