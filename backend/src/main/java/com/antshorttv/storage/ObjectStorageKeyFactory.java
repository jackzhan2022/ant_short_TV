package com.antshorttv.storage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class ObjectStorageKeyFactory {
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    public String projectOriginal(
        Long tenantId,
        Long projectId,
        String assetType,
        Long assetId,
        String versionId,
        LocalDate date,
        String extension
    ) {
        return "materials/%d/%d/%s/%s/%d/%s/original.%s".formatted(
            requirePositive(tenantId, "tenantId"),
            requirePositive(projectId, "projectId"),
            segment(assetType, "assetType"),
            require(date, "date").format(MONTH),
            requirePositive(assetId, "assetId"),
            segment(versionId, "versionId"),
            extension(extension)
        );
    }

    public String tenantUpload(Long tenantId, String sessionId, String fileName) {
        return "uploads/%d/%s/%s".formatted(
            requirePositive(tenantId, "tenantId"),
            segment(sessionId, "sessionId"),
            segment(fileName, "fileName")
        );
    }

    public String projectUpload(Long tenantId, Long projectId, String sessionId, String fileName) {
        return "uploads/%d/%d/%s/%s".formatted(
            requirePositive(tenantId, "tenantId"),
            requirePositive(projectId, "projectId"),
            segment(sessionId, "sessionId"),
            segment(fileName, "fileName")
        );
    }

    public String verifiedUploadOriginal(
        Long tenantId,
        Long projectId,
        String sessionId,
        LocalDate date,
        String extension
    ) {
        String prefix = projectId == null
            ? "materials/%d/uploads".formatted(requirePositive(tenantId, "tenantId"))
            : "materials/%d/%d/uploads".formatted(
                requirePositive(tenantId, "tenantId"),
                requirePositive(projectId, "projectId")
            );
        return "%s/%s/%s/v1/original.%s".formatted(
            prefix,
            require(date, "date").format(MONTH),
            segment(sessionId, "sessionId"),
            extension(extension)
        );
    }

    public String rendition(String originalKey, String rendition, String extension) {
        String normalized = objectKey(originalKey);
        int separator = normalized.lastIndexOf('/');
        if (separator < 0 || !normalized.substring(separator + 1).startsWith("original.")) {
            throw new IllegalArgumentException("原始对象键必须以 original.<ext> 结尾。");
        }
        return normalized.substring(0, separator)
            + "/derived/" + segment(rendition, "rendition") + "." + extension(extension);
    }

    public String objectKey(String value) {
        if (value == null || value.isBlank() || value.startsWith("/") || value.contains("..")
            || value.contains("\\") || value.contains(":") || value.contains("?") || value.contains("#")) {
            throw new IllegalArgumentException("对象键不合法。");
        }
        return value;
    }

    private String segment(String value, String name) {
        if (value == null || value.isBlank() || value.contains("/") || value.contains("\\")
            || value.contains("..") || value.contains("?") || value.contains("#")) {
            throw new IllegalArgumentException(name + " 不合法。");
        }
        return value.trim();
    }

    private String extension(String value) {
        String normalized = segment(value, "extension");
        return normalized.startsWith(".") ? normalized.substring(1) : normalized;
    }

    private long requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + " 必须大于 0。");
        }
        return value;
    }

    private <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " 不能为空。");
        }
        return value;
    }
}
