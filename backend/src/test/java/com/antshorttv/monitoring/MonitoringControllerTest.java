package com.antshorttv.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.antshorttv.authsession.AuthenticatedUser;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MonitoringControllerTest extends com.antshorttv.support.OfflineMediaTestSupport {
    @Autowired private MockMvc mvc;
    @MockBean private MonitoringBuffer buffer;

    @Test
    void requiresSessionAndCsrfAndUsesServerIdentityAndValidatedFrontendSources() throws Exception {
        String path = "/api/monitoring/frontend-events";
        String body = """
            {"source":"FRONTEND_RENDER","message":"render failed","location":"/projects","userId":999}
            """;
        var principal = new AuthenticatedUser(7L, "", "", LocalDateTime.now().plusHours(1));
        var auth = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
        mvc.perform(post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized());
        mvc.perform(post(path).with(authentication(auth)).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mvc.perform(post(path).with(authentication(auth)).with(csrf()).header("X-Tenant-Id", "12")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        var captured = org.mockito.ArgumentCaptor.forClass(MonitoringEvent.class);
        verify(buffer).record(captured.capture());
        assertThat(captured.getValue().userId()).isEqualTo(7L);
        assertThat(captured.getValue().occurredAt()).isNotNull();
        clearInvocations(buffer);
        for (String invalid : new String[]{body.replace("FRONTEND_RENDER", "API"), body.replace("render failed", "x".repeat(501)), "{}"}) {
            mvc.perform(post(path).with(authentication(auth)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(buffer);
    }
}
