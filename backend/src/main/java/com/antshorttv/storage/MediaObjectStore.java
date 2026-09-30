package com.antshorttv.storage;

abstract class MediaObjectStore {
    abstract void ready(Long id, long size, String eTag, String mimeType, int width, int height);
    abstract void failed(Long id, String message);
}
