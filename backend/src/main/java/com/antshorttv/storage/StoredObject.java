package com.antshorttv.storage;

public record StoredObject(
    String key,
    long size,
    String contentType,
    String eTag,
    String storageClass
) {
}
