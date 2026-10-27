package com.antshorttv.aiimage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/ai-image-results")
public class AiImageDownloadController {
    private final AiImageDownloadService service;
    public AiImageDownloadController(AiImageDownloadService service) { this.service = service; }

    @GetMapping("/{resultId}/download-file")
    public ResponseEntity<Resource> original(@PathVariable Long projectId, @PathVariable Long resultId) throws IOException {
        return attachment(service.original(projectId, resultId));
    }

    @PostMapping("/download-archive")
    public ResponseEntity<Resource> archive(@PathVariable Long projectId, @Valid @RequestBody ArchiveRequest request) throws IOException {
        return attachment(service.archive(projectId, request.resultIds()));
    }

    private ResponseEntity<Resource> attachment(AiImageDownloadService.ImageDownload download) throws IOException {
        var response = ResponseEntity.ok().contentType(MediaType.parseMediaType(download.contentType()))
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8).build().toString());
        if (download.fileSize() != null && download.fileSize() > 0) response.contentLength(download.fileSize());
        // Use a single-use stream so Range handling cannot bypass temporary ZIP cleanup.
        return response.body(new InputStreamResource(download.resource().getInputStream()));
    }

    public record ArchiveRequest(@NotEmpty @Size(max = 100) List<@NotNull @Positive Long> resultIds) {}
}
