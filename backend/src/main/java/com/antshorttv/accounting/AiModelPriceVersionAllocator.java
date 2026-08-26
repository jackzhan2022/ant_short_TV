package com.antshorttv.accounting;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class AiModelPriceVersionAllocator {
    private final AiModelPriceVersionSequenceMapper sequenceMapper;

    public AiModelPriceVersionAllocator(AiModelPriceVersionSequenceMapper sequenceMapper) {
        this.sequenceMapper = sequenceMapper;
    }

    public int next(Long modelId, String priceType) {
        try {
            sequenceMapper.insertNextFromHistory(modelId, priceType);
            Integer initialized = sequenceMapper.selectLastForUpdate(modelId, priceType);
            if (initialized == null) {
                throw new IllegalStateException("Model price version sequence was not initialized.");
            }
            return initialized;
        } catch (DuplicateKeyException ignored) {
            Integer last = sequenceMapper.selectLastForUpdate(modelId, priceType);
            if (last == null) {
                throw new IllegalStateException("Model price version sequence was not initialized.");
            }
            int next = Math.addExact(last, 1);
            sequenceMapper.updateLast(modelId, priceType, next);
            return next;
        }
    }
}
