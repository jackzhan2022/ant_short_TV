package com.antshorttv.review;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.common.BusinessException;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.rbac.RbacPermissionService;
import com.antshorttv.security.TenantContext;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewAccessGuardTest {

    @Mock
    private ProjectPermissionGuard projectPermissionGuard;

    @Mock
    private RbacPermissionService permissionService;

    private ReviewAccessGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ReviewAccessGuard(projectPermissionGuard, permissionService);
    }

    @Test
    void delegatesBoundReviewDraftToOwningMainProject() {
        TenantContext context = context(10L, 20L);
        ReviewProjectEntity review = review(20L, 10L, 30L);

        guard.require(context, review, "SCRIPT:EDIT");

        verify(projectPermissionGuard).require(20L, 30L, "SCRIPT:EDIT");
    }

    @Test
    void allowsCreatorToUseUnboundReviewDraft() {
        TenantContext context = context(10L, 20L);
        ReviewProjectEntity review = review(20L, 10L, null);

        guard.require(context, review, "SCRIPT:EDIT");

        verify(projectPermissionGuard, never()).require(20L, null, "SCRIPT:EDIT");
    }

    @Test
    void allowsTenantWideAdministratorToViewAnotherUsersUnboundDraft() {
        TenantContext context = context(10L, 20L);
        ReviewProjectEntity review = review(20L, 99L, null);
        when(permissionService.permissionCodes(context)).thenReturn(Set.of("PROJECT:VIEW_ALL"));

        guard.require(context, review, "PROJECT:VIEW");
    }

    @Test
    void deniesOtherMemberAndCrossTenantReviewDrafts() {
        TenantContext context = context(10L, 20L);
        ReviewProjectEntity otherUsersDraft = review(20L, 99L, null);
        ReviewProjectEntity crossTenantDraft = review(21L, 10L, null);
        when(permissionService.permissionCodes(context)).thenReturn(Set.of());

        assertThatThrownBy(() -> guard.require(context, otherUsersDraft, "PROJECT:VIEW"))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> guard.require(context, crossTenantDraft, "PROJECT:VIEW"))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void bindingRequiresTargetProjectEditPermission() {
        TenantContext context = context(10L, 20L);
        ReviewProjectEntity review = review(20L, 10L, null);

        guard.requireBinding(context, review, 30L);

        verify(projectPermissionGuard).require(20L, 30L, "SCRIPT:EDIT");
    }

    @Test
    void rejectsRebindingAnAlreadyBoundReviewDraft() {
        TenantContext context = context(10L, 20L);
        ReviewProjectEntity review = review(20L, 10L, 30L);

        assertThatThrownBy(() -> guard.requireBinding(context, review, 31L))
            .isInstanceOf(BusinessException.class);
        verify(projectPermissionGuard, never()).require(20L, 31L, "SCRIPT:EDIT");
    }

    private TenantContext context(Long userId, Long tenantId) {
        return new TenantContext(userId, tenantId, 1L, "MEMBER");
    }

    private ReviewProjectEntity review(Long tenantId, Long createdBy, Long mainProjectId) {
        ReviewProjectEntity review = new ReviewProjectEntity();
        review.setTenantId(tenantId);
        review.setCreatedBy(createdBy);
        review.setMainProjectId(mainProjectId);
        return review;
    }
}
