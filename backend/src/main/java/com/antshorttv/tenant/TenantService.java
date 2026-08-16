package com.antshorttv.tenant;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.member.MemberStatus;
import com.antshorttv.member.MemberType;
import com.antshorttv.member.TenantMemberEntity;
import com.antshorttv.member.TenantMemberMapper;
import com.antshorttv.operationlog.OperationLogService;
import com.antshorttv.operationlog.OperationResult;
import com.antshorttv.security.CurrentTenantStore;
import com.antshorttv.security.CurrentUserHolder;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {

    private static final char[] CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private final SecureRandom secureRandom = new SecureRandom();

    private final TenantMapper tenantMapper;
    private final TenantMemberMapper tenantMemberMapper;
    private final TenantContextResolver tenantContextResolver;
    private final CurrentTenantStore currentTenantStore;
    private final OperationLogService operationLogService;

    public TenantService(
        TenantMapper tenantMapper,
        TenantMemberMapper tenantMemberMapper,
        TenantContextResolver tenantContextResolver,
        CurrentTenantStore currentTenantStore,
        OperationLogService operationLogService
    ) {
        this.tenantMapper = tenantMapper;
        this.tenantMemberMapper = tenantMemberMapper;
        this.tenantContextResolver = tenantContextResolver;
        this.currentTenantStore = currentTenantStore;
        this.operationLogService = operationLogService;
    }

    @Transactional
    public TenantSummaryResponse create(CreateTenantRequest request, HttpServletRequest servletRequest) {
        Long userId = CurrentUserHolder.require().userId();
        LocalDateTime now = LocalDateTime.now();

        TenantEntity tenant = new TenantEntity();
        tenant.setCode(generateTenantCode());
        tenant.setName(validateTenantName(request.name()));
        tenant.setType(validateTenantType(request.type()).name());
        tenant.setLogo(blankToNull(request.logo()));
        tenant.setDescription(blankToNull(request.description()));
        tenant.setStatus(TenantStatus.ACTIVE.name());
        tenant.setCreatedAt(now);
        tenant.setUpdatedAt(now);
        tenantMapper.insert(tenant);

        TenantMemberEntity member = new TenantMemberEntity();
        member.setTenantId(tenant.getId());
        member.setUserId(userId);
        member.setMemberType(MemberType.OWNER.name());
        member.setStatus(MemberStatus.ACTIVE.name());
        member.setJoinedAt(now);
        member.setCreatedAt(now);
        member.setUpdatedAt(now);
        tenantMemberMapper.insert(member);

        tenant.setOwnerMemberId(member.getId());
        tenantMapper.updateById(tenant);

        operationLogService.record(userId, tenant.getId(), "CREATE_TENANT", tenant.getId(), OperationResult.SUCCESS, servletRequest);
        return TenantSummaryResponse.from(tenant, member.getMemberType(), member.getId());
    }

    public List<TenantSummaryResponse> myTenants() {
        Long userId = CurrentUserHolder.require().userId();
        List<TenantSummaryResponse> responses = new ArrayList<>();
        for (TenantMemberEntity member : tenantMemberMapper.selectActiveByUserId(userId)) {
            TenantEntity tenant = tenantMapper.selectById(member.getTenantId());
            if (tenant != null && tenant.getDeletedAt() == null) {
                responses.add(TenantSummaryResponse.from(tenant, member.getMemberType(), member.getId()));
            }
        }
        return responses;
    }

    public TenantSummaryResponse detail(Long tenantId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        TenantEntity tenant = tenantMapper.selectById(tenantId);
        return TenantSummaryResponse.from(tenant, context.memberType(), context.memberId());
    }

    @Transactional
    public TenantSummaryResponse update(Long tenantId, UpdateTenantRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireOwner(tenantId);
        TenantEntity tenant = tenantMapper.selectById(tenantId);
        tenant.setName(validateTenantName(request.name()));
        tenant.setType(validateTenantType(request.type()).name());
        tenant.setLogo(blankToNull(request.logo()));
        tenant.setDescription(blankToNull(request.description()));
        tenant.setUpdatedAt(LocalDateTime.now());
        tenantMapper.updateById(tenant);
        operationLogService.record(context.userId(), tenantId, "UPDATE_TENANT", tenantId, OperationResult.SUCCESS, servletRequest);
        return TenantSummaryResponse.from(tenant, context.memberType(), context.memberId());
    }

    @Transactional
    public TenantSummaryResponse updateStatus(Long tenantId, UpdateTenantStatusRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireOwner(tenantId);
        TenantStatus status = validateTenantStatus(request.status());
        TenantEntity tenant = tenantMapper.selectById(tenantId);
        tenant.setStatus(status.name());
        tenant.setUpdatedAt(LocalDateTime.now());
        tenantMapper.updateById(tenant);
        operationLogService.record(context.userId(), tenantId, "UPDATE_TENANT_STATUS", tenantId, OperationResult.SUCCESS, servletRequest);
        return TenantSummaryResponse.from(tenant, context.memberType(), context.memberId());
    }

    public CurrentTenantResponse switchCurrent(SwitchTenantRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(request.tenantId());
        currentTenantStore.put(context);
        operationLogService.record(context.userId(), context.tenantId(), "SWITCH_TENANT", context.tenantId(), OperationResult.SUCCESS, servletRequest);
        return new CurrentTenantResponse(context.userId(), context.tenantId(), context.memberId(), context.memberType());
    }

    public CurrentTenantResponse current() {
        Long userId = CurrentUserHolder.require().userId();
        TenantContext context = currentTenantStore.get(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "尚未选择当前创作团队。"));
        return new CurrentTenantResponse(context.userId(), context.tenantId(), context.memberId(), context.memberType());
    }

    private String validateTenantName(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.length() < 2 || name.length() > 50 || name.chars().noneMatch(Character::isLetterOrDigit)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "团队名称需为2-50个字符，且不能全部为特殊字符。");
        }
        return name;
    }

    private TenantType validateTenantType(String rawType) {
        try {
            return TenantType.valueOf(rawType);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "团队类型不正确。");
        }
    }

    private TenantStatus validateTenantStatus(String rawStatus) {
        try {
            return TenantStatus.valueOf(rawStatus);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "团队状态不正确。");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String generateTenantCode() {
        StringBuilder code = new StringBuilder("T");
        for (int i = 0; i < 8; i++) {
            code.append(CODE_CHARS[secureRandom.nextInt(CODE_CHARS.length)]);
        }
        return code.toString();
    }
}
