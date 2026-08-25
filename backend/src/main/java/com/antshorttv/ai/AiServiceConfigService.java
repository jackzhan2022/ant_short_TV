package com.antshorttv.ai;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.operationlog.OperationLogService;
import com.antshorttv.operationlog.OperationResult;
import com.antshorttv.security.CurrentPrincipal;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiServiceConfigService {

    private static final List<String> SERVICE_TYPES = List.of("TEXT", "IMAGE", "VIDEO", "VOICE");
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(5);

    private final AiProviderMapper aiProviderMapper;
    private final AiServiceConfigMapper aiServiceConfigMapper;
    private final AiServiceTestLogMapper aiServiceTestLogMapper;
    private final HttpClient httpClient;
    private final AiSecretCodec aiSecretCodec;
    private final TenantContextResolver tenantContextResolver;
    private final OperationLogService operationLogService;
    private final PlatformAiManagementService platformAiManagementService;
    private final CurrentPrincipal currentPrincipal;

    public AiServiceConfigService(
        AiProviderMapper aiProviderMapper,
        AiServiceConfigMapper aiServiceConfigMapper,
        AiServiceTestLogMapper aiServiceTestLogMapper,
        AiSecretCodec aiSecretCodec,
        TenantContextResolver tenantContextResolver,
        OperationLogService operationLogService,
        PlatformAiManagementService platformAiManagementService,
        CurrentPrincipal currentPrincipal
    ) {
        this.aiProviderMapper = aiProviderMapper;
        this.aiServiceConfigMapper = aiServiceConfigMapper;
        this.aiServiceTestLogMapper = aiServiceTestLogMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TEST_TIMEOUT).build();
        this.aiSecretCodec = aiSecretCodec;
        this.tenantContextResolver = tenantContextResolver;
        this.operationLogService = operationLogService;
        this.platformAiManagementService = platformAiManagementService;
        this.currentPrincipal = currentPrincipal;
    }

    public List<AiProviderResponse> providers() {
        currentPrincipal.require();
        return aiProviderMapper.selectList(
                new LambdaQueryWrapper<AiProviderEntity>()
                    .orderByAsc(AiProviderEntity::getId)
            )
            .stream()
            .map(AiProviderResponse::from)
            .toList();
    }

    public List<AiServiceConfigResponse> list(Long tenantId) {
        tenantContextResolver.requireActiveMember(tenantId);
        return aiServiceConfigMapper.selectList(new LambdaQueryWrapper<AiServiceConfigEntity>()
                .isNull(AiServiceConfigEntity::getDeletedAt)
                .orderByDesc(AiServiceConfigEntity::getPriority)
                .orderByDesc(AiServiceConfigEntity::getId))
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public AiServiceConfigResponse create(Long tenantId, AiServiceConfigRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        validateRequest(request, true);
        LocalDateTime now = LocalDateTime.now();

        AiServiceConfigEntity entity = new AiServiceConfigEntity();
        entity.setTenantId(tenantId);
        fill(entity, request, now);
        entity.setApiKeyCipher(aiSecretCodec.encrypt(request.apiKey().trim()));
        entity.setLastTestStatus("UNTESTED");
        entity.setCreatedBy(context.userId());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            clearDefault(tenantId, entity.getServiceType(), null);
        }
        aiServiceConfigMapper.insert(entity);
        platformAiManagementService.syncLegacyConfig(entity);
        operationLogService.record(context.userId(), tenantId, "CREATE_AI_SERVICE_CONFIG", entity.getId(), OperationResult.SUCCESS, servletRequest);
        return toResponse(entity);
    }

    @Transactional
    public AiServiceConfigResponse update(
        Long tenantId,
        Long id,
        AiServiceConfigRequest request,
        HttpServletRequest servletRequest
    ) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        validateRequest(request, false);
        AiServiceConfigEntity entity = requireConfig(tenantId, id);
        LocalDateTime now = LocalDateTime.now();
        fill(entity, request, now);
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            entity.setApiKeyCipher(aiSecretCodec.encrypt(request.apiKey().trim()));
        }
        entity.setLastTestStatus("UNTESTED");
        entity.setLastTestMessage(null);
        entity.setLastTestAt(null);
        entity.setUpdatedAt(now);

        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            clearDefault(tenantId, entity.getServiceType(), entity.getId());
        }
        aiServiceConfigMapper.updateById(entity);
        platformAiManagementService.syncLegacyConfig(entity);
        operationLogService.record(context.userId(), tenantId, "UPDATE_AI_SERVICE_CONFIG", entity.getId(), OperationResult.SUCCESS, servletRequest);
        return toResponse(entity);
    }

    @Transactional
    public AiServiceConfigResponse updateStatus(
        Long tenantId,
        Long id,
        AiServiceStatusRequest request,
        HttpServletRequest servletRequest
    ) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        AiServiceConfigEntity entity = requireConfig(tenantId, id);
        entity.setEnabled(request.enabled());
        entity.setUpdatedAt(LocalDateTime.now());
        aiServiceConfigMapper.updateById(entity);
        platformAiManagementService.syncLegacyConfig(entity);
        operationLogService.record(context.userId(), tenantId, "UPDATE_AI_SERVICE_CONFIG_STATUS", entity.getId(), OperationResult.SUCCESS, servletRequest);
        return toResponse(entity);
    }

    @Transactional
    public AiServiceConfigResponse setDefault(Long tenantId, Long id, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        AiServiceConfigEntity entity = requireConfig(tenantId, id);
        clearDefault(tenantId, entity.getServiceType(), entity.getId());
        entity.setIsDefault(true);
        entity.setUpdatedAt(LocalDateTime.now());
        aiServiceConfigMapper.updateById(entity);
        platformAiManagementService.syncLegacyConfig(entity);
        operationLogService.record(context.userId(), tenantId, "SET_DEFAULT_AI_SERVICE_CONFIG", entity.getId(), OperationResult.SUCCESS, servletRequest);
        return toResponse(entity);
    }

    @Transactional
    public AiServiceTestResponse test(Long tenantId, Long id, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        AiServiceConfigEntity entity = requireConfig(tenantId, id);
        LocalDateTime startedAt = LocalDateTime.now();
        AiServiceTestResponse response = testConnection(entity);
        LocalDateTime finishedAt = LocalDateTime.now();

        entity.setLastTestStatus(response.status());
        entity.setLastTestMessage(response.message());
        entity.setLastTestAt(finishedAt);
        entity.setUpdatedAt(finishedAt);
        aiServiceConfigMapper.updateById(entity);

        AiServiceTestLogEntity log = new AiServiceTestLogEntity();
        log.setTenantId(tenantId);
        log.setServiceConfigId(id);
        log.setProvider(entity.getProvider());
        log.setServiceType(entity.getServiceType());
        log.setModel(entity.getModel());
        log.setTestStatus(response.status());
        log.setMessage(response.message());
        log.setDurationMs(Duration.between(startedAt, finishedAt).toMillis());
        log.setCreatedBy(context.userId());
        log.setCreatedAt(finishedAt);
        aiServiceTestLogMapper.insert(log);
        operationLogService.record(context.userId(), tenantId, "TEST_AI_SERVICE_CONFIG", entity.getId(), OperationResult.SUCCESS, servletRequest);
        return response;
    }

    @Transactional
    public void delete(Long tenantId, Long id, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        AiServiceConfigEntity entity = requireConfig(tenantId, id);
        entity.setDeletedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        aiServiceConfigMapper.updateById(entity);
        operationLogService.record(context.userId(), tenantId, "DELETE_AI_SERVICE_CONFIG", entity.getId(), OperationResult.SUCCESS, servletRequest);
    }

    private void fill(AiServiceConfigEntity entity, AiServiceConfigRequest request, LocalDateTime now) {
        entity.setName(trim(request.name()));
        entity.setProvider(trim(request.provider()));
        entity.setServiceType(trim(request.serviceType()).toUpperCase());
        entity.setBaseUrl(trim(request.baseUrl()));
        entity.setModel(trim(request.model()));
        entity.setEndpoint(blankToNull(request.endpoint()));
        entity.setQueryEndpoint(blankToNull(request.queryEndpoint()));
        entity.setPriority(request.priority());
        entity.setIsDefault(Boolean.TRUE.equals(request.isDefault()));
        entity.setEnabled(request.enabled() == null || Boolean.TRUE.equals(request.enabled()));
        entity.setRemark(blankToNull(request.remark()));
        entity.setUpdatedAt(now);
    }

    private void validateRequest(AiServiceConfigRequest request, boolean requireApiKey) {
        String serviceType = trim(request.serviceType()).toUpperCase();
        if (!SERVICE_TYPES.contains(serviceType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "AI 服务类型不正确。");
        }
        if (requireApiKey && (request.apiKey() == null || request.apiKey().isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请输入 API Key。");
        }
        AiProviderEntity provider = aiProviderMapper.selectOne(
            new LambdaQueryWrapper<AiProviderEntity>()
                .eq(AiProviderEntity::getCode, trim(request.provider()))
                .eq(AiProviderEntity::getStatus, "ENABLED")
        );
        if (provider == null) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_NOT_FOUND, "AI 服务商不可用。");
        }
        if (!List.of(provider.getSupportedTypes().split(",")).contains(serviceType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前服务商不支持该服务类型。");
        }
    }

    private AiServiceConfigEntity requireConfig(Long tenantId, Long id) {
        AiServiceConfigEntity entity = aiServiceConfigMapper.selectOne(
            new LambdaQueryWrapper<AiServiceConfigEntity>()
                .eq(AiServiceConfigEntity::getId, id)
                .isNull(AiServiceConfigEntity::getDeletedAt)
        );
        if (entity == null) {
            throw new BusinessException(ErrorCode.AI_SERVICE_CONFIG_NOT_FOUND, "AI 服务配置不存在。");
        }
        return entity;
    }

    private void clearDefault(Long tenantId, String serviceType, Long exceptId) {
        List<AiServiceConfigEntity> defaults = aiServiceConfigMapper.selectList(
            new LambdaQueryWrapper<AiServiceConfigEntity>()
                .eq(AiServiceConfigEntity::getServiceType, serviceType)
                .eq(AiServiceConfigEntity::getIsDefault, true)
                .isNull(AiServiceConfigEntity::getDeletedAt)
        );
        for (AiServiceConfigEntity item : defaults) {
            if (exceptId != null && exceptId.equals(item.getId())) {
                continue;
            }
            item.setIsDefault(false);
            item.setUpdatedAt(LocalDateTime.now());
            aiServiceConfigMapper.updateById(item);
        }
    }

    private AiServiceTestResponse testConnection(AiServiceConfigEntity entity) {
        if (entity.getBaseUrl() == null || entity.getBaseUrl().isBlank()
            || entity.getModel() == null || entity.getModel().isBlank()) {
            return new AiServiceTestResponse("FAILED", "请完善接口地址和模型。");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(testUri(entity))
                .timeout(TEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + aiSecretCodec.decrypt(entity.getApiKeyCipher()))
                .POST(HttpRequest.BodyPublishers.ofString(testPayload(entity)))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new AiServiceTestResponse("SUCCESS", "服务可用。");
            }
            return new AiServiceTestResponse("FAILED", "服务测试失败，HTTP %d。".formatted(response.statusCode()));
        } catch (Exception exception) {
            return new AiServiceTestResponse("FAILED", "服务测试失败：" + exception.getMessage());
        }
    }

    private URI testUri(AiServiceConfigEntity entity) {
        String endpoint = entity.getEndpoint() == null || entity.getEndpoint().isBlank()
            ? defaultEndpoint(entity.getProvider())
            : entity.getEndpoint();
        String baseUrl = entity.getBaseUrl().endsWith("/")
            ? entity.getBaseUrl().substring(0, entity.getBaseUrl().length() - 1)
            : entity.getBaseUrl();
        String normalizedEndpoint = endpoint.startsWith("/") ? endpoint : "/" + endpoint;
        return URI.create(baseUrl + normalizedEndpoint);
    }

    private String defaultEndpoint(String provider) {
        if ("Gemini".equals(provider)) {
            return "/v1beta/models/gemini-2.5-flash:generateContent";
        }
        return "/chat/completions";
    }

    private String testPayload(AiServiceConfigEntity entity) {
        if ("Gemini".equals(entity.getProvider())) {
            return """
                {"contents":[{"parts":[{"text":"ping"}]}]}
                """;
        }
        return """
            {"model":"%s","messages":[{"role":"user","content":"ping"}],"max_tokens":1}
            """.formatted(entity.getModel());
    }

    private AiServiceConfigResponse toResponse(AiServiceConfigEntity entity) {
        return AiServiceConfigResponse.from(entity, aiSecretCodec.mask(entity.getApiKeyCipher()));
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
