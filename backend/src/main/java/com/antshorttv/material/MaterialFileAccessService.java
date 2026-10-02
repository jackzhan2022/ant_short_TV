package com.antshorttv.material;

import com.antshorttv.storage.ObjectStorageKeyFactory;
import com.antshorttv.storage.ObjectStorageService;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class MaterialFileAccessService {
    private final ObjectStorageService objectStorageService;
    private final ObjectStorageKeyFactory keys;

    public MaterialFileAccessService(ObjectStorageService objectStorageService, ObjectStorageKeyFactory keys) {
        this.objectStorageService = objectStorageService;
        this.keys = keys;
    }

    public String publicUrl(String storagePath) {
        if (storagePath == null || storagePath.isBlank()
            || storagePath.startsWith("http://") || storagePath.startsWith("https://")
            || storagePath.startsWith("/api/")) {
            return storagePath;
        }
        String key = normalize(storagePath);
        return key.startsWith("materials/") ? "/" + key : key;
    }

    public Resource resource(String storagePath) {
        return objectStorageService.resource(normalize(storagePath));
    }

    public String contentType(String storagePath) {
        String value = storagePath == null ? "" : storagePath.toLowerCase();
        if (value.endsWith(".mp4")) return "video/mp4";
        if (value.endsWith(".webp")) return "image/webp";
        if (value.endsWith(".png")) return "image/png";
        if (value.endsWith(".jpg") || value.endsWith(".jpeg")) return "image/jpeg";
        if (value.endsWith(".mp3")) return "audio/mpeg";
        if (value.endsWith(".srt")) return "application/x-subrip";
        if (value.endsWith(".vtt")) return "text/vtt";
        return "application/octet-stream";
    }

    public String normalize(String storagePath) {
        String value = storagePath != null && storagePath.startsWith("/")
            ? storagePath.substring(1) : storagePath;
        return keys.objectKey(value);
    }
}
