package com.antshorttv.storage;

import org.springframework.stereotype.Repository;

@Repository
class MyBatisMediaDeliveryGrantStore extends MediaDeliveryGrantStore {
    private final MediaDeliveryGrantMapper mapper;

    MyBatisMediaDeliveryGrantStore(MediaDeliveryGrantMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public MediaDeliveryGrantEntity find(Long userId, String objectKeyHash) {
        return mapper.findForUpdate(userId, objectKeyHash);
    }

    @Override
    public void insert(MediaDeliveryGrantEntity entity) {
        mapper.insert(entity);
    }

    @Override
    public void update(MediaDeliveryGrantEntity entity) {
        mapper.updateById(entity);
    }
}
