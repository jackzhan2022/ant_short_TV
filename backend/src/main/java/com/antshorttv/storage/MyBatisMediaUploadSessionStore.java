package com.antshorttv.storage;

import org.springframework.stereotype.Repository;

@Repository
class MyBatisMediaUploadSessionStore extends MediaUploadSessionStore {
    private final MediaUploadSessionMapper mapper;

    MyBatisMediaUploadSessionStore(MediaUploadSessionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(MediaUploadSessionEntity entity) { mapper.insert(entity); }

    @Override
    public MediaUploadSessionEntity find(String token) { return mapper.findForUpdate(token); }

    @Override
    public void update(MediaUploadSessionEntity entity) { mapper.updateById(entity); }
}
