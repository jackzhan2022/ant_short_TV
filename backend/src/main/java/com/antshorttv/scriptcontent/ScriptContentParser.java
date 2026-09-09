package com.antshorttv.scriptcontent;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ScriptContentParser {

    public String parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择非空剧本文件。");
        }
        String fileName = Optional.ofNullable(file.getOriginalFilename())
            .orElse("")
            .trim()
            .toLowerCase(Locale.ROOT);
        try {
            String content = switch (extension(fileName)) {
                case "txt", "md" -> new String(file.getBytes(), StandardCharsets.UTF_8);
                case "docx" -> parseDocx(file.getBytes());
                case "doc" -> parseDoc(file.getBytes());
                default -> throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "仅支持 txt、md、docx 文件。"
                );
            };
            String normalized = content.trim();
            if (normalized.isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "剧本文件中没有可用文字。");
            }
            return normalized;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "文件解析失败：" + exception.getMessage()
            );
        }
    }

    private String parseDocx(byte[] bytes) throws Exception {
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes);
             XWPFDocument document = new XWPFDocument(input)) {
            return document.getParagraphs().stream()
                .map(paragraph -> paragraph.getText() == null ? "" : paragraph.getText().trim())
                .filter(text -> !text.isBlank())
                .collect(Collectors.joining("\n"));
        }
    }

    private String parseDoc(byte[] bytes) throws Exception {
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes);
             HWPFDocument document = new HWPFDocument(input);
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extension(String fileName) {
        int separator = fileName.lastIndexOf('.');
        return separator < 0 ? "" : fileName.substring(separator + 1);
    }
}
