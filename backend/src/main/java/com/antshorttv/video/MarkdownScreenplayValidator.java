package com.antshorttv.video;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MarkdownScreenplayValidator {
    static final String FORMAT_VERSION = "markdown-screenplay-v1";
    private static final int MAX_LENGTH = 200_000;
    private static final Pattern SCENE = Pattern.compile(
        "(?m)^##\\s+(\\d+)-(\\d+)\\s+(日|夜|清晨|黄昏)\\s+(内外|内|外)\\s+(\\S.+|\\S)$"
    );

    void validate(String script, int episodeNo) {
        String normalized = script == null ? "" : script.replace("\r\n", "\n").strip();
        if (normalized.length() > MAX_LENGTH) {
            throw invalid("剧本长度超过 200000 字符。");
        }
        if (!normalized.matches("(?s)^# 第" + episodeNo + "集：[^\\n]+.*")) {
            throw invalid("剧本集标题必须为 # 第" + episodeNo + "集：标题。");
        }
        if (!normalized.endsWith("——本集完")) {
            throw invalid("剧本必须以 ——本集完 结束。");
        }
        Matcher matcher = SCENE.matcher(normalized);
        List<SceneBlock> scenes = new ArrayList<>();
        while (matcher.find()) {
            scenes.add(new SceneBlock(
                Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                matcher.start(), matcher.end()
            ));
        }
        if (scenes.isEmpty()) {
            throw invalid("剧本缺少合法场景头。");
        }
        for (int index = 0; index < scenes.size(); index++) {
            SceneBlock scene = scenes.get(index);
            int expectedSceneNo = index + 1;
            if (scene.episodeNo != episodeNo || scene.sceneNo != expectedSceneNo) {
                throw invalid("场景编号必须与集号一致并从 1 连续递增。");
            }
            int blockEnd = index + 1 < scenes.size() ? scenes.get(index + 1).start : normalized.length();
            String block = normalized.substring(scene.end, blockEnd).strip();
            Matcher cast = Pattern.compile("(?m)^出场人物：\\S.*$").matcher(block);
            if (!cast.find()) {
                throw invalid("每个场景必须包含非空的 出场人物： 声明。");
            }
            String body = block.substring(cast.end()).replace("——本集完", "").strip();
            if (body.isBlank()) {
                throw invalid("每个场景必须包含非空正文。");
            }
        }
    }

    private VideoAnalysisParseException invalid(String message) {
        return new VideoAnalysisParseException(message);
    }

    private record SceneBlock(int episodeNo, int sceneNo, int start, int end) {
    }
}
