declare namespace API {
  type acceptParams = {
    token: string;
  };

  type accountParams = {
    tenantId: number;
  };

  type addMemberParams = {
    id: number;
  };

  type AddProjectMemberRequest = {
    userId: number;
    roleId?: number;
  };

  type adjustParams = {
    tenantId: number;
  };

  type agentParams = {
    code: string;
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
    serviceConfigId?: number;
    taskId?: number;
    modelId?: number;
    providerId?: number;
    serviceConfigName?: string;
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
    serviceConfigId?: number;
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

  type AiProviderResponse = {
    id?: number;
    name?: string;
    code?: string;
    supportedTypes?: string;
    defaultBaseUrl?: string;
    recommendedModels?: string;
    description?: string;
    status?: string;
  };

  type AiServiceConfigRequest = {
    name: string;
    serviceType: string;
    provider: string;
    baseUrl: string;
    apiKey?: string;
    model: string;
    endpoint?: string;
    queryEndpoint?: string;
    priority: number;
    isDefault?: boolean;
    enabled?: boolean;
    remark?: string;
  };

  type AiServiceConfigResponse = {
    id?: number;
    tenantId?: number;
    name?: string;
    provider?: string;
    serviceType?: string;
    baseUrl?: string;
    apiKey?: string;
    model?: string;
    endpoint?: string;
    queryEndpoint?: string;
    priority?: number;
    isDefault?: boolean;
    enabled?: boolean;
    lastTestStatus?: string;
    lastTestMessage?: string;
    lastTestAt?: string;
    remark?: string;
    updatedAt?: string;
  };

  type AiServiceStatusRequest = {
    enabled: boolean;
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
    serviceConfigId?: number;
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
    serviceConfigId?: number;
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

  type ApiResponseAiServiceConfigResponse = {
    success?: boolean;
    data?: AiServiceConfigResponse;
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

  type ApiResponseBuiltInAgentPreviewResponse = {
    success?: boolean;
    data?: BuiltInAgentPreviewResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseBuiltInAgentResponse = {
    success?: boolean;
    data?: BuiltInAgentResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseBuiltInSkillResponse = {
    success?: boolean;
    data?: BuiltInSkillResponse;
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

  type ApiResponseListAiImageTaskResponse = {
    success?: boolean;
    data?: AiImageTaskResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListAiProviderResponse = {
    success?: boolean;
    data?: AiProviderResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListAiServiceConfigResponse = {
    success?: boolean;
    data?: AiServiceConfigResponse[];
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

  type ApiResponseListBuiltInAgentResponse = {
    success?: boolean;
    data?: BuiltInAgentResponse[];
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseListBuiltInSkillResponse = {
    success?: boolean;
    data?: BuiltInSkillResponse[];
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

  type ApiResponseListInspirationCreationListResponse = {
    success?: boolean;
    data?: InspirationCreationListResponse[];
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

  type ApiResponseListVideoDecompositionBatchResponse = {
    success?: boolean;
    data?: VideoDecompositionBatchResponse[];
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

  type ApiResponseReviewIssueResponse = {
    success?: boolean;
    data?: ReviewIssueResponse;
    errorCode?: string;
    errorMessage?: string;
  };

  type ApiResponseReviewProjectDetailResponse = {
    success?: boolean;
    data?: ReviewProjectDetailResponse;
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

  type ApiResponseScriptWorkspaceResponse = {
    success?: boolean;
    data?: ScriptWorkspaceResponse;
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

  type ApiResponseStoryboardSubtitleResponse = {
    success?: boolean;
    data?: StoryboardSubtitleResponse;
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

  type ApiResponseVideoDecompositionBatchResponse = {
    success?: boolean;
    data?: VideoDecompositionBatchResponse;
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

  type applyVersionParams = {
    projectId: number;
    versionId: number;
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

  type batchRepairParams = {
    taskId: number;
  };

  type BatchRepairReviewRequest = {
    actionType: string;
    replacementFrom?: string;
    replacementTo?: string;
    insertionText?: string;
    deletionText?: string;
    selectedHitIds?: number[];
  };

  type bindComposeResultParams = {
    projectId: number;
    resultId: number;
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

  type BuiltInAgentPreviewRequest = {
    variables?: Record<string, any>;
  };

  type BuiltInAgentPreviewResponse = {
    agentCode?: string;
    prompt?: string;
    outputSchema?: string;
  };

  type BuiltInAgentResponse = {
    code?: string;
    name?: string;
    description?: string;
    businessScene?: string;
    businessSceneName?: string;
    capability?: string;
    modelRouting?: string;
    variables?: BuiltInAgentVariableResponse[];
    outputSchema?: string;
    skills?: BuiltInSkillSummaryResponse[];
  };

  type BuiltInAgentSummaryResponse = {
    code?: string;
    name?: string;
    businessScene?: string;
  };

  type BuiltInAgentVariableResponse = {
    name?: string;
    label?: string;
    type?: string;
    required?: boolean;
    description?: string;
  };

  type BuiltInSkillResponse = {
    code?: string;
    name?: string;
    description?: string;
    category?: string;
    content?: string;
    agents?: BuiltInAgentSummaryResponse[];
  };

  type BuiltInSkillSummaryResponse = {
    code?: string;
    name?: string;
    category?: string;
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

  type CharacterAssetResponse = {
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

  type confirmElementParams = {
    projectId: number;
    elementType: string;
    elementId: number;
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

  type CreateAiImageTaskRequest = {
    taskType: string;
    targetType: string;
    targetId: number;
    serviceConfigId?: number;
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
    serviceConfigId?: number;
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
    serviceConfigId?: number;
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

  type createVoiceTaskParams = {
    projectId: number;
  };

  type currentAnalysisParams = {
    projectId: number;
  };

  type defaultModelParams = {
    id: number;
  };

  type delete1Params = {
    projectId: number;
    taskId: number;
  };

  type delete2Params = {
    id: number;
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

  type deleteGlobalParams = {
    id: number;
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
    tenantId: number;
    id: number;
  };

  type deleteVoiceResultParams = {
    projectId: number;
    resultId: number;
  };

  type deleteVoiceTaskParams = {
    projectId: number;
    taskId: number;
  };

  type detail1Params = {
    tenantId: number;
    roleId: number;
  };

  type detail2Params = {
    tenantId: number;
    executionId: number;
  };

  type detail3Params = {
    id: number;
  };

  type detail4Params = {
    projectId: number;
    taskId: number;
  };

  type detail5Params = {
    projectId: number;
    taskId: number;
  };

  type detail6Params = {
    id: number;
  };

  type detail7Params = {
    token: string;
  };

  type detail8Params = {
    id: number;
  };

  type detailParams = {
    batchId: number;
  };

  type disableModelParams = {
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

  type enableModelParams = {
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

  type episodeParams = {
    episodeId: number;
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
  };

  type exportUsingPOSTParams = {
    projectId: number;
  };

  type extractElementsParams = {
    projectId: number;
  };

  type ExtractScriptElementsRequest = {
    elementType: string;
  };

  type fileParams = {
    id: number;
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

  type imageParams = {
    externalId: string;
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
    mimeType?: string;
    fileSize?: number;
    sortOrder?: number;
    sourceCreatedAt?: string;
  };

  type JsonNode = true;

  type leaveParams = {
    tenantId: number;
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

  type MarkReviewIssueResolvedRequest = {
    note?: string;
  };

  type memberRolesParams = {
    tenantId: number;
    memberId: number;
  };

  type membersParams = {
    id: number;
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

  type pollParams = {
    projectId: number;
    taskId: number;
  };

  type previewParams = {
    code: string;
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

  type PropAssetResponse = {
    id?: number;
    name?: string;
    propType?: string;
    appearance?: string;
    plotFunction?: string;
    prompt?: string;
    status?: string;
    mergeTargetId?: number;
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

  type regenerateEpisodeComposeTaskParams = {
    projectId: number;
    taskId: number;
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

  type resolveIssueParams = {
    issueId: number;
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
  };

  type RetryVideoDecompositionEpisodeRequest = {
    phase?: string;
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

  type ReviewIssueHitResponse = {
    id?: number;
    issueId?: number;
    hitNo?: number;
    episodeNo?: number;
    sceneNo?: string;
    shotNo?: number;
    lineNo?: number;
    anchorLabel?: string;
    excerpt?: string;
    entityName?: string;
    selected?: boolean;
    replacementText?: string;
  };

  type ReviewIssueMappingResponse = {
    issueId?: number;
    issueNo?: string;
    roundNo?: number;
    status?: string;
    relatedIssueNo?: string;
    dimension?: string;
    title?: string;
    hitCount?: number;
    hitIds?: number[];
  };

  type ReviewIssueResponse = {
    id?: number;
    taskId?: number;
    scriptVersionId?: number;
    roundNo?: number;
    issueNo?: string;
    dimension?: string;
    severity?: string;
    title?: string;
    position?: Record<string, any>;
    excerpt?: string;
    problem?: string;
    evidence?: string[];
    suggestion?: string;
    status?: string;
    relatedIssueNo?: string;
    manuallyResolved?: boolean;
    manuallyResolvedAt?: string;
    manuallyResolvedBy?: number;
    hits?: ReviewIssueHitResponse[];
  };

  type ReviewProjectDetailResponse = {
    project?: ReviewProjectSummaryResponse;
    versions?: ReviewVersionResponse[];
    tasks?: ReviewTaskResponse[];
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
    createdAt?: string;
    updatedAt?: string;
  };

  type ReviewReviewSummaryResponse = {
    overallConclusion?: string;
    overallScore?: number;
    summary?: string;
  };

  type ReviewRoundHistoryResponse = {
    taskId?: number;
    roundNo?: number;
    status?: string;
    reviewMode?: string;
    issueCount?: number;
    processedIssueCount?: number;
    summary?: ReviewReviewSummaryResponse;
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
    status?: string;
    currentStage?: string;
    overallProgress?: number;
    currentAction?: string;
    errorCode?: string;
    errorMessage?: string;
    completedAt?: string;
    canceledAt?: string;
    summary?: ReviewReviewSummaryResponse;
    issues?: ReviewIssueResponse[];
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
    issueMappings?: ReviewIssueMappingResponse[];
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
    status?: string;
  };

  type saveVersionParams = {
    projectId: number;
  };

  type saveVoiceMaterialParams = {
    projectId: number;
    resultId: number;
  };

  type SceneAssetResponse = {
    id?: number;
    name?: string;
    sceneType?: string;
    atmosphere?: string;
    description?: string;
    visualStyle?: string;
    prompt?: string;
    status?: string;
    mergeTargetId?: number;
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
    resultJson?: string;
    providerRequestId?: string;
    aiCallLogId?: number;
    durationMs?: number;
    resultErrorCode?: string;
    resultErrorMessage?: string;
    resultRetryable?: boolean;
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
  };

  type ScriptEpisodeResponse = {
    episodeNo?: number;
    title?: string;
    content?: string;
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

  type ScriptWorkspaceResponse = {
    projectId?: number;
    script?: ScriptResponse;
    versions?: ScriptVersionResponse[];
    characters?: CharacterAssetResponse[];
    scenes?: SceneAssetResponse[];
    props?: PropAssetResponse[];
    storyboards?: StoryboardResponse[];
    episodes?: ScriptEpisodeResponse[];
    analysis?: ScriptAnalysisTaskResponse;
  };

  type SelectedTenantResponse = {
    tenant?: TenantSummaryResponse;
    membership?: TenantMembershipResponse;
    roles?: string[];
    permissions?: string[];
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

  type setDefaultGlobalParams = {
    id: number;
  };

  type setDefaultParams = {
    tenantId: number;
    id: number;
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

  type skillParams = {
    code: string;
  };

  type StoryboardBreakdownRequest = {
    scope?: string;
    episodeNo?: number;
    selectedText?: string;
  };

  type StoryboardResponse = {
    id?: number;
    shotNo?: number;
    episodeNo?: number;
    shotType?: string;
    visualDescription?: string;
    characters?: string;
    scene?: string;
    dialogue?: string;
    durationSeconds?: number;
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

  type TeamPointAdjustmentRequest = {
    amount: number;
    description?: string;
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

  type testGlobalParams = {
    id: number;
  };

  type testParams = {
    tenantId: number;
    id: number;
  };

  type testProviderParams = {
    id: number;
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

  type update1Params = {
    id: number;
  };

  type update2Params = {
    id: number;
  };

  type updateDraftParams = {
    episodeId: number;
  };

  type updateElementParams = {
    projectId: number;
    elementType: string;
    elementId: number;
  };

  type updateGlobalParams = {
    id: number;
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

  type updateModelParams = {
    id: number;
  };

  type updateOwnerParams = {
    id: number;
  };

  type updateParams = {
    tenantId: number;
    id: number;
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

  type updateStatus1Params = {
    tenantId: number;
    id: number;
  };

  type updateStatus2Params = {
    id: number;
  };

  type updateStatus3Params = {
    id: number;
  };

  type updateStatusGlobalParams = {
    id: number;
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

  type UserProfileResponse = {
    id?: number;
    mobile?: string;
    email?: string;
    nickname?: string;
    avatar?: string;
    status?: string;
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
    createdAt?: string;
    updatedAt?: string;
    episodes?: VideoDecompositionEpisodeResponse[];
  };

  type VideoDecompositionEpisodeDetailResponse = {
    episode?: VideoDecompositionEpisodeResponse;
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
    retryable?: boolean;
    createdAt?: string;
    updatedAt?: string;
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

  type workspaceParams = {
    projectId: number;
  };
}
