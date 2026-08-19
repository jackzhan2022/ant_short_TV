package com.antshorttv.material;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.project.ProjectEntity;
import com.antshorttv.project.ProjectMapper;
import com.antshorttv.project.ProjectMemberEntity;
import com.antshorttv.project.ProjectMemberMapper;
import com.antshorttv.rbac.RbacPermissionService;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MaterialFileController {
    private final MaterialFileAccessService accessService;
    private final TenantContextResolver tenantContextResolver;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final RbacPermissionService rbacPermissionService;

    public MaterialFileController(
        MaterialFileAccessService accessService,
        TenantContextResolver tenantContextResolver,
        ProjectMapper projectMapper,
        ProjectMemberMapper projectMemberMapper,
        RbacPermissionService rbacPermissionService
    ) {
        this.accessService = accessService;
        this.tenantContextResolver = tenantContextResolver;
        this.projectMapper = projectMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.rbacPermissionService = rbacPermissionService;
    }

    @GetMapping("/materials/{tenantId}/{projectId}/**")
    public ResponseEntity<Resource> read(
        @PathVariable Long tenantId,
        @PathVariable Long projectId,
        @RequestParam(required = false) String token,
        HttpServletRequest request
    ) {
        String storagePath = storagePath(request);
        if (!accessService.isValidToken(storagePath, token)) {
            requireProjectAccess(tenantId, projectId);
        }
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .contentType(MediaType.parseMediaType(accessService.contentType(storagePath)))
            .body(accessService.resource(storagePath));
    }

    private void requireProjectAccess(Long tenantId, Long projectId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ProjectEntity project = projectMapper.selectByTenantIdAndId(context.tenantId(), projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "项目不存在。");
        }
        if (rbacPermissionService.hasPermission(context, "PROJECT:VIEW")) {
            return;
        }
        ProjectMemberEntity member = projectMemberMapper.selectActiveByProjectIdAndUserId(context.tenantId(), projectId, context.userId());
        if (member == null) {
            throw new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED, "无权访问该项目。");
        }
    }

    private String storagePath(HttpServletRequest request) {
        String uri = URLDecoder.decode(request.getRequestURI(), StandardCharsets.UTF_8);
        int start = uri.indexOf("/materials/");
        if (start < 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "素材文件不存在。");
        }
        return uri.substring(start);
    }
}
