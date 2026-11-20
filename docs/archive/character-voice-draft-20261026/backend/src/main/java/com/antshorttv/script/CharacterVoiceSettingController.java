package com.antshorttv.script;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.TenantRequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/character-voice-settings")
public class CharacterVoiceSettingController {
    private final CharacterVoiceSettingService service;
    public CharacterVoiceSettingController(CharacterVoiceSettingService service) { this.service = service; }
    @GetMapping
    public ApiResponse<List<CharacterVoiceSettingService.Setting>> list(@PathVariable Long projectId,
        @RequestParam(required=false) List<Long> storyboardIds, HttpServletRequest request) {
        return ApiResponse.success(service.list(TenantRequestSupport.tenantId(request),projectId,storyboardIds == null ? List.of() : storyboardIds));
    }
    @PutMapping
    public ApiResponse<CharacterVoiceSettingService.Setting> save(@PathVariable Long projectId,
        @Valid @RequestBody CharacterVoiceSettingService.Command command, HttpServletRequest request) {
        return ApiResponse.success(service.save(TenantRequestSupport.tenantId(request),projectId,command));
    }
    @DeleteMapping
    public ApiResponse<Void> delete(@PathVariable Long projectId, @RequestParam Long characterId,
        @RequestParam(defaultValue="0") long storyboardId, @RequestParam(defaultValue="0") long variantId, HttpServletRequest request) {
        service.delete(TenantRequestSupport.tenantId(request),projectId,characterId,storyboardId,variantId);
        return ApiResponse.ok();
    }
}
