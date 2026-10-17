package com.antshorttv.trend;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.antshorttv.authsession.AuthenticatedUser;
import com.antshorttv.platform.RequirePlatformPermission;
import com.antshorttv.security.CurrentPrincipal;
import com.antshorttv.trend.TrendModels.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TrendControllerTest {
    @Test void managementEndpointsRequireDedicatedPlatformPermission() throws Exception {
        for (var method : List.of(TrendController.class.getMethod("collect", String.class), TrendController.class.getMethod("run", String.class), TrendController.class.getMethod("coverage"), TrendController.class.getMethod("diagnostics", String.class))) {
            assertThat(method.getAnnotation(RequirePlatformPermission.class)).isNotNull();
            assertThat(method.getAnnotation(RequirePlatformPermission.class).value()).isEqualTo("PLATFORM_TREND_COLLECT");
        }
    }

    @Test void exposesFiveQueryDimensionsAndReportsValidationErrors() throws Exception {
        var store = mock(TrendStore.class);
        var principal = mock(CurrentPrincipal.class);
        var service = mock(TrendCollectionService.class);
        when(principal.require()).thenReturn(new AuthenticatedUser(1L,"test","session",LocalDateTime.now().plusHours(1)));
        when(store.query(any())).thenReturn(new Page<>(List.of(),0,1,20));
        var mvc = MockMvcBuilders.standaloneSetup(new TrendController(service, store, principal)).build();
        mvc.perform(get("/api/short-drama-trends/sources"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(17));
        mvc.perform(get("/api/short-drama-trends/snapshots")
            .param("market","DOMESTIC").param("platform","HONGGUO").param("contentType","AI")
            .param("sourceType","OFFICIAL").param("from","2026-10-01").param("to","2026-10-09"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        verify(store).query(new Query("DOMESTIC","HONGGUO","AI","OFFICIAL",LocalDate.of(2026,10,1),LocalDate.of(2026,10,9),1,20));
        mvc.perform(post("/api/platform/short-drama-trends/collections/UNKNOWN"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }
}
