package com.antshorttv.invitation;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.member.MemberStatus;
import com.antshorttv.member.MemberType;
import com.antshorttv.member.TenantMemberEntity;
import com.antshorttv.member.TenantMemberMapper;
import com.antshorttv.operationlog.OperationLogService;
import com.antshorttv.operationlog.OperationResult;
import com.antshorttv.security.CurrentUser;
import com.antshorttv.security.CurrentUserHolder;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import com.antshorttv.tenant.TenantEntity;
import com.antshorttv.tenant.TenantMapper;
import com.antshorttv.user.UserEntity;
import com.antshorttv.user.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantInvitationService {

    private final SecureRandom secureRandom = new SecureRandom();

    private final TenantInvitationMapper tenantInvitationMapper;
    private final TenantMemberMapper tenantMemberMapper;
    private final TenantMapper tenantMapper;
    private final UserMapper userMapper;
    private final TenantContextResolver tenantContextResolver;
    private final OperationLogService operationLogService;

    public TenantInvitationService(
        TenantInvitationMapper tenantInvitationMapper,
        TenantMemberMapper tenantMemberMapper,
        TenantMapper tenantMapper,
        UserMapper userMapper,
        TenantContextResolver tenantContextResolver,
        OperationLogService operationLogService
    ) {
        this.tenantInvitationMapper = tenantInvitationMapper;
        this.tenantMemberMapper = tenantMemberMapper;
        this.tenantMapper = tenantMapper;
        this.userMapper = userMapper;
        this.tenantContextResolver = tenantContextResolver;
        this.operationLogService = operationLogService;
    }

    @Transactional
    public TenantInvitationResponse create(Long tenantId, CreateInvitationRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        UserEntity invitedUser = userMapper.selectByMobile(request.mobile());
        if (invitedUser != null) {
            TenantMemberEntity existingMember = tenantMemberMapper.selectByTenantIdAndUserId(tenantId, invitedUser.getId());
            if (existingMember != null && MemberStatus.ACTIVE.name().equals(existingMember.getStatus())) {
                throw new BusinessException(ErrorCode.ALREADY_TENANT_MEMBER, "你已经是该创作团队成员，无需重复加入。");
            }
        }
        if (tenantInvitationMapper.selectPendingByTenantIdAndMobile(tenantId, request.mobile()) != null) {
            throw new BusinessException(ErrorCode.DUPLICATE_PENDING_INVITATION, "该用户已有待处理邀请。");
        }

        LocalDateTime now = LocalDateTime.now();
        TenantInvitationEntity invitation = new TenantInvitationEntity();
        invitation.setTenantId(tenantId);
        invitation.setInviteMobile(request.mobile());
        invitation.setInviteUserId(invitedUser == null ? null : invitedUser.getId());
        invitation.setInvitedBy(context.userId());
        invitation.setToken(generateToken());
        invitation.setStatus(InvitationStatus.PENDING.name());
        invitation.setExpiredAt(now.plusDays(7));
        invitation.setCreatedAt(now);
        invitation.setUpdatedAt(now);
        tenantInvitationMapper.insert(invitation);

        operationLogService.record(context.userId(), tenantId, "INVITE_MEMBER", invitation.getId(), OperationResult.SUCCESS, servletRequest);
        return toResponse(invitation);
    }

    public List<TenantInvitationResponse> myInvitations() {
        CurrentUser currentUser = CurrentUserHolder.require();
        return tenantInvitationMapper.selectByInviteMobile(currentUser.mobile())
            .stream()
            .map(this::toResponse)
            .toList();
    }

    public List<TenantInvitationResponse> tenantInvitations(Long tenantId) {
        tenantContextResolver.requireActiveMember(tenantId);
        return tenantInvitationMapper.selectByTenantId(tenantId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    public TenantInvitationResponse detail(String token) {
        return toResponse(requireByToken(token));
    }

    @Transactional
    public TenantInvitationResponse accept(String token, HttpServletRequest servletRequest) {
        TenantInvitationEntity invitation = requirePendingUsable(token);
        CurrentUser currentUser = requireInvitee(invitation);
        LocalDateTime now = LocalDateTime.now();

        TenantMemberEntity member = tenantMemberMapper.selectByTenantIdAndUserId(invitation.getTenantId(), currentUser.userId());
        if (member != null && MemberStatus.ACTIVE.name().equals(member.getStatus())) {
            throw new BusinessException(ErrorCode.ALREADY_TENANT_MEMBER, "你已经是该创作团队成员，无需重复加入。");
        }
        if (member == null) {
            member = new TenantMemberEntity();
            member.setTenantId(invitation.getTenantId());
            member.setUserId(currentUser.userId());
            member.setMemberType(MemberType.MEMBER.name());
            member.setStatus(MemberStatus.ACTIVE.name());
            member.setJoinedAt(now);
            member.setInvitedBy(invitation.getInvitedBy());
            member.setCreatedAt(now);
            member.setUpdatedAt(now);
            tenantMemberMapper.insert(member);
        } else {
            member.setMemberType(MemberType.MEMBER.name());
            member.setStatus(MemberStatus.ACTIVE.name());
            member.setJoinedAt(now);
            member.setInvitedBy(invitation.getInvitedBy());
            member.setUpdatedAt(now);
            tenantMemberMapper.updateById(member);
        }

        invitation.setInviteUserId(currentUser.userId());
        invitation.setStatus(InvitationStatus.ACCEPTED.name());
        invitation.setAcceptedAt(now);
        invitation.setUpdatedAt(now);
        tenantInvitationMapper.updateById(invitation);
        operationLogService.record(currentUser.userId(), invitation.getTenantId(), "ACCEPT_INVITATION", invitation.getId(), OperationResult.SUCCESS, servletRequest);
        return toResponse(invitation);
    }

    @Transactional
    public TenantInvitationResponse reject(String token, HttpServletRequest servletRequest) {
        TenantInvitationEntity invitation = requirePendingUsable(token);
        CurrentUser currentUser = requireInvitee(invitation);
        invitation.setInviteUserId(currentUser.userId());
        invitation.setStatus(InvitationStatus.REJECTED.name());
        invitation.setUpdatedAt(LocalDateTime.now());
        tenantInvitationMapper.updateById(invitation);
        operationLogService.record(currentUser.userId(), invitation.getTenantId(), "REJECT_INVITATION", invitation.getId(), OperationResult.SUCCESS, servletRequest);
        return toResponse(invitation);
    }

    @Transactional
    public TenantInvitationResponse cancel(Long invitationId, HttpServletRequest servletRequest) {
        TenantInvitationEntity invitation = tenantInvitationMapper.selectById(invitationId);
        if (invitation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "邀请不存在。");
        }
        TenantContext context = tenantContextResolver.requireOwner(invitation.getTenantId());
        if (!InvitationStatus.PENDING.name().equals(invitation.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "仅待处理邀请可以取消。");
        }
        invitation.setStatus(InvitationStatus.CANCELLED.name());
        invitation.setUpdatedAt(LocalDateTime.now());
        tenantInvitationMapper.updateById(invitation);
        operationLogService.record(context.userId(), invitation.getTenantId(), "CANCEL_INVITATION", invitation.getId(), OperationResult.SUCCESS, servletRequest);
        return toResponse(invitation);
    }

    private TenantInvitationEntity requirePendingUsable(String token) {
        TenantInvitationEntity invitation = requireByToken(token);
        if (!InvitationStatus.PENDING.name().equals(invitation.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "邀请状态不可操作。");
        }
        if (invitation.getExpiredAt().isBefore(LocalDateTime.now())) {
            invitation.setStatus(InvitationStatus.EXPIRED.name());
            invitation.setUpdatedAt(LocalDateTime.now());
            tenantInvitationMapper.updateById(invitation);
            throw new BusinessException(ErrorCode.INVITATION_EXPIRED, "该邀请已过期，请联系团队管理员重新发送邀请。");
        }
        return invitation;
    }

    private CurrentUser requireInvitee(TenantInvitationEntity invitation) {
        CurrentUser currentUser = CurrentUserHolder.require();
        if (!invitation.getInviteMobile().equals(currentUser.mobile())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权处理该邀请。");
        }
        return currentUser;
    }

    private TenantInvitationEntity requireByToken(String token) {
        TenantInvitationEntity invitation = tenantInvitationMapper.selectByToken(token);
        if (invitation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "邀请不存在。");
        }
        return invitation;
    }

    private TenantInvitationResponse toResponse(TenantInvitationEntity invitation) {
        TenantEntity tenant = tenantMapper.selectById(invitation.getTenantId());
        return TenantInvitationResponse.from(invitation, tenant);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
