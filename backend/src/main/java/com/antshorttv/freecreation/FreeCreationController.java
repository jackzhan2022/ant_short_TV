package com.antshorttv.freecreation;

import com.antshorttv.common.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@com.antshorttv.rbac.RequirePermission("MENU:FREE_CREATION")
@RestController
@RequestMapping("/api/free-creations")
public class FreeCreationController {
    private final FreeCreationService service;
    public FreeCreationController(FreeCreationService service) {this.service=service;}
    @GetMapping("/models") public ApiResponse<FreeCreationService.ModelCatalog> models(HttpServletRequest request) {
        return ApiResponse.success(service.models(TenantRequestSupport.tenantId(request)));
    }
    @GetMapping public ApiResponse<Map<String,Object>> list(HttpServletRequest request,
        @RequestParam(defaultValue="mine") String scope,@RequestParam(required=false) String mediaType,
        @RequestParam(required=false) String status,@RequestParam(required=false) String keyword,
        @RequestParam(defaultValue="1") int current,@RequestParam(defaultValue="20") int pageSize) {
        return ApiResponse.success(service.list(TenantRequestSupport.tenantId(request),scope,mediaType,status,keyword,current,pageSize));
    }
    @PostMapping public ApiResponse<Map<String,Object>> create(HttpServletRequest request,@Valid @RequestBody FreeCreationRequest body) {
        return ApiResponse.success(service.create(TenantRequestSupport.tenantId(request),body));
    }
    @GetMapping("/{id}") public ApiResponse<Map<String,Object>> detail(HttpServletRequest request,@PathVariable Long id) {
        return ApiResponse.success(service.detail(TenantRequestSupport.tenantId(request),id));
    }
    @GetMapping("/{id}/outputs/{outputId}/download") public ApiResponse<Map<String,Object>> download(HttpServletRequest request,@PathVariable Long id,@PathVariable Long outputId) {
        return ApiResponse.success(service.download(TenantRequestSupport.tenantId(request),id,outputId));
    }
    @GetMapping("/{id}/outputs/{outputId}/download-file")
    public ResponseEntity<Resource> downloadFile(HttpServletRequest request,@PathVariable Long id,@PathVariable Long outputId) throws IOException {
        var file=service.downloadFile(TenantRequestSupport.tenantId(request),id,outputId);
        var response=ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
            .header(HttpHeaders.CACHE_CONTROL,"no-store")
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment()
                .filename(file.fileName(),StandardCharsets.UTF_8).build().toString());
        if(file.fileSize()!=null && file.fileSize()>0)response.contentLength(file.fileSize());
        return response.body(new InputStreamResource(file.resource().getInputStream()));
    }
}
