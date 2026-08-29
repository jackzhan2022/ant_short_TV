package com.antshorttv.script;

enum EpisodeReconciliationStatus { CREATED, HEADING_MATCHED, CONTENT_MATCHED, AMBIGUOUS, RETIRED }

enum NormalizationRunStatus { NORMALIZING, READY_FOR_REVIEW, BUSINESS_FAILED }

enum CandidateValidationStatus { VALID, INVALID }

enum CandidateReviewStatus { PENDING_REVIEW, ACCEPTED_NEW, ACCEPTED_MERGE, REJECTED }

enum PromotionDecisionType { ACCEPT_NEW, ACCEPT_MERGE, RETARGET, REJECT }

enum VisualVariantGenerationStatus { EMPTY, QUEUED, GENERATING, SUCCEEDED, FAILED }

enum VisualVariantSourceType { MANUAL, AI_GENERATED, LEGACY_BACKFILL }

enum EpisodeBindingStatus { ACTIVE, RETIRED, AMBIGUOUS }

record AssetOwner(long tenantId, long projectId, AssetType assetType, long assetId) {
    AssetOwner {
        if (tenantId <= 0 || projectId <= 0 || assetId <= 0 || assetType == null) {
            throw new IllegalArgumentException("asset owner requires positive ids and a type");
        }
    }
}
