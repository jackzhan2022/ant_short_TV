package com.antshorttv.ai;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.platform.RequirePlatformPermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/ai")
public class PlatformAiManagementController {
    private final PlatformAiManagementService service;

    public PlatformAiManagementController(PlatformAiManagementService service) {
        this.service = service;
    }

    @GetMapping("/providers")
    @RequirePlatformPermission("PLATFORM_AI_PROVIDER_VIEW")
    public ApiResponse<List<PlatformProviderResponse>> providers(HttpServletRequest request) {
        return ApiResponse.success(service.providers());
    }

    @PostMapping("/providers")
    @RequirePlatformPermission("PLATFORM_AI_PROVIDER_CREATE")
    public ApiResponse<PlatformProviderResponse> createProvider(@Valid @RequestBody PlatformProviderRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.createProvider(body, request));
    }

    @PutMapping("/providers/{id}")
    @RequirePlatformPermission("PLATFORM_AI_PROVIDER_EDIT")
    public ApiResponse<PlatformProviderResponse> updateProvider(@PathVariable Long id, @Valid @RequestBody PlatformProviderRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.updateProvider(id, body, request));
    }

    @PostMapping("/providers/{id}/enable")
    @RequirePlatformPermission("PLATFORM_AI_PROVIDER_ENABLE")
    public ApiResponse<PlatformProviderResponse> enableProvider(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.success(service.updateProviderStatus(id, true, request));
    }

    @PostMapping("/providers/{id}/disable")
    @RequirePlatformPermission("PLATFORM_AI_PROVIDER_ENABLE")
    public ApiResponse<PlatformProviderResponse> disableProvider(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.success(service.updateProviderStatus(id, false, request));
    }

    @PostMapping("/providers/{id}/test")
    @RequirePlatformPermission("PLATFORM_AI_PROVIDER_TEST")
    public ApiResponse<AiServiceTestResponse> testProvider(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.success(service.testProvider(id, request));
    }

    @GetMapping("/models")
    @RequirePlatformPermission("PLATFORM_AI_MODEL_VIEW")
    public ApiResponse<List<PlatformModelResponse>> models(HttpServletRequest request) {
        return ApiResponse.success(service.models());
    }

    @PostMapping("/models")
    @RequirePlatformPermission("PLATFORM_AI_MODEL_CREATE")
    public ApiResponse<PlatformModelResponse> createModel(@Valid @RequestBody PlatformModelRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.createModel(body, request));
    }

    @PutMapping("/models/{id}")
    @RequirePlatformPermission("PLATFORM_AI_MODEL_EDIT")
    public ApiResponse<PlatformModelResponse> updateModel(@PathVariable Long id, @Valid @RequestBody PlatformModelRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.updateModel(id, body, request));
    }

    @PostMapping("/models/{id}/enable")
    @RequirePlatformPermission("PLATFORM_AI_MODEL_ENABLE")
    public ApiResponse<PlatformModelResponse> enableModel(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.success(service.updateModelStatus(id, true, request));
    }

    @PostMapping("/models/{id}/disable")
    @RequirePlatformPermission("PLATFORM_AI_MODEL_ENABLE")
    public ApiResponse<PlatformModelResponse> disableModel(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.success(service.updateModelStatus(id, false, request));
    }

    @PostMapping("/models/{id}/default")
    @RequirePlatformPermission("PLATFORM_AI_MODEL_EDIT")
    public ApiResponse<PlatformModelResponse> defaultModel(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.success(service.setDefault(id, request));
    }
}
