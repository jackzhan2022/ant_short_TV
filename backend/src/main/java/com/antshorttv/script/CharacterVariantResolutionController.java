package com.antshorttv.script;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.TenantRequestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/character-prompt-similarity/{checkId}/characters/{characterId}/variant-resolution")
public class CharacterVariantResolutionController {
    private final CharacterVariantResolutionService service;
    public CharacterVariantResolutionController(CharacterVariantResolutionService service) { this.service=service; }

    @GetMapping
    public ApiResponse<JsonNode> preview(@PathVariable long projectId,@PathVariable long checkId,
        @PathVariable long characterId,@RequestParam long leftVariantId,@RequestParam long rightVariantId,HttpServletRequest request) {
        return ApiResponse.success(service.preview(TenantRequestSupport.tenantId(request),projectId,checkId,characterId,leftVariantId,rightVariantId));
    }

    @PostMapping
    public ApiResponse<JsonNode> resolve(@PathVariable long projectId,@PathVariable long checkId,
        @PathVariable long characterId,@RequestBody CharacterVariantResolutionService.Resolution body,HttpServletRequest request) {
        return ApiResponse.success(service.resolve(TenantRequestSupport.tenantId(request),projectId,checkId,characterId,body));
    }
}
