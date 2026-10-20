package com.antshorttv.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.antshorttv.authsession.AuthenticatedUser;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.security.CurrentPrincipal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

class MonitoringApiExceptionResolverTest {
    @Test
    void observesTechnicalFailuresWithoutHandlingOrChangingExistingResponseContracts() {
        var principal = mock(CurrentPrincipal.class);
        when(principal.get()).thenReturn(Optional.of(new AuthenticatedUser(7L, "", "", LocalDateTime.now())));
        var buffer = mock(MonitoringBuffer.class);
        var resolver = new MonitoringApiExceptionResolver(buffer, principal);
        var request = new MockHttpServletRequest("POST", "/api/projects");
        request.addHeader("X-Tenant-Id", "12");
        var response = new MockHttpServletResponse();
        assertThat(resolver.resolveException(request, response, null, new IllegalStateException("storage failed"))).isNull();
        var event = org.mockito.ArgumentCaptor.forClass(MonitoringEvent.class);
        verify(buffer).record(event.capture());
        assertThat(event.getValue().userId()).isEqualTo(7);
        assertThat(event.getValue().tenantId()).isEqualTo(12);
        assertThat(event.getValue().location()).isEqualTo("POST /api/projects");
        assertThat(response.getStatus()).isEqualTo(200);
        clearInvocations(buffer);
        request.removeAttribute(MonitoringApiExceptionResolver.class.getName());
        resolver.resolveException(request, response, null, new BusinessException(ErrorCode.TEAM_POINTS_INSUFFICIENT, "points"));
        resolver.resolveException(request, response, null, new ResponseStatusException(HttpStatus.NOT_FOUND));
        verifyNoInteractions(buffer);
        resolver.resolveException(request, response, null, new BusinessException(ErrorCode.AI_PROVIDER_TIMEOUT, "timeout"));
        verify(buffer).record(any());
        clearInvocations(buffer);
        request.setRequestURI("/api/monitoring/frontend-events");
        resolver.resolveException(request, response, null, new IllegalStateException("report failed"));
        verifyNoInteractions(buffer);
    }

    @Test
    void collectionFailureCannotReplaceOriginalApplicationError() {
        var buffer = mock(MonitoringBuffer.class);
        doThrow(new IllegalStateException("monitor unavailable")).when(buffer).record(any());
        var principal = mock(CurrentPrincipal.class);
        when(principal.get()).thenReturn(Optional.empty());
        var resolver = new MonitoringApiExceptionResolver(buffer, principal);
        assertThat(resolver.resolveException(new MockHttpServletRequest("GET", "/api/test"),
            new MockHttpServletResponse(), null, new RuntimeException("original"))).isNull();
    }

    @Test
    void ignoresConstraintValidationAndMalformedJsonAsExpectedClientErrors() {
        var buffer = mock(MonitoringBuffer.class);
        var principal = mock(CurrentPrincipal.class);
        when(principal.get()).thenReturn(Optional.empty());
        var resolver = new MonitoringApiExceptionResolver(buffer, principal);
        for (Exception exception : new Exception[]{
            new jakarta.validation.ConstraintViolationException(java.util.Set.of()),
            new org.springframework.http.converter.HttpMessageNotReadableException("invalid JSON",
                new org.springframework.mock.http.MockHttpInputMessage(new byte[0])),
            new org.springframework.beans.TypeMismatchException("abc", Long.class)
        }) {
            resolver.resolveException(new MockHttpServletRequest("POST", "/api/projects"), new MockHttpServletResponse(), null, exception);
        }
        verifyNoInteractions(buffer);
    }
}
