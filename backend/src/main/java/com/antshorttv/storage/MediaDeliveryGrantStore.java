package com.antshorttv.storage;

public abstract class MediaDeliveryGrantStore {
    public abstract MediaDeliveryGrantEntity find(Long userId, String objectKeyHash);
    public java.util.List<MediaDeliveryGrantEntity> findAll(Long userId, java.util.List<String> hashes) {
        return hashes.stream().map(hash -> find(userId, hash)).filter(java.util.Objects::nonNull).toList();
    }
    public abstract void insert(MediaDeliveryGrantEntity entity);
    public abstract void update(MediaDeliveryGrantEntity entity);
}
