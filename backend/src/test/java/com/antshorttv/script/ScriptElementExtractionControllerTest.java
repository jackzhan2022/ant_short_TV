package com.antshorttv.script;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ScriptElementExtractionControllerTest {
    @Test
    void retiredPathsAreUnmappedAndCannotSubmitPaidWork() throws Exception {
        ScriptWorkflowService workflow = mock(ScriptWorkflowService.class);
        StoryboardBatchService batches = mock(StoryboardBatchService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ScriptWorkflowController(workflow, batches)).build();
        for (String path : java.util.List.of("/script-workspace", "/asset-settings-workspace",
            "/asset-candidates", "/asset-candidates/2")) {
            mvc.perform(get("/api/projects/1" + path)).andExpect(status().isNotFound());
        }
        for (String path : java.util.List.of("/scripts/ai-extract-elements", "/asset-candidates/2/decisions")) {
            mvc.perform(post("/api/projects/1" + path)).andExpect(status().isNotFound());
        }
        mvc.perform(put("/api/projects/1/script-elements/CHARACTER/2/confirm")).andExpect(status().isNotFound());
        verifyNoInteractions(workflow, batches);
    }
}
