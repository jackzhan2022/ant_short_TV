package com.antshorttv.script;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AssetNameControllerTest {
    @Test void exposesDedicatedAssetAndVariantNameRoutes() throws Exception {
        var workflow=mock(ScriptWorkflowService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new ScriptWorkflowController(workflow,
            mock(StoryboardBatchService.class),mock(StoryboardAssetReferenceService.class))).build();
        for(String path:new String[]{"/script-elements/CHARACTER/7/name","/visual-variants/8/name"})
            mvc.perform(put("/api/projects/2"+path).header("X-Tenant-Id","1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"新名称\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
    }
}
