package com.antshorttv.storage;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.qcloud.cos.COS;
import com.qcloud.cos.model.ciModel.snapshot.CosSnapshotRequest;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CloudInfiniteVideoCoverService {
    private final COS cos;
    private final ObjectStorageService storage;
    private final ObjectStorageKeyFactory keys;
    private final String bucket;

    @Autowired
    public CloudInfiniteVideoCoverService(
        COS cos,
        ObjectStorageService storage,
        ObjectStorageKeyFactory keys,
        ObjectStorageProperties properties
    ) {
        this(cos, storage, keys, properties.getBucket());
    }

    CloudInfiniteVideoCoverService(
        COS cos,
        ObjectStorageService storage,
        ObjectStorageKeyFactory keys,
        String bucket
    ) {
        this.cos = cos;
        this.storage = storage;
        this.keys = keys;
        this.bucket = bucket;
    }

    public String create(String videoKey, String coverOriginalKey) {
        try {
            byte[] bytes;
            try {
                bytes = snapshot(videoKey, "1");
            } catch (Exception shortVideo) {
                bytes = snapshot(videoKey, "0");
            }
            storage.upload(keys.objectKey(coverOriginalKey), bytes, "image/jpeg");
            return keys.rendition(coverOriginalKey, "display", "jpg");
        } catch (Exception exception) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "视频封面生成失败：" + SensitiveValueRedactor.redact(exception.getMessage())
            );
        }
    }

    private byte[] snapshot(String videoKey, String time) throws Exception {
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
            return bytes;
        }
    }
}
