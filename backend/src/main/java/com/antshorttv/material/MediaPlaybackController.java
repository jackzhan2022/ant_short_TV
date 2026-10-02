package com.antshorttv.material;

import java.net.URI;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class MediaPlaybackController {
    private final MediaPlaybackService service;

    public MediaPlaybackController(MediaPlaybackService service) {
        this.service = service;
    }

    @GetMapping("/ai-video-results/{resultId}/playback")
    public ResponseEntity<Void> aiVideo(@PathVariable Long projectId, @PathVariable Long resultId) {
        return redirect(projectId, resultId, MediaPlaybackService.ResourceKind.AI_VIDEO_RESULT);
    }

    @GetMapping("/shot-compose-results/{resultId}/playback")
    public ResponseEntity<Void> shot(@PathVariable Long projectId, @PathVariable Long resultId) {
        return redirect(projectId, resultId, MediaPlaybackService.ResourceKind.SHOT_COMPOSE_RESULT);
    }

    @GetMapping("/episode-video-versions/{versionId}/playback")
    public ResponseEntity<Void> episode(@PathVariable Long projectId, @PathVariable Long versionId) {
        return redirect(projectId, versionId, MediaPlaybackService.ResourceKind.EPISODE_VIDEO_VERSION);
    }

    @GetMapping("/ai-video-results/{resultId}/download-file")
    public ResponseEntity<Void> videoDownload(@PathVariable Long projectId, @PathVariable Long resultId) {
        var grant = service.download(projectId, resultId);
        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("ai-video-result-" + resultId + ".mp4").build().toString())
            .location(URI.create(grant.url())).build();
    }

    private ResponseEntity<Void> redirect(Long projectId, Long resourceId, MediaPlaybackService.ResourceKind kind) {
        var grant = service.issue(projectId, resourceId, kind);
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.CACHE_CONTROL, "no-store")
            .location(URI.create(grant.url())).build();
    }
}
