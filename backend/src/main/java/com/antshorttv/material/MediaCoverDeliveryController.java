package com.antshorttv.material;

import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class MediaCoverDeliveryController {
    private final MediaCoverDeliveryService service;

    public MediaCoverDeliveryController(MediaCoverDeliveryService service) { this.service = service; }

    @GetMapping("/ai-video-results/{resultId}/cover")
    public ResponseEntity<Void> aiVideo(@PathVariable Long projectId, @PathVariable Long resultId) {
        return cover(projectId, resultId, MediaPlaybackService.ResourceKind.AI_VIDEO_RESULT);
    }

    @GetMapping("/shot-compose-results/{resultId}/cover")
    public ResponseEntity<Void> shot(@PathVariable Long projectId, @PathVariable Long resultId) {
        return cover(projectId, resultId, MediaPlaybackService.ResourceKind.SHOT_COMPOSE_RESULT);
    }

    @GetMapping("/episode-video-versions/{versionId}/cover")
    public ResponseEntity<Void> episode(@PathVariable Long projectId, @PathVariable Long versionId) {
        return cover(projectId, versionId, MediaPlaybackService.ResourceKind.EPISODE_VIDEO_VERSION);
    }

    @GetMapping("/storyboards/{storyboardId}/first-frame")
    public ResponseEntity<Void> storyboardFirstFrame(
        @PathVariable Long projectId, @PathVariable Long storyboardId
    ) {
        var grant = service.storyboardFirstFrame(projectId, storyboardId);
        return grant == null ? ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").build()
            : ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .location(URI.create(grant.url())).build();
    }

    private ResponseEntity<Void> cover(Long projectId, Long id, MediaPlaybackService.ResourceKind kind) {
        var grant = service.delivery(projectId, id, kind);
        return grant == null ? ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").build()
            : ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .location(URI.create(grant.url())).build();
    }
}
