package com.antshorttv.project;

import com.antshorttv.aiimage.AiImageResultMapper;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.storage.*;
import com.antshorttv.style.StyleLibraryEntity;
import com.antshorttv.style.StyleLibraryMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;

@Service
public class ProjectCoverImageService {
    private static final int MAX_BYTES = 20 * 1024 * 1024;
    private static final java.util.regex.Pattern IMAGE_RESULT = java.util.regex.Pattern.compile(
        "/api/projects/(\\d+)/ai-image-results/(\\d+)/(?:image|display|thumbnail|download)");
    private final ProjectMapper projects;
    private final ProjectAccessResolver access;
    private final ImageDisplayRenditionService renditions;
    private final ObjectStorageService storage;
    private final MediaDeliveryGrantService grants;
    private final StyleLibraryMapper styles;
    private final AiImageResultMapper images;
    private final ObjectStorageKeyFactory keys;
    private final ObjectStorageProperties properties;

    public ProjectCoverImageService(ProjectMapper projects, ProjectAccessResolver access,
        ImageDisplayRenditionService renditions, ObjectStorageService storage,
        MediaDeliveryGrantService grants, StyleLibraryMapper styles, AiImageResultMapper images,
        ObjectStorageKeyFactory keys, ObjectStorageProperties properties) {
        this.projects = projects;
        this.access = access;
        this.renditions = renditions;
        this.storage = storage;
        this.grants = grants;
        this.styles = styles;
        this.images = images;
        this.keys = keys;
        this.properties = properties;
    }

    public DeliveryGrant delivery(Long projectId) {
        ProjectAccessContext context = authorized(projectId);
        RegisteredMediaDetails display = resolve(context.project(), false);
        if (!ready(display)) return null;
        return grants.issue(new DeliveryGrantRequest(context.project().tenantId, projectId,
            context.tenant().userId(), "PROJECT_COVER", projectId, version(context.project()),
            "DISPLAY", display.objectKey(), false));
    }

    public CoverStatus status(Long projectId) {
        ProjectEntity project = authorized(projectId).project();
        resolve(project, false);
        return new CoverStatus(summaryStatus(project), "FAILED".equals(project.coverStatus), url(project));
    }

    public CoverStatus retry(Long projectId) {
        ProjectAccessContext context = authorized(projectId);
        if (context.capabilities() == null || !context.capabilities().canEdit()) throw denied();
        resolve(context.project(), true);
        return new CoverStatus(summaryStatus(context.project()),
            "FAILED".equals(context.project().coverStatus), url(context.project()));
    }

    private ProjectAccessContext authorized(Long id) {
        ProjectEntity project = projects.selectById(id);
        if (project == null || project.deletedAt != null) throw denied();
        return access.requireView(project.tenantId, id);
    }

    public CoverStatus bindUpload(ProjectEntity project, VerifiedMediaUpload upload) {
        if (ProjectStatus.ARCHIVED.name().equals(project.status))
            throw new BusinessException(ErrorCode.PROJECT_ARCHIVED, "Archived project cannot replace cover.");
        String prefix = "materials/" + project.tenantId + "/";
        if (!upload.contentType().startsWith("image/") || upload.size() <= 0 || upload.size() > MAX_BYTES
            || !(upload.objectKey().startsWith(prefix + project.id + "/uploads/")
                 || upload.objectKey().startsWith(prefix + "uploads/"))) throw unavailable();
        ProjectEntity source = new ProjectEntity();
        source.coverUrl = upload.objectKey();
        String version = version(source);
        String extension = upload.contentType().equals("image/jpeg") ? "jpg"
            : upload.contentType().substring("image/".length());
        if (!java.util.Set.of("jpg", "png", "gif", "webp").contains(extension)) throw unavailable();
        String key = keys.projectOriginal(project.tenantId, project.id, "project_cover", project.id,
            version, LocalDate.of(2000, 1, 1), extension);
        boolean same = version.equals(project.coverVersion);
        projects.installUploadCover(project.id, project.tenantId, version, key);
        project.coverUrl = key;
        project.coverVersion = version;
        if (!same) project.coverStatus = "UNPROCESSED";
        if (projects.claimCover(project, key, version, true) != 1)
            return new CoverStatus(summaryStatus(project), "FAILED".equals(project.coverStatus), url(project));
        project.coverStatus = "PENDING";
        try {
            byte[] bytes;
            try (var input = storage.resource(upload.objectKey()).getInputStream()) {
                bytes = input.readNBytes(MAX_BYTES + 1);
            }
            if (bytes.length > MAX_BYTES) throw unavailable();
            ImageMetadata metadata = metadata(bytes);
            if (!upload.contentType().equals(metadata.mimeType())) throw unavailable();
            StoredObject original = storage.copyCompletedUploadOriginal(new StoredObject(upload.objectKey(),
                upload.size(), upload.contentType(), upload.eTag(), properties.getStorageClass()), key);
            MediaObjectIdentity identity = new MediaObjectIdentity(project.tenantId, project.id,
                "PROJECT_COVER", project.id, version);
            if (renditions.originalDetails(identity) == null)
                renditions.registerOriginalAndSubmit(identity, original, metadata.width(), metadata.height(),
                    "project-cover-" + project.id + "-" + version);
            return new CoverStatus("PENDING", false, url(project));
        } catch (Exception exception) {
            state(project, version, "FAILED", "Cover upload processing failed: " + exception.getClass().getSimpleName());
            return new CoverStatus("FAILED", true, url(project));
        }
    }

    private RegisteredMediaDetails resolve(ProjectEntity project, boolean retry) {
        if (url(project) == null) return null;
        String version = version(project);
        MediaObjectIdentity identity = new MediaObjectIdentity(project.tenantId, project.id,
            "PROJECT_COVER", project.id, version);
        try {
            RegisteredMediaDetails display = renditions.displayDetails(identity);
            if (display == null) display = existingSourceDisplay(project);
            if (display != null) {
                state(project, version, ready(display) ? "READY" :
                    java.util.Set.of("FAILED", "RETIRED").contains(display.status()) ? "FAILED" : "PENDING", null);
                if (retry && "FAILED".equals(display.status())) {
                    renditions.retryFailedDisplay(display.identity(), "project-cover-" + project.id + "-" + version);
                    state(project, version, "PENDING", null);
                    return null;
                }
                return ready(display) ? display : null;
            }
            if ("FAILED".equals(project.coverStatus) && !retry) return null;
            if (projects.claimCover(project, project.coverUrl, version, retry) != 1) return null;
            project.coverVersion = version;
            project.coverStatus = "PENDING";
            RegisteredMediaDetails original = renditions.originalDetails(identity);
            if (original != null) {
                renditions.retryFailedDisplay(identity, "project-cover-" + project.id + "-" + version);
                return null;
            }
            byte[] bytes = originalBytes(project);
            ImageMetadata metadata = metadata(bytes);
            String key = keys.projectOriginal(project.tenantId, project.id, "project_cover", project.id,
                version, LocalDate.of(2000, 1, 1), metadata.extension());
            StoredObject uploaded = storage.uploadOriginal(key, bytes, metadata.mimeType());
            // Keep the version across canonicalization; callbacks address immutable media identities.
            if (projects.canonicalizeCover(project.id, version, uploaded.key()) == 1) project.coverUrl = uploaded.key();
            renditions.registerOriginalAndSubmit(identity, uploaded, metadata.width(), metadata.height(),
                "project-cover-" + project.id + "-" + version);
            return null;
        } catch (Exception exception) {
            state(project, version, "FAILED", "Cover source unavailable or processing failed: "
                + exception.getClass().getSimpleName());
            return null;
        }
    }

    private RegisteredMediaDetails existingSourceDisplay(ProjectEntity project) {
        String source = project.coverUrl;
        if (source.startsWith("/api/style-library/images/")) {
            StyleLibraryEntity style = style(source);
            if (style.getId() == null || style.getExternalId() == null) return null;
            return renditions.displayDetails(new MediaObjectIdentity(0L, null, "STYLE_LIBRARY",
                style.getId(), style.getExternalId()));
        }
        if (source.startsWith("/api/projects/")) {
            var matcher = IMAGE_RESULT.matcher(source);
            if (!matcher.matches()) throw unavailable();
            Long projectId = Long.valueOf(matcher.group(1));
            Long resultId = Long.valueOf(matcher.group(2));
            var image = images.selectById(resultId);
            if (image == null || !Objects.equals(project.id, projectId)
                || !Objects.equals(project.id, image.getProjectId())
                || !Objects.equals(project.tenantId, image.getTenantId())
                || !"ACTIVE".equals(image.getStatus())) throw unavailable();
            return renditions.displayDetails(new MediaObjectIdentity(project.tenantId, project.id,
                "AI_IMAGE_RESULT", resultId, "result-" + resultId));
        }
        if (source.startsWith("data:")) return null;
        String key = ownedKey(project, source);
        ProjectCoverObject object = projects.selectOwnedCoverObject(project.tenantId, project.id, key);
        if (object == null) return null;
        if ("RETIRED".equals(object.status)) throw unavailable();
        return "DISPLAY_IMAGE_SLIM".equals(object.renditionType)
            ? object.details() : renditions.displayDetails(object.identity());
    }

    private StyleLibraryEntity style(String source) {
        String id = source.substring("/api/style-library/images/".length());
        if (id.isBlank() || id.contains("/") || id.contains("?")) throw unavailable();
        StyleLibraryEntity style = styles.selectOne(new LambdaQueryWrapper<StyleLibraryEntity>()
            .eq(StyleLibraryEntity::getExternalId, id).eq(StyleLibraryEntity::getIsPublic, true).last("limit 1"));
        if (style == null || !Boolean.TRUE.equals(style.getIsPublic())) throw unavailable();
        return style;
    }

    private byte[] originalBytes(ProjectEntity project) throws Exception {
        String source = project.coverUrl;
        if (source.startsWith("data:image/")) {
            int separator = source.indexOf(",");
            if (separator < 0 || !source.substring(0, separator).endsWith(";base64")
                || source.length() - separator > MAX_BYTES * 4L / 3 + 8) throw unavailable();
            byte[] bytes = Base64.getDecoder().decode(source.substring(separator + 1));
            if (bytes.length > MAX_BYTES) throw unavailable();
            return bytes;
        }
        String path;
        if (source.startsWith("/api/style-library/images/")) path = style(source).getStoragePath();
        else if (source.startsWith("/api/projects/")) {
            var matcher = IMAGE_RESULT.matcher(source);
            if (!matcher.matches()) throw unavailable();
            var image = images.selectById(Long.valueOf(matcher.group(2)));
            if (image == null || !Objects.equals(project.id, image.getProjectId())
                || !Objects.equals(project.tenantId, image.getTenantId())
                || !"ACTIVE".equals(image.getStatus())) throw unavailable();
            path = image.getStoragePath();
        } else path = ownedKey(project, source);
        if (path == null || path.isBlank()) throw unavailable();
        try (var input = storage.resource(path).getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw unavailable();
            return bytes;
        }
    }

    private String ownedKey(ProjectEntity project, String source) {
        String key = source;
        if (source.startsWith("http://") || source.startsWith("https://")) {
            URI uri = URI.create(source);
            URI cdn = URI.create(properties.getCdnDomain());
            if (!Objects.equals(uri.getScheme(), cdn.getScheme())
                || !Objects.equals(uri.getAuthority(), cdn.getAuthority())) throw unavailable();
            key = uri.getPath();
        }
        if (key.startsWith("/")) key = key.substring(1);
        key = keys.objectKey(key);
        if (!key.startsWith("materials/" + project.tenantId + "/" + project.id + "/")) throw unavailable();
        return key;
    }

    private ImageMetadata metadata(byte[] bytes) throws Exception {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw unavailable();
            var reader = readers.next();
            try {
                reader.setInput(input);
                String extension = switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                    case "jpeg", "jpg" -> "jpg";
                    case "png" -> "png";
                    case "gif" -> "gif";
                    case "webp" -> "webp";
                    default -> throw unavailable();
                };
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > 100_000_000) throw unavailable();
                return new ImageMetadata(extension, extension.equals("jpg") ? "image/jpeg" : "image/" + extension,
                    width, height);
            } finally { reader.dispose(); }
        }
    }

    private void state(ProjectEntity project, String version, String status, String error) {
        if (project.coverVersion == null && projects.claimCover(project, project.coverUrl, version, false) == 1)
            project.coverVersion = version;
        projects.updateCoverState(project.id, version, status, error);
        project.coverStatus = status;
        project.coverError = error;
    }

    private boolean ready(RegisteredMediaDetails display) {
        return display != null && "READY".equals(display.status()) && display.fileSize() > 0
            && display.mimeType() != null && display.mimeType().startsWith("image/")
            && "DISPLAY_IMAGE_SLIM".equals(display.renditionType());
    }

    static void assignSource(ProjectEntity project, String source) {
        if (Objects.equals(source, project.coverUrl)) return;
        project.coverUrl = source == null || source.isBlank() ? null : source;
        project.coverVersion = null;
        project.coverVersion = url(project) == null ? null : version(project);
        project.coverStatus = url(project) == null ? "MISSING" : "UNPROCESSED";
        project.coverError = null;
    }

    public void assign(ProjectEntity project, String source) {
        if (source != null && source.startsWith("data:image/") && source.length() > 500) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Inline project covers are too large; upload the image through a controlled media session.");
        }
        assignSource(project, source);
        if (url(project) == null) return;
        try {
            String value = project.coverUrl;
            if (value.startsWith("data:image/")) {
                if (value.length() > MAX_BYTES * 4L / 3 + 128) throw unavailable();
            } else if (value.startsWith("/api/style-library/images/")
                || value.matches("/api/projects/\\d+/ai-image-results/\\d+/(image|display|thumbnail|download)")) {
                // Resource ownership is checked again on every authorized delivery.
            } else if (value.startsWith("materials/") || value.startsWith("/materials/")) {
                keys.objectKey(value.startsWith("/") ? value.substring(1) : value);
            } else {
                URI uri = URI.create(value), cdn = URI.create(properties.getCdnDomain());
                if (!Objects.equals(uri.getScheme(), cdn.getScheme())
                    || !Objects.equals(uri.getAuthority(), cdn.getAuthority())
                    || !uri.getPath().startsWith("/materials/")) throw unavailable();
            }
        } catch (Exception exception) {
            project.coverStatus = "FAILED";
            project.coverError = "Unsupported cover source; use a controlled upload or owned platform image.";
        }
    }

    public static String url(ProjectEntity project) {
        return project.coverUrl == null || project.coverUrl.isBlank() ? null : "/api/projects/" + project.id + "/cover";
    }

    public static String summaryStatus(ProjectEntity project) {
        if (url(project) == null) return "MISSING";
        return "READY".equals(project.coverStatus) || "FAILED".equals(project.coverStatus)
            ? project.coverStatus : "PENDING";
    }

    public static String source(ProjectEntity project, String submitted) {
        return Objects.equals(submitted, url(project)) ? project.coverUrl : submitted;
    }

    static String version(ProjectEntity project) {
        if (project.coverVersion != null) return project.coverVersion;
        try {
            String source = project.coverUrl;
            if (source.startsWith("http://") || source.startsWith("https://")) {
                try {
                    URI uri = URI.create(source);
                    source = uri.getScheme() + "://" + uri.getAuthority() + uri.getPath();
                } catch (IllegalArgumentException ignored) {
                    // Malformed legacy sources still need an identity and a durable FAILED diagnostic.
                }
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int start = 0; start < source.length();) {
                int end = Math.min(source.length(), start + 8192);
                if (end < source.length() && Character.isHighSurrogate(source.charAt(end - 1))) end--;
                digest.update(source.substring(start, end).getBytes(StandardCharsets.UTF_8));
                start = end;
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) { throw unavailable(); }
    }

    private static BusinessException unavailable() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, "Cover source unavailable.");
    }
    private static BusinessException denied() {
        return new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED, "Project access denied.");
    }
    private record ImageMetadata(String extension, String mimeType, int width, int height) {}
    public record CoverStatus(String status, boolean retryable, String coverUrl) {}
}
