package com.antshorttv.aiimage;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.material.MaterialFileAccessService;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.storage.ObjectStorageKeyFactory;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class AiImageDownloadService {
    private final AiImageResultMapper results;
    private final ProjectPermissionGuard permissions;
    private final MaterialFileAccessService files;
    private final ObjectStorageKeyFactory keys;

    public AiImageDownloadService(AiImageResultMapper results, ProjectPermissionGuard permissions,
        MaterialFileAccessService files, ObjectStorageKeyFactory keys) {
        this.results = results;
        this.permissions = permissions;
        this.files = files;
        this.keys = keys;
    }

    public ImageDownload original(Long projectId, Long resultId) {
        Original image = resolve(projectId, resultId);
        return new ImageDownload(files.resource(image.objectKey()), image.fileName(), image.contentType(), image.size());
    }

    public ImageDownload archive(Long projectId, List<Long> resultIds) throws IOException {
        if (resultIds == null || resultIds.isEmpty() || resultIds.size() > 100
            || resultIds.stream().anyMatch(id -> id == null || id < 1)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择 1 至 100 张图片。");
        }
        // Validate the whole selection before opening any private storage objects.
        List<Original> images = new LinkedHashSet<>(resultIds).stream().map(id -> resolve(projectId, id)).toList();
        Path archive = Files.createTempFile("antv-asset-images-", ".zip");
        try {
            // Finish the ZIP before sending successful headers; missing objects fail the entire download.
            try (var zip = new ZipOutputStream(Files.newOutputStream(archive), StandardCharsets.UTF_8)) {
                for (Original image : images) {
                    zip.putNextEntry(new ZipEntry(image.fileName()));
                    try (var input = files.resource(image.objectKey()).getInputStream()) {
                        input.transferTo(zip);
                    }
                    zip.closeEntry();
                }
            }
            return new ImageDownload(new TemporaryArchiveResource(archive), "项目" + projectId + "_资产图片.zip",
                "application/zip", Files.size(archive));
        } catch (IOException | RuntimeException exception) {
            try { Files.deleteIfExists(archive); } catch (IOException cleanup) { exception.addSuppressed(cleanup); }
            throw exception;
        }
    }

    private Original resolve(Long projectId, Long resultId) {
        AiImageResultEntity result = results.selectById(resultId);
        if (result == null || !projectId.equals(result.getProjectId()) || !"ACTIVE".equals(result.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片结果不存在或尚未就绪。");
        }
        permissions.require(result.getTenantId(), projectId, "AI_IMAGE_TASK:VIEW");
        String key;
        try {
            String path = result.getStoragePath();
            key = keys.objectKey(path != null && path.startsWith("/") ? path.substring(1) : path);
        } catch (IllegalArgumentException invalid) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片原文件不可用。");
        }
        if (!key.startsWith("materials/" + result.getTenantId() + "/" + projectId + "/")) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片原文件不可用。");
        }
        String extension = switch (result.getMimeType() == null ? "" : result.getMimeType()) {
            case "image/png" -> "png";
            case "image/jpeg" -> key.endsWith(".jpg") ? "jpg" : "jpeg";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "bin";
        };
        String contentType = extension.equals("bin") ? "application/octet-stream" : result.getMimeType();
        return new Original(key, "图片_" + resultId + "." + extension, contentType, result.getFileSize());
    }

    public record ImageDownload(Resource resource, String fileName, String contentType, Long fileSize) {}
    public Original describe(Long projectId, Long resultId) { return resolve(projectId, resultId); }
    public record Original(String objectKey, String fileName, String contentType, Long size) {}

    private static class TemporaryArchiveResource extends FileSystemResource {
        private final Path archive;
        TemporaryArchiveResource(Path archive) { super(archive); this.archive = archive; }
        @Override
        public InputStream getInputStream() throws IOException {
            return new FilterInputStream(super.getInputStream()) {
                @Override
                public void close() throws IOException {
                    try { super.close(); } finally { Files.deleteIfExists(archive); }
                }
            };
        }
    }
}
