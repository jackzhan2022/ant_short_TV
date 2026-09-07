package com.antshorttv.workflowagent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EpisodeSourceSegmenterTest {
    private final EpisodeSourceSegmenter segmenter = new EpisodeSourceSegmenter();

    @Test
    void assignsStableIdsTypesAndExactOffsetsToPhysicalLines() {
        String source = "第1集：门缝里的阴谋\r\n\r\n"
            + "场景：夜 内 走廊\r\n"
            + "△ Serena停下。\r\n"
            + "Serena：谁在那里？\r\n"
            + "V.S.（低沉）：暴雨将至。\r\n"
            + "OS（警觉）：有人。";

        List<EpisodeSourceSegmenter.EpisodeSourceSegment> segments = segmenter.segment(source);

        assertThat(segments).extracting(EpisodeSourceSegmenter.EpisodeSourceSegment::id)
            .containsExactly("S0001", "S0002", "S0003", "S0004", "S0005", "S0006");
        assertThat(segments).extracting(EpisodeSourceSegmenter.EpisodeSourceSegment::type)
            .containsExactly(
                EpisodeSourceSegmenter.SourceSegmentType.METADATA,
                EpisodeSourceSegmenter.SourceSegmentType.SCENE,
                EpisodeSourceSegmenter.SourceSegmentType.ACTION,
                EpisodeSourceSegmenter.SourceSegmentType.DIALOGUE,
                EpisodeSourceSegmenter.SourceSegmentType.NARRATION,
                EpisodeSourceSegmenter.SourceSegmentType.INNER_OS);
        assertThat(segments).extracting(EpisodeSourceSegmenter.EpisodeSourceSegment::requiredCoverage)
            .containsExactly(false, true, true, true, true, true);
        for (EpisodeSourceSegmenter.EpisodeSourceSegment segment : segments) {
            assertThat(source.substring(segment.startOffset(), segment.endOffset()))
                .isEqualTo(segment.text());
        }
    }

    @Test
    void preservesLineTextAndSkipsBlankLinesForLfInput() {
        String source = "  第一集到第三集剧情  \n\n普通动作，包含尾随空格。  \n角色A: English punctuation";

        List<EpisodeSourceSegmenter.EpisodeSourceSegment> segments = segmenter.segment(source);

        assertThat(segments).hasSize(3);
        assertThat(segments.get(0).text()).isEqualTo("  第一集到第三集剧情  ");
        assertThat(segments.get(0).requiredCoverage()).isFalse();
        assertThat(segments.get(1).text()).isEqualTo("普通动作，包含尾随空格。  ");
        assertThat(segments.get(2).type())
            .isEqualTo(EpisodeSourceSegmenter.SourceSegmentType.DIALOGUE);
    }

    @Test
    void doesNotSplitOneLongPhysicalLine() {
        String longAction = "△ " + "连续动作。".repeat(80);

        List<EpisodeSourceSegmenter.EpisodeSourceSegment> segments = segmenter.segment(longAction);

        assertThat(segments).singleElement().satisfies(segment -> {
            assertThat(segment.id()).isEqualTo("S0001");
            assertThat(segment.text()).isEqualTo(longAction);
            assertThat(segment.startOffset()).isZero();
            assertThat(segment.endOffset()).isEqualTo(longAction.length());
        });
    }

    @Test
    void classifiesMultilineSpeechWithoutTreatingSubtitlesOrActionLabelsAsSound() {
        String source = "Rowan（压低声音，冷笑）:\n"
            + "\"Once she falls, everything will be ours.\"\n"
            + "【字幕：只要她坠落，一切都会属于我们。】\n"
            + "Serena Aldwych（os，颤抖）:\n"
            + "\"No... how could you...\"\n"
            + "V.O.（低沉）:\n"
            + "暴雨将至。\n"
            + "结尾钩子：Rowan猛地推开房门。";

        List<EpisodeSourceSegmenter.EpisodeSourceSegment> segments = segmenter.segment(source);

        assertThat(segments).extracting(EpisodeSourceSegmenter.EpisodeSourceSegment::type)
            .containsExactly(
                EpisodeSourceSegmenter.SourceSegmentType.ACTION,
                EpisodeSourceSegmenter.SourceSegmentType.DIALOGUE,
                EpisodeSourceSegmenter.SourceSegmentType.ACTION,
                EpisodeSourceSegmenter.SourceSegmentType.ACTION,
                EpisodeSourceSegmenter.SourceSegmentType.INNER_OS,
                EpisodeSourceSegmenter.SourceSegmentType.ACTION,
                EpisodeSourceSegmenter.SourceSegmentType.NARRATION,
                EpisodeSourceSegmenter.SourceSegmentType.ACTION);
    }

    @Test
    void usesTrustedSpeakersAndDoesNotTreatProductionStructureLinesAsSound() {
        String source = "出场人物：Cassian、Serena\n"
            + "▲ 镜头缓缓移向墙上的古老壁画：画中，一名白裙女子持剑。\n"
            + "Serena：你别过来。\n"
            + "新闻主播（VO）：紧急消息。\n"
            + "Serena OS：他怎么会在这里？";

        var segments = segmenter.segment(source,
            new EpisodeSourceSegmenter.SegmentationContext(
                Set.of("Cassian", "Serena"), Set.of()));

        assertThat(segments).extracting(EpisodeSourceSegmenter.EpisodeSourceSegment::type)
            .containsExactly(
                EpisodeSourceSegmenter.SourceSegmentType.METADATA,
                EpisodeSourceSegmenter.SourceSegmentType.ACTION,
                EpisodeSourceSegmenter.SourceSegmentType.DIALOGUE,
                EpisodeSourceSegmenter.SourceSegmentType.NARRATION,
                EpisodeSourceSegmenter.SourceSegmentType.INNER_OS);
        assertThat(segments.get(0).requiredCoverage()).isFalse();
        assertThat(segments.get(1).requiredCoverage()).isTrue();
    }

    @Test
    void keepsUnknownColonPrefixAsVisualCoverageWithWarning() {
        var segments = segmenter.segment("神秘人：别动。",
            new EpisodeSourceSegmenter.SegmentationContext(Set.of("Serena"), Set.of()));

        assertThat(segments).singleElement().satisfies(segment -> {
            assertThat(segment.type()).isEqualTo(EpisodeSourceSegmenter.SourceSegmentType.ACTION);
            assertThat(segment.requiredCoverage()).isTrue();
            assertThat(segment.classificationWarning()).isEqualTo("SOURCE_SPEAKER_UNCONFIRMED");
            assertThat(segment.text()).isEqualTo("神秘人：别动。");
        });
    }
}
