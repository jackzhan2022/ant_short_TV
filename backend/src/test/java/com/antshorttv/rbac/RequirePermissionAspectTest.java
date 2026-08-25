package com.antshorttv.rbac;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RequirePermissionAspectTest {

    private TenantPermissionGuard tenantPermissionGuard;
    private RequirePermissionAspect aspect;

    @BeforeEach
    void setUp() {
        tenantPermissionGuard = mock(TenantPermissionGuard.class);
        aspect = new RequirePermissionAspect(tenantPermissionGuard);
    }

    @Test
    void usesExplicitlyNamedTenantParameterInsteadOfFirstLongArgument() {
        JoinPoint joinPoint = joinPoint(
            new String[] {"resourceId", "tenantId"},
            new Object[] {99L, 20L}
        );
        RequirePermission permission = permission("ROLE:EDIT", "tenantId");

        aspect.require(joinPoint, permission);

        verify(tenantPermissionGuard).require(20L, "ROLE:EDIT");
    }

    @Test
    void usesTenantHeaderWhenNoParameterIsDeclared() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Tenant-Id")).thenReturn("21");
        JoinPoint joinPoint = joinPoint(
            new String[] {"resourceId", "request"},
            new Object[] {99L, request}
        );
        RequirePermission permission = permission("PROJECT:CREATE", "");

        aspect.require(joinPoint, permission);

        verify(tenantPermissionGuard).require(21L, "PROJECT:CREATE");
    }

    @Test
    void failsClosedWithoutDeclaredParameterOrTenantHeader() {
        JoinPoint joinPoint = joinPoint(new String[] {"resourceId"}, new Object[] {99L});

        assertThatThrownBy(() -> aspect.require(joinPoint, permission("ROLE:EDIT", "")))
            .isInstanceOf(BusinessException.class);
    }

    private JoinPoint joinPoint(String[] parameterNames, Object[] arguments) {
        JoinPoint joinPoint = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(arguments);
        when(signature.getParameterNames()).thenReturn(parameterNames);
        return joinPoint;
    }

    private RequirePermission permission(String value, String tenantIdParameter) {
        RequirePermission permission = mock(RequirePermission.class);
        when(permission.value()).thenReturn(value);
        when(permission.tenantIdParameter()).thenReturn(tenantIdParameter);
        return permission;
    }
}
