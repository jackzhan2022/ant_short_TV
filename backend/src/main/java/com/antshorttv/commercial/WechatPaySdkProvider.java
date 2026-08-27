package com.antshorttv.commercial;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
class WechatPaySdkProvider {
    private final WechatPayProperties properties;
    private final WechatPaySdkFactory factory;
    private volatile WechatPaySdk sdk;

    WechatPaySdkProvider(WechatPayProperties properties, WechatPaySdkFactory factory) {
        this.properties = properties;
        this.factory = factory;
    }

    WechatPaySdk get() {
        if (!properties.isEnabled()) throw new IllegalStateException("WeChat Pay is disabled");
        WechatPaySdk current = sdk;
        if (current != null) return current;
        synchronized (this) {
            if (sdk == null) {
                validateConfiguration();
                sdk = factory.create(properties);
            }
            return sdk;
        }
    }

    private void validateConfiguration() {
        require(properties.getMerchantId(), "WeChat merchant ID is required");
        require(properties.getMerchantSerialNumber(), "WeChat merchant certificate serial number is required");
        require(properties.getMerchantPrivateKeyPath(), "WeChat merchant private key path is required");
        require(properties.getApiV3Key(), "WeChat API v3 key is required");
        if (properties.getApiV3Key().getBytes(StandardCharsets.UTF_8).length != 32) {
            throw new IllegalStateException("WeChat API v3 key must contain 32 bytes");
        }
        validatePrivateKey(properties.getMerchantPrivateKeyPath());
    }

    private void validatePrivateKey(String path) {
        try {
            String pem = Files.readString(Path.of(path), StandardCharsets.UTF_8);
            if (!pem.contains("-----BEGIN PRIVATE KEY-----") || !pem.contains("-----END PRIVATE KEY-----")) {
                throw new IllegalArgumentException();
            }
            String encoded = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
            KeyFactory.getInstance("RSA").generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded)));
        } catch (Exception exception) {
            throw new IllegalStateException("WeChat merchant private key must be a readable PKCS#8 PEM file", exception);
        }
    }

    private void require(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalStateException(message);
    }
}
