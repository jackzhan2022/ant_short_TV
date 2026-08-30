package com.antshorttv.workflowagent.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillDocumentParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesUtf8FrontmatterAndProducesStableRevision() {
        String content = """
            ---
            name: script-rewriter
            description: 小说改写为格式化剧本的方法论和规范
            ---

            # 剧本改写指南
            """;
        SkillDocumentParser parser = new SkillDocumentParser();

        SkillDocument first = parser.parse("script-rewriter", content);
        SkillDocument second = parser.parse("script-rewriter", content);

        assertThat(first.name()).isEqualTo("script-rewriter");
        assertThat(first.description()).isEqualTo("小说改写为格式化剧本的方法论和规范");
        assertThat(first.markdown()).contains("# 剧本改写指南");
        assertThat(first.revision()).hasSize(64).isEqualTo(second.revision());
    }

    @Test
    void rejectsMissingOrMalformedFrontmatter() {
        SkillDocumentParser parser = new SkillDocumentParser();

        assertThatThrownBy(() -> parser.parse("broken", "# no frontmatter"))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).getErrorCode())
            .isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThatThrownBy(() -> parser.parse("broken", "---\nname: broken\n---\nbody"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("description");
    }

    @Test
    void resolvesOnlySafeKebabCaseCodesBelowTheConfiguredRoot() {
        SkillPathResolver resolver = new SkillPathResolver(tempDir);

        assertThat(resolver.skillFile("script-rewriter"))
            .isEqualTo(tempDir.toAbsolutePath().normalize().resolve("script-rewriter/SKILL.md"));
        assertThatThrownBy(() -> resolver.skillFile("../outside"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Skill code");
        assertThatThrownBy(() -> resolver.skillFile("C:\\outside"))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> resolver.skillFile("Script_Rewriter"))
            .isInstanceOf(BusinessException.class);
    }
}
