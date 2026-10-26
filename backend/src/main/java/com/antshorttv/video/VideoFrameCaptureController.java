package com.antshorttv.video;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.TenantRequestSupport;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.storage.ObjectStorageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/ai-video-results/{resultId}")
public class VideoFrameCaptureController {
    private final VideoFrameCaptureService frames;
    private final ObjectStorageService storage;
    private final ProjectPermissionGuard permissions;

    public VideoFrameCaptureController(VideoFrameCaptureService frames,ObjectStorageService storage,
        ProjectPermissionGuard permissions) {
        this.frames=frames; this.storage=storage; this.permissions=permissions;
    }

    @GetMapping("/frame-source")
    public ResponseEntity<Resource> source(@PathVariable Long projectId,@PathVariable Long resultId,
        @RequestHeader(value=HttpHeaders.RANGE,required=false) String range,HttpServletRequest request) {
        Long tenantId=frames.sourceTenantId(projectId,resultId);
        permissions.require(tenantId,projectId,"AI_VIDEO_TASK:VIEW");
        var source=frames.source(tenantId,projectId,resultId);
        var metadata=storage.metadata(source.storagePath());
        long length=metadata.size();
        var headers=new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT_RANGES,"bytes");
        headers.setCacheControl("private, no-store");
        headers.setContentType("video/webm".equals(metadata.contentType()) ? MediaType.parseMediaType("video/webm")
            : MediaType.parseMediaType("video/mp4"));
        if(range!=null) {
            long start,end;
            try {
                var ranges=HttpRange.parseRanges(range);
                if(ranges.size()!=1 || length<=0) throw new IllegalArgumentException("Unsupported range");
                start=ranges.get(0).getRangeStart(length);
                end=ranges.get(0).getRangeEnd(length);
                if(start>=length || end<start) throw new IllegalArgumentException("Unsatisfiable range");
            } catch(IllegalArgumentException exception) {
                headers.set(HttpHeaders.CONTENT_RANGE,"bytes */"+length);
                return new ResponseEntity<>(null,headers,HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
            }
            headers.setContentLength(end-start+1);
            headers.set(HttpHeaders.CONTENT_RANGE,"bytes "+start+"-"+end+"/"+length);
            // InputStreamResource deliberately bypasses Spring's automatic Resource slicing. The COS
            // request fetches only these bytes, avoiding an in-memory buffer or a full-video download.
            return new ResponseEntity<>(storage.rangeResource(source.storagePath(),start,end),headers,HttpStatus.PARTIAL_CONTENT);
        }
        headers.setContentLength(length);
        return new ResponseEntity<>(storage.resource(source.storagePath()),headers,HttpStatus.OK);
    }

    @PostMapping("/frames")
    public ApiResponse<VideoFrameCaptureService.FrameResponse> save(@PathVariable Long projectId,
        @PathVariable Long resultId,@Valid @RequestBody FrameRequest body,HttpServletRequest request) {
        Long tenantId=TenantRequestSupport.tenantId(request);
        var context=permissions.require(tenantId,projectId,"STORYBOARD:EDIT");
        permissions.require(tenantId,projectId,"AI_VIDEO_TASK:VIEW");
        permissions.require(tenantId,projectId,"AI_IMAGE_TASK:VIEW");
        return ApiResponse.success(frames.save(tenantId,projectId,resultId,context.userId(),body.sessionToken(),body.timeSeconds()));
    }
    public record FrameRequest(@NotBlank @Size(max=64) String sessionToken,@NotNull Double timeSeconds) {}
}
