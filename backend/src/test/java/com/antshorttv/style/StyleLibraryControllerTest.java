package com.antshorttv.style;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antshorttv.storage.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class StyleLibraryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ObjectStorageService objectStorageService;

    @Test
    void queriesPublicStylesWithFilters() throws Exception {
        mockMvc.perform(get("/api/style-library"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(139)))
            .andExpect(jsonPath("$.data[0].externalId", is("864621266010645040")))
            .andExpect(jsonPath("$.data[0].imageUrl", is("/api/style-library/images/864621266010645040")))
            .andExpect(jsonPath("$.data[0].sourceImageUrl").doesNotExist());

        mockMvc.perform(get("/api/style-library").param("category", "3D风格"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(29)))
            .andExpect(jsonPath("$.data[*].category", everyItem(is("3D风格"))));

        mockMvc.perform(get("/api/style-library").param("keyword", "赛博朋克"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", not(hasSize(0))))
            .andExpect(jsonPath("$.data[0].name", startsWith("2D风格-赛博朋克")));
    }

    @Test
    void redirectsPublicStyleImageToPrivateCdn() throws Exception {
        mockMvc.perform(get("/api/style-library/images/864621266010645040"))
            .andExpect(status().isFound())
            .andExpect(header().string(
                HttpHeaders.LOCATION,
                startsWith("https://antvcdn.aixmax.cn/style-library/public/864621266010645040/cover-compressed.jpg")
            ));
    }
}
