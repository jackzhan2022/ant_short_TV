package com.antshorttv.storage;

import org.springframework.stereotype.Repository;

@Repository
class MyBatisMediaObjectStore extends MediaObjectStore {
    private final MediaObjectMapper mapper;

    MyBatisMediaObjectStore(MediaObjectMapper mapper) { this.mapper = mapper; }

    @Override MediaObjectEntity find(MediaObjectIdentity identity, String renditionType) {
        return mapper.findForUpdate(identity, renditionType);
    }
    @Override void insert(MediaObjectEntity entity) { mapper.insert(entity); }
    @Override void ready(Long id, long size, String eTag, String mimeType, int width, int height) {
        mapper.markReady(id, size, eTag, mimeType, width, height);
    }
    @Override void failed(Long id, String message) { mapper.markFailed(id, message); }
}
