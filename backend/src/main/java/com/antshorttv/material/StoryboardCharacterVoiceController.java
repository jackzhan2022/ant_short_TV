package com.antshorttv.material;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.TenantRequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/storyboards/{storyboardId}/characters/{characterId}/voice-reference")
public class StoryboardCharacterVoiceController {
    private final StoryboardCharacterVoiceService service;
    public StoryboardCharacterVoiceController(StoryboardCharacterVoiceService service) { this.service = service; }

    @GetMapping
    public ApiResponse<StoryboardCharacterVoiceService.EffectiveVoice> current(@PathVariable Long projectId,
        @PathVariable Long storyboardId, @PathVariable Long characterId, @RequestParam(required=false) Long variantId, HttpServletRequest request) {
        return ApiResponse.success(service.current(TenantRequestSupport.tenantId(request), projectId, storyboardId, characterId, variantId));
    }
    @PutMapping
    public ApiResponse<StoryboardCharacterVoiceService.EffectiveVoice> bind(@PathVariable Long projectId,
        @PathVariable Long storyboardId, @PathVariable Long characterId, @RequestParam(required=false) Long variantId,
        @Valid @RequestBody ProjectAudioController.Binding body, HttpServletRequest request) {
        return ApiResponse.success(service.bind(TenantRequestSupport.tenantId(request), projectId, storyboardId, characterId, variantId,
            body.sourceProjectId(), body.resourceType(), body.resourceId()));
    }
    @DeleteMapping
    public ApiResponse<StoryboardCharacterVoiceService.EffectiveVoice> reset(@PathVariable Long projectId,
        @PathVariable Long storyboardId, @PathVariable Long characterId, @RequestParam(required=false) Long variantId, HttpServletRequest request) {
        return ApiResponse.success(service.reset(TenantRequestSupport.tenantId(request), projectId, storyboardId, characterId, variantId));
    }
}
