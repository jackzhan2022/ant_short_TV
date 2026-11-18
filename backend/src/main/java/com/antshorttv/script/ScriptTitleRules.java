package com.antshorttv.script;

import java.util.Set;
import java.util.regex.Pattern;

/** Conservative title extraction: a book mentioned in dialogue is not the drama's title. */
public final class ScriptTitleRules {
    private static final Set<String> PLACEHOLDERS = Set.of("未命名短剧", "未命名剧本");
    private static final Pattern LABEL = Pattern.compile(
        "(?m)^\\h*(?:#{1,6}\\h*)?(?:剧名|片名|剧本名称|短剧名称|作品名称|剧本名|AI短剧剧本|短剧剧本)\\h*[:：]\\h*(.{2,100}?)\\h*$");
    private static final Pattern BOOK_TITLE = Pattern.compile(
        "\\A\\s*(?:#{1,6}\\h*)?《([^《》\\r\\n]{2,50})》\\h*(?:\\r?\\n|$)");
    private static final Pattern DRAMA_HEADING = Pattern.compile(
        "\\A\\s*(?:#{1,6}\\h*)?[^\\r\\n《》]{0,20}(?:短剧|剧本)《([^《》\\r\\n]{2,50})》(?:第[^\\r\\n]{1,10}集)?\\h*(?:\\r?\\n|$)");

    private ScriptTitleRules() {}

    public static boolean unnamed(String title) {
        return title == null || title.isBlank() || PLACEHOLDERS.contains(title.trim());
    }

    public static String explicitTitle(String content) {
        if (content == null) return null;
        var labeled = LABEL.matcher(content);
        while (labeled.find()) {
            String title = cleanGenerated(labeled.group(1));
            if (title != null) return title;
        }
        var opening = BOOK_TITLE.matcher(content);
        if (opening.find()) return cleanGenerated(opening.group(1));
        var drama = DRAMA_HEADING.matcher(content);
        return drama.find() ? cleanGenerated(drama.group(1)) : null;
    }

    public static boolean hasStoryText(String content) {
        return content != null && content.codePoints().anyMatch(Character::isLetter);
    }

    public static String excerpt(String content) {
        if (content == null) return "";
        String text = content.strip();
        return text.substring(0, text.offsetByCodePoints(0, Math.min(5000, text.codePointCount(0, text.length()))));
    }

    public static String cleanGenerated(String value) {
        if (value == null) return null;
        String title = value.strip();
        if (title.startsWith("《") && title.endsWith("》")) title = title.substring(1, title.length() - 1).strip();
        int size = title.codePointCount(0, title.length());
        if (size < 2 || size > 50 || unnamed(title) || Set.of("无题", "无法命名", "未命名").contains(title)
            || title.matches("(?s).*[\\r\\n<>《》].*")
            || title.contains("剧名：") || title.contains("剧名:") || title.contains("```")) return null;
        return title;
    }
}
