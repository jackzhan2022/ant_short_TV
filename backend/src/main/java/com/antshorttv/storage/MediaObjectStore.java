package com.antshorttv.storage;

abstract class MediaObjectStore {
    abstract MediaObjectEntity find(MediaObjectIdentity identity, String renditionType);
    abstract void insert(MediaObjectEntity entity);
    abstract boolean retryFailed(Long id);
    abstract void ready(Long id, long size, String eTag, String mimeType, int width, int height);
    abstract void failed(Long id, String message);
}
