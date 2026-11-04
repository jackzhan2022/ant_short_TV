package com.antshorttv.script;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.common.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AssetVisualStatusControllerTest {
    private AssetVisualStatusService service;
    private MockMvc mvc;
    @BeforeEach void setUp() {
        service = mock(AssetVisualStatusService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AssetVisualStatusController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void readsCommaSeparatedIdsWithinTheSelectedTenantAndProject() throws Exception {
        when(service.read(10L,33L,List.of(1L,2L))).thenReturn(List.of(
            new AssetVisualStatusService.Status(1L,true,"GENERATING",100L,null,false,null,null)));
        mvc.perform(get("/api/projects/33/visual-variants/generation-status")
                .header("X-Tenant-Id","10").param("variantIds","1,2"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].variantId").value(1))
            .andExpect(jsonPath("$.data[0].generationStatus").value("GENERATING"));
        verify(service).read(10L,33L,List.of(1L,2L));
    }
    @Test void doesNotAcceptMissingTenantOrSuppressPermissionFailures() throws Exception {
        mvc.perform(get("/api/projects/33/visual-variants/generation-status").param("variantIds","1"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
        when(service.read(10L,33L,List.of(1L)))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN,"无权访问该项目。"));
        mvc.perform(get("/api/projects/33/visual-variants/generation-status")
                .header("X-Tenant-Id","10").param("variantIds","1"))
            .andExpect(status().isForbidden());
    }
}
