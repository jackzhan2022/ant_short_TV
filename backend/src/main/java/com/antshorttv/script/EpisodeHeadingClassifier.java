package com.antshorttv.script;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EpisodeHeadingClassifier {
    private static final String NUMBER = "(?:\\d{1,3}|[零〇〇一二两三四五六七八九十百]+)";
    private static final Pattern GROUP = Pattern.compile(
        "^第\\s*(" + NUMBER + ")\\s*集\\s*(?:到|至|[-—~～])\\s*(?:第\\s*)?(" + NUMBER
            + ")\\s*集(?:\\s*(?:剧情|概览|梗概|总结|分组))?.*$",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern CHINESE_SINGLE = Pattern.compile(
        "^第\\s*(" + NUMBER + ")\\s*集(?!\\s*(?:到|至|[-—~～])).*$",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern ENGLISH_SINGLE = Pattern.compile(
        "^(?:EP|EPISODE)\\s*0*(\\d{1,3})(?:\\b|\\s*[:：-]).*$",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern DIRECTORY_ENTRY = Pattern.compile(".*(?:\\.{2,}|…{2,})\\s*\\d+\\s*$");
    private static final Pattern QUOTED_EPISODE = Pattern.compile(
        ".*[“\"].*?(?:第\\s*(" + NUMBER + ")\\s*集|(?:EP|EPISODE)\\s*0*(\\d{1,3})).*?[”\"].*",
        Pattern.CASE_INSENSITIVE);

    public List<Heading> scan(String source) {
        if (source == null || source.isBlank()) return List.of();
        List<Heading> found = new ArrayList<>();
        boolean directory = false;
        int offset = 0;
        while (offset < source.length()) {
            int lineEnd = source.indexOf('\n', offset);
            if (lineEnd < 0) lineEnd = source.length();
            int contentEnd = lineEnd > offset && source.charAt(lineEnd - 1) == '\r'
                ? lineEnd - 1 : lineEnd;
            String raw = source.substring(offset, contentEnd);
            String marker = raw.strip();
            int markerStart = offset + raw.indexOf(marker);
            if (marker.matches("(?:分集)?目录(?:\\s*[:：])?")) {
                directory = true;
            } else if (marker.isBlank()) {
                directory = false;
            } else {
                Heading heading = classify(marker, markerStart, markerStart + marker.length(), directory);
                if (heading != null) found.add(heading);
            }
            offset = lineEnd == source.length() ? source.length() : lineEnd + 1;
        }

        Map<Integer, Integer> individualCounts = new HashMap<>();
        found.stream().filter(item -> item.type == Type.SINGLE)
            .forEach(item -> individualCounts.merge(item.episodeNo, 1, Integer::sum));
        return found.stream().map(item -> item.type == Type.SINGLE
                && individualCounts.getOrDefault(item.episodeNo, 0) > 1
                ? item.withType(Type.AMBIGUOUS) : item)
            .toList();
    }

    private Heading classify(String marker, int start, int end, boolean directory) {
        Matcher quoted = QUOTED_EPISODE.matcher(marker);
        if (quoted.matches()) {
            int episodeNo = parseNumber(quoted.group(1) == null ? quoted.group(2) : quoted.group(1));
            return new Heading(start, end, marker, Type.QUOTED, episodeNo, episodeNo);
        }
        Matcher group = GROUP.matcher(marker);
        if (group.matches()) {
            return new Heading(start, end, marker, Type.GROUP,
                parseNumber(group.group(1)), parseNumber(group.group(2)));
        }
        Matcher chinese = CHINESE_SINGLE.matcher(marker);
        Matcher english = ENGLISH_SINGLE.matcher(marker);
        if (!chinese.matches() && !english.matches()) return null;
        int episodeNo = parseNumber(chinese.matches() ? chinese.group(1) : english.group(1));
        Type type = directory || DIRECTORY_ENTRY.matcher(marker).matches()
            ? Type.DIRECTORY : Type.SINGLE;
        return episodeNo > 0 ? new Heading(start, end, marker, type, episodeNo, episodeNo) : null;
    }

    static int parseNumber(String value) {
        if (value == null || value.isBlank()) return 0;
        if (value.chars().allMatch(Character::isDigit)) return Integer.parseInt(value);
        int total = 0;
        int current = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            int digit = digit(character);
            if (character == '百') {
                total += (current == 0 ? 1 : current) * 100;
                current = 0;
            } else if (character == '十') {
                total += (current == 0 ? 1 : current) * 10;
                current = 0;
            } else {
                current = current * 10 + digit;
            }
        }
        return total + current;
    }

    private static int digit(char value) {
        return switch (value) {
            case '一' -> 1;
            case '二', '两' -> 2;
            case '三' -> 3;
            case '四' -> 4;
            case '五' -> 5;
            case '六' -> 6;
            case '七' -> 7;
            case '八' -> 8;
            case '九' -> 9;
            default -> 0;
        };
    }

    public enum Type { SINGLE, GROUP, DIRECTORY, QUOTED, AMBIGUOUS }

    public record Heading(
        int startOffset,
        int endOffset,
        String marker,
        Type type,
        int episodeNo,
        int endEpisodeNo
    ) {
        private Heading withType(Type nextType) {
            return new Heading(startOffset, endOffset, marker, nextType, episodeNo, endEpisodeNo);
        }
    }
}
