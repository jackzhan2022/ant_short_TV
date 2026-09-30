package com.antshorttv.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "object-storage")
public class ObjectStorageProperties {
    private String bucket = "antv-1418200553";
    private String region = "ap-guangzhou";
    private String storageClass = "INTELLIGENT_TIERING";
    private String cdnDomain = "https://antvcdn.aixmax.cn";
    private String cdnTypeDKey;
    private long cdnAuthorizationSeconds = 604800;
    private long videoRenewalThresholdSeconds = 7200;
    private long stsDurationSeconds = 3600;
    private String stsNamePrefix = "antv-upload";
    private int imageWebpQuality = 80;
    private String ciCallbackUrl;

    public void validate() {
        require(bucket, "object-storage.bucket");
        require(region, "object-storage.region");
        require(cdnDomain, "object-storage.cdn-domain");
        require(cdnTypeDKey, "object-storage.cdn-type-d-key");
        if (cdnAuthorizationSeconds < 1 || videoRenewalThresholdSeconds < 0
            || videoRenewalThresholdSeconds >= cdnAuthorizationSeconds || stsDurationSeconds < 1) {
            throw new IllegalStateException("对象存储授权有效期配置不合法。");
        }
        if (imageWebpQuality < 1 || imageWebpQuality > 100) {
            throw new IllegalStateException("对象存储图片派生配置不合法。");
        }
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getStorageClass() { return storageClass; }
    public void setStorageClass(String storageClass) { this.storageClass = storageClass; }
    public String getCdnDomain() { return cdnDomain; }
    public void setCdnDomain(String cdnDomain) { this.cdnDomain = cdnDomain; }
    public String getCdnTypeDKey() { return cdnTypeDKey; }
    public void setCdnTypeDKey(String cdnTypeDKey) { this.cdnTypeDKey = cdnTypeDKey; }
    public long getCdnAuthorizationSeconds() { return cdnAuthorizationSeconds; }
    public void setCdnAuthorizationSeconds(long value) { this.cdnAuthorizationSeconds = value; }
    public long getVideoRenewalThresholdSeconds() { return videoRenewalThresholdSeconds; }
    public void setVideoRenewalThresholdSeconds(long value) { this.videoRenewalThresholdSeconds = value; }
    public long getStsDurationSeconds() { return stsDurationSeconds; }
    public void setStsDurationSeconds(long value) { this.stsDurationSeconds = value; }
    public String getStsNamePrefix() { return stsNamePrefix; }
    public void setStsNamePrefix(String value) { this.stsNamePrefix = value; }
    public int getImageWebpQuality() { return imageWebpQuality; }
    public void setImageWebpQuality(int value) { this.imageWebpQuality = value; }
    public String getCiCallbackUrl() { return ciCallbackUrl; }
    public void setCiCallbackUrl(String value) { this.ciCallbackUrl = value; }

    private void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 未配置。");
        }
    }
}
