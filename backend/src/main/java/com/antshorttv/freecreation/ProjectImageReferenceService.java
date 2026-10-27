package com.antshorttv.freecreation;

import com.antshorttv.aiimage.AiImageResultMapper;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.security.TenantContext;
import com.antshorttv.storage.ObjectStorageKeyFactory;
import java.util.Locale;
import org.springframework.stereotype.Service;

/** Resolves project image identities after checking current project access. */
@Service
public class ProjectImageReferenceService {
    private final AiImageResultMapper images;
    private final ProjectPermissionGuard permissions;
    private final ObjectStorageKeyFactory keys;

    public ProjectImageReferenceService(AiImageResultMapper images, ProjectPermissionGuard permissions,
        ObjectStorageKeyFactory keys) {
        this.images=images; this.permissions=permissions; this.keys=keys;
    }

    public FreeCreationMediaEntity resolve(TenantContext context, FreeCreationRequest.ProjectImage source) {
        permissions.require(context.tenantId(),source.projectId(),"AI_IMAGE_TASK:VIEW");
        var image=images.selectById(source.resultId());
        if (image==null || !context.tenantId().equals(image.getTenantId())
            || !source.projectId().equals(image.getProjectId()) || !"ACTIVE".equals(image.getStatus()))
            throw unavailable();
        String objectKey;
        try {
            String path=image.getStoragePath();
            objectKey=keys.objectKey(path!=null && path.startsWith("/")?path.substring(1):path);
        } catch (IllegalArgumentException failure) { throw unavailable(); }
        if (!objectKey.startsWith("materials/"+context.tenantId()+"/"+source.projectId()+"/")
            || image.getMimeType()==null || !image.getMimeType().startsWith("image/")
            || image.getFileSize()==null || image.getFileSize()<=0) throw unavailable();
        var result=new FreeCreationMediaEntity();
        result.tenantId=context.tenantId(); result.kind="IMAGE"; result.objectKey=objectKey;
        result.thumbnailKey=objectKey; result.mimeType=image.getMimeType();
        result.format=image.getMimeType().substring("image/".length()).toLowerCase(Locale.ROOT);
        if ("jpg".equals(result.format)) result.format="jpeg";
        result.fileName="项目图片_"+source.resultId()+"."+result.format;
        result.fileSize=image.getFileSize(); result.width=image.getWidth(); result.height=image.getHeight();
        return result;
    }

    private BusinessException unavailable() {
        return new BusinessException(ErrorCode.NOT_FOUND,"项目参考图片不存在、无权访问或原文件不可用。");
    }
}
