package com.antshorttv.inspiration;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.storage.CloudInfiniteVideoCoverService;
import com.antshorttv.storage.VerifiedMediaUpload;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class InspirationManagementMediaService {
    private final InspirationCreationMediaStorage storage;
    private final CloudInfiniteVideoCoverService covers;

    public InspirationManagementMediaService(
        InspirationCreationMediaStorage storage,
        CloudInfiniteVideoCoverService covers
    ) {
        this.storage = storage;
        this.covers = covers;
    }

    public ManagedInspirationMedia store(String externalId, VerifiedMediaUpload upload) {
        if (upload == null || upload.objectKey() == null || upload.objectKey().isBlank()) {
            throw validation("请选择要上传的图片或视频。");
        }
        String mimeType = upload.contentType() == null
            ? "" : upload.contentType().toLowerCase(Locale.ROOT);
        if (mimeType.equals("video/mp4")) {
            return storeVideo(externalId, upload, mimeType);
        }
        throw validation("仅支持 JPEG、PNG 图片或 MP4 视频。");
    }

    public boolean isImage(VerifiedMediaUpload upload) {
        String mimeType = upload == null || upload.contentType() == null
            ? "" : upload.contentType().toLowerCase(Locale.ROOT);
        return mimeType.equals("image/jpeg") || mimeType.equals("image/png");
    }

    public ManagedInspirationMedia storeImage(
        Long assetId,
        String externalId,
        VerifiedMediaUpload upload
    ) {
        if (!isImage(upload)) {
            throw validation("仅支持 JPEG、PNG 图片。");
        }
        InspirationCreationMediaTransfer transfer = storage.storeUploadedImage(
            assetId, externalId, upload
        );
        return new ManagedInspirationMedia(
            "IMAGE", transfer.mimeType(), transfer.fileSize(), transfer.storagePath(),
            transfer.displayPath(), transfer.displayMimeType(), null, "PENDING"
        );
    }

    private ManagedInspirationMedia storeVideo(
        String externalId,
        VerifiedMediaUpload upload,
        String mimeType
    ) {
        String originalPath = base(externalId) + "/original.mp4";
        String coverOriginalPath = InspirationCreationMediaStorage.coverOriginalPath(externalId);
        try {
            storage.copyVerifiedUpload(upload.objectKey(), originalPath, upload.size(), mimeType);
            String thumbnailPath = covers.create(originalPath, coverOriginalPath);
            ManagedInspirationMedia media = new ManagedInspirationMedia(
                "VIDEO", mimeType, upload.size(), originalPath,
                thumbnailPath, "image/jpeg", 0L, "READY"
            );
            storage.delete(upload.objectKey());
            return media;
        } catch (Exception exception) {
            cleanup(originalPath, coverOriginalPath);
            throw validation("视频缩略图生成失败，请检查视频文件后重试。");
        }
    }

    private void cleanup(String originalPath, String thumbnailPath) {
        storage.delete(originalPath);
        storage.delete(thumbnailPath);
    }

    private String base(String externalId) {
        return "inspiration/creations/" + externalId;
    }

    private BusinessException validation(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}

record ManagedInspirationMedia(
    String creationType,
    String mimeType,
    long fileSize,
    String storagePath,
    String thumbnailPath,
    String thumbnailMimeType,
    Long thumbnailFileSize,
    String thumbnailStatus
) {
}
