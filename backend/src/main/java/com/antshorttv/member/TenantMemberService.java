package com.antshorttv.member;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.operationlog.OperationLogService;
import com.antshorttv.operationlog.OperationResult;
import com.antshorttv.rbac.RbacService;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import com.antshorttv.tenant.TenantEntity;
import com.antshorttv.tenant.TenantMapper;
import com.antshorttv.tenant.TenantSummaryResponse;
import com.antshorttv.user.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantMemberService {

    private final TenantMemberMapper tenantMemberMapper;
    private final TenantMapper tenantMapper;
    private final UserMapper userMapper;
    private final TenantContextResolver tenantContextResolver;
    private final OperationLogService operationLogService;
    private final RbacService rbacService;

    public TenantMemberService(
        TenantMemberMapper tenantMemberMapper,
        TenantMapper tenantMapper,
        UserMapper userMapper,
        TenantContextResolver tenantContextResolver,
        OperationLogService operationLogService,
        RbacService rbacService
    ) {
        this.tenantMemberMapper = tenantMemberMapper;
        this.tenantMapper = tenantMapper;
        this.userMapper = userMapper;
        this.tenantContextResolver = tenantContextResolver;
        this.operationLogService = operationLogService;
        this.rbacService = rbacService;
    }

    public List<TenantMemberResponse> list(Long tenantId) {
        tenantContextResolver.requireActiveMember(tenantId);
        return tenantMemberMapper.selectActiveByTenantId(tenantId)
            .stream()
            .map(member -> TenantMemberResponse.from(member, userMapper.selectById(member.getUserId())))
            .toList();
    }

    @Transactional
    public ApiResponse<Void> remove(Long tenantId, Long memberId, HttpServletRequest request) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        TenantMemberEntity target = requireMemberInTenant(tenantId, memberId);
        if (MemberType.OWNER.name().equals(target.getMemberType())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能直接移除团队所有者。");
        }
        target.setStatus(MemberStatus.REMOVED.name());
        target.setUpdatedAt(LocalDateTime.now());
        tenantMemberMapper.updateById(target);
        rbacService.removeMemberRoles(memberId);
        operationLogService.record(context.userId(), tenantId, "REMOVE_MEMBER", memberId, OperationResult.SUCCESS, request);
        return ApiResponse.ok();
    }

    @Transactional
    public ApiResponse<Void> leave(Long tenantId, HttpServletRequest request) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        TenantMemberEntity member = tenantMemberMapper.selectById(context.memberId());
        if (MemberType.OWNER.name().equals(member.getMemberType())) {
            throw new BusinessException(ErrorCode.OWNER_LEAVE_BLOCKED, "团队所有者不能直接退出，请先转让团队所有权。");
        }
        member.setStatus(MemberStatus.REMOVED.name());
        member.setUpdatedAt(LocalDateTime.now());
        tenantMemberMapper.updateById(member);
        rbacService.removeMemberRoles(member.getId());
        operationLogService.record(context.userId(), tenantId, "LEAVE_TENANT", member.getId(), OperationResult.SUCCESS, request);
        return ApiResponse.ok();
    }

    @Transactional
    public TenantSummaryResponse transferOwner(Long tenantId, TransferOwnerRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireOwner(tenantId);
        TenantMemberEntity currentOwner = tenantMemberMapper.selectById(context.memberId());
        TenantMemberEntity target = requireMemberInTenant(tenantId, request.targetMemberId());
        if (!MemberStatus.ACTIVE.name().equals(target.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "目标成员不是有效团队成员。");
        }
        if (target.getId().equals(currentOwner.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不能转让给当前所有者。");
        }

        currentOwner.setMemberType(MemberType.MEMBER.name());
        currentOwner.setUpdatedAt(LocalDateTime.now());
        tenantMemberMapper.updateById(currentOwner);

        target.setMemberType(MemberType.OWNER.name());
        target.setUpdatedAt(LocalDateTime.now());
        tenantMemberMapper.updateById(target);

        TenantEntity tenant = tenantMapper.selectById(tenantId);
        tenant.setOwnerMemberId(target.getId());
        tenant.setUpdatedAt(LocalDateTime.now());
        tenantMapper.updateById(tenant);
        rbacService.syncOwnerTransfer(tenantId, currentOwner.getId(), target.getId());

        operationLogService.record(context.userId(), tenantId, "TRANSFER_OWNER", target.getId(), OperationResult.SUCCESS, servletRequest);
        return TenantSummaryResponse.from(tenant, currentOwner.getMemberType(), currentOwner.getId());
    }

    private TenantMemberEntity requireMemberInTenant(Long tenantId, Long memberId) {
        TenantMemberEntity member = tenantMemberMapper.selectById(memberId);
        if (member == null || !tenantId.equals(member.getTenantId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "团队成员不存在。");
        }
        return member;
    }
}
