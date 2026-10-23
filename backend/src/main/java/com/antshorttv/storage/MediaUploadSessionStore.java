package com.antshorttv.storage;

public abstract class MediaUploadSessionStore {
    public abstract void insert(MediaUploadSessionEntity entity);
    public abstract MediaUploadSessionEntity find(String token);
    public abstract MediaUploadSessionEntity findReadOnly(String token);
    public abstract void update(MediaUploadSessionEntity entity);
}
