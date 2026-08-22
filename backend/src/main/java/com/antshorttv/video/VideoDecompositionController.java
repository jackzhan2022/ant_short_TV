package com.antshorttv.video;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.TenantRequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/video-script-decomposition")
public class VideoDecompositionController {
    private final VideoDecompositionService service;

    public VideoDecompositionController(VideoDecompositionService service) {
        this.service = service;
    }

    @GetMapping("/batches")
    public ApiResponse<List<VideoDecompositionBatchResponse>> list(
        @RequestParam(required = false) Long projectId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.list(tenantId(request), projectId));
    }

    @PostMapping("/batches")
    public ApiResponse<VideoDecompositionBatchResponse> create(
        @Valid @RequestBody CreateVideoDecompositionBatchRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.create(tenantId(request), body, request));
    }

    @PostMapping("/uploads")
    public ApiResponse<VideoDecompositionUploadResponse> upload(
        @RequestParam Long projectId,
        @RequestParam("file") MultipartFile file,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.upload(tenantId(request), projectId, file));
    }

    @GetMapping("/batches/{batchId}")
    public ApiResponse<VideoDecompositionBatchResponse> detail(
        @PathVariable Long batchId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.detail(tenantId(request), batchId));
    }

    @GetMapping("/episodes/{episodeId}")
    public ApiResponse<VideoDecompositionEpisodeDetailResponse> episode(
        @PathVariable Long episodeId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.episodeDetail(tenantId(request), episodeId));
    }

    @PostMapping("/episodes/{episodeId}/retry")
    public ApiResponse<VideoDecompositionEpisodeResponse> retry(
        @PathVariable Long episodeId,
        @RequestBody(required = false) RetryVideoDecompositionEpisodeRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.retry(tenantId(request), episodeId, body));
    }

    @PutMapping("/episodes/{episodeId}/draft")
    public ApiResponse<VideoDecompositionEpisodeResponse> updateDraft(
        @PathVariable Long episodeId,
        @Valid @RequestBody UpdateVideoDecompositionDraftRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.updateDraft(tenantId(request), episodeId, body));
    }

    @PostMapping("/episodes/{episodeId}/confirm")
    public ApiResponse<VideoDecompositionEpisodeResponse> confirm(
        @PathVariable Long episodeId,
        @Valid @RequestBody ConfirmVideoDecompositionDraftRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.success(service.confirmDraft(tenantId(request), episodeId, body));
    }

    private Long tenantId(HttpServletRequest request) {
        return TenantRequestSupport.tenantId(request);
    }
}
