package com.antshorttv.storage;

public abstract class MediaDeliveryGrantStore {
    public abstract MediaDeliveryGrantEntity find(Long userId, String objectKeyHash);
    public abstract void insert(MediaDeliveryGrantEntity entity);
    public abstract void update(MediaDeliveryGrantEntity entity);
}
