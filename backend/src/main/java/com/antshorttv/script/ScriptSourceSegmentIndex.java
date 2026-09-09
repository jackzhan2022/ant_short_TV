package com.antshorttv.script;

import com.antshorttv.workflowagent.tool.EpisodeSourceSegmenter;
import com.antshorttv.workflowagent.tool.EpisodeSourceSegmenter.EpisodeSourceSegment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** IDs belong to one exact source revision; callers must check the source hash on save. */
public final class ScriptSourceSegmentIndex {
    public static final String RUN_STATE_KEY = "scriptSegmentProtocol";
    public static final String FORMAT = "SEGMENTED_V1";
    public static final String INSTRUCTION = """
        整剧分集使用服务端片段编号协议：read_current_script 返回的 content 中，
        每个非空原文行前的 [S0001] 是定位编号，不是原文。编号只属于本次 contentHash。
        调用 save_episode_splitting 时使用 schemaVersion=2，
        每集仅提交 title、startSegmentId、endSegmentId；首尾编号均包含在该集内。
        各集范围必须从首段连续覆盖到末段，不重叠、不漏段，不得抄写 startMarker/endMarker。
        分块模式编号沿用整剧编号，不能从 S0001 重新编号；segmentCatalog 给出首末编号，
        candidates/anchors 中 startSegmentId 是候选起点，previousSegmentId 是上一集可用终点。
        最后一集结束于 segmentCatalog.lastSegmentId。recommendedEpisodes 是旧文本协议，
        新协议请使用编号字段，不要提交其中的文本标记。
        """;

    private final String source;
    private final List<EpisodeSourceSegment> segments;
    private final Map<String, Integer> positions = new HashMap<>();

    public ScriptSourceSegmentIndex(String source) {
        this.source = source == null ? "" : source;
        this.segments = new EpisodeSourceSegmenter().segment(this.source);
        for (int index = 0; index < segments.size(); index++) {
            positions.put(segments.get(index).id(), index);
        }
    }

    public List<EpisodeSourceSegment> segments() { return segments; }

    public String numberedContent() {
        return numberedContent(0, source.length());
    }

    public String numberedContent(int from, int to) {
        if (from < 0 || to < from || to > source.length()) {
            throw new IllegalArgumentException("片段读取范围无效。");
        }
        StringBuilder result = new StringBuilder(source.length() + segments.size() * 10);
        int cursor = from;
        for (EpisodeSourceSegment segment : segments) {
            if (segment.startOffset() < from || segment.startOffset() >= to) continue;
            result.append(source, cursor, segment.startOffset());
            result.append('[').append(segment.id()).append("] ");
            cursor = Math.min(segment.endOffset(), to);
            result.append(source, segment.startOffset(), cursor);
        }
        return result.append(source, cursor, to).toString();
    }

    public EpisodeSourceSegment require(String id) {
        Integer position = positions.get(id);
        if (position == null) throw new IllegalArgumentException("原文片段编号不存在：" + id);
        return segments.get(position);
    }

    public SegmentReference referenceAt(int offset) {
        int low = 0;
        int high = segments.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (segments.get(middle).startOffset() <= offset) low = middle + 1;
            else high = middle - 1;
        }
        if (high < 0) return null;
        EpisodeSourceSegment segment = segments.get(high);
        if (offset >= segment.endOffset()) return null;
        return new SegmentReference(segment.id(), high == 0 ? null : segments.get(high - 1).id());
    }

    public record SegmentReference(String startSegmentId, String previousSegmentId) {}

    public List<ScriptEpisodeResponse> resolve(List<Range> ranges) {
        if (segments.isEmpty()) throw new IllegalArgumentException("当前剧本没有可分集的非空片段。");
        if (ranges == null || ranges.isEmpty()) throw new IllegalArgumentException("分集结果不能为空。");
        List<ScriptEpisodeResponse> episodes = new ArrayList<>();
        int expectedStart = 0;
        for (Range range : ranges) {
            if (range == null || range.title() == null || range.title().isBlank()) {
                throw new IllegalArgumentException("分集标题不能为空。");
            }
            Integer start = positions.get(range.startSegmentId());
            Integer end = positions.get(range.endSegmentId());
            if (start == null || end == null) {
                throw new IllegalArgumentException("分集片段编号不存在，请使用当前读取结果中的 S 编号。");
            }
            if (start != expectedStart || end < start) {
                throw new IllegalArgumentException("分集片段范围必须连续覆盖，不能缺段、重叠或倒序。");
            }
            int from = start == 0 ? 0 : segments.get(start).startOffset();
            int to = end + 1 == segments.size() ? source.length() : segments.get(end + 1).startOffset();
            episodes.add(new ScriptEpisodeResponse(
                episodes.size() + 1, range.title(), source.substring(from, to)));
            expectedStart = end + 1;
        }
        if (expectedStart != segments.size()) {
            throw new IllegalArgumentException("分集片段范围未覆盖剧本末尾。");
        }
        new EpisodeGranularityValidator().validate(source, episodes);
        return List.copyOf(episodes);
    }

    public record Range(String title, String startSegmentId, String endSegmentId) {}
}
