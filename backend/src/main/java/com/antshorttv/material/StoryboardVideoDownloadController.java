package com.antshorttv.material;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class StoryboardVideoDownloadController {
    private final StoryboardVideoDownloadService service;
    public StoryboardVideoDownloadController(StoryboardVideoDownloadService service) { this.service = service; }

    @GetMapping("/storyboards/{storyboardId}/download-video")
    public ResponseEntity<Resource> storyboard(@PathVariable Long projectId, @PathVariable Long storyboardId) throws IOException {
        return attachment(service.downloadStoryboard(projectId, storyboardId), "video/mp4");
    }

    @GetMapping("/episodes/{episodeNo}/download-videos")
    public ResponseEntity<Resource> episode(@PathVariable Long projectId, @PathVariable int episodeNo) throws IOException {
        return attachment(service.downloadEpisode(projectId, episodeNo), "application/zip");
    }

    private ResponseEntity<Resource> attachment(StoryboardVideoDownloadService.VideoDownload download, String contentType) throws IOException {
        var response = ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8).build().toString());
        if (download.fileSize() != null && download.fileSize() > 0) response.contentLength(download.fileSize());
        // Generated ZIPs are single-use streams, not resumable ResourceRegion downloads.
        // This also ensures empty Range requests reach the stream-close cleanup hook.
        Resource body = contentType.equals("application/zip")
            ? new InputStreamResource(download.resource().getInputStream()) : download.resource();
        return response.body(body);
    }
}
