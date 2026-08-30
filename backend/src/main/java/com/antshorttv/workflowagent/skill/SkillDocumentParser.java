package com.antshorttv.workflowagent.skill;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

@Component
public class SkillDocumentParser {
    private final Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

    public SkillDocument parse(String code, String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            throw invalid("SKILL.md 内容不能为空。");
        }
        String content = rawContent.replace("\r\n", "\n").replace('\r', '\n');
        if (!content.startsWith("---\n")) {
            throw invalid("SKILL.md 必须以 YAML frontmatter 开始。");
        }
        int closing = content.indexOf("\n---\n", 4);
        if (closing < 0) {
            throw invalid("SKILL.md 的 YAML frontmatter 未正确结束。");
        }
        Map<?, ?> frontmatter;
        try {
            Object parsed = yaml.load(content.substring(4, closing));
            if (!(parsed instanceof Map<?, ?> map)) {
                throw invalid("SKILL.md frontmatter 必须是键值结构。");
            }
            frontmatter = map;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalid("SKILL.md frontmatter 不是合法 YAML。");
        }
        String name = required(frontmatter.get("name"), "name");
        String description = required(frontmatter.get("description"), "description");
        String markdown = content.substring(closing + 5);
        if (markdown.isBlank()) {
            throw invalid("SKILL.md Markdown 正文不能为空。");
        }
        return new SkillDocument(code, name, description, markdown, content, sha256(content));
    }

    private String required(Object value, String field) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw invalid("SKILL.md frontmatter 缺少 " + field + "。");
        }
        return text.trim();
    }

    private String sha256(String content) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}
