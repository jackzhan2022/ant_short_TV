package com.antshorttv.inspiration;

import com.antshorttv.common.ApiResponse;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inspiration-creations")
public class InspirationCreationController {
    private final InspirationCreationService inspirationCreationService;

    public InspirationCreationController(InspirationCreationService inspirationCreationService) {
        this.inspirationCreationService = inspirationCreationService;
    }

    @GetMapping
    public ApiResponse<List<InspirationCreationResponse>> list() {
        return ApiResponse.success(inspirationCreationService.list());
    }

    @GetMapping("/{externalId}")
    public ApiResponse<InspirationCreationDetailResponse> detail(@PathVariable String externalId) {
        return ApiResponse.success(inspirationCreationService.detail(externalId));
    }

    @GetMapping("/{externalId}/file")
    public ResponseEntity<Resource> file(@PathVariable String externalId) {
        InspirationCreationEntity inspiration = inspirationCreationService.findImported(externalId);
        return ResponseEntity.ok()
            .contentType(mediaType(inspiration.getMimeType()))
            .body(inspirationCreationService.media(externalId));
    }

    private MediaType mediaType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        return MediaType.parseMediaType(mimeType);
    }
}
