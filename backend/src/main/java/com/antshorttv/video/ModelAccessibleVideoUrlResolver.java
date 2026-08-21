package com.antshorttv.video;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.material.MaterialFileAccessService;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ModelAccessibleVideoUrlResolver {
    private final MaterialFileAccessService materialFileAccessService;
    private final String publicBaseUrl;

    public ModelAccessibleVideoUrlResolver(
        MaterialFileAccessService materialFileAccessService,
        @Value("${app.public-base-url:}") String publicBaseUrl
    ) {
        this.materialFileAccessService = materialFileAccessService;
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim();
    }

    public String resolve(String storagePath) {
        String publicUrl = materialFileAccessService.publicUrl(storagePath);
        URI uri = parse(publicUrl);
        if (uri.isAbsolute()) {
            validateHttpUrl(uri);
            return uri.toString();
        }
        if (publicBaseUrl.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未配置模型可访问的外部访问地址。");
        }
        URI baseUri = parse(publicBaseUrl.endsWith("/") ? publicBaseUrl : publicBaseUrl + "/");
        validateHttpUrl(baseUri);
        URI resolved = baseUri.resolve(publicUrl.startsWith("/") ? publicUrl.substring(1) : publicUrl);
        validateHttpUrl(resolved);
        return resolved.toString();
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
