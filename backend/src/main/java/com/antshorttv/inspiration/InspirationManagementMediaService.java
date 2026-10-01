package com.antshorttv.inspiration;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.storage.CloudInfiniteVideoCoverService;
import com.antshorttv.storage.VerifiedMediaUpload;
import com.antshorttv.storage.RegisteredImageDisplay;
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

    public ManagedInspirationMedia storeVideoOriginal(Long assetId, String externalId, VerifiedMediaUpload upload) {
        if (upload == null || upload.objectKey() == null || upload.objectKey().isBlank()) {
            throw validation("请选择要上传的图片或视频。");
        }
        String mimeType = upload.contentType() == null
            ? "" : upload.contentType().toLowerCase(Locale.ROOT);
        if (mimeType.equals("video/mp4")) {
            InspirationCreationMediaTransfer transfer = storage.storeUploadedVideo(assetId, externalId, upload);
            return new ManagedInspirationMedia("VIDEO", transfer.mimeType(), transfer.fileSize(),
                transfer.storagePath(), null, null, null, "PENDING");
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

    public ManagedInspirationMedia storeVideoCover(InspirationCreationEntity entity) {
        RegisteredImageDisplay display = covers.create(
            InspirationImageRenditionReconciler.identity(entity), entity.getStoragePath(),
            InspirationCreationMediaStorage.coverOriginalPath(entity.getStoragePath())
        );
        return new ManagedInspirationMedia("VIDEO", entity.getMimeType(), entity.getFileSize(),
            entity.getStoragePath(), display.displayKey(), "image/jpeg", null, "PENDING");
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
