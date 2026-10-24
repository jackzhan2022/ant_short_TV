package com.antshorttv.material;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.TenantRequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class ProjectAudioController {
    private final ProjectAudioService service;
    public ProjectAudioController(ProjectAudioService service) { this.service = service; }

    @PostMapping("/audio-assets")
    public ApiResponse<ProjectAudioService.AudioReference> upload(@PathVariable Long projectId,
        @Valid @RequestBody Upload body, HttpServletRequest request) {
        return ApiResponse.success(service.upload(TenantRequestSupport.tenantId(request), projectId, body.sessionToken(), body.name().trim()));
    }
    @GetMapping("/characters/{characterId}/voice-reference")
    public ApiResponse<ProjectAudioService.AudioReference> current(@PathVariable Long projectId,
        @PathVariable Long characterId, HttpServletRequest request) {
        return ApiResponse.success(service.current(TenantRequestSupport.tenantId(request), projectId, characterId));
    }
    @PutMapping("/characters/{characterId}/voice-reference")
    public ApiResponse<ProjectAudioService.AudioReference> bind(@PathVariable Long projectId, @PathVariable Long characterId,
        @Valid @RequestBody Binding body, HttpServletRequest request) {
        return ApiResponse.success(service.bind(TenantRequestSupport.tenantId(request), projectId, characterId,
            body.sourceProjectId(), body.resourceType(), body.resourceId()));
    }
    @GetMapping("/audio-assets/{id}/playback")
    public ResponseEntity<Void> uploaded(@PathVariable Long projectId, @PathVariable Long id, HttpServletRequest request) {
        return playback(projectId, id, "PROJECT_AUDIO_ASSET", request);
    }
    @GetMapping("/ai-voice-results/{id}/playback")
    public ResponseEntity<Void> generated(@PathVariable Long projectId, @PathVariable Long id, HttpServletRequest request) {
        return playback(projectId, id, "AI_VOICE_RESULT", request);
    }
    private ResponseEntity<Void> playback(Long projectId, Long id, String type, HttpServletRequest request) {
        var grant = service.playback(projectId, type, id);
        return ResponseEntity.status(302).header("Cache-Control", "no-store").location(URI.create(grant.url())).build();
    }
    public record Upload(@NotBlank String sessionToken, @NotBlank @Size(max=255) String name) {}
    public record Binding(@NotNull @Positive Long sourceProjectId,
        @NotBlank @Pattern(regexp="PROJECT_AUDIO_ASSET|AI_VOICE_RESULT") String resourceType, @NotNull @Positive Long resourceId) {}
}
