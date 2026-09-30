package com.antshorttv.video;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.storage.ObjectStorageService;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ModelAccessibleVideoUrlResolver {
    private final ObjectStorageService objectStorageService;
    private final Duration validity;

    public ModelAccessibleVideoUrlResolver(
        ObjectStorageService objectStorageService,
        @Value("${ai.video.task-timeout-minutes:20}") int taskTimeoutMinutes
    ) {
        this.objectStorageService = objectStorageService;
        this.validity = Duration.ofMinutes(taskTimeoutMinutes + 30L);
    }

    public String resolve(String storagePath) {
        URI uri = parse(storagePath);
        if (uri.isAbsolute()) {
            validateHttpUrl(uri);
            return uri.toString();
        }
        String key = storagePath.startsWith("/") ? storagePath.substring(1) : storagePath;
        return objectStorageService.modelAccessUrl(key, validity);
    }

    private URI parse(String value) {
        try {
            return URI.create(value);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "视频访问地址不合法。");
        }
    }

    private void validateHttpUrl(URI uri) {
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "视频访问地址必须是 HTTP 或 HTTPS。");
        }
        String query = uri.getRawQuery() == null ? "" : uri.getRawQuery().toLowerCase();
        if (query.contains("api_key=") || query.contains("access_key=") || query.contains("secret_key=")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "视频访问地址不能包含服务商凭据。");
        }
    }
}
