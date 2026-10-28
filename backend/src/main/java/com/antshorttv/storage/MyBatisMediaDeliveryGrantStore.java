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
    public java.util.List<MediaDeliveryGrantEntity> findAll(Long userId, java.util.List<String> hashes) {
        if (hashes.isEmpty()) return java.util.List.of();
        // Valid grants only need a snapshot; creation and renewal still take the single-row lock.
        return mapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MediaDeliveryGrantEntity>()
            .eq("user_id", userId).in("object_key_hash", hashes));
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
