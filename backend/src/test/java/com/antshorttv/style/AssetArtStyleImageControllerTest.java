package com.antshorttv.style;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AssetArtStyleImageControllerTest {
    @Test
    void redirectsWithoutCachingExpiringUrlAndReturns404ForUnknownStyle() throws Exception {
        var images = org.mockito.Mockito.mock(AssetArtStyleImageService.class);
        org.mockito.Mockito.when(images.deliveryUrl("known")).thenReturn("https://cdn.example/display.png?sign=test");
        org.mockito.Mockito.when(images.deliveryUrl("unknown")).thenThrow(new com.antshorttv.common.BusinessException(
            com.antshorttv.common.ErrorCode.NOT_FOUND, "Missing"));
        var mvc = MockMvcBuilders.standaloneSetup(new AssetArtStyleController(null, images))
            .setControllerAdvice(new com.antshorttv.common.GlobalExceptionHandler()).build();
        mvc.perform(get("/api/style-library/v2/images/known"))
            .andExpect(status().isFound())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("Location", "https://cdn.example/display.png?sign=test"));
        mvc.perform(get("/api/style-library/v2/images/unknown"))
            .andExpect(status().isNotFound()).andExpect(header().doesNotExist("Location"));
    }

    @Test
    void catalogReturnsStableBackendImageUrlsForAllElevenStyles() throws Exception {
        var styles = new AssetArtStyleService(null, new ObjectMapper());
        var images = new AssetArtStyleImageService(null, null, null, null, new ObjectMapper());
        var mvc = MockMvcBuilders.standaloneSetup(new AssetArtStyleController(styles, images)).build();
        var result = mvc.perform(get("/api/style-library/v2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(11))
            .andExpect(jsonPath("$.data[0].imageUrl").value("/api/style-library/v2/images/2D_90s_japanese_anime"))
            .andReturn();
        var data = new ObjectMapper().readTree(result.getResponse().getContentAsString()).path("data");
        for (var entry : data) {
            org.assertj.core.api.Assertions.assertThat(entry.path("imageUrl").asText())
                .isEqualTo("/api/style-library/v2/images/" + entry.path("code").asText());
        }
    }
}
