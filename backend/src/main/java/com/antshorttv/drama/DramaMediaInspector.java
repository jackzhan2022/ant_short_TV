package com.antshorttv.drama;

import com.antshorttv.drama.DramaModels.Episode;
import com.antshorttv.storage.ObjectStorageProperties;
import com.qcloud.cos.COS;
import com.qcloud.cos.model.ciModel.mediaInfo.MediaInfoRequest;
import com.qcloud.cos.model.ciModel.common.MediaInputObject;
import org.springframework.stereotype.Service;

@Service
public class DramaMediaInspector {
    private final COS cos;
    private final ObjectStorageProperties properties;
    public DramaMediaInspector(COS cos, ObjectStorageProperties properties) { this.cos = cos; this.properties = properties; }

    public void inspect(Episode episode) {
        try {
            MediaInfoRequest request = new MediaInfoRequest(); request.setBucketName(properties.getBucket());
            MediaInputObject input = new MediaInputObject(); input.setObject(episode.storagePath); request.setInput(input);
            var info = cos.generateMediainfo(request).getMediaInfo().getStream().getMediaInfoVideoList().get(0);
            int width = Integer.parseInt(info.getWidth()), height = Integer.parseInt(info.getHeight());
            double duration = Double.parseDouble(info.getDuration());
            if (width <= 0 || height <= 0 || !Double.isFinite(duration) || duration <= 0 || duration > 86400)
                throw new IllegalArgumentException("无效媒体元数据");
            episode.width = width; episode.height = height; episode.durationSeconds = duration;
        } catch (Exception ex) {
            throw DramaLibraryService.invalid("无法读取视频时长及分辨率，请检查媒体分析配置后重新检测。");
        }
    }
}
