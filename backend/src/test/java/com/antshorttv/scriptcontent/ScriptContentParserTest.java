package com.antshorttv.scriptcontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.common.BusinessException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ScriptContentParserTest {

    private final ScriptContentParser parser = new ScriptContentParser();

    @Test
    void parsesUtf8TextAndMarkdown() {
        assertThat(parser.parse(file("story.txt", "第一集\n开场"))).isEqualTo("第一集\n开场");
        assertThat(parser.parse(file("story.md", "# 第一集\n开场"))).isEqualTo("# 第一集\n开场");
    }

    @Test
    void parsesDocxParagraphs() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("第一集");
            document.createParagraph().createRun().setText("开场");
            document.write(output);

            assertThat(parser.parse(file("story.docx", output.toByteArray())))
                .isEqualTo("第一集\n开场");
        }
    }

    @Test
    void rejectsEmptyUnsupportedAndUnreadableFiles() {
        assertThatThrownBy(() -> parser.parse(file("story.txt", new byte[0])))
            .isInstanceOf(BusinessException.class)
            .hasMessage("请选择非空剧本文件。");
        assertThatThrownBy(() -> parser.parse(file("story.pdf", "pdf")))
            .isInstanceOf(BusinessException.class)
            .hasMessage("仅支持 txt、md、docx 文件。");
        assertThatThrownBy(() -> parser.parse(file("story.docx", "broken")))
            .isInstanceOf(BusinessException.class)
            .hasMessageStartingWith("文件解析失败：");
    }

    private MockMultipartFile file(String name, String content) {
        return file(name, content.getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }
}
