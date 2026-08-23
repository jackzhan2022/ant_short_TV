package com.antshorttv.script;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ScriptEpisodeParser {
    private static final Pattern CHINESE_HEADING = Pattern.compile(
        "^((?:第\\s*(?:\\d{1,3}|[零〇一二两三四五六七八九十百]+)\\s*集))(?:\\s*[:：-]\\s*|\\s+)?(.*)$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ENGLISH_HEADING = Pattern.compile(
        "^(EP\\s*0*(\\d{1,3}))(?:\\s*[:：-]\\s*|\\s+)?(.*)$",
        Pattern.CASE_INSENSITIVE
    );

    private ScriptEpisodeParser() {
    }

    public static List<ScriptEpisodeResponse> parse(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<ParsedSection> sections = new ArrayList<>();
        ParsedSection current = null;
        List<String> preamble = new ArrayList<>();
        for (String line : content.split("\\r?\\n", -1)) {
            EpisodeHeading heading = parseHeading(line.trim());
            if (heading != null) {
                if (current != null) {
                    current.finish();
                    sections.add(current);
                }
                current = new ParsedSection(heading.episodeNo(), line.trim());
                if (!preamble.isEmpty()) {
                    current.lines.addAll(preamble);
                    preamble.clear();
                }
            } else if (current == null) {
                preamble.add(line);
            } else {
                current.lines.add(line);
            }
        }

        if (current != null) {
            current.finish();
            sections.add(current);
        }

        if (sections.isEmpty()) {
            return List.of(new ScriptEpisodeResponse(1, "第1集", content.trim()));
        }

        Map<Integer, ParsedSection> merged = new LinkedHashMap<>();
        for (ParsedSection section : sections) {
            ParsedSection existing = merged.get(section.episodeNo);
            if (existing == null) {
                merged.put(section.episodeNo, section);
            } else {
                existing.lines.addAll(section.lines);
                existing.finish();
            }
        }

        return merged.values().stream()
            .sorted(Comparator.comparingInt(section -> section.episodeNo))
            .map(section -> new ScriptEpisodeResponse(
                section.episodeNo,
                section.title,
                section.content
            ))
            .toList();
    }

    private static EpisodeHeading parseHeading(String line) {
        Matcher chinese = CHINESE_HEADING.matcher(line);
        if (chinese.matches()) {
            String numberText = chinese.group(1)
                .replaceAll("[^0-9零〇一二两三四五六七八九十百]", "");
            int episodeNo = numberText.chars().allMatch(Character::isDigit)
                ? Integer.parseInt(numberText)
                : chineseNumberToInt(numberText);
            return episodeNo > 0 ? new EpisodeHeading(episodeNo) : null;
        }

        Matcher english = ENGLISH_HEADING.matcher(line);
        if (english.matches()) {
            int episodeNo = Integer.parseInt(english.group(2));
            return episodeNo > 0 ? new EpisodeHeading(episodeNo) : null;
        }
        return null;
    }

    private static int chineseNumberToInt(String value) {
        int hundredIndex = value.indexOf('百');
        int tenIndex = value.indexOf('十');
        if (hundredIndex >= 0) {
            int hundreds = hundredIndex == 0 ? 1 : digit(value.charAt(hundredIndex - 1));
            return hundreds * 100 + chineseNumberToInt(value.substring(hundredIndex + 1));
        }
        if (tenIndex >= 0) {
            int tens = tenIndex == 0 ? 1 : digit(value.charAt(tenIndex - 1));
            int ones = tenIndex + 1 < value.length()
                ? digit(value.charAt(tenIndex + 1))
                : 0;
            return tens * 10 + ones;
        }
        return value.chars()
            .mapToObj(character -> (char) character)
            .mapToInt(ScriptEpisodeParser::digit)
            .reduce(0, (left, right) -> left * 10 + right);
    }

    private static int digit(char character) {
        return switch (character) {
            case '一' -> 1;
            case '二', '两' -> 2;
            case '三' -> 3;
            case '四' -> 4;
            case '五' -> 5;
            case '六' -> 6;
            case '七' -> 7;
            case '八' -> 8;
            case '九' -> 9;
            case '零', '〇' -> 0;
            default -> 0;
        };
    }

    private record EpisodeHeading(int episodeNo) {
    }

    private static final class ParsedSection {
        private final int episodeNo;
        private final String title;
        private final List<String> lines = new ArrayList<>();
        private String content = "";

        private ParsedSection(int episodeNo, String title) {
            this.episodeNo = episodeNo;
            this.title = title;
        }

        private void finish() {
            int start = 0;
            int end = lines.size();
            while (start < end && lines.get(start).isBlank()) {
                start++;
            }
            while (end > start && lines.get(end - 1).isBlank()) {
                end--;
            }
            content = String.join("\n", lines.subList(start, end));
        }
    }
}
