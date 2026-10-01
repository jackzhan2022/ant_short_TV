package com.antshorttv.storage;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.qcloud.cos.COS;
import com.qcloud.cos.model.ciModel.snapshot.CosSnapshotRequest;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CloudInfiniteVideoCoverService {
    private final COS cos;
    private final ObjectStorageService storage;
    private final ObjectStorageKeyFactory keys;
    private final String bucket;
    private final ImageDisplayRenditionService renditions;

    @Autowired
    public CloudInfiniteVideoCoverService(
        COS cos,
        ObjectStorageService storage,
        ObjectStorageKeyFactory keys,
        ObjectStorageProperties properties,
        ImageDisplayRenditionService renditions
    ) {
        this(cos, storage, keys, properties.getBucket(), renditions);
    }

    CloudInfiniteVideoCoverService(
        COS cos,
        ObjectStorageService storage,
        ObjectStorageKeyFactory keys,
        String bucket,
        ImageDisplayRenditionService renditions
    ) {
        this.cos = cos;
        this.storage = storage;
        this.keys = keys;
        this.bucket = bucket;
        this.renditions = renditions;
    }

    public RegisteredImageDisplay create(
        MediaObjectIdentity identity,
        String videoKey,
        String coverOriginalKey
    ) {
        try {
            RegisteredMediaDetails existing = renditions.originalDetails(identity);
            if (existing != null) return reuse(identity, existing, coverOriginalKey);
            Snapshot snapshot;
            try {
                snapshot = snapshot(videoKey, "1");
            } catch (Exception shortVideo) {
                snapshot = snapshot(videoKey, "0");
            }
            StoredObject original = storage.uploadOriginal(
                keys.objectKey(coverOriginalKey), snapshot.bytes(), "image/jpeg"
            );
            if (original == null || !coverOriginalKey.equals(original.key())
                || original.size() != snapshot.bytes().length
                || !"image/jpeg".equals(original.contentType())) {
                throw new IllegalStateException("视频封面原图元数据不一致。");
            }
            return renditions.registerOriginalAndSubmit(
                identity, original, snapshot.width(), snapshot.height(),
                "inspiration-creation:" + identity.assetId()
            );
        } catch (Exception exception) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "视频封面生成失败：" + SensitiveValueRedactor.redact(exception.getMessage())
            );
        }
    }

    private RegisteredImageDisplay reuse(
        MediaObjectIdentity identity,
        RegisteredMediaDetails original,
        String expectedOriginalKey
    ) {
        if (!"READY".equals(original.status()) || original.fileSize() <= 0
            || !expectedOriginalKey.equals(original.objectKey())
            || !"image/jpeg".equals(original.mimeType())) {
            throw new IllegalStateException("视频封面原图尚未就绪或归属不一致。");
        }
        RegisteredMediaDetails display = renditions.displayDetails(identity);
        if (display == null || "FAILED".equals(display.status())) {
            return renditions.retryFailedDisplay(identity, "inspiration-creation:" + identity.assetId());
        }
        if (!keys.rendition(original.objectKey(), "display", "jpg").equals(display.objectKey())
            || !"image/jpeg".equals(display.mimeType())
            || !("PENDING".equals(display.status()) || "SUBMITTED".equals(display.status())
                || "READY".equals(display.status()) && display.fileSize() > 0)) {
            throw new IllegalStateException("视频封面展示版本不可用。");
        }
        return new RegisteredImageDisplay(original.objectKey(), display.objectKey(), display.status());
    }

    private Snapshot snapshot(String videoKey, String time) throws Exception {
        CosSnapshotRequest request = new CosSnapshotRequest();
        request.setBucketName(bucket);
        request.setObjectKey(keys.objectKey(videoKey));
        request.setTime(time);
        request.setWidth("0");
        request.setHeight("0");
        request.setFormat("jpg");
        request.setMode("exactframe");
        try (InputStream snapshot = cos.getSnapshot(request)) {
            byte[] bytes = snapshot.readAllBytes();
            if (bytes.length == 0) {
                throw new IllegalStateException("视频截帧结果为空。");
            }
            try (ImageInputStream imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(imageInput);
                if (!readers.hasNext()) throw new IllegalStateException("视频截帧图片无法解析。");
                ImageReader reader = readers.next();
                try {
                    if (!"JPEG".equalsIgnoreCase(reader.getFormatName())) {
                        throw new IllegalStateException("视频截帧结果不是 JPEG 图片。");
                    }
                    reader.setInput(imageInput);
                    BufferedImage image = reader.read(0);
                    if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                        throw new IllegalStateException("视频截帧尺寸不合法。");
                    }
                    return new Snapshot(bytes, image.getWidth(), image.getHeight());
                } finally {
                    reader.dispose();
                }
            }
        }
    }

    private record Snapshot(byte[] bytes, int width, int height) { }
}
