package com.antshorttv.inspiration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "inspiration.thumbnail")
public class InspirationThumbnailProperties {
    private int maxDimension = 640;
    private float jpegQuality = 0.82f;
    private int backfillBatchSize = 20;

    public int getMaxDimension() {
        return maxDimension;
    }

    public void setMaxDimension(int maxDimension) {
        this.maxDimension = maxDimension;
    }

    public float getJpegQuality() {
        return jpegQuality;
    }

    public void setJpegQuality(float jpegQuality) {
        this.jpegQuality = jpegQuality;
    }

    public int getBackfillBatchSize() {
        return backfillBatchSize;
    }

    public void setBackfillBatchSize(int backfillBatchSize) {
        this.backfillBatchSize = backfillBatchSize;
    }
}
