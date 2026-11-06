package com.antshorttv.desktop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.antshorttv.desktop.DesktopSourceModels.*;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DesktopSourceControllerTest {
    @Test void exposesCompleteCurrentInventoryEndpoint() throws Exception {
        var service=mock(DesktopSourceService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new DesktopSourceController(service)).build();
        mvc.perform(post("/api/desktop/source-projects/sync-current").header("X-Tenant-Id","1")
            .contentType(MediaType.APPLICATION_JSON).content("""
            {"connectionId":"local","sourceTeamId":"team","sourceProjectId":"project","sourceScriptId":"current",
             "name":"Source","complete":true,"assets":[]}
            """))
            .andExpect(status().isOk());
        verify(service).syncCurrent(eq(1L),any());
        verify(service,never()).sync(any(),any());
    }

    @Test void exposesDedicatedAtomicAiConfirmationEndpoint() throws Exception {
        var service=mock(DesktopSourceService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new DesktopSourceController(service)).build();
        mvc.perform(put("/api/desktop/source-projects/assets/12/ai-match").header("X-Tenant-Id","1")
            .contentType(MediaType.APPLICATION_JSON).content("""
            {"assetId":22,"variantId":null,"expected":{"sourceName":"Hero","sourceParentId":null,
             "sourceTargetAssetId":null,"sourceTargetVariantId":null,"sourceMatchStatus":"UNMATCHED","targetProjectId":10,
             "candidates":[{"assetId":22,"variantId":null,"name":"Hero target","type":"CHARACTER","parentName":null,"hasPrompt":true}]}}
            """))
            .andExpect(status().isOk());
        var body=org.mockito.ArgumentCaptor.forClass(AiMatchRequest.class);
        verify(service).aiMatch(eq(1L),eq(12L),body.capture());
        assertThat(body.getValue().expected().sourceName()).isEqualTo("Hero");
        assertThat(body.getValue().expected().candidates()).containsExactly(new Candidate(22L,null,"Hero target","CHARACTER",null,true));
        verify(service,never()).manualMatch(any(),any(),any(),any());
    }

    @Test void exposesContractAndDropsUnrecognizedSourceSecrets() throws Exception {
        var service=mock(DesktopSourceService.class);
        when(service.sync(eq(1L),any())).thenReturn(new SourceProject(7L,"local","Source","team","project","script",null,null,true,LocalDateTime.now()));
        var mvc=MockMvcBuilders.standaloneSetup(new DesktopSourceController(service)).build();
        mvc.perform(post("/api/desktop/source-projects/sync").header("X-Tenant-Id","1")
            .contentType(MediaType.APPLICATION_JSON).content("""
            {"connectionId":"local","sourceTeamId":"team","sourceProjectId":"project","sourceScriptId":"script","name":"Source","complete":true,
             "credential":"secret","script":"private screenplay","modelKey":"key","assets":[{"sourceAssetId":"a","name":"Hero","type":"CHARACTER","image":"private","prompt":"source private prompt"}]}
            """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(7)).andExpect(jsonPath("$.data.targetProjectId").isEmpty());
        var body=org.mockito.ArgumentCaptor.forClass(SyncRequest.class);
        verify(service).sync(eq(1L),body.capture());
        assertThat(body.getValue().assets()).containsExactly(new SyncAsset("a",null,"Hero","CHARACTER"));
        assertThat(List.of(SyncRequest.class.getRecordComponents()).stream().map(java.lang.reflect.RecordComponent::getName))
            .doesNotContain("credential","script","modelKey","payload");
    }

    @Test void promptResponseUsesNoStoreAndSourceRowIds() throws Exception {
        var service=mock(DesktopSourceService.class);
        when(service.prompts(1L,List.of(12L))).thenReturn(List.of(new Prompt(12L,"Hero","CHARACTER","Target",10L,22L,null,"canonical",7L,"Source",null)));
        var mvc=MockMvcBuilders.standaloneSetup(new DesktopSourceController(service)).build();
        mvc.perform(post("/api/desktop/source-projects/prompts").header("X-Tenant-Id","1")
            .contentType(MediaType.APPLICATION_JSON).content("{\"assetIds\":[12]}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.data[0].sourceAssetId").value(12)).andExpect(jsonPath("$.data[0].prompt").value("canonical"))
            .andExpect(jsonPath("$.data[0].sourceProjectId").value(7)).andExpect(jsonPath("$.data[0].sourceProjectName").value("Source"));
    }
}
