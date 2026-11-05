package com.antshorttv.operations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.antshorttv.authsession.AuthenticatedUser;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.common.GlobalExceptionHandler;
import com.antshorttv.operationlog.OperationLogService;
import com.antshorttv.operations.alerts.OperationsAlertService;
import com.antshorttv.platform.PlatformPermissionGuard;
import com.antshorttv.security.CurrentPrincipal;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OperationsControllerTest {
    final CurrentPrincipal principal=mock(CurrentPrincipal.class);
    final PlatformPermissionGuard permissions=mock(PlatformPermissionGuard.class);
    final OperationsQueryService queries=mock(OperationsQueryService.class);
    final OperationsSettingsService settings=mock(OperationsSettingsService.class);
    final OperationsAlertService alerts=mock(OperationsAlertService.class);
    final OperationLogService audit=mock(OperationLogService.class);
    OperationsController controller(){return new OperationsController(principal,permissions,queries,settings,alerts,audit);}
    @Test void rejectsImpersonationBeforeCheckingOriginalAdministratorPermissions() throws Exception {
        when(principal.require()).thenReturn(new AuthenticatedUser(2L,"","session",LocalDateTime.now().plusDays(1),new AuthenticatedUser.Impersonation(1L,"admin",3L)));
        var mvc=MockMvcBuilders.standaloneSetup(controller()).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/platform/operations/snapshot")).andExpect(status().isForbidden());
        verifyNoInteractions(queries,permissions);
    }
    @Test void metricsPermissionDoesNotGrantLogDataOrTenantAdminAccess() throws Exception {
        when(principal.require()).thenReturn(new AuthenticatedUser(2L,"","session",LocalDateTime.now().plusDays(1)));
        doThrow(new BusinessException(ErrorCode.FORBIDDEN,"无日志权限")).when(permissions).require("PLATFORM_OPERATIONS_LOG_VIEW");
        var mvc=MockMvcBuilders.standaloneSetup(controller()).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/platform/operations/events/7")).andExpect(status().isForbidden());verifyNoInteractions(queries);
        doThrow(new BusinessException(ErrorCode.FORBIDDEN,"无平台权限")).when(permissions).require("PLATFORM_OPERATIONS_VIEW");
        mvc.perform(get("/api/platform/operations/snapshot")).andExpect(status().isForbidden());
    }
    @Test void writesRequireDedicatedPermissionAndProduceAudit() throws Exception {
        when(principal.require()).thenReturn(new AuthenticatedUser(2L,"","session",LocalDateTime.now().plusDays(1)));
        when(settings.current()).thenReturn(Map.of("version",0));when(settings.update(anyMap())).thenReturn(Map.of("version",1));
        var mvc=MockMvcBuilders.standaloneSetup(controller()).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(put("/api/platform/operations/settings").contentType("application/json").content("{\"version\":0,\"refreshSeconds\":30}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(permissions).require("PLATFORM_OPERATIONS_SETTINGS_EDIT");verify(audit).record(eq(2L),isNull(),eq("PLATFORM_OPERATIONS_SETTINGS_EDIT"),isNull(),any(),any(),anyMap());
    }
}
