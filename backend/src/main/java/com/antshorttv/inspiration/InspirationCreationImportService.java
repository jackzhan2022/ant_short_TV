package com.antshorttv.inspiration;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.storage.CloudInfiniteVideoCoverService;
import com.antshorttv.storage.ImageDisplayRenditionService;
import com.antshorttv.storage.RegisteredImageDisplay;
import com.antshorttv.storage.RegisteredMediaDetails;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspirationCreationImportService {
    private static final String ADMIN_AUTHOR = "管理员";

    private final InspirationCreationMapper mapper;
    private final InspirationCreationMediaStorage mediaStorage;
    private final ObjectMapper objectMapper;
    private final CloudInfiniteVideoCoverService videoCovers;
    private final ImageDisplayRenditionService imageRenditions;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    public InspirationCreationImportService(
        InspirationCreationMapper mapper,
        InspirationCreationMediaStorage mediaStorage,
        ObjectMapper objectMapper,
        CloudInfiniteVideoCoverService videoCovers,
        ImageDisplayRenditionService imageRenditions
    ) {
        this.mapper = mapper;
        this.mediaStorage = mediaStorage;
        this.objectMapper = objectMapper;
        this.videoCovers = videoCovers;
        this.imageRenditions = imageRenditions;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<InspirationCreationEntity> importFrom(InspirationCreationImportRequest request) {
        if (request.listUrl() == null || request.listUrl().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "列表接口地址不能为空。");
        }
        JsonNode listRoot = getJson(request.listUrl(), request.headers());
        List<InspirationCreationEntity> results = new ArrayList<>();
        int index = 0;
        for (JsonNode item : items(listRoot)) {
            index++;
            String externalId = text(item, "id", "externalId", "creationId");
            if (externalId == null) {
                continue;
            }
            try {
                results.add(importOne(request, item, externalId, index));
            } catch (Exception exception) {
                results.add(markFailed(item, externalId, index, message(exception)));
            }
        }
        return results;
    }

    private InspirationCreationEntity importOne(InspirationCreationImportRequest request, JsonNode item, String externalId, int sortOrder) {
        JsonNode detailRoot = getJson(detailUrl(request, externalId), request.headers());
        JsonNode detail = payload(detailRoot);
        String mediaUrl = firstNonBlank(text(item, "url", "mediaUrl", "imageUrl", "videoUrl"), mediaUrl(detail));
        InspirationCreationEntity entity = mapper.selectByExternalId(externalId);
        LocalDateTime now = LocalDateTime.now();
        if (entity != null && entity.getDeletedAt() != null) return entity;
        InspirationCreationMediaTransfer existing = mediaStorage.reuseRegisteredMedia(entity);
        if (existing != null && existing.mimeType().startsWith("image/")) {
            fillFrom(item, detail, entity, sortOrder);
            entity.setCreationType("IMAGE");
            entity.setAuthorName(ADMIN_AUTHOR);
            entity.setStoragePath(existing.storagePath());
            entity.setMimeType(existing.mimeType());
            entity.setFileSize(existing.fileSize());
            entity.setUrl(localUrl(entity.getId()));
            entity.setThumbnailPath(existing.displayPath());
            entity.setThumbnailUrl(thumbnailUrl(entity.getId()));
            entity.setThumbnailMimeType(existing.displayMimeType());
            entity.setThumbnailFileSize(existing.displayFileSize());
            entity.setThumbnailStatus(existing.displayStatus());
            entity.setThumbnailError(null);
            entity.setImportStatus("READY".equals(existing.displayStatus()) ? "IMPORTED" : "PROCESSING");
            entity.setImportError(null);
            entity.setDetailJson(writeJson(sanitize(detail.deepCopy(), entity.getUrl())));
            entity.setUpdatedAt(now);
            if (mapper.attachMediaIfActive(entity) != 1 || mapper.updateImportMetadataIfActive(entity) != 1) {
                throw new IllegalStateException("灵感导入处理状态写入失败。");
            }
            mapper.clearImageRenditionErrors(entity.getId(), existing.displayPath());
            return entity;
        }
        if (entity == null) {
            entity = new InspirationCreationEntity();
            entity.setExternalId(externalId);
            entity.setCreatedAt(now);
            entity.setUrl("");
            entity.setStoragePath("");
            fillFrom(item, detail, entity, sortOrder);
            entity.setAuthorName(ADMIN_AUTHOR);
            entity.setImportStatus(InspirationCreationImportStatus.PROCESSING.name());
            entity.setThumbnailStatus("PENDING");
            entity.setPublishStatus("PUBLISHED");
            entity.setSourceType("IMPORTED");
            entity.setUpdatedAt(now);
            mapper.insert(entity);
        } else {
            fillFrom(item, detail, entity, sortOrder);
            entity.setAuthorName(ADMIN_AUTHOR);
            entity.setImportStatus(InspirationCreationImportStatus.PROCESSING.name());
            entity.setImportError(null);
            if ("IMAGE".equals(entity.getCreationType()) || "VIDEO".equals(entity.getCreationType())) {
                entity.setThumbnailStatus("PENDING");
                entity.setThumbnailError(null);
                entity.setThumbnailFileSize(null);
            }
            entity.setUpdatedAt(now);
            if (mapper.updateImportMetadataIfActive(entity) != 1) {
                throw new IllegalStateException("灵感导入记录已失效。");
            }
        }

        InspirationCreationMediaTransfer transfer = existing != null ? existing
            : mediaStorage.transfer(entity.getId(), externalId, mediaUrl);
        String localUrl = localUrl(entity.getId());
        fillFrom(item, detail, entity, sortOrder);
        entity.setAuthorName(ADMIN_AUTHOR);
        entity.setUrl(localUrl);
        entity.setStoragePath(transfer.storagePath());
        entity.setMimeType(transfer.mimeType());
        entity.setFileSize(transfer.fileSize());
        entity.setImportStatus(InspirationCreationImportStatus.PROCESSING.name());
        entity.setThumbnailStatus("PENDING");
        entity.setThumbnailError(null);
        entity.setThumbnailFileSize(null);
        entity.setImportError(null);
        entity.setDetailJson(writeJson(sanitize(detail.deepCopy(), localUrl)));
        if (transfer.mimeType().startsWith("image/")) {
            entity.setCreationType("IMAGE");
        } else if (transfer.mimeType().startsWith("video/")) {
            entity.setCreationType("VIDEO");
        }
        if (transfer.mimeType().startsWith("image/")) {
            entity.setThumbnailPath(transfer.displayPath());
            entity.setThumbnailUrl(thumbnailUrl(entity.getId()));
            entity.setThumbnailMimeType(transfer.displayMimeType());
            entity.setThumbnailFileSize(null);
            entity.setThumbnailStatus("PENDING");
            entity.setThumbnailError(null);
        } else {
            if (mapper.attachMediaIfActive(entity) != 1) {
                throw new IllegalStateException("灵感视频原图状态写入失败。");
            }
            updateThumbnail(entity, transfer);
        }
        entity.setUpdatedAt(now);
        try {
            if (mapper.attachMediaIfActive(entity) != 1 || mapper.updateImportMetadataIfActive(entity) != 1) {
                throw new IllegalStateException("灵感导入处理状态写入失败。");
            }
        } catch (RuntimeException exception) {
            if (transfer.mimeType().startsWith("image/")) {
                imageRenditions.retire(InspirationImageRenditionReconciler.identity(entity));
            }
            throw exception;
        }
        return mapper.selectById(entity.getId());
    }

    private void updateThumbnail(InspirationCreationEntity entity, InspirationCreationMediaTransfer transfer) {
        RegisteredImageDisplay display = videoCovers.create(
            InspirationImageRenditionReconciler.identity(entity), transfer.storagePath(),
            InspirationCreationMediaStorage.coverOriginalPath(transfer.storagePath())
        );
        entity.setThumbnailPath(display.displayKey());
        entity.setThumbnailUrl(thumbnailUrl(entity.getId()));
        entity.setThumbnailMimeType("image/jpeg");
        entity.setThumbnailFileSize(null);
        entity.setThumbnailStatus("PENDING");
        entity.setThumbnailError(null);
        RegisteredMediaDetails ready = imageRenditions.displayDetails(InspirationImageRenditionReconciler.identity(entity));
        if (ready != null && "READY".equals(ready.status()) && ready.fileSize() > 0
            && display.displayKey().equals(ready.objectKey()) && "image/jpeg".equals(ready.mimeType())) {
            entity.setThumbnailMimeType(ready.mimeType());
            entity.setThumbnailFileSize(ready.fileSize());
            entity.setThumbnailStatus("READY");
            entity.setImportStatus("IMPORTED");
        }
    }

    private InspirationCreationEntity markFailed(JsonNode item, String externalId, int sortOrder, String error) {
        InspirationCreationEntity entity = mapper.selectByExternalId(externalId);
        if (entity != null && (entity.getDeletedAt() != null
            || ("IMAGE".equals(entity.getCreationType()) || "VIDEO".equals(entity.getCreationType()))
                && "IMPORTED".equals(entity.getImportStatus())
                && "READY".equals(entity.getThumbnailStatus()))) {
            return entity;
        }
        boolean keepOriginal = entity != null && ("IMAGE".equals(entity.getCreationType()) || "VIDEO".equals(entity.getCreationType()))
            && entity.getStoragePath() != null && !entity.getStoragePath().isBlank();
        String acceptedType = entity == null ? null : entity.getCreationType();
        LocalDateTime now = LocalDateTime.now();
        if (entity == null) {
            entity = new InspirationCreationEntity();
            entity.setExternalId(externalId);
            entity.setCreatedAt(now);
        }
        fillFrom(item, item, entity, sortOrder);
        entity.setAuthorName(ADMIN_AUTHOR);
        if (keepOriginal) {
            entity.setCreationType(acceptedType);
        } else {
            entity.setUrl("");
            entity.setStoragePath("");
        }
        entity.setImportStatus(InspirationCreationImportStatus.FAILED.name());
        entity.setImportError(error);
        if ("IMAGE".equals(entity.getCreationType()) || "VIDEO".equals(entity.getCreationType())) {
            entity.setThumbnailStatus("FAILED");
            entity.setThumbnailError(error);
        }
        entity.setUpdatedAt(now);
        if (entity.getId() == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
        return entity;
    }

    private void fillFrom(JsonNode item, JsonNode detail, InspirationCreationEntity entity, int sortOrder) {
        entity.setExternalTaskId(firstNonBlank(text(item, "taskId", "externalTaskId"), text(detail, "taskId", "externalTaskId")));
        entity.setCreationType(defaultValue(firstNonBlank(text(item, "creationType", "type"), text(detail, "creationType", "type")), "UNKNOWN"));
        entity.setTaskType(defaultValue(firstNonBlank(text(item, "taskType"), text(detail, "taskType")), "UNKNOWN"));
        entity.setTitle(defaultValue(firstNonBlank(text(detail, "title", "name"), text(item, "title", "name")), "灵感案例"));
        entity.setSourceCreatedAt(firstNonNull(date(item, "createdAt", "createTime", "sourceCreatedAt"), date(detail, "createdAt", "createTime", "sourceCreatedAt")));
        entity.setSourceUpdatedAt(firstNonNull(date(item, "updatedAt", "updateTime", "sourceUpdatedAt"), date(detail, "updatedAt", "updateTime", "sourceUpdatedAt")));
        entity.setSortOrder(sortOrder);
    }

    private JsonNode getJson(String url, Map<String, String> headers) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).GET();
            if (headers != null) {
                headers.forEach(builder::header);
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "外部接口请求失败：" + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "外部接口请求失败：" + exception.getMessage());
        }
    }

    private List<JsonNode> items(JsonNode root) {
        JsonNode payload = payload(root);
        if (payload.isArray()) {
            return toList(payload);
        }
        for (String field : List.of("records", "list", "items", "rows")) {
            JsonNode value = payload.path(field);
            if (value.isArray()) {
                return toList(value);
            }
        }
        return List.of();
    }

    private JsonNode payload(JsonNode root) {
        JsonNode data = root.path("data");
        return data.isMissingNode() || data.isNull() ? root : data;
    }

    private List<JsonNode> toList(JsonNode array) {
        List<JsonNode> values = new ArrayList<>();
        array.forEach(values::add);
        return values;
    }

    private String detailUrl(InspirationCreationImportRequest request, String externalId) {
        String encoded = URLEncoder.encode(externalId, StandardCharsets.UTF_8);
        return request.detailUrlTemplate().replace("{id}", encoded);
    }

    private JsonNode sanitize(JsonNode node, String localUrl) {
        if (node == null || node.isNull()) {
            return objectMapper.createObjectNode();
        }
        if (node.isTextual()) {
            String value = node.asText();
            if (value.startsWith("http://") || value.startsWith("https://")) {
                return TextNode.valueOf(localUrl);
            }
            return node;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            List<String> names = new ArrayList<>();
            fields.forEachRemaining(entry -> names.add(entry.getKey()));
            for (String name : names) {
                object.set(name, sanitize(object.get(name), localUrl));
            }
            object.put("url", localUrl);
            return object;
        }
        if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int index = 0; index < array.size(); index++) {
                array.set(index, sanitize(array.get(index), localUrl));
            }
            return array;
        }
        return node;
    }

    private String mediaUrl(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            String value = node.asText();
            if (value.startsWith("http://") || value.startsWith("https://")) {
                return value;
            }
            return null;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String name = field.getKey().toLowerCase();
                if (name.contains("url")) {
                    String value = mediaUrl(field.getValue());
                    if (value != null) {
                        return value;
                    }
                }
            }
            fields = node.fields();
            while (fields.hasNext()) {
                String value = mediaUrl(fields.next().getValue());
                if (value != null) {
                    return value;
                }
            }
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = mediaUrl(item);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private String text(JsonNode node, String... fields) {
        if (node == null || node.isNull()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private LocalDateTime date(JsonNode node, String... fields) {
        String value = text(node, fields);
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (Exception ignored) {
            try {
                return OffsetDateTime.parse(value).toLocalDateTime();
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "详情JSON处理失败。");
        }
    }

    private String localUrl(Long id) {
        return "/api/inspiration-creations/%d/file".formatted(id);
    }

    private String thumbnailUrl(Long id) {
        return "/api/inspiration-creations/%d/thumbnail".formatted(id);
    }

    private String message(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private LocalDateTime firstNonNull(LocalDateTime first, LocalDateTime second) {
        return first != null ? first : second;
    }
}
