package com.antshorttv.project;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.member.TenantMemberMapper;
import com.antshorttv.operationlog.OperationLogService;
import com.antshorttv.operationlog.OperationResult;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {
    private final OrganizationMapper organizationMapper;
    private final OrganizationMemberMapper organizationMemberMapper;
    private final ProjectMapper projectMapper;
    private final TenantMemberMapper tenantMemberMapper;
    private final TenantContextResolver tenantContextResolver;
    private final OperationLogService operationLogService;

    public OrganizationService(
        OrganizationMapper organizationMapper,
        OrganizationMemberMapper organizationMemberMapper,
        ProjectMapper projectMapper,
        TenantMemberMapper tenantMemberMapper,
        TenantContextResolver tenantContextResolver,
        OperationLogService operationLogService
    ) {
        this.organizationMapper = organizationMapper;
        this.organizationMemberMapper = organizationMemberMapper;
        this.projectMapper = projectMapper;
        this.tenantMemberMapper = tenantMemberMapper;
        this.tenantContextResolver = tenantContextResolver;
        this.operationLogService = operationLogService;
    }

    public List<OrganizationResponse> tree(Long tenantId) {
        tenantContextResolver.requireActiveMember(tenantId);
        List<OrganizationEntity> organizations = organizationMapper.selectByTenantId(tenantId);
        return childrenOf(organizations, null);
    }

    @Transactional
    public OrganizationResponse create(Long tenantId, CreateOrganizationRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        LocalDateTime now = LocalDateTime.now();
        OrganizationEntity parent = null;
        int level = 1;
        if (request.parentId() != null) {
            parent = requireOrganization(tenantId, request.parentId());
            level = parent.level + 1;
        }
        if (level > 5) {
            throw new BusinessException(ErrorCode.ORGANIZATION_LEVEL_EXCEEDED, "组织最多支持5级。");
        }
        if (organizationMapper.selectByTenantIdAndCode(tenantId, normalizeCode(request.code())) != null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "组织编码已存在。");
        }
        validateLeader(tenantId, request.leaderId());
        OrganizationEntity entity = new OrganizationEntity();
        entity.tenantId = tenantId;
        entity.parentId = parent == null ? null : parent.id;
        entity.name = validateName(request.name());
        entity.code = normalizeCode(request.code());
        entity.level = level;
        entity.leaderId = request.leaderId();
        entity.sort = request.sort() == null ? 0 : request.sort();
        entity.status = OrganizationStatus.ACTIVE.name();
        entity.createdBy = context.userId();
        entity.createdAt = now;
        entity.updatedAt = now;
        organizationMapper.insert(entity);
        operationLogService.record(context.userId(), tenantId, "CREATE_ORGANIZATION", entity.id, OperationResult.SUCCESS, servletRequest);
        return OrganizationResponse.from(entity, List.of());
    }

    public OrganizationResponse detail(Long tenantId, Long id) {
        tenantContextResolver.requireActiveMember(tenantId);
        return OrganizationResponse.from(requireOrganization(tenantId, id), List.of());
    }

    @Transactional
    public OrganizationResponse update(Long tenantId, Long id, UpdateOrganizationRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        OrganizationEntity entity = requireOrganization(tenantId, id);
        OrganizationEntity parent = null;
        int level = 1;
        if (request.parentId() != null) {
            if (request.parentId().equals(id)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不能将组织移动到自身下级。");
            }
            parent = requireOrganization(tenantId, request.parentId());
            ensureNotDescendant(tenantId, id, parent);
            level = parent.level + 1;
        }
        int subtreeDepth = subtreeDepth(organizationMapper.selectByTenantId(tenantId), id);
        if (level + subtreeDepth - 1 > 5) {
            throw new BusinessException(ErrorCode.ORGANIZATION_LEVEL_EXCEEDED, "组织最多支持5级。");
        }
        validateLeader(tenantId, request.leaderId());
        entity.parentId = parent == null ? null : parent.id;
        entity.level = level;
        entity.name = validateName(request.name());
        entity.leaderId = request.leaderId();
        entity.sort = request.sort() == null ? entity.sort : request.sort();
        entity.updatedAt = LocalDateTime.now();
        organizationMapper.updateById(entity);
        syncChildrenLevel(tenantId, entity.id, entity.level);
        operationLogService.record(context.userId(), tenantId, "UPDATE_ORGANIZATION", id, OperationResult.SUCCESS, servletRequest);
        return OrganizationResponse.from(entity, List.of());
    }

    @Transactional
    public void delete(Long tenantId, Long id, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        OrganizationEntity entity = requireOrganization(tenantId, id);
        if (organizationMapper.countChildren(tenantId, id) > 0) {
            throw new BusinessException(ErrorCode.ORGANIZATION_HAS_CHILDREN, "当前组织存在下级组织，请先处理下级组织。");
        }
        if (organizationMemberMapper.countByOrganizationId(tenantId, id) > 0) {
            throw new BusinessException(ErrorCode.ORGANIZATION_HAS_MEMBERS, "当前组织存在成员，请先处理成员归属。");
        }
        if (projectMapper.countByOrganizationId(tenantId, id) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前组织仍有关联项目，请先调整项目归属。");
        }
        entity.deletedAt = LocalDateTime.now();
        entity.updatedAt = entity.deletedAt;
        organizationMapper.updateById(entity);
        operationLogService.record(context.userId(), tenantId, "DELETE_ORGANIZATION", id, OperationResult.SUCCESS, servletRequest);
    }

    @Transactional
    public OrganizationResponse updateStatus(Long tenantId, Long id, UpdateOrganizationStatusRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        OrganizationEntity entity = requireOrganization(tenantId, id);
        entity.status = validateOrganizationStatus(request.status()).name();
        entity.updatedAt = LocalDateTime.now();
        organizationMapper.updateById(entity);
        operationLogService.record(context.userId(), tenantId, "UPDATE_ORGANIZATION_STATUS", id, OperationResult.SUCCESS, servletRequest);
        return OrganizationResponse.from(entity, List.of());
    }

    @Transactional
    public OrganizationResponse updateLeader(Long tenantId, Long id, UpdateOrganizationLeaderRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        OrganizationEntity entity = requireOrganization(tenantId, id);
        validateLeader(tenantId, request.leaderId());
        entity.leaderId = request.leaderId();
        entity.updatedAt = LocalDateTime.now();
        organizationMapper.updateById(entity);
        operationLogService.record(context.userId(), tenantId, "UPDATE_ORGANIZATION_LEADER", id, OperationResult.SUCCESS, servletRequest);
        return OrganizationResponse.from(entity, List.of());
    }

    OrganizationEntity requireOrganization(Long tenantId, Long id) {
        OrganizationEntity entity = organizationMapper.selectByTenantIdAndId(tenantId, id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.ORGANIZATION_NOT_FOUND, "组织不存在。");
        }
        return entity;
    }

    private List<OrganizationResponse> childrenOf(List<OrganizationEntity> organizations, Long parentId) {
        List<OrganizationResponse> result = new ArrayList<>();
        for (OrganizationEntity organization : organizations) {
            if (parentId == null ? organization.parentId == null : parentId.equals(organization.parentId)) {
                result.add(OrganizationResponse.from(organization, childrenOf(organizations, organization.id)));
            }
        }
        return result;
    }

    private void ensureNotDescendant(Long tenantId, Long currentId, OrganizationEntity parent) {
        OrganizationEntity cursor = parent;
        while (cursor != null) {
            if (currentId.equals(cursor.id)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不能将组织移动到自身下级。");
            }
            cursor = cursor.parentId == null ? null : organizationMapper.selectByTenantIdAndId(tenantId, cursor.parentId);
        }
    }

    private int subtreeDepth(List<OrganizationEntity> organizations, Long organizationId) {
        int depth = 1;
        for (OrganizationEntity organization : organizations) {
            if (organizationId.equals(organization.parentId)) {
                depth = Math.max(depth, 1 + subtreeDepth(organizations, organization.id));
            }
        }
        return depth;
    }

    private void syncChildrenLevel(Long tenantId, Long parentId, int parentLevel) {
        List<OrganizationEntity> organizations = organizationMapper.selectByTenantId(tenantId);
        for (OrganizationEntity organization : organizations) {
            if (parentId.equals(organization.parentId)) {
                organization.level = parentLevel + 1;
                organization.updatedAt = LocalDateTime.now();
                organizationMapper.updateById(organization);
                syncChildrenLevel(tenantId, organization.id, organization.level);
            }
        }
    }

    private void validateLeader(Long tenantId, Long leaderId) {
        if (leaderId == null) {
            return;
        }
        if (tenantMemberMapper.selectByTenantIdAndUserId(tenantId, leaderId) == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "负责人必须是当前团队成员。");
        }
    }

    private String validateName(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.length() < 2 || name.length() > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "组织名称需为2-100个字符。");
        }
        return name;
    }

    private String normalizeCode(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase();
        if (!code.matches("[A-Z][A-Z0-9_]{1,49}")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "组织编码需为2-50位字母、数字或下划线。");
        }
        return code;
    }

    private OrganizationStatus validateOrganizationStatus(String rawStatus) {
        try {
            return OrganizationStatus.valueOf(rawStatus);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "组织状态不正确。");
        }
    }
}
