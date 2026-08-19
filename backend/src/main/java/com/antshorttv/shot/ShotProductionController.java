package com.antshorttv.shot;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.rbac.RequirePermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class ShotProductionController {
    private final ShotProductionService service;

    public ShotProductionController(ShotProductionService service) {
        this.service = service;
    }

    @GetMapping("/ai-voice-tasks")
    @RequirePermission("AI_VOICE_TASK:VIEW")
    public ApiResponse<List<AiVoiceTaskResponse>> voiceTasks(
        @PathVariable Long projectId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long storyboardId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.voiceTasks(tenantId(request), projectId, status, storyboardId));
    }

    @PostMapping("/ai-voice-tasks")
    @RequirePermission("AI_VOICE_TASK:CREATE")
    public ApiResponse<AiVoiceTaskResponse> createVoiceTask(
        @PathVariable Long projectId,
        @Valid @RequestBody CreateAiVoiceTaskRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.createVoiceTask(tenantId(request), projectId, body, request));
    }

    @PostMapping("/ai-voice-results/{resultId}/bind-storyboard")
    @RequirePermission("AI_VOICE_TASK:CREATE")
    public ApiResponse<AiVoiceResultResponse> bindVoiceResult(
        @PathVariable Long projectId,
        @PathVariable Long resultId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.bindVoiceResult(tenantId(request), projectId, resultId, request));
    }

    @GetMapping("/ai-voice-tasks/{taskId}")
    @RequirePermission("AI_VOICE_TASK:VIEW")
    public ApiResponse<AiVoiceTaskResponse> voiceTask(
        @PathVariable Long projectId,
        @PathVariable Long taskId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.voiceTask(tenantId(request), projectId, taskId));
    }

    @PostMapping("/ai-voice-tasks/{taskId}/cancel")
    @RequirePermission("AI_VOICE_TASK:CANCEL")
    public ApiResponse<AiVoiceTaskResponse> cancelVoiceTask(
        @PathVariable Long projectId,
        @PathVariable Long taskId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.cancelVoiceTask(tenantId(request), projectId, taskId, request));
    }

    @PostMapping("/ai-voice-tasks/{taskId}/regenerate")
    @RequirePermission("AI_VOICE_TASK:CREATE")
    public ApiResponse<AiVoiceTaskResponse> regenerateVoiceTask(
        @PathVariable Long projectId,
        @PathVariable Long taskId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.regenerateVoiceTask(tenantId(request), projectId, taskId, request));
    }

    @DeleteMapping("/ai-voice-tasks/{taskId}")
    @RequirePermission("AI_VOICE_TASK:DELETE")
    public ApiResponse<Void> deleteVoiceTask(
        @PathVariable Long projectId,
        @PathVariable Long taskId,
        HttpServletRequest request
    ) {
        service.deleteVoiceTask(tenantId(request), projectId, taskId, request);
        return ApiResponse.ok();
    }

    @GetMapping("/ai-voice-tasks/{taskId}/results")
    @RequirePermission("AI_VOICE_TASK:VIEW")
    public ApiResponse<List<AiVoiceResultResponse>> voiceTaskResults(
        @PathVariable Long projectId,
        @PathVariable Long taskId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.voiceTaskResults(tenantId(request), projectId, taskId));
    }

    @GetMapping("/ai-voice-results/{resultId}/download")
    @RequirePermission("AI_VOICE_RESULT:DOWNLOAD")
    public ApiResponse<AiVoiceResultResponse> downloadVoiceResult(
        @PathVariable Long projectId,
        @PathVariable Long resultId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.downloadVoiceResult(tenantId(request), projectId, resultId, request));
    }

    @PostMapping("/ai-voice-results/{resultId}/save-material")
    @RequirePermission("AI_VOICE_RESULT:SAVE")
    public ApiResponse<AiVoiceResultResponse> saveVoiceMaterial(
        @PathVariable Long projectId,
        @PathVariable Long resultId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.saveVoiceMaterial(tenantId(request), projectId, resultId, request));
    }

    @DeleteMapping("/ai-voice-results/{resultId}")
    @RequirePermission("AI_VOICE_RESULT:DELETE")
    public ApiResponse<Void> deleteVoiceResult(
        @PathVariable Long projectId,
        @PathVariable Long resultId,
        HttpServletRequest request
    ) {
        service.deleteVoiceResult(tenantId(request), projectId, resultId, request);
        return ApiResponse.ok();
    }

    @PostMapping("/storyboard-subtitles")
    @RequirePermission("SUBTITLE:EDIT")
    public ApiResponse<StoryboardSubtitleResponse> createSubtitle(
        @PathVariable Long projectId,
        @Valid @RequestBody CreateStoryboardSubtitleRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.createSubtitle(tenantId(request), projectId, body, request));
    }

    @GetMapping("/storyboard-subtitles")
    @RequirePermission("SUBTITLE:VIEW")
    public ApiResponse<List<StoryboardSubtitleResponse>> subtitles(
        @PathVariable Long projectId,
        @RequestParam(required = false) Long storyboardId,
        @RequestParam(required = false) String status,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.subtitles(tenantId(request), projectId, storyboardId, status));
    }

    @GetMapping("/storyboard-subtitles/{subtitleId}")
    @RequirePermission("SUBTITLE:VIEW")
    public ApiResponse<StoryboardSubtitleResponse> subtitle(
        @PathVariable Long projectId,
        @PathVariable Long subtitleId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.subtitle(tenantId(request), projectId, subtitleId));
    }

    @PutMapping("/storyboard-subtitles/{subtitleId}")
    @RequirePermission("SUBTITLE:EDIT")
    public ApiResponse<StoryboardSubtitleResponse> updateSubtitle(
        @PathVariable Long projectId,
        @PathVariable Long subtitleId,
        @Valid @RequestBody UpdateStoryboardSubtitleRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.updateSubtitle(tenantId(request), projectId, subtitleId, body, request));
    }

    @PutMapping("/storyboard-subtitles/{subtitleId}/selected")
    @RequirePermission("SUBTITLE:EDIT")
    public ApiResponse<StoryboardSubtitleResponse> selectSubtitle(
        @PathVariable Long projectId,
        @PathVariable Long subtitleId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.selectSubtitle(tenantId(request), projectId, subtitleId, request));
    }

    @DeleteMapping("/storyboard-subtitles/{subtitleId}")
    @RequirePermission("SUBTITLE:DELETE")
    public ApiResponse<Void> deleteSubtitle(
        @PathVariable Long projectId,
        @PathVariable Long subtitleId,
        HttpServletRequest request
    ) {
        service.deleteSubtitle(tenantId(request), projectId, subtitleId, request);
        return ApiResponse.ok();
    }

    @GetMapping("/shot-compose-tasks")
    @RequirePermission("SHOT_COMPOSE:VIEW")
    public ApiResponse<List<ShotComposeTaskResponse>> composeTasks(
        @PathVariable Long projectId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long storyboardId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.composeTasks(tenantId(request), projectId, status, storyboardId));
    }

    @GetMapping("/shot-compose-tasks/{taskId}")
    @RequirePermission("SHOT_COMPOSE:VIEW")
    public ApiResponse<ShotComposeTaskResponse> composeTask(
        @PathVariable Long projectId,
        @PathVariable Long taskId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.composeTask(tenantId(request), projectId, taskId));
    }

    @PostMapping("/shot-compose-tasks")
    @RequirePermission("SHOT_COMPOSE:CREATE")
    public ApiResponse<ShotComposeTaskResponse> createComposeTask(
        @PathVariable Long projectId,
        @Valid @RequestBody CreateShotComposeTaskRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.createComposeTask(tenantId(request), projectId, body, request));
    }

    @PostMapping("/shot-compose-tasks/{taskId}/cancel")
    @RequirePermission("SHOT_COMPOSE:CREATE")
    public ApiResponse<ShotComposeTaskResponse> cancelComposeTask(
        @PathVariable Long projectId,
        @PathVariable Long taskId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.cancelComposeTask(tenantId(request), projectId, taskId, request));
    }

    @PostMapping("/shot-compose-tasks/{taskId}/regenerate")
    @RequirePermission("SHOT_COMPOSE:CREATE")
    public ApiResponse<ShotComposeTaskResponse> regenerateComposeTask(
        @PathVariable Long projectId,
        @PathVariable Long taskId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.regenerateComposeTask(tenantId(request), projectId, taskId, request));
    }

    @DeleteMapping("/shot-compose-tasks/{taskId}")
    @RequirePermission("SHOT_COMPOSE:DELETE")
    public ApiResponse<Void> deleteComposeTask(
        @PathVariable Long projectId,
        @PathVariable Long taskId,
        HttpServletRequest request
    ) {
        service.deleteComposeTask(tenantId(request), projectId, taskId, request);
        return ApiResponse.ok();
    }

    @GetMapping("/shot-compose-tasks/{taskId}/results")
    @RequirePermission("SHOT_COMPOSE:VIEW")
    public ApiResponse<List<ShotComposeResultResponse>> composeTaskResults(
        @PathVariable Long projectId,
        @PathVariable Long taskId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.composeTaskResults(tenantId(request), projectId, taskId));
    }

    @GetMapping("/episode-compose-tasks")
    @RequirePermission("EPISODE_COMPOSE:VIEW")
    public ApiResponse<List<EpisodeComposeTaskResponse>> episodeComposeTasks(
        @PathVariable Long projectId,
        @RequestParam(required = false) Integer episodeNo,
        @RequestParam(required = false) String status,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.episodeComposeTasks(tenantId(request), projectId, episodeNo, status));
    }

    @GetMapping("/episode-compose-tasks/{taskId}")
    @RequirePermission("EPISODE_COMPOSE:VIEW")
    public ApiResponse<EpisodeComposeTaskResponse> episodeComposeTask(
        @PathVariable Long projectId,
        @PathVariable Long taskId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.episodeComposeTask(tenantId(request), projectId, taskId));
    }

    @PostMapping("/episode-compose-tasks")
    @RequirePermission("EPISODE_COMPOSE:CREATE")
    public ApiResponse<EpisodeComposeTaskResponse> createEpisodeComposeTask(
        @PathVariable Long projectId,
        @Valid @RequestBody CreateEpisodeComposeTaskRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.createEpisodeComposeTask(tenantId(request), projectId, body, request));
    }

    @PostMapping("/episode-compose-tasks/{taskId}/cancel")
    @RequirePermission("EPISODE_COMPOSE:CANCEL")
    public ApiResponse<EpisodeComposeTaskResponse> cancelEpisodeComposeTask(
        @PathVariable Long projectId,
        @PathVariable Long taskId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.cancelEpisodeComposeTask(tenantId(request), projectId, taskId, request));
    }

    @PostMapping("/episode-compose-tasks/{taskId}/regenerate")
    @RequirePermission("EPISODE_COMPOSE:CREATE")
    public ApiResponse<EpisodeComposeTaskResponse> regenerateEpisodeComposeTask(
        @PathVariable Long projectId,
        @PathVariable Long taskId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.regenerateEpisodeComposeTask(tenantId(request), projectId, taskId, request));
    }

    @DeleteMapping("/episode-compose-tasks/{taskId}")
    @RequirePermission("EPISODE_COMPOSE:DELETE")
    public ApiResponse<Void> deleteEpisodeComposeTask(
        @PathVariable Long projectId,
        @PathVariable Long taskId,
        HttpServletRequest request
    ) {
        service.deleteEpisodeComposeTask(tenantId(request), projectId, taskId, request);
        return ApiResponse.ok();
    }

    @GetMapping("/episode-video-versions")
    @RequirePermission("EPISODE_VERSION:VIEW")
    public ApiResponse<List<EpisodeVideoVersionResponse>> episodeVideoVersions(
        @PathVariable Long projectId,
        @RequestParam Integer episodeNo,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.episodeVideoVersions(tenantId(request), projectId, episodeNo));
    }

    @GetMapping("/episode-video-versions/{versionId}")
    @RequirePermission("EPISODE_VERSION:VIEW")
    public ApiResponse<EpisodeVideoVersionResponse> episodeVideoVersion(
        @PathVariable Long projectId,
        @PathVariable Long versionId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.episodeVideoVersion(tenantId(request), projectId, versionId));
    }

    @PutMapping("/episode-video-versions/{versionId}")
    @RequirePermission("EPISODE_VERSION:SET_CURRENT")
    public ApiResponse<EpisodeVideoVersionResponse> renameEpisodeVideoVersion(
        @PathVariable Long projectId,
        @PathVariable Long versionId,
        @Valid @RequestBody RenameEpisodeVideoVersionRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.renameEpisodeVideoVersion(tenantId(request), projectId, versionId, body, request));
    }

    @PostMapping("/episode-video-versions/{versionId}/current")
    @RequirePermission("EPISODE_VERSION:SET_CURRENT")
    public ApiResponse<EpisodeVideoVersionResponse> setCurrentEpisodeVideoVersion(
        @PathVariable Long projectId,
        @PathVariable Long versionId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.setCurrentEpisodeVideoVersion(tenantId(request), projectId, versionId, request));
    }

    @GetMapping("/episode-video-versions/{versionId}/download")
    @RequirePermission("EPISODE_VERSION:DOWNLOAD")
    public ResponseEntity<Resource> downloadEpisodeVideoVersion(
        @PathVariable Long projectId,
        @PathVariable Long versionId,
        HttpServletRequest request
    ) {
        EpisodeVideoDownloadResource download = service.downloadEpisodeVideoVersion(tenantId(request), projectId, versionId, request);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
            .contentType(MediaType.valueOf("video/mp4"))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(download.fileName())
                .build()
                .toString());
        if (download.fileSize() != null) {
            response.contentLength(download.fileSize());
        }
        return response.body(download.resource());
    }

    @GetMapping("/episode-video-versions/{versionId}/cover")
    @RequirePermission("EPISODE_VERSION:VIEW")
    public ResponseEntity<Resource> episodeVideoCover(
        @PathVariable Long projectId,
        @PathVariable Long versionId,
        HttpServletRequest request
    ) {
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .body(service.episodeVideoCover(tenantId(request), projectId, versionId));
    }

    @PostMapping("/episode-video-versions/{versionId}/save-material")
    @RequirePermission("EPISODE_VERSION:SAVE_MATERIAL")
    public ApiResponse<EpisodeVideoVersionResponse> saveEpisodeVideoMaterial(
        @PathVariable Long projectId,
        @PathVariable Long versionId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.saveEpisodeVideoMaterial(tenantId(request), projectId, versionId, request));
    }

    @DeleteMapping("/episode-video-versions/{versionId}")
    @RequirePermission("EPISODE_VERSION:DELETE")
    public ApiResponse<Void> deleteEpisodeVideoVersion(
        @PathVariable Long projectId,
        @PathVariable Long versionId,
        HttpServletRequest request
    ) {
        service.deleteEpisodeVideoVersion(tenantId(request), projectId, versionId, request);
        return ApiResponse.ok();
    }

    @GetMapping("/episode-export-records")
    @RequirePermission("EPISODE_VERSION:VIEW")
    public ApiResponse<List<EpisodeExportRecordResponse>> episodeExportRecords(
        @PathVariable Long projectId,
        @RequestParam(required = false) Integer episodeNo,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.episodeExportRecords(tenantId(request), projectId, episodeNo));
    }

    @GetMapping("/shot-compose-results/{resultId}/download")
    @RequirePermission("SHOT_COMPOSE:DOWNLOAD")
    public ApiResponse<ShotComposeResultResponse> downloadComposeResult(
        @PathVariable Long projectId,
        @PathVariable Long resultId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.downloadComposeResult(tenantId(request), projectId, resultId, request));
    }

    @PostMapping("/shot-compose-results/{resultId}/save-material")
    @RequirePermission("SHOT_COMPOSE:SAVE")
    public ApiResponse<ShotComposeResultResponse> saveComposeMaterial(
        @PathVariable Long projectId,
        @PathVariable Long resultId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.saveComposeMaterial(tenantId(request), projectId, resultId, request));
    }

    @PostMapping("/shot-compose-results/{resultId}/bind-storyboard")
    @RequirePermission("SHOT_COMPOSE:BIND")
    public ApiResponse<ShotComposeResultResponse> bindComposeResult(
        @PathVariable Long projectId,
        @PathVariable Long resultId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.bindComposeResult(tenantId(request), projectId, resultId, request));
    }

    @DeleteMapping("/shot-compose-results/{resultId}")
    @RequirePermission("SHOT_COMPOSE:DELETE")
    public ApiResponse<Void> deleteComposeResult(
        @PathVariable Long projectId,
        @PathVariable Long resultId,
        HttpServletRequest request
    ) {
        service.deleteComposeResult(tenantId(request), projectId, resultId, request);
        return ApiResponse.ok();
    }

    private Long tenantId(HttpServletRequest request) {
        return Long.valueOf(request.getHeader("X-Tenant-Id"));
    }
}
