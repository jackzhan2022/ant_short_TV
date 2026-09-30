package com.antshorttv.storage;

abstract class MediaProcessingJobStore {
    abstract MediaProcessingJobEntity find(String outputKey, String operation);
    abstract MediaProcessingJobEntity findByTokenHash(String tokenHash);
    abstract void insert(MediaProcessingJobEntity entity);
    abstract void update(MediaProcessingJobEntity entity);
}
