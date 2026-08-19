package com.antshorttv.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MaterialStorageConfig implements WebMvcConfigurer {
    private final Path storageRoot;

    public MaterialStorageConfig(@Value("${ai.video.storage-root:storage}") String storageRoot) {
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Files under /materials are served by MaterialFileController so every
        // request passes through project access or signed URL validation.
    }
}
