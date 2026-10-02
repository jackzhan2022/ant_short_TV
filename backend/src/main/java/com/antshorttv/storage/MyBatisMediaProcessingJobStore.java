package com.antshorttv.storage;

import org.springframework.stereotype.Repository;

@Repository
class MyBatisMediaProcessingJobStore extends MediaProcessingJobStore {
    private final MediaProcessingJobMapper mapper;

    MyBatisMediaProcessingJobStore(MediaProcessingJobMapper mapper) {
        this.mapper = mapper;
    }

    @Override MediaProcessingJobEntity find(String outputKey, String operation) {
        return mapper.findForUpdate(outputKey, operation);
    }
    @Override MediaProcessingJobEntity findByTokenHash(String tokenHash) {
        return mapper.findByTokenHashForUpdate(tokenHash);
    }
    @Override void insert(MediaProcessingJobEntity entity) { mapper.insert(entity); }
    @Override void update(MediaProcessingJobEntity entity) { mapper.updateById(entity); }
    @Override void resetForSubmission(MediaProcessingJobEntity entity) {
        mapper.resetForSubmission(entity);
    }
}
