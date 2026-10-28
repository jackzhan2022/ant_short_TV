package com.antshorttv.downloadcenter;

import com.antshorttv.common.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/downloads")
public class DownloadCenterController {
    private final DownloadCenterService service;
    public DownloadCenterController(DownloadCenterService service){this.service=service;}
    @PostMapping public ApiResponse<DownloadTask> create(HttpServletRequest request,@Valid @RequestBody DownloadTask.Request body){return ApiResponse.success(service.create(TenantRequestSupport.tenantId(request),body));}
    @GetMapping public ApiResponse<DownloadTask.Page> list(HttpServletRequest request,@RequestParam(required=false) String status,
        @RequestParam(required=false) String keyword,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int pageSize){
        return ApiResponse.success(service.list(TenantRequestSupport.tenantId(request),status,keyword,page,pageSize));
    }
    @GetMapping("/{id}") public ApiResponse<DownloadTask> detail(HttpServletRequest request,@PathVariable String id){return ApiResponse.success(service.detail(TenantRequestSupport.tenantId(request),id));}
    @PostMapping("/{id}/retry") public ApiResponse<DownloadTask> retry(HttpServletRequest request,@PathVariable String id){return ApiResponse.success(service.retry(TenantRequestSupport.tenantId(request),id));}
    @GetMapping("/{id}/prepare") public ApiResponse<DownloadTask> prepare(HttpServletRequest request,@PathVariable String id){return ApiResponse.success(service.prepare(TenantRequestSupport.tenantId(request),id));}
    @GetMapping("/{id}/file") public ResponseEntity<Resource> download(@RequestParam Long tenantId,@PathVariable String id)throws IOException {
        var file=service.download(tenantId,id);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store")
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.fileName(),StandardCharsets.UTF_8).build().toString())
            .contentType(MediaType.parseMediaType("application/zip")).contentLength(file.size())
            .body(new InputStreamResource(file.resource().getInputStream()));
    }
}
