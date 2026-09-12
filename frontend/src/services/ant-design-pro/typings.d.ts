declare namespace API {
  type acceptParams = {
    token: string;
  };

  type accountingDetailParams = {
    executionId: number;
  };

  type accountParams = {
    tenantId: number;
  };

  type activeParams = {
    tenantId: number;
  };

  type addMemberParams = {
    id: number;
  };

  type AddProjectMemberRequest = {
    userId: number;
    roleId?: number;
  };

  type AiCallLogPageResponse = {
    records?: AiCallLogResponse[];
    total?: number;
    current?: number;
    pageSize?: number;
  };

  type AiCallLogResponse = {
    id?: number;
    tenantId?: number;
    userId?: number;
    taskId?: number;
    modelId?: number;
    providerId?: number;
    provider?: string;
    serviceType?: string;
    model?: string;
    businessScene?: string;
    requestSummary?: string;
    responseSummary?: string;
    status?: string;
    errorMessage?: string;
    durationMs?: number;
    traceId?: string;
    providerRequestId?: string;
    promptTokens?: number;
    completionTokens?: number;
    totalTokens?: number;
    cachedInputTokens?: number;
    cacheWriteTokens?: number;
    promptCacheKey?: string;
    responseLength?: number;
    finishReason?: string;
    truncated?: boolean;
    createdAt?: string;
  };

  type AiExecutionRegenerateRequest = {
    clientIdempotencyKey: string;
    traceId: string;
  };

  type AiExecutionResponse = {
    id?: number;
    tenantId?: number;
    projectId?: number;
    scene?: string;
    businessType?: string;
    businessId?: number;
    status?: string;
    phase?: string;
    progress?: number;
    executionVersion?: number;
    sourceExecutionId?: number;
    rootExecutionId?: number;
    retryable?: boolean;
    resultType?: string;
    resultId?: number;
    errorCode?: string;
    errorMessage?: string;
    usageCostStatus?: string;
    providerCostSummaryJson?: string;
    businessCallCount?: number;
    technicalRetryCount?: number;
    pointSettlementStatus?: string;
    reservedPoints?: number;
    settledPoints?: number;
    releasedPoints?: number;
    startedAt?: string;
    createdAt?: string;
    updatedAt?: string;
    completedAt?: string;
    canceledAt?: string;
  };

  type AiImageResultResponse = {
    id?: number;
    taskId?: number;
    targetType?: string;
    targetId?: number;
    imageUrl?: string;
    thumbnailUrl?: string;
    width?: number;
    height?: number;
    fileSize?: number;
    materialId?: number;
    selected?: boolean;
    status?: string;
    createdAt?: string;
  };

  type AiImageTaskResponse = {
    id?: number;
    projectId?: number;
    taskType?: string;
    targetType?: string;
    targetId?: number;
    modelId?: number;
    providerCode?: string;
    model?: string;
    prompt?: string;
    negativePrompt?: string;
    referenceImages?: string[];
    aspectRatio?: string;
    imageCount?: number;
    style?: string;
    quality?: string;
    seed?: string;
    executionId?: number;
    execution?: AiExecutionResponse;
    status?: string;
    errorMessage?: string;
    startedAt?: string;
    completedAt?: string;
    createdBy?: number;
    createdAt?: string;
    results?: AiImageResultResponse[];
  };

  type AiModelParameterRequest = {
    temperature?: number;
    topP?: number;
    maxTokens?: number;
    jsonMode?: boolean;
    timeoutSeconds?: number;
    retryCount?: number;
  };

  type AiModelParameterResponse = {
    modelId?: number;
    versionNo?: number;
    temperature?: number;
    topP?: number;
    maxTokens?: number;
    jsonMode?: boolean;
    timeoutSeconds?: number;
    retryCount?: number;
    status?: string;
    published?: boolean;
  };

  type AiPointReconciliation = {
    tenantId?: number;
    accountAvailable?: number;
    ledgerAvailable?: number;
    accountReserved?: number;
    ledgerReserved?: number;
    reservationReserved?: number;
    matches?: boolean;
  };

  type AiServiceTestResponse = {
    status?: string;
    message?: string;
  };

  type AiVideoResultResponse = {
    id?: number;
    taskId?: number;
    storyboardId?: number;
    videoUrl?: string;
    storagePath?: string;
    coverUrl?: string;
    durationSeconds?: number;
    width?: number;
    height?: number;
    fileSize?: number;
    format?: string;
    materialId?: number;
    isSelected?: boolean;
    status?: string;
    createdAt?: string;
  };

  type AiVideoTaskResponse = {
    id?: number;
    executionId?: number;
    projectId?: number;
    storyboardId?: number;
    modelId?: number;
    providerCode?: string;
    model?: string;
    prompt?: string;
    negativePrompt?: string;
    firstFrameUrl?: string;
    durationSeconds?: number;
    aspectRatio?: string;
    resolution?: string;
    motionStrength?: string;
    cameraMovement?: string;
    externalTaskId?: string;
    externalStatus?: string;
    status?: string;
    errorMessage?: string;
    executionPhase?: string;
    retryable?: boolean;
    submittedAt?: string;
    startedAt?: string;
    completedAt?: string;
    createdAt?: string;
    results?: AiVideoResultResponse[];
  };

  type AiVoiceResultResponse = {
    id?: number;
    taskId?: number;
    storyboardId?: number;
    audioUrl?: string;
    storagePath?: string;
    durationSeconds?: number;
    fileSize?: number;
    format?: string;
    materialId?: number;
    selected?: boolean;
    status?: string;
    createdAt?: string;
  };

  type AiVoiceTaskResponse = {
    id?: number;
    projectId?: number;
    storyboardId?: number;
    providerCode?: string;
    model?: string;
    voiceType?: string;
    speakerName?: string;
    voiceId?: string;
    textContent?: string;
    speed?: number;
    pitch?: number;
    volume?: number;
    status?: string;
    errorMessage?: string;
    startedAt?: string;
    completedAt?: string;
    createdAt?: string;
    results?: AiVoiceResultResponse[];
  };

  type ApiResponseAiCallLogPageResponse = {
    success?: boolean;
    data?: AiCallLogPageResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseAiExecutionResponse = {
    success?: boolean;
    data?: AiExecutionResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseAiImageResultResponse = {
    success?: boolean;
    data?: AiImageResultResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseAiImageTaskResponse = {
    success?: boolean;
    data?: AiImageTaskResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseAiModelParameterResponse = {
    success?: boolean;
    data?: AiModelParameterResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseAiPointReconciliation = {
    success?: boolean;
    data?: AiPointReconciliation;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseAiServiceTestResponse = {
    success?: boolean;
    data?: AiServiceTestResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseAiVideoResultResponse = {
    success?: boolean;
    data?: AiVideoResultResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseAiVideoTaskResponse = {
    success?: boolean;
    data?: AiVideoTaskResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseAiVoiceResultResponse = {
    success?: boolean;
    data?: AiVoiceResultResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseAiVoiceTaskResponse = {
    success?: boolean;
    data?: AiVoiceTaskResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseAssetReextractionPreflight = {
    success?: boolean;
    data?: AssetReextractionPreflight;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseAssetSettingsSummaryResponse = {
    success?: boolean;
    data?: AssetSettingsSummaryResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseAssetVisualWorkspace = {
    success?: boolean;
    data?: AssetVisualWorkspace;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseAuthBootstrapResponse = {
    success?: boolean;
    data?: AuthBootstrapResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseAuthSessionResponse = {
    success?: boolean;
    data?: AuthSessionResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseCommercialEntitlementDefinitionResponse = {
    success?: boolean;
    data?: CommercialEntitlementDefinitionResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseCommercialOrderEntity = {
    success?: boolean;
    data?: CommercialOrderEntity;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseCommercialOrderResponse = {
    success?: boolean;
    data?: CommercialOrderResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseCommercialPackageVersionResponse = {
    success?: boolean;
    data?: CommercialPackageVersionResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseEpisodeComposeTaskResponse = {
    success?: boolean;
    data?: EpisodeComposeTaskResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseEpisodeVideoVersionResponse = {
    success?: boolean;
    data?: EpisodeVideoVersionResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseInspirationCreationDetailResponse = {
    success?: boolean;
    data?: InspirationCreationDetailResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseInspirationCreationPageResponse = {
    success?: boolean;
    data?: InspirationCreationPageResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseInspirationThumbnailBackfillResult = {
    success?: boolean;
    data?: InspirationThumbnailBackfillResult;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListAiImageTaskResponse = {
    success?: boolean;
    data?: AiImageTaskResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListAiVideoResultResponse = {
    success?: boolean;
    data?: AiVideoResultResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListAiVideoTaskResponse = {
    success?: boolean;
    data?: AiVideoTaskResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListAiVoiceResultResponse = {
    success?: boolean;
    data?: AiVoiceResultResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListAiVoiceTaskResponse = {
    success?: boolean;
    data?: AiVoiceTaskResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListBindingResponse = {
    success?: boolean;
    data?: BindingResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListCommercialCatalogItemResponse = {
    success?: boolean;
    data?: CommercialCatalogItemResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListCommercialEntitlementDefinitionResponse = {
    success?: boolean;
    data?: CommercialEntitlementDefinitionResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListCommercialEntitlementGrantEntity = {
    success?: boolean;
    data?: CommercialEntitlementGrantEntity[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListCommercialOrderResponse = {
    success?: boolean;
    data?: CommercialOrderResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListCommercialPackageSummaryResponse = {
    success?: boolean;
    data?: CommercialPackageSummaryResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListCommercialPackageVersionResponse = {
    success?: boolean;
    data?: CommercialPackageVersionResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListEpisodeComposeTaskResponse = {
    success?: boolean;
    data?: EpisodeComposeTaskResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListEpisodeExportRecordResponse = {
    success?: boolean;
    data?: EpisodeExportRecordResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListEpisodeVideoVersionResponse = {
    success?: boolean;
    data?: EpisodeVideoVersionResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListPermissionResponse = {
    success?: boolean;
    data?: PermissionResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListPermissionTreeNodeResponse = {
    success?: boolean;
    data?: PermissionTreeNodeResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListPlatformModelResponse = {
    success?: boolean;
    data?: PlatformModelResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListPlatformProviderResponse = {
    success?: boolean;
    data?: PlatformProviderResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListProjectMemberResponse = {
    success?: boolean;
    data?: ProjectMemberResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListProjectResponse = {
    success?: boolean;
    data?: ProjectResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListProjectRolePermissionResponse = {
    success?: boolean;
    data?: ProjectRolePermissionResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListProjectRoleResponse = {
    success?: boolean;
    data?: ProjectRoleResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListReviewProjectListSummaryResponse = {
    success?: boolean;
    data?: ReviewProjectListSummaryResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListReviewProjectMetricsResponse = {
    success?: boolean;
    data?: ReviewProjectMetricsResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListReviewProjectSummaryResponse = {
    success?: boolean;
    data?: ReviewProjectSummaryResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListReviewTaskResponse = {
    success?: boolean;
    data?: ReviewTaskResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListRoleResponse = {
    success?: boolean;
    data?: RoleResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListShotComposeResultResponse = {
    success?: boolean;
    data?: ShotComposeResultResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListShotComposeTaskResponse = {
    success?: boolean;
    data?: ShotComposeTaskResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListStoryboardSubtitleResponse = {
    success?: boolean;
    data?: StoryboardSubtitleResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListStyleLibraryResponse = {
    success?: boolean;
    data?: StyleLibraryResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListTeamSubscriptionEntity = {
    success?: boolean;
    data?: TeamSubscriptionEntity[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListTenantInvitationResponse = {
    success?: boolean;
    data?: TenantInvitationResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListTenantMemberResponse = {
    success?: boolean;
    data?: TenantMemberResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListTenantSummaryResponse = {
    success?: boolean;
    data?: TenantSummaryResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListVariantResponse = {
    success?: boolean;
    data?: VariantResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListVideoDecompositionBatchResponse = {
    success?: boolean;
    data?: VideoDecompositionBatchResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListWorkflowAgentRecord = {
    success?: boolean;
    data?: WorkflowAgentRecord[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListWorkflowAgentRunSummary = {
    success?: boolean;
    data?: WorkflowAgentRunSummary[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListWorkflowSkillView = {
    success?: boolean;
    data?: WorkflowSkillView[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListWorkflowToolMetadata = {
    success?: boolean;
    data?: WorkflowToolMetadata[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseModelBillingHistoryResponse = {
    success?: boolean;
    data?: ModelBillingHistoryResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseModelPointPriceVersionResponse = {
    success?: boolean;
    data?: ModelPointPriceVersionResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseModelPriceVersionResponse = {
    success?: boolean;
    data?: ModelPriceVersionResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponsePlatformAiAccountingDetailResponse = {
    success?: boolean;
    data?: PlatformAiAccountingDetailResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponsePlatformAiOperationsOverview = {
    success?: boolean;
    data?: PlatformAiOperationsOverview;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponsePlatformCommercialOrderDetailResponse = {
    success?: boolean;
    data?: PlatformCommercialOrderDetailResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponsePlatformCommercialOrderPageResponse = {
    success?: boolean;
    data?: PlatformCommercialOrderPageResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponsePlatformModelResponse = {
    success?: boolean;
    data?: PlatformModelResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponsePlatformProviderResponse = {
    success?: boolean;
    data?: PlatformProviderResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponsePlatformTenantDetailResponse = {
    success?: boolean;
    data?: PlatformTenantDetailResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponsePlatformTenantPageResponse = {
    success?: boolean;
    data?: PlatformTenantPageResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponsePlatformTenantSummaryResponse = {
    success?: boolean;
    data?: PlatformTenantSummaryResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseProjectAiConfigResponse = {
    success?: boolean;
    data?: ProjectAiConfigResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseProjectAiModelsResponse = {
    success?: boolean;
    data?: ProjectAiModelsResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseProjectMemberResponse = {
    success?: boolean;
    data?: ProjectMemberResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseProjectResponse = {
    success?: boolean;
    data?: ProjectResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseProjectRoleResponse = {
    success?: boolean;
    data?: ProjectRoleResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseReviewExportRecordResponse = {
    success?: boolean;
    data?: ReviewExportRecordResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseReviewProjectDetailResponse = {
    success?: boolean;
    data?: ReviewProjectDetailResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseReviewProjectReviewHistoryResponse = {
    success?: boolean;
    data?: ReviewProjectReviewHistoryResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseReviewTaskResponse = {
    success?: boolean;
    data?: ReviewTaskResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseReviewVersionHistoryResponse = {
    success?: boolean;
    data?: ReviewVersionHistoryResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseReviewVersionResponse = {
    success?: boolean;
    data?: ReviewVersionResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseRoleResponse = {
    success?: boolean;
    data?: RoleResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseScriptAnalysisTaskResponse = {
    success?: boolean;
    data?: ScriptAnalysisTaskResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseScriptContentParseResponse = {
    success?: boolean;
    data?: ScriptContentParseResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseScriptEpisodeResponse = {
    success?: boolean;
    data?: ScriptEpisodeResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseScriptEpisodeSummaryDocument = {
    success?: boolean;
    data?: ScriptEpisodeSummaryDocument;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseScriptPageWorkspaceResponse = {
    success?: boolean;
    data?: ScriptPageWorkspaceResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseScriptResponse = {
    success?: boolean;
    data?: ScriptResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseScriptVersionResponse = {
    success?: boolean;
    data?: ScriptVersionResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseShotComposeResultResponse = {
    success?: boolean;
    data?: ShotComposeResultResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseShotComposeTaskResponse = {
    success?: boolean;
    data?: ShotComposeTaskResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseStoryboardBatchResponse = {
    success?: boolean;
    data?: StoryboardBatchResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseStoryboardSubtitleResponse = {
    success?: boolean;
    data?: StoryboardSubtitleResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseStoryboardWorkspacePageResponse = {
    success?: boolean;
    data?: StoryboardWorkspacePageResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseTeamPointAccountResponse = {
    success?: boolean;
    data?: TeamPointAccountResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseTeamPointTransactionPageResponse = {
    success?: boolean;
    data?: TeamPointTransactionPageResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseTeamSubscriptionEntity = {
    success?: boolean;
    data?: TeamSubscriptionEntity;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseTenantInvitationResponse = {
    success?: boolean;
    data?: TenantInvitationResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseTenantSummaryResponse = {
    success?: boolean;
    data?: TenantSummaryResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseVariantResponse = {
    success?: boolean;
    data?: VariantResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseVideoDecompositionBatchResponse = {
    success?: boolean;
    data?: VideoDecompositionBatchResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseVideoDecompositionBatchScreenplaysResponse = {
    success?: boolean;
    data?: VideoDecompositionBatchScreenplaysResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseVideoDecompositionEpisodeDetailResponse = {
    success?: boolean;
    data?: VideoDecompositionEpisodeDetailResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseVideoDecompositionEpisodeResponse = {
    success?: boolean;
    data?: VideoDecompositionEpisodeResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseVideoDecompositionUploadResponse = {
    success?: boolean;
    data?: VideoDecompositionUploadResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseVoid = {
    success?: boolean;
    data?: Record<string, any>;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseWorkflowAgentRecord = {
    success?: boolean;
    data?: WorkflowAgentRecord;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseWorkflowAgentRunDetail = {
    success?: boolean;
    data?: WorkflowAgentRunDetail;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseWorkflowAgentRunResult = {
    success?: boolean;
    data?: WorkflowAgentRunResult;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseWorkflowSkillView = {
    success?: boolean;
    data?: WorkflowSkillView;
    errorCode?: string;
    errorMessage?: string;
  };

  type applyVersionParams = {
    projectId: number;
    versionId: number;
  };

  type AssetReextractionPreflight = {
    targetType?: string;
    existingAssets?: number;
    existingVariants?: number;
    existingPrompts?: number;
    requiresConfirmation?: boolean;
  };

  type assetReextractionPreflightParams = {
    projectId: number;
    targetType: string;
  };

  type assetSettingsSummaryParams = {
    projectId: number;
  };

  type AssetSettingsSummaryResponse = {
    projectId?: number;
    characters?: CharacterAssetSummaryResponse[];
    scenes?: SceneAssetSummaryResponse[];
    props?: PropAssetSummaryResponse[];
  };

  type AssetVisualWorkspace = {
    variantCount?: number;
    primaryVariant?: VariantResponse;
    variants?: VariantResponse[];
    generationSummary?: Record<string, any>;
    episodeBindings?: BindingResponse[];
    resolvedImageUrl?: string;
    resolvedImageSource?: string;
  };

  type assetVisualWorkspaceParams = {
    projectId: number;
    elementType: string;
    elementId: number;
  };

  type AuthBootstrapResponse = {
    user?: UserProfileResponse;
    session?: BootstrapSessionResponse;
    platform?: PlatformAccessResponse;
    tenants?: TenantSummaryResponse[];
    selectedTenant?: SelectedTenantResponse;
    unavailableSelectionReason?: string;
    nextAction?: string;
  };

  type AuthSessionResponse = {
    user?: UserProfileResponse;
    tenants?: TenantSummaryResponse[];
    nextAction?: string;
    expiresAt?: string;
  };

  type backfillParams = {
    limit?: number;
  };

  type BillingEvidenceResponse = {
    costPriceVersionId?: number;
    pointPriceVersionId?: number;
    pointComponents?: PointPolicyComponentResponse[];
  };

  type billingHistoryParams = {
    modelId: number;
  };

  type bindComposeResultParams = {
    projectId: number;
    resultId: number;
  };

  type BindingCommand = {
    episodeIds?: number[];
    preferred?: boolean;
  };

  type BindingResponse = {
    id?: number;
    variantId?: number;
    episodeId?: number;
    episodeNo?: number;
    episodeTitle?: string;
    preferred?: boolean;
    status?: string;
  };

  type bindProjectParams = {
    reviewProjectId: number;
  };

  type BindReviewProjectRequest = {
    mainProjectId: number;
  };

  type bindStoryboardParams = {
    projectId: number;
    resultId: number;
  };

  type bindVisualVariantEpisodesParams = {
    projectId: number;
    variantId: number;
  };

  type bindVoiceResultParams = {
    projectId: number;
    resultId: number;
  };

  type BootstrapSessionResponse = {
    sessionId?: string;
    expiresAt?: string;
  };

  type breakdownStoryboardsParams = {
    projectId: number;
  };

  type CacheUsageResponse = {
    knownCalls?: number;
    unknownCalls?: number;
    promptTokens?: number;
    cachedInputTokens?: number;
    hitRate?: number;
  };

  type cancel1Params = {
    tenantId: number;
    executionId: number;
  };

  type cancel2Params = {
    projectId: number;
    taskId: number;
  };

  type cancel3Params = {
    id: number;
  };

  type cancelComposeTaskParams = {
    projectId: number;
    taskId: number;
  };

  type cancelEpisodeComposeTaskParams = {
    projectId: number;
    taskId: number;
  };

  type cancelParams = {
    projectId: number;
    taskId: number;
  };

  type cancelTaskParams = {
    taskId: number;
  };

  type cancelVoiceTaskParams = {
    projectId: number;
    taskId: number;
  };

  type CharacterAssetSummaryResponse = {
    id?: number;
    name?: string;
    roleType?: string;
    gender?: string;
    ageRange?: string;
    identity?: string;
    personality?: string[];
    appearance?: string;
    prompt?: string;
    status?: string;
    mergeTargetId?: number;
    mainImageUrl?: string;
    mainImageThumbnailUrl?: string;
  };

  type CommercialCatalogItemResponse = {
    packageId?: number;
    packageVersionId?: number;
    code?: string;
    packageType?: string;
    name?: string;
    description?: string;
    billingPeriod?: string;
    periodMonths?: number;
    price?: number;
    listPrice?: number;
    currency?: string;
    entitlements?: CommercialEntitlementInput[];
  };

  type CommercialDisplayEntitlementCommand = {
    name?: string;
    description?: string;
    sortOrder?: number;
  };

  type CommercialEntitlementDefinitionResponse = {
    id?: number;
    code?: string;
    name?: string;
    description?: string;
    category?: string;
    status?: string;
    sortOrder?: number;
    createdAt?: string;
    updatedAt?: string;
  };

  type CommercialEntitlementGrantEntity = {
    id?: number;
    tenantId?: number;
    orderId?: number;
    subscriptionId?: number;
    periodNo?: number;
    entitlementType?: string;
    amount?: number;
    status?: string;
    idempotencyKey?: string;
    grantedAt?: string;
    errorMessage?: string;
    createdAt?: string;
    updatedAt?: string;
  };

  type CommercialEntitlementInput = {
    type?: string;
    value?: number;
    name?: string;
    category?: string;
  };

  type CommercialOrderCreateRequest = {
    packageVersionId?: number;
  };

  type CommercialOrderEntity = {
    id?: number;
    tenantId?: number;
    userId?: number;
    packageVersionId?: number;
    packageSnapshotJson?: string;
    merchantOrderNo?: string;
    amount?: number;
    currency?: string;
    status?: string;
    expiresAt?: string;
    paidAt?: string;
    completedAt?: string;
    createdAt?: string;
    updatedAt?: string;
  };

  type CommercialOrderResponse = {
    id?: number;
    merchantOrderNo?: string;
    amount?: number;
    currency?: string;
    status?: string;
    expiresAt?: string;
    codeUrl?: string;
  };

  type CommercialPackageDraftCommand = {
    code?: string;
    packageType?: string;
    name?: string;
    description?: string;
    billingPeriod?: string;
    periodMonths?: number;
    price?: number;
    listPrice?: number;
    currency?: string;
    effectiveFrom?: string;
    effectiveTo?: string;
    entitlements?: CommercialEntitlementInput[];
    operatorId?: number;
  };

  type CommercialPackageSummaryResponse = {
    id?: number;
    code?: string;
    packageType?: string;
    status?: string;
    latestVersionNo?: number;
    latestName?: string;
    latestPrice?: number;
    latestCurrency?: string;
    latestStatus?: string;
    latestEntitlements?: CommercialEntitlementInput[];
    updatedAt?: string;
  };

  type CommercialPackageVersionResponse = {
    packageId?: number;
    versionId?: number;
    versionNo?: number;
    status?: string;
    name?: string;
    description?: string;
    billingPeriod?: string;
    periodMonths?: number;
    price?: number;
    listPrice?: number;
    currency?: string;
    effectiveFrom?: string;
    effectiveTo?: string;
    entitlements?: CommercialEntitlementInput[];
  };

  type composeTaskParams = {
    projectId: number;
    taskId: number;
  };

  type composeTaskResultsParams = {
    projectId: number;
    taskId: number;
  };

  type composeTasksParams = {
    projectId: number;
    status?: string;
    storyboardId?: number;
  };

  type configParams = {
    projectId: number;
  };

  type confirmParams = {
    episodeId: number;
  };

  type confirmStoryboardsParams = {
    projectId: number;
  };

  type ConfirmVideoDecompositionDraftRequest = {
    draftContent: string;
    expectedDraftVersion?: number;
    projectId: number;
    expectedCurrentScriptVersionId?: number;
  };

  type copy1Params = {
    code: string;
  };

  type CopyAgentRequest = {
    targetCode: string;
  };

  type copyParams = {
    code: string;
  };

  type CopySkillRequest = {
    targetCode: string;
  };

  type create2Params = {
    tenantId: number;
  };

  type create3Params = {
    tenantId: number;
  };

  type create5Params = {
    projectId: number;
  };

  type create6Params = {
    projectId: number;
  };

  type CreateAgentRequest = {
    code: string;
    name: string;
    description?: string;
    systemPrompt: string;
    modelId: number;
    temperature: number;
    maxTokens: number;
    maxSteps: number;
    status: string;
    skillCodes?: string[];
    toolCodes?: string[];
  };

  type CreateAiImageTaskRequest = {
    taskType: string;
    targetType: string;
    targetId: number;
    modelId?: number;
    prompt: string;
    negativePrompt?: string;
    referenceImages?: string[];
    aspectRatio: string;
    imageCount: number;
    style?: string;
    quality?: string;
    seed?: string;
  };

  type CreateAiVideoTaskRequest = {
    storyboardId: number;
    modelId?: number;
    prompt: string;
    negativePrompt?: string;
    firstFrameImageId?: number;
    firstFrameUrl?: string;
    lastFrameImageId?: number;
    lastFrameUrl?: string;
    durationSeconds?: number;
    aspectRatio: string;
    resolution?: string;
    cameraMovement?: string;
    motionStrength?: string;
    randomSeed?: number;
  };

  type CreateAiVoiceTaskRequest = {
    storyboardId: number;
    voiceType: string;
    speakerName?: string;
    voiceId: string;
    textContent: string;
    speed?: number;
    pitch?: number;
    volume?: number;
  };

  type createComposeTaskParams = {
    projectId: number;
  };

  type createEpisodeComposeTaskParams = {
    projectId: number;
  };

  type CreateEpisodeComposeTaskRequest = {
    episodeNo: number;
    taskName?: string;
    versionName?: string;
    outputFormat?: string;
    quality?: string;
    generateCover?: boolean;
  };

  type CreateInvitationRequest = {
    mobile: string;
  };

  type CreateProjectRequest = {
    name: string;
    code: string;
    description?: string;
    coverUrl?: string;
    coverSource?: string;
    ownerId: number;
    startDate?: string;
    endDate?: string;
    aspectRatio?: string;
    fileFormat?: string;
    scriptType?: string;
    breakdownStrength?: string;
    visualStyle?: string;
    scriptName?: string;
    initialScriptContent?: string;
  };

  type CreateProjectRoleRequest = {
    code: string;
    name: string;
    description?: string;
    permissionCodes?: string[];
  };

  type CreateReviewTaskRequest = {
    versionId?: number;
    reviewMode: string;
    selectedDimensions: string[];
    reviewScopeType: string;
    reviewScope?: Record<string, any>;
    taskName?: string;
  };

  type createRole1Params = {
    id: number;
  };

  type createRoleParams = {
    tenantId: number;
  };

  type CreateRoleRequest = {
    code: string;
    name: string;
    description?: string;
    permissionCodes?: string[];
  };

  type CreateShotComposeTaskRequest = {
    storyboardId: number;
    voiceResultId?: number;
    subtitleId?: number;
    includeSubtitle?: boolean;
    audioVolume?: number;
    outputFormat?: string;
  };

  type CreateSkillRequest = {
    code: string;
    content: string;
  };

  type createStoryboardBatchParams = {
    projectId: number;
  };

  type CreateStoryboardBatchRequest = {
    episodeIds: number[];
  };

  type createStoryboardParams = {
    projectId: number;
  };

  type CreateStoryboardSubtitleRequest = {
    storyboardId: number;
    voiceResultId?: number;
    subtitleType: string;
    textContent: string;
    startTime?: number;
    endTime?: number;
    styleConfig?: Record<string, any>;
  };

  type createSubtitleParams = {
    projectId: number;
  };

  type createTaskParams = {
    projectId: number;
  };

  type CreateTenantRequest = {
    name: string;
    type: string;
    logo?: string;
    description?: string;
  };

  type CreateVideoDecompositionBatchRequest = {
    name: string;
    modelId?: number;
    videos: VideoUploadMetadataRequest[];
  };

  type createVisualVariantParams = {
    projectId: number;
    elementType: string;
    elementId: number;
  };

  type createVoiceTaskParams = {
    projectId: number;
  };

  type currentAnalysisParams = {
    projectId: number;
  };

  type currentParams = {
    tenantId: number;
  };

  type defaultModelParams = {
    id: number;
  };

  type delete1Params = {
    id: number;
  };

  type delete2Params = {
    code: string;
  };

  type delete3Params = {
    code: string;
  };

  type deleteComposeResultParams = {
    projectId: number;
    resultId: number;
  };

  type deleteComposeTaskParams = {
    projectId: number;
    taskId: number;
  };

  type deleteElementParams = {
    projectId: number;
    elementType: string;
    elementId: number;
  };

  type deleteEpisodeComposeTaskParams = {
    projectId: number;
    taskId: number;
  };

  type deleteEpisodeVideoVersionParams = {
    projectId: number;
    versionId: number;
  };

  type deleteResult1Params = {
    projectId: number;
    resultId: number;
    force?: boolean;
  };

  type deleteResultParams = {
    projectId: number;
    resultId: number;
  };

  type deleteRole1Params = {
    id: number;
    roleId: number;
  };

  type deleteRoleParams = {
    tenantId: number;
    roleId: number;
  };

  type deleteStoryboardParams = {
    projectId: number;
    storyboardId: number;
  };

  type deleteSubtitleParams = {
    projectId: number;
    subtitleId: number;
  };

  type deleteTaskParams = {
    projectId: number;
    taskId: number;
  };

  type deleteUsingDELETEParams = {
    projectId: number;
    taskId: number;
  };

  type deleteVisualVariantParams = {
    projectId: number;
    variantId: number;
  };

  type deleteVoiceResultParams = {
    projectId: number;
    resultId: number;
  };

  type deleteVoiceTaskParams = {
    projectId: number;
    taskId: number;
  };

  type detail10Params = {
    code: string;
  };

  type detail11Params = {
    code: string;
  };

  type detail12Params = {
    runId: number;
  };

  type detail13Params = {
    token: string;
  };

  type detail14Params = {
    id: number;
  };

  type detail1Params = {
    tenantId: number;
    roleId: number;
  };

  type detail2Params = {
    tenantId: number;
    orderId: number;
  };

  type detail3Params = {
    tenantId: number;
    executionId: number;
  };

  type detail4Params = {
    id: number;
  };

  type detail5Params = {
    projectId: number;
    taskId: number;
  };

  type detail6Params = {
    projectId: number;
    taskId: number;
  };

  type detail7Params = {
    id: number;
  };

  type detail8Params = {
    tenantId: number;
  };

  type detail9Params = {
    orderId: number;
  };

  type detailParams = {
    batchId: number;
  };

  type disable1Params = {
    code: string;
  };

  type disableModelParams = {
    id: number;
  };

  type disableParams = {
    id: number;
  };

  type disableProviderParams = {
    id: number;
  };

  type downloadComposeResultParams = {
    projectId: number;
    resultId: number;
  };

  type downloadEpisodeVideoVersionParams = {
    projectId: number;
    versionId: number;
  };

  type downloadExportParams = {
    fileName: string;
  };

  type downloadParams = {
    projectId: number;
    resultId: number;
  };

  type downloadResultParams = {
    projectId: number;
    resultId: number;
  };

  type downloadVoiceResultParams = {
    projectId: number;
    resultId: number;
  };

  type enable1Params = {
    code: string;
  };

  type enableModelParams = {
    id: number;
  };

  type enableParams = {
    id: number;
  };

  type enableProviderParams = {
    id: number;
  };

  type EpisodeComposeItemResponse = {
    id?: number;
    taskId?: number;
    episodeNo?: number;
    storyboardId?: number;
    storyboardOrder?: number;
    shotResultId?: number;
    videoUrl?: string;
    durationSeconds?: number;
    width?: number;
    height?: number;
    status?: string;
    errorMessage?: string;
    createdAt?: string;
  };

  type episodeComposeTaskParams = {
    projectId: number;
    taskId: number;
  };

  type EpisodeComposeTaskResponse = {
    id?: number;
    projectId?: number;
    episodeNo?: number;
    taskName?: string;
    composeConfig?: string;
    storyboardCount?: number;
    totalDurationSeconds?: number;
    status?: string;
    errorMessage?: string;
    startedAt?: string;
    completedAt?: string;
    createdAt?: string;
    items?: EpisodeComposeItemResponse[];
    videoVersion?: EpisodeVideoVersionResponse;
  };

  type episodeComposeTasksParams = {
    projectId: number;
    episodeNo?: number;
    status?: string;
  };

  type EpisodeExportRecordResponse = {
    id?: number;
    episodeNo?: number;
    videoVersionId?: number;
    exportType?: string;
    exportStatus?: string;
    fileName?: string;
    fileSize?: number;
    downloadUrl?: string;
    errorMessage?: string;
    createdAt?: string;
  };

  type episodeExportRecordsParams = {
    projectId: number;
    episodeNo?: number;
  };

  type EpisodeFanoutProgressResponse = {
    snapshotId?: number;
    status?: string;
    total?: number;
    completed?: number;
    failed?: number;
    currentEpisodeId?: number;
    currentEpisodeKey?: string;
    retryable?: boolean;
    stale?: boolean;
    units?: EpisodeFanoutUnitResponse[];
    cache?: CacheUsageResponse;
    timing?: TimingUsageResponse;
  };

  type EpisodeFanoutUnitResponse = {
    episodeId?: number;
    episodeKey?: string;
    status?: string;
    childRunId?: number;
    errorCode?: string;
    errorMessage?: string;
  };

  type episodeParams = {
    episodeId: number;
  };

  type EpisodePipelineStatusResponse = {
    episodeId?: number;
    episodeKey?: string;
    episodeNo?: number;
    summaryStatus?: string;
    summaryRunId?: number;
    summaryError?: string;
    recognitionStatus?: string;
    recognitionRunId?: number;
    recognitionError?: string;
    storyboardStatus?: string;
    storyboardExecutionId?: number;
    storyboardId?: number;
    storyboardError?: string;
    autoTriggered?: boolean;
    protectedExisting?: boolean;
  };

  type EpisodeSplitProgressResponse = {
    mode?: string;
    fallbackReason?: string;
    totalChunks?: number;
    completedChunks?: number;
    failedChunks?: number;
    stale?: boolean;
  };

  type episodeVideoCoverParams = {
    projectId: number;
    versionId: number;
  };

  type episodeVideoVersionParams = {
    projectId: number;
    versionId: number;
  };

  type EpisodeVideoVersionResponse = {
    id?: number;
    episodeNo?: number;
    composeTaskId?: number;
    versionNo?: number;
    versionName?: string;
    videoUrl?: string;
    storagePath?: string;
    coverUrl?: string;
    durationSeconds?: number;
    width?: number;
    height?: number;
    fileSize?: number;
    format?: string;
    materialId?: number;
    current?: boolean;
    status?: string;
    createdAt?: string;
  };

  type episodeVideoVersionsParams = {
    projectId: number;
    episodeNo: number;
  };

  type ExportReviewReportRequest = {
    exportType: string;
    versionId: number;
    taskId?: number;
  };

  type exportUsingPOSTParams = {
    projectId: number;
  };

  type fileParams = {
    id: number;
  };

  type FormalRunRequest = {
    agentCode: string;
    input: string;
    projectId?: number;
    episodeId?: number;
    scriptId?: number;
    taskId?: number;
  };

  type generateParams = {
    projectId: number;
  };

  type GeneratePromptRequest = {
    targetType: string;
    targetId?: number;
  };

  type generatePromptsParams = {
    projectId: number;
  };

  type GenerateScriptRequest = {
    title?: string;
    storyIdea: string;
    genre: string;
    episodeCount?: number;
    duration?: number;
    mainCharacter?: string;
    styleRequirement?: string;
    referenceContent?: string;
  };

  type grantsParams = {
    tenantId: number;
  };

  type historyParams = {
    packageId: number;
  };

  type imageParams = {
    externalId: string;
  };

  type importProjectParams = {
    mainProjectId?: number;
  };

  type InspirationCreationDetailResponse = {
    id?: number;
    externalId?: string;
    externalTaskId?: string;
    creationType?: string;
    taskType?: string;
    title?: string;
    authorName?: string;
    url?: string;
    thumbnailUrl?: string;
    mimeType?: string;
    fileSize?: number;
    sortOrder?: number;
    sourceCreatedAt?: string;
    detailJson?: JsonNode;
  };

  type InspirationCreationListResponse = {
    id?: number;
    externalId?: string;
    externalTaskId?: string;
    creationType?: string;
    taskType?: string;
    title?: string;
    authorName?: string;
    url?: string;
    thumbnailUrl?: string;
    mimeType?: string;
    fileSize?: number;
    sortOrder?: number;
    sourceCreatedAt?: string;
  };

  type InspirationCreationPageResponse = {
    records?: InspirationCreationListResponse[];
    total?: number;
    current?: number;
    pageSize?: number;
  };

  type InspirationThumbnailBackfillResult = {
    processed?: number;
    failed?: number;
  };

  type JsonNode = true;

  type latestStoryboardBatchParams = {
    projectId: number;
  };

  type leaveParams = {
    tenantId: number;
  };

  type list10Params = {
    keyword?: string;
    status?: string;
    packageType?: string;
    current?: number;
    pageSize?: number;
  };

  type list12Params = {
    query?: string;
  };

  type list13Params = {
    query?: string;
  };

  type list14Params = {
    agentCode?: string;
    limit?: number;
  };

  type list15Params = {
    page?: number;
    pageSize?: number;
  };

  type list1Params = {
    tenantId: number;
  };

  type list2Params = {
    tenantId: number;
  };

  type list3Params = {
    tenantId: number;
    current?: number;
    pageSize?: number;
    serviceType?: string;
    status?: string;
    businessScene?: string;
  };

  type list4Params = {
    category?: string;
    keyword?: string;
  };

  type list6Params = {
    projectId: number;
    status?: string;
    storyboardId?: number;
  };

  type list7Params = {
    projectId: number;
    taskType?: string;
    status?: string;
  };

  type list8Params = {
    keyword?: string;
    status?: string;
    packageType?: string;
    current?: number;
    pageSize?: number;
  };

  type listParams = {
    projectId?: number;
  };

  type listRolesParams = {
    tenantId: number;
  };

  type LoginByMobileRequest = {
    mobile: string;
    password: string;
  };

  type memberRolesParams = {
    tenantId: number;
    memberId: number;
  };

  type membersParams = {
    id: number;
  };

  type ModelBillingHistoryResponse = {
    modelId?: number;
    costPrices?: ModelPriceVersionResponse[];
    pointPrices?: ModelPointPriceVersionResponse[];
  };

  type modelParametersParams = {
    id: number;
  };

  type ModelPointPriceVersionResponse = {
    id?: number;
    modelId?: number;
    versionNo?: number;
    status?: string;
    effectiveFrom?: string;
    effectiveTo?: string;
    publishedAt?: string;
    createdBy?: number;
    components?: PointPolicyComponentResponse[];
  };

  type ModelPriceComponentRequest = {
    metric: string;
    unitSize: number;
    unitPrice: number;
    currency: string;
    dimensions?: Record<string, any>;
  };

  type ModelPriceComponentResponse = {
    id?: number;
    metric?: string;
    unitSize?: number;
    unitPrice?: number;
    currency?: string;
  };

  type ModelPriceVersionResponse = {
    id?: number;
    modelId?: number;
    versionNo?: number;
    status?: string;
    effectiveFrom?: string;
    effectiveTo?: string;
    publishedAt?: string;
    createdBy?: number;
    components?: ModelPriceComponentResponse[];
  };

  type modelsParams = {
    projectId: number;
  };

  type moveStoryboardParams = {
    projectId: number;
    storyboardId: number;
  };

  type MoveStoryboardRequest = {
    shotNo?: number;
  };

  type PermissionResponse = {
    id?: number;
    code?: string;
    name?: string;
    type?: string;
    resource?: string;
    action?: string;
  };

  type PermissionTreeNodeResponse = {
    key?: string;
    title?: string;
    resource?: string;
    permissionCode?: string;
  };

  type PlatformAccessResponse = {
    roles?: string[];
    permissions?: string[];
  };

  type PlatformAiAccountingDetailResponse = {
    execution?: AiExecutionResponse;
    usageLines?: UsageLineResponse[];
    costLines?: UsageCostLineResponse[];
    billingEvidence?: BillingEvidenceResponse;
    settlement?: PointSettlementDetailResponse;
  };

  type PlatformAiOperationsOverview = {
    expiredClaims?: number;
    retryExhausted?: number;
    unpricedUsage?: number;
    incompleteUsage?: number;
    settlementReview?: number;
    totalProviderCost?: number;
    totalSettledPoints?: number;
    providerFailureRates?: ProviderFailureRate[];
  };

  type PlatformCommercialOrderDetailResponse = {
    id?: number;
    merchantOrderNo?: string;
    tenantId?: number;
    tenantName?: string;
    tenantCode?: string;
    packageVersionId?: number;
    packageName?: string;
    packageVersionNo?: number;
    packageType?: string;
    amount?: number;
    currency?: string;
    status?: string;
    expiresAt?: string;
    paidAt?: string;
    completedAt?: string;
    createdAt?: string;
    updatedAt?: string;
    payment?: PlatformCommercialOrderPaymentResponse;
  };

  type PlatformCommercialOrderPageResponse = {
    records?: PlatformCommercialOrderSummaryResponse[];
    total?: number;
    current?: number;
    pageSize?: number;
  };

  type PlatformCommercialOrderPaymentResponse = {
    provider?: string;
    providerTradeNo?: string;
    status?: string;
    paidAt?: string;
  };

  type PlatformCommercialOrderSummaryResponse = {
    id?: number;
    merchantOrderNo?: string;
    tenantId?: number;
    tenantName?: string;
    tenantCode?: string;
    packageVersionId?: number;
    packageName?: string;
    packageVersionNo?: number;
    packageType?: string;
    amount?: number;
    currency?: string;
    status?: string;
    paidAt?: string;
    createdAt?: string;
    payment?: PlatformCommercialOrderPaymentResponse;
  };

  type PlatformModelRequest = {
    providerId: number;
    code: string;
    name: string;
    modelCode: string;
    serviceType: string;
    description?: string;
    enabled?: boolean;
    isDefault?: boolean;
    sort?: number;
    configJson?: string;
  };

  type PlatformModelResponse = {
    id?: number;
    providerId?: number;
    providerName?: string;
    code?: string;
    name?: string;
    modelCode?: string;
    serviceType?: string;
    description?: string;
    status?: string;
    isDefault?: boolean;
    sort?: number;
    capabilities?: string[];
    updatedAt?: string;
  };

  type PlatformProviderRequest = {
    name: string;
    code: string;
    baseUrl?: string;
    defaultBaseUrl?: string;
    supportedTypes?: string;
    description?: string;
    apiKey?: string;
    enabled?: boolean;
  };

  type PlatformProviderResponse = {
    id?: number;
    name?: string;
    code?: string;
    supportedTypes?: string;
    defaultBaseUrl?: string;
    baseUrl?: string;
    apiKey?: string;
    description?: string;
    status?: string;
    lastTestStatus?: string;
    lastTestMessage?: string;
    lastTestAt?: string;
    updatedAt?: string;
  };

  type PlatformTenantDetailResponse = {
    id?: number;
    code?: string;
    name?: string;
    type?: string;
    status?: string;
    logo?: string;
    description?: string;
    owner?: PlatformTenantOwnerResponse;
    activeMemberCount?: number;
    pointBalance?: number;
    currentPackage?: PlatformTenantPackageResponse;
    queuedPackages?: PlatformTenantPackageResponse[];
    createdAt?: string;
    updatedAt?: string;
  };

  type PlatformTenantOwnerResponse = {
    memberId?: number;
    userId?: number;
    nickname?: string;
    mobile?: string;
    email?: string;
  };

  type PlatformTenantPackageResponse = {
    subscriptionId?: number;
    packageId?: number;
    packageVersionId?: number;
    packageType?: string;
    name?: string;
    subscriptionStatus?: string;
    startsAt?: string;
    endsAt?: string;
  };

  type PlatformTenantPageResponse = {
    records?: PlatformTenantSummaryResponse[];
    total?: number;
    current?: number;
    pageSize?: number;
  };

  type PlatformTenantSummaryResponse = {
    id?: number;
    code?: string;
    name?: string;
    type?: string;
    status?: string;
    owner?: PlatformTenantOwnerResponse;
    activeMemberCount?: number;
    pointBalance?: number;
    currentPackage?: PlatformTenantPackageResponse;
    createdAt?: string;
  };

  type PointLedgerResponse = {
    id?: number;
    attemptId?: number;
    aiCallLogId?: number;
    policyVersionId?: number;
    entryType?: string;
    amount?: number;
    availableBalanceAfter?: number;
    reservedBalanceAfter?: number;
    createdAt?: string;
  };

  type PointPolicyComponentRequest = {
    metric: string;
    unitSize: number;
    pointRate: number;
    dimensions?: Record<string, any>;
  };

  type PointPolicyComponentResponse = {
    id?: number;
    metric?: string;
    unitSize?: number;
    pointRate?: number;
  };

  type PointReservationResponse = {
    id?: number;
    policyVersionId?: number;
    pointPriceVersionId?: number;
    status?: string;
    reservedPoints?: number;
    settledPoints?: number;
    releasedPoints?: number;
    refundedPoints?: number;
    createdAt?: string;
    settledAt?: string;
    releasedAt?: string;
    refundedAt?: string;
  };

  type PointSettlementDetailResponse = {
    reservation?: PointReservationResponse;
    ledger?: PointLedgerResponse[];
  };

  type pollParams = {
    projectId: number;
    taskId: number;
  };

  type ProjectAiConfigRequest = {
    textModelId?: number;
    imageModelId?: number;
    videoModelId?: number;
    audioModelId?: number;
  };

  type ProjectAiConfigResponse = {
    projectId?: number;
    textModelId?: number;
    imageModelId?: number;
    videoModelId?: number;
    audioModelId?: number;
  };

  type ProjectAiModelsResponse = {
    textModels?: ProjectModelOptionResponse[];
    imageModels?: ProjectModelOptionResponse[];
    videoModels?: ProjectModelOptionResponse[];
    audioModels?: ProjectModelOptionResponse[];
  };

  type ProjectCapabilities = {
    canView?: boolean;
    canEdit?: boolean;
    canDelete?: boolean;
    canManageMembers?: boolean;
    canManageRoles?: boolean;
  };

  type ProjectMemberResponse = {
    id?: number;
    tenantId?: number;
    projectId?: number;
    userId?: number;
    nickname?: string;
    mobile?: string;
    roleId?: number;
    roleName?: string;
    roleCode?: string;
    status?: string;
    joinedAt?: string;
  };

  type ProjectModelOptionResponse = {
    id?: number;
    name?: string;
    description?: string;
  };

  type projectParams = {
    projectId: number;
  };

  type ProjectResponse = {
    id?: number;
    tenantId?: number;
    name?: string;
    code?: string;
    description?: string;
    coverUrl?: string;
    coverSource?: string;
    ownerId?: number;
    ownerName?: string;
    status?: string;
    startDate?: string;
    endDate?: string;
    aspectRatio?: string;
    fileFormat?: string;
    scriptType?: string;
    breakdownStrength?: string;
    visualStyle?: string;
    initialScriptContent?: string;
    memberCount?: number;
    accessSource?: "TENANT_WIDE" | "PROJECT_MEMBER";
    projectRoleCode?: string;
    projectRoleName?: string;
    effectivePermissions?: string[];
    capabilities?: ProjectCapabilities;
    createdAt?: string;
    updatedAt?: string;
  };

  type ProjectRolePermissionResponse = {
    id?: number;
    code?: string;
    name?: string;
    resource?: string;
    action?: string;
  };

  type ProjectRoleResponse = {
    id?: number;
    tenantId?: number;
    projectId?: number;
    name?: string;
    code?: string;
    description?: string;
    isSystem?: boolean;
    status?: string;
    createdAt?: string;
    updatedAt?: string;
  };

  type PropAssetSummaryResponse = {
    id?: number;
    name?: string;
    propType?: string;
    appearance?: string;
    plotFunction?: string;
    prompt?: string;
    status?: string;
    mergeTargetId?: number;
    mainImageUrl?: string;
    mainImageThumbnailUrl?: string;
  };

  type ProviderFailureRate = {
    provider?: string;
    total?: number;
    failed?: number;
    failureRate?: number;
  };

  type PublishModelPointPriceRequest = {
    effectiveFrom: string;
    effectiveTo?: string;
    components: PointPolicyComponentRequest[];
  };

  type publishModelPriceParams = {
    modelId: number;
  };

  type PublishModelPriceRequest = {
    effectiveFrom: string;
    effectiveTo?: string;
    components: ModelPriceComponentRequest[];
  };

  type publishParams = {
    packageId: number;
    versionId: number;
  };

  type publishPointPriceParams = {
    modelId: number;
  };

  type queuedParams = {
    tenantId: number;
  };

  type readParams = {
    tenantId: number;
    projectId: number;
    token?: string;
  };

  type reanalyzeParams = {
    projectId: number;
  };

  type reanalyzeVersionParams = {
    projectId: number;
    versionId: number;
  };

  type reconcileParams = {
    orderId: number;
  };

  type reconciliationParams = {
    tenantId: number;
  };

  type refreshParams = {
    tenantId: number;
    orderId: number;
  };

  type regenerate1Params = {
    projectId: number;
    taskId: number;
  };

  type regenerate2Params = {
    projectId: number;
    taskId: number;
  };

  type regenerateComposeTaskParams = {
    projectId: number;
    taskId: number;
  };

  type regenerateEpisodeAssetsParams = {
    projectId: number;
    episodeId: number;
  };

  type regenerateEpisodeComposeTaskParams = {
    projectId: number;
    taskId: number;
  };

  type regenerateEpisodesParams = {
    projectId: number;
  };

  type regenerateEpisodeSummaryParams = {
    projectId: number;
    episodeId: number;
  };

  type RegenerateEpisodeSummaryRequest = {
    overwrite: boolean;
  };

  type regenerateParams = {
    tenantId: number;
    executionId: number;
  };

  type regenerateVoiceTaskParams = {
    projectId: number;
    taskId: number;
  };

  type RegisterRequest = {
    mobile: string;
    verificationCode: string;
    nickname: string;
    password: string;
  };

  type rejectParams = {
    token: string;
  };

  type removeMemberParams = {
    id: number;
    userId: number;
  };

  type removeParams = {
    tenantId: number;
    memberId: number;
  };

  type renameEpisodeVideoVersionParams = {
    projectId: number;
    versionId: number;
  };

  type RenameEpisodeVideoVersionRequest = {
    versionName: string;
  };

  type resultsParams = {
    projectId: number;
    taskId: number;
  };

  type retry1Params = {
    tenantId: number;
    executionId: number;
  };

  type retryAnalysisParams = {
    projectId: number;
    stageCode: string;
  };

  type retryParams = {
    episodeId: number;
  };

  type retryTaskParams = {
    taskId: number;
    fullRegeneration?: boolean;
  };

  type RetryVideoDecompositionEpisodeRequest = {
    phase?: string;
  };

  type ReviewCacheUsageResponse = {
    promptTokens?: number;
    ordinaryInputTokens?: number;
    cachedInputTokens?: number;
    cacheWriteTokens?: number;
    outputTokens?: number;
    latencyMs?: number;
    cacheHitRatio?: number;
    cacheObservable?: boolean;
  };

  type ReviewExportRecordResponse = {
    id?: number;
    projectId?: number;
    versionId?: number;
    taskId?: number;
    exportType?: string;
    exportStatus?: string;
    fileName?: string;
    fileSize?: number;
    downloadUrl?: string;
    errorMessage?: string;
    createdAt?: string;
  };

  type ReviewFanoutProgressResponse = {
    status?: string;
    totalUnits?: number;
    completedUnits?: number;
    failedUnits?: number;
    currentUnitId?: number;
    aggregationStatus?: string;
    units?: ReviewUnitProgressResponse[];
  };

  type reviewHistoryParams = {
    projectId: number;
    page?: number;
    pageSize?: number;
  };

  type ReviewHistoryTaskResponse = {
    id?: number;
    scriptVersionId?: number;
    roundNo?: number;
    reviewMode?: string;
    selectedDimensions?: string[];
    reviewScopeType?: string;
    reportMarkdown?: string;
    status?: string;
    overallProgress?: number;
    createdBy?: number;
    createdAt?: string;
    completedAt?: string;
    canceledAt?: string;
    errorMessage?: string;
  };

  type ReviewObservabilityResponse = {
    cacheUsage?: ReviewCacheUsageResponse;
  };

  type ReviewProjectDetailResponse = {
    project?: ReviewProjectSummaryResponse;
    versions?: ReviewVersionResponse[];
    tasks?: ReviewTaskResponse[];
  };

  type ReviewProjectListSummaryResponse = {
    id?: number;
    mainProjectId?: number;
    accessSource?: string;
    name?: string;
    sourceFileName?: string;
    sourceType?: string;
    currentVersionId?: number;
    status?: string;
    createdAt?: string;
    updatedAt?: string;
  };

  type ReviewProjectMetricsResponse = {
    projectId?: number;
    versionCount?: number;
    latestRoundNo?: number;
    reviewState?: string;
    actionLabel?: string;
  };

  type ReviewProjectReviewHistoryResponse = {
    project?: ReviewProjectSummaryResponse;
    versions?: ReviewVersionMetadataResponse[];
    items?: ReviewHistoryTaskResponse[];
    page?: number;
    pageSize?: number;
    total?: number;
  };

  type ReviewProjectSummaryResponse = {
    id?: number;
    mainProjectId?: number;
    accessSource?: string;
    name?: string;
    sourceFileName?: string;
    sourceType?: string;
    currentVersionId?: number;
    lastTaskId?: number;
    status?: string;
    versionCount?: number;
    latestRoundNo?: number;
    reviewState?: string;
    actionLabel?: string;
    createdAt?: string;
    updatedAt?: string;
  };

  type ReviewRoundHistoryResponse = {
    taskId?: number;
    roundNo?: number;
    status?: string;
    reviewMode?: string;
    completedAt?: string;
  };

  type ReviewTaskResponse = {
    id?: number;
    projectId?: number;
    scriptVersionId?: number;
    roundNo?: number;
    reviewMode?: string;
    selectedDimensions?: string[];
    reviewScopeType?: string;
    reviewScope?: Record<string, any>;
    reportMarkdown?: string;
    status?: string;
    currentStage?: string;
    overallProgress?: number;
    currentAction?: string;
    errorCode?: string;
    errorMessage?: string;
    workflowAgentCode?: string;
    workflowAgentRevision?: number;
    workflowAgentRunId?: number;
    workflowPhase?: string;
    workflowAttemptNo?: number;
    fanoutSnapshotId?: number;
    aggregationRunId?: number;
    retryKind?: string;
    stale?: boolean;
    fanout?: ReviewFanoutProgressResponse;
    observability?: ReviewObservabilityResponse;
    completedAt?: string;
    canceledAt?: string;
    boundVersion?: ReviewVersionResponse;
  };

  type ReviewUnitProgressResponse = {
    id?: number;
    unitNo?: number;
    unitKey?: string;
    stageType?: string;
    dimension?: string;
    status?: string;
    childRunId?: number;
    attemptNo?: number;
    reportSaved?: boolean;
    errorCode?: string;
    errorMessage?: string;
    cacheUsage?: ReviewCacheUsageResponse;
  };

  type ReviewVersionDiffLineResponse = {
    type?: string;
    lineNo?: number;
    beforeText?: string;
    afterText?: string;
  };

  type ReviewVersionDiffResponse = {
    fromVersionId?: number;
    toVersionId?: number;
    addedLines?: number;
    removedLines?: number;
    lines?: ReviewVersionDiffLineResponse[];
  };

  type ReviewVersionHistoryResponse = {
    project?: ReviewProjectSummaryResponse;
    selectedVersion?: ReviewVersionResponse;
    versions?: ReviewVersionResponse[];
    diffLines?: ReviewVersionDiffResponse[];
    roundHistory?: ReviewRoundHistoryResponse[];
  };

  type ReviewVersionMetadataResponse = {
    id?: number;
    projectId?: number;
    versionNo?: number;
    sourceType?: string;
    fileName?: string;
    createdAt?: string;
  };

  type ReviewVersionResponse = {
    id?: number;
    projectId?: number;
    versionNo?: number;
    sourceType?: string;
    fileName?: string;
    content?: string;
    createdAt?: string;
  };

  type revokeCostPriceParams = {
    modelId: number;
    versionId: number;
  };

  type revokePointPriceParams = {
    modelId: number;
    versionId: number;
  };

  type rewriteParams = {
    projectId: number;
  };

  type RewriteScriptRequest = {
    rewriteType: string;
    requirement?: string;
    outputLength?: string;
  };

  type rolePermissions1Params = {
    id: number;
    roleId: number;
  };

  type rolePermissionsParams = {
    tenantId: number;
    roleId: number;
  };

  type RoleResponse = {
    id?: number;
    tenantId?: number;
    code?: string;
    name?: string;
    description?: string;
    roleType?: string;
    status?: string;
    isDefault?: boolean;
    memberCount?: number;
    createdAt?: string;
    updatedAt?: string;
  };

  type rolesParams = {
    id: number;
  };

  type rollbackParams = {
    projectId: number;
  };

  type RollbackReviewVersionRequest = {
    versionId: number;
  };

  type saveComposeMaterialParams = {
    projectId: number;
    resultId: number;
  };

  type saveCurrentParams = {
    projectId: number;
  };

  type SaveEpisodeSummaryRequest = {
    summary: string;
    highlights: string[];
    endingHook?: string;
    overwrite: boolean;
  };

  type saveEpisodeVideoMaterialParams = {
    projectId: number;
    versionId: number;
  };

  type saveMaterial1Params = {
    projectId: number;
    resultId: number;
  };

  type saveMaterialParams = {
    projectId: number;
    resultId: number;
  };

  type saveParams = {
    projectId: number;
  };

  type SaveReviewVersionRequest = {
    content: string;
    fileName?: string;
    sourceType?: string;
  };

  type SaveScriptRequest = {
    title?: string;
    content: string;
    status?: string;
  };

  type SaveStoryboardRequest = {
    episodeNo?: number;
    shotNo?: number;
    storyboardNo?: number;
    sceneNo?: string;
    shotType?: string;
    visualDescription: string;
    characters?: string;
    actions?: string;
    dialogue?: string;
    scene?: string;
    props?: string;
    mood?: string;
    durationSeconds?: number;
    imagePrompt?: string;
    videoPrompt?: string;
    promptDocument?: JsonNode;
    status?: string;
  };

  type saveVersionParams = {
    projectId: number;
  };

  type saveVoiceMaterialParams = {
    projectId: number;
    resultId: number;
  };

  type SceneAssetSummaryResponse = {
    id?: number;
    name?: string;
    sceneType?: string;
    atmosphere?: string;
    description?: string;
    visualStyle?: string;
    prompt?: string;
    status?: string;
    mergeTargetId?: number;
    mainImageUrl?: string;
    mainImageThumbnailUrl?: string;
  };

  type scopedAssetReextractionParams = {
    projectId: number;
  };

  type ScopedAssetReextractionRequest = {
    targetType: string;
    promptPolicy: string;
  };

  type screenplaysParams = {
    batchId: number;
  };

  type ScriptAnalysisStageResponse = {
    id?: number;
    stageCode?: string;
    stageOrder?: number;
    status?: string;
    progressPercent?: number;
    completedUnits?: number;
    totalUnits?: number;
    currentAction?: string;
    errorCode?: string;
    errorMessage?: string;
    retryable?: boolean;
    agentRunId?: number;
    resultJson?: string;
    providerRequestId?: string;
    aiCallLogId?: number;
    durationMs?: number;
    resultErrorCode?: string;
    resultErrorMessage?: string;
    resultRetryable?: boolean;
    fanout?: EpisodeFanoutProgressResponse;
    splitProgress?: EpisodeSplitProgressResponse;
  };

  type ScriptAnalysisTaskResponse = {
    id?: number;
    scriptVersionId?: number;
    status?: string;
    currentStage?: string;
    overallProgress?: number;
    currentAction?: string;
    errorCode?: string;
    errorMessage?: string;
    stages?: ScriptAnalysisStageResponse[];
    episodes?: EpisodePipelineStatusResponse[];
  };

  type scriptContentParams = {
    projectId: number;
  };

  type ScriptContentParseResponse = {
    fileName?: string;
    content?: string;
  };

  type scriptEpisodeParams = {
    projectId: number;
    episodeId: number;
  };

  type ScriptEpisodeResponse = {
    episodeId?: number;
    episodeNo?: number;
    title?: string;
    content?: string;
    summary?: string;
    contentFingerprint?: string;
    generatedByRunId?: number;
    formalSummary?: ScriptEpisodeSummaryDocument;
  };

  type ScriptEpisodeSummaryDocument = {
    id?: number;
    tenantId?: number;
    projectId?: number;
    scriptId?: number;
    episodeId?: number;
    schemaVersion?: number;
    content?: JsonNode;
    source?: string;
    generatedByRunId?: number;
    createdBy?: number;
    updatedBy?: number;
    createdAt?: string;
    updatedAt?: string;
  };

  type ScriptEpisodeSummaryResponse = {
    episodeId?: number;
    episodeNo?: number;
    title?: string;
    summary?: string;
    contentFingerprint?: string;
    generatedByRunId?: number;
    formalSummary?: ScriptEpisodeSummaryDocument;
  };

  type ScriptGlobalUnderstandingResponse = {
    id?: number;
    schemaVersion?: number;
    content?: JsonNode;
    analyzedContentHash?: string;
    lastAgentRunId?: number;
    updatedAt?: string;
  };

  type ScriptPageScriptResponse = {
    id?: number;
    projectId?: number;
    title?: string;
    sourceType?: string;
    status?: string;
    currentVersionId?: number;
    updatedAt?: string;
  };

  type scriptPageWorkspaceParams = {
    projectId: number;
  };

  type ScriptPageWorkspaceResponse = {
    projectId?: number;
    script?: ScriptPageScriptResponse;
    versions?: ScriptVersionSummaryResponse[];
    episodes?: ScriptEpisodeSummaryResponse[];
    episodeWarnings?: Warning[];
    analysis?: ScriptAnalysisTaskResponse;
    globalUnderstanding?: ScriptGlobalUnderstandingResponse;
  };

  type ScriptResponse = {
    id?: number;
    projectId?: number;
    title?: string;
    sourceType?: string;
    content?: string;
    status?: string;
    currentVersionId?: number;
    updatedAt?: string;
  };

  type scriptVersionParams = {
    projectId: number;
    versionId: number;
  };

  type ScriptVersionResponse = {
    id?: number;
    scriptId?: number;
    versionNo?: number;
    sourceType?: string;
    inputSummary?: string;
    content?: string;
    status?: string;
    createdAt?: string;
  };

  type ScriptVersionSummaryResponse = {
    id?: number;
    scriptId?: number;
    versionNo?: number;
    sourceType?: string;
    inputSummary?: string;
    status?: string;
    createdAt?: string;
  };

  type SelectedTenantResponse = {
    tenant?: TenantSummaryResponse;
    membership?: TenantMembershipResponse;
    roles?: string[];
    permissions?: string[];
  };

  type selectPrimaryVisualVariantParams = {
    projectId: number;
    variantId: number;
  };

  type selectResultParams = {
    projectId: number;
    resultId: number;
  };

  type selectSubtitleParams = {
    projectId: number;
    subtitleId: number;
  };

  type setCurrentEpisodeVideoVersionParams = {
    projectId: number;
    versionId: number;
  };

  type ShotComposeResultResponse = {
    id?: number;
    taskId?: number;
    storyboardId?: number;
    videoUrl?: string;
    storagePath?: string;
    coverUrl?: string;
    durationSeconds?: number;
    width?: number;
    height?: number;
    fileSize?: number;
    format?: string;
    materialId?: number;
    selected?: boolean;
    status?: string;
    createdAt?: string;
  };

  type ShotComposeTaskResponse = {
    id?: number;
    projectId?: number;
    storyboardId?: number;
    voiceResultId?: number;
    subtitleId?: number;
    composeConfig?: string;
    status?: string;
    errorMessage?: string;
    startedAt?: string;
    completedAt?: string;
    createdAt?: string;
    results?: ShotComposeResultResponse[];
  };

  type StoryboardBatchItemResponse = {
    id?: number;
    episodeId?: number;
    episodeNo?: number;
    executionId?: number;
    status?: string;
    warningCount?: number;
    businessCallCount?: number;
    technicalRetryCount?: number;
    settledPoints?: number;
    errorCode?: string;
    errorMessage?: string;
  };

  type storyboardBatchParams = {
    projectId: number;
    batchId: number;
  };

  type StoryboardBatchResponse = {
    id?: number;
    projectId?: number;
    name?: string;
    status?: string;
    total?: number;
    pending?: number;
    running?: number;
    succeeded?: number;
    warning?: number;
    failed?: number;
    businessCallCount?: number;
    technicalRetryCount?: number;
    settledPoints?: number;
    items?: StoryboardBatchItemResponse[];
    createdAt?: string;
  };

  type StoryboardBreakdownRequest = {
    episodeId: number;
  };

  type StoryboardResponse = {
    id?: number;
    shotNo?: number;
    storyboardNo?: number;
    episodeId?: number;
    episodeNo?: number;
    shotType?: string;
    visualDescription?: string;
    characters?: string;
    scene?: string;
    dialogue?: string;
    durationSeconds?: number;
    shotPlan?: JsonNode;
    promptDocument?: JsonNode;
    materialBindingStatus?: string;
    sourceFingerprint?: string;
    generatedByRunId?: number;
    imagePrompt?: string;
    videoPrompt?: string;
    firstFrameUrl?: string;
    currentVideoResultId?: number;
    currentVideoUrl?: string;
  };

  type StoryboardSubtitleResponse = {
    id?: number;
    storyboardId?: number;
    voiceResultId?: number;
    subtitleType?: string;
    textContent?: string;
    srtUrl?: string;
    styleConfig?: string;
    selected?: boolean;
    status?: string;
    createdAt?: string;
    segments?: SubtitleSegmentResponse[];
  };

  type StoryboardWorkspacePageResponse = {
    projectId?: number;
    episodes?: ScriptEpisodeSummaryResponse[];
    episodeNo?: number;
    current?: number;
    pageSize?: number;
    total?: number;
    storyboards?: StoryboardResponse[];
  };

  type storyboardWorkspaceParams = {
    projectId: number;
    episodeNo?: number;
    current?: number;
    pageSize?: number;
  };

  type StyleLibraryResponse = {
    id?: number;
    externalId?: string;
    name?: string;
    category?: string;
    description?: string;
    imageUrl?: string;
    storagePath?: string;
    imageWidth?: number;
    imageHeight?: number;
  };

  type subtitleParams = {
    projectId: number;
    subtitleId: number;
  };

  type SubtitleSegmentResponse = {
    text?: string;
    startTime?: number;
    endTime?: number;
  };

  type subtitlesParams = {
    projectId: number;
    storyboardId?: number;
    status?: string;
  };

  type taskParams = {
    taskId: number;
  };

  type tasksParams = {
    projectId: number;
  };

  type TeamPointAccountResponse = {
    tenantId?: number;
    balance?: number;
    totalGranted?: number;
    totalConsumed?: number;
    updatedAt?: string;
  };

  type TeamPointTransactionPageResponse = {
    records?: TeamPointTransactionResponse[];
    total?: number;
    current?: number;
    pageSize?: number;
  };

  type TeamPointTransactionResponse = {
    id?: number;
    tenantId?: number;
    userId?: number;
    transactionType?: string;
    changeAmount?: number;
    balanceAfter?: number;
    businessScene?: string;
    businessId?: number;
    description?: string;
    createdAt?: string;
  };

  type TeamSubscriptionEntity = {
    id?: number;
    tenantId?: number;
    packageVersionId?: number;
    sourceOrderId?: number;
    status?: string;
    startsAt?: string;
    endsAt?: string;
    nextGrantAt?: string;
    snapshotJson?: string;
    createdAt?: string;
    updatedAt?: string;
  };

  type TenantInvitationResponse = {
    id?: number;
    tenantId?: number;
    tenantName?: string;
    inviteMobile?: string;
    inviteUserId?: number;
    invitedBy?: number;
    token?: string;
    status?: string;
    expiredAt?: string;
    acceptedAt?: string;
    createdAt?: string;
  };

  type tenantInvitationsParams = {
    tenantId: number;
  };

  type TenantMemberResponse = {
    id?: number;
    tenantId?: number;
    userId?: number;
    mobile?: string;
    nickname?: string;
    avatar?: string;
    memberType?: string;
    status?: string;
    joinedAt?: string;
  };

  type TenantMembershipResponse = {
    id?: number;
    memberType?: string;
    status?: string;
  };

  type TenantSummaryResponse = {
    id?: number;
    code?: string;
    name?: string;
    type?: string;
    logo?: string;
    description?: string;
    status?: string;
    memberType?: string;
    memberId?: number;
  };

  type testProviderParams = {
    id: number;
  };

  type TestRunRequest = {
    code?: string;
    name: string;
    description?: string;
    systemPrompt: string;
    modelId: number;
    temperature: number;
    maxTokens: number;
    maxSteps: number;
    status: string;
    skillCodes?: string[];
    toolCodes?: string[];
    input: string;
    projectId?: number;
    episodeId?: number;
    scriptId?: number;
    taskId?: number;
  };

  type thumbnailParams = {
    id: number;
  };

  type thumbnailResultParams = {
    projectId: number;
    resultId: number;
  };

  type TimingUsageResponse = {
    queueMs?: number;
    preparationMs?: number;
    modelMs?: number;
    validationSaveMs?: number;
    totalMs?: number;
    firstByteMs?: number;
  };

  type transactionsParams = {
    tenantId: number;
    current?: number;
    pageSize?: number;
  };

  type transferOwnerParams = {
    tenantId: number;
  };

  type TransferOwnerRequest = {
    targetMemberId: number;
  };

  type unpublishParams = {
    packageId: number;
    versionId: number;
  };

  type update1Params = {
    id: number;
  };

  type update2Params = {
    id: number;
  };

  type update3Params = {
    code: string;
  };

  type update4Params = {
    code: string;
  };

  type UpdateAgentRequest = {
    name: string;
    description?: string;
    systemPrompt: string;
    modelId: number;
    temperature: number;
    maxTokens: number;
    maxSteps: number;
    status: string;
    skillCodes?: string[];
    toolCodes?: string[];
    expectedRevision: number;
  };

  type updateDraftParams = {
    episodeId: number;
  };

  type updateElementParams = {
    projectId: number;
    elementType: string;
    elementId: number;
  };

  type updateEpisodeSummaryParams = {
    projectId: number;
    episodeId: number;
  };

  type updateMemberRoleParams = {
    id: number;
    userId: number;
  };

  type updateMemberRolesParams = {
    tenantId: number;
    memberId: number;
  };

  type UpdateMemberRolesRequest = {
    roleIds?: number[];
  };

  type updateModelParametersParams = {
    id: number;
  };

  type updateModelParams = {
    id: number;
  };

  type updateOwnerParams = {
    id: number;
  };

  type updateParams = {
    id: number;
  };

  type UpdatePlatformTenantStatusRequest = {
    status: string;
  };

  type UpdateProjectMemberRoleRequest = {
    roleId: number;
  };

  type UpdateProjectOwnerRequest = {
    ownerId: number;
  };

  type UpdateProjectRequest = {
    name: string;
    description?: string;
    coverUrl?: string;
    coverSource?: string;
    startDate?: string;
    endDate?: string;
    aspectRatio?: string;
    fileFormat?: string;
    scriptType?: string;
    breakdownStrength?: string;
    visualStyle?: string;
    initialScriptContent?: string;
  };

  type UpdateProjectRolePermissionsRequest = {
    permissionCodes?: string[];
  };

  type UpdateProjectRoleRequest = {
    name: string;
    description?: string;
    status?: string;
    permissionCodes?: string[];
  };

  type UpdateProjectStatusRequest = {
    status: string;
  };

  type updateProviderParams = {
    id: number;
  };

  type UpdateReviewTaskRequest = {
    reviewMode?: string;
    selectedDimensions?: string[];
    reviewScopeType?: string;
    reviewScope?: Record<string, any>;
  };

  type updateRole1Params = {
    id: number;
    roleId: number;
  };

  type updateRoleParams = {
    tenantId: number;
    roleId: number;
  };

  type updateRolePermissions1Params = {
    id: number;
    roleId: number;
  };

  type updateRolePermissionsParams = {
    tenantId: number;
    roleId: number;
  };

  type UpdateRolePermissionsRequest = {
    permissionCodes?: string[];
  };

  type UpdateRoleRequest = {
    name: string;
    description?: string;
    permissionCodes?: string[];
  };

  type UpdateRoleStatusRequest = {
    status: string;
  };

  type UpdateScriptElementRequest = {
    name: string;
    roleType?: string;
    gender?: string;
    ageRange?: string;
    identity?: string;
    personality?: string[];
    appearance?: string;
    sceneType?: string;
    atmosphere?: string;
    description?: string;
    visualStyle?: string;
    propType?: string;
    plotFunction?: string;
    relatedCharacter?: string;
    prompt?: string;
    status?: string;
  };

  type UpdateSkillRequest = {
    content: string;
    expectedRevision: string;
  };

  type updateStatus1Params = {
    id: number;
  };

  type updateStatus2Params = {
    id: number;
  };

  type updateStatus3Params = {
    tenantId: number;
  };

  type updateStatusParams = {
    tenantId: number;
    roleId: number;
  };

  type updateStoryboardParams = {
    projectId: number;
    storyboardId: number;
  };

  type UpdateStoryboardSubtitleRequest = {
    textContent: string;
    startTime?: number;
    endTime?: number;
    styleConfig?: Record<string, any>;
  };

  type updateSubtitleParams = {
    projectId: number;
    subtitleId: number;
  };

  type updateTaskConfigParams = {
    taskId: number;
  };

  type UpdateTenantRequest = {
    name: string;
    type: string;
    logo?: string;
    description?: string;
  };

  type UpdateTenantStatusRequest = {
    status: string;
  };

  type UpdateVideoDecompositionDraftRequest = {
    draftContent: string;
    expectedDraftVersion?: number;
  };

  type updateVisualVariantParams = {
    projectId: number;
    variantId: number;
  };

  type UsageCostLineResponse = {
    id?: number;
    usageLineId?: number;
    priceVersionId?: number;
    priceComponentId?: number;
    metric?: string;
    quantity?: number;
    unitSize?: number;
    unitPrice?: number;
    currency?: string;
    rawCost?: number;
    roundedCost?: number;
    pricingStatus?: string;
    adjustmentOfCostLineId?: number;
  };

  type UsageLineResponse = {
    id?: number;
    attemptId?: number;
    aiCallLogId?: number;
    modelId?: number;
    metric?: string;
    quantity?: number;
    unit?: string;
    source?: string;
    observedAt?: string;
    adjustmentOfUsageLineId?: number;
  };

  type UserProfileResponse = {
    id?: number;
    mobile?: string;
    email?: string;
    nickname?: string;
    avatar?: string;
    status?: string;
  };

  type VariantCommand = {
    name?: string;
    appearance?: string;
    prompt?: string;
    sourceType?: string;
    generationStatus?: string;
    currentImageResultId?: number;
    currentImageUrl?: string;
    primary?: boolean;
  };

  type VariantResponse = {
    id?: number;
    assetType?: string;
    assetId?: number;
    name?: string;
    appearance?: string;
    prompt?: string;
    sourceType?: string;
    generationStatus?: string;
    generationTaskId?: number;
    currentImageResultId?: number;
    currentImageUrl?: string;
    currentImageThumbnailUrl?: string;
    errorCode?: string;
    errorMessage?: string;
    primary?: boolean;
    usable?: boolean;
  };

  type versionHistoryParams = {
    projectId: number;
    versionId: number;
  };

  type VideoDecompositionAttemptResponse = {
    id?: number;
    attemptNo?: number;
    phase?: string;
    status?: string;
    providerRequestId?: string;
    aiCallLogId?: number;
    idempotencyKey?: string;
    retryable?: boolean;
    errorCode?: string;
    errorMessage?: string;
    startedAt?: string;
    finishedAt?: string;
  };

  type VideoDecompositionBatchResponse = {
    id?: number;
    tenantId?: number;
    projectId?: number;
    name?: string;
    modelId?: number;
    status?: string;
    totalEpisodes?: number;
    completedEpisodes?: number;
    failedEpisodes?: number;
    succeededEpisodes?: number;
    processingEpisodes?: number;
    pendingEpisodes?: number;
    percentage?: number;
    createdAt?: string;
    updatedAt?: string;
    episodes?: VideoDecompositionEpisodeResponse[];
  };

  type VideoDecompositionBatchScreenplaysResponse = {
    batchId?: number;
    batchName?: string;
    status?: string;
    percentage?: number;
    totalEpisodes?: number;
    succeededEpisodes?: number;
    failedEpisodes?: number;
    processingEpisodes?: number;
    pendingEpisodes?: number;
    episodes?: VideoDecompositionScreenplayEpisodeResponse[];
  };

  type VideoDecompositionEpisodeDetailResponse = {
    episode?: VideoDecompositionEpisodeResponse;
    screenplayContent?: string;
    formatVersion?: string;
    draftContent?: string;
    currentScriptVersionId?: number;
    rawResponse?: string;
    normalizedJson?: string;
    attempts?: VideoDecompositionAttemptResponse[];
  };

  type VideoDecompositionEpisodeResponse = {
    id?: number;
    executionId?: number;
    batchId?: number;
    projectId?: number;
    episodeNo?: number;
    sourceFileName?: string;
    storagePath?: string;
    mimeType?: string;
    fileSize?: number;
    durationSeconds?: number;
    status?: string;
    analysisVersion?: number;
    draftStatus?: string;
    draftVersion?: number;
    confirmedScriptVersionId?: number;
    errorCode?: string;
    errorMessage?: string;
    executionPhase?: string;
    percentage?: number;
    retryable?: boolean;
    createdAt?: string;
    updatedAt?: string;
  };

  type VideoDecompositionScreenplayEpisodeResponse = {
    episode?: VideoDecompositionEpisodeResponse;
    screenplayContent?: string;
    formatVersion?: string;
  };

  type VideoDecompositionUploadResponse = {
    fileName?: string;
    storagePath?: string;
    mimeType?: string;
    fileSize?: number;
    durationSeconds?: number;
  };

  type VideoUploadMetadataRequest = {
    fileName: string;
    storagePath: string;
    mimeType?: string;
    fileSize: number;
    durationSeconds?: number;
  };

  type visualVariantBindingsParams = {
    projectId: number;
    elementType: string;
    elementId: number;
  };

  type visualVariantsParams = {
    projectId: number;
    elementType: string;
    elementId: number;
  };

  type voiceTaskParams = {
    projectId: number;
    taskId: number;
  };

  type voiceTaskResultsParams = {
    projectId: number;
    taskId: number;
  };

  type voiceTasksParams = {
    projectId: number;
    status?: string;
    storyboardId?: number;
  };

  type Warning = {
    code?: string;
    message?: string;
  };

  type WorkflowAgentModelCall = {
    callLogId?: number;
    modelId?: number;
    providerId?: number;
    providerRequestId?: string;
    transportOutcome?: string;
    businessOutcome?: string;
    attemptId?: number;
    promptTokens?: number;
    completionTokens?: number;
    cachedInputTokens?: number;
    cacheWriteTokens?: number;
  };

  type WorkflowAgentRecord = {
    id?: number;
    code?: string;
    name?: string;
    description?: string;
    systemPrompt?: string;
    modelId?: number;
    temperature?: number;
    maxTokens?: number;
    maxSteps?: number;
    status?: string;
    revision?: number;
    createdBy?: number;
    updatedBy?: number;
    createdAt?: string;
    updatedAt?: string;
    skillCodes?: string[];
    toolCodes?: string[];
  };

  type WorkflowAgentRunDetail = {
    id?: number;
    agentId?: number;
    agentCode?: string;
    runType?: string;
    tenantId?: number;
    userId?: number;
    projectId?: number;
    episodeId?: number;
    scriptId?: number;
    taskId?: number;
    analysisStageId?: number;
    status?: string;
    modelId?: number;
    temperature?: number;
    maxTokens?: number;
    maxSteps?: number;
    promptSnapshot?: string;
    skillSnapshots?: WorkflowAgentSkillSnapshot[];
    toolCodes?: string[];
    finalOutput?: string;
    errorCode?: string;
    errorMessage?: string;
    startedAt?: string;
    finishedAt?: string;
    steps?: WorkflowAgentRunStepView[];
  };

  type WorkflowAgentRunResult = {
    runId?: number;
    output?: string;
    modelCalls?: WorkflowAgentModelCall[];
  };

  type WorkflowAgentRunStepView = {
    stepNo?: number;
    stepType?: string;
    status?: string;
    aiCallLogId?: number;
    toolCode?: string;
    inputJson?: string;
    outputJson?: string;
    errorCode?: string;
    errorMessage?: string;
    startedAt?: string;
    finishedAt?: string;
  };

  type WorkflowAgentRunSummary = {
    id?: number;
    agentCode?: string;
    runType?: string;
    status?: string;
    projectId?: number;
    episodeId?: number;
    finalOutput?: string;
    errorCode?: string;
    errorMessage?: string;
    startedAt?: string;
    finishedAt?: string;
  };

  type WorkflowAgentSkillSnapshot = {
    code?: string;
    name?: string;
    revision?: string;
    content?: string;
  };

  type WorkflowSkillView = {
    code?: string;
    name?: string;
    description?: string;
    content?: string;
    revision?: string;
    referencingAgentCodes?: string[];
  };

  type WorkflowToolMetadata = {
    code?: string;
    name?: string;
    description?: string;
    inputSchema?: JsonNode;
    outputSchema?: JsonNode;
    riskLevel?: "READ_ONLY" | "WRITE";
    failurePolicy?: "TERMINAL" | "RETURN_TO_MODEL";
  };
}
