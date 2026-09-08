package com.antshorttv.commercial;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class CommercialControllerPermissionTest {
    @Autowired MockMvc mockMvc;

    @Test
    void teamOwnerCanViewCatalogButCannotUsePlatformPackageApi() throws Exception {
        Cookie session = register("13800017001", "Commercial Owner");
        Cookie csrf = mockMvc.perform(get("/api/tenants/my").cookie(session)).andReturn().getResponse().getCookie("XSRF-TOKEN");
        mockMvc.perform(post("/api/tenants").cookie(session, csrf).header("X-XSRF-TOKEN", csrf.getValue())
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Commercial Team\",\"type\":\"STUDIO\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/tenants/1/commercial/catalog").cookie(session).header("X-Tenant-Id", "1"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/platform/commercial/packages").cookie(session).header("X-Tenant-Id", "1"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode", is("FORBIDDEN")));
        mockMvc.perform(get("/api/platform/commercial/entitlements").cookie(session).header("X-Tenant-Id", "1"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode", is("FORBIDDEN")));
    }

    @Test
    void platformAdministratorCanManageDisplayEntitlements() throws Exception {
        Cookie session = register("13800000999", "Platform Commercial Admin");
        Cookie csrf = mockMvc.perform(get("/api/tenants/my").cookie(session))
            .andReturn().getResponse().getCookie("XSRF-TOKEN");
        mockMvc.perform(get("/api/platform/commercial/packages").cookie(session))
            .andExpect(status().isOk());

        MvcResult created = mockMvc.perform(post("/api/platform/commercial/entitlements")
                .cookie(session, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"使用全部模型\",\"description\":\"展示文案\",\"sortOrder\":40}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.category", is("DISPLAY")))
            .andReturn();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(
            created.getResponse().getContentAsString(), "$.data.id")).longValue();

        mockMvc.perform(put("/api/platform/commercial/entitlements/{id}", id)
                .cookie(session, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"使用所有模型\",\"description\":\"新文案\",\"sortOrder\":45}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.name", is("使用所有模型")));
        mockMvc.perform(post("/api/platform/commercial/entitlements/{id}/disable", id)
                .cookie(session, csrf).header("X-XSRF-TOKEN", csrf.getValue()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status", is("INACTIVE")));
        mockMvc.perform(post("/api/platform/commercial/entitlements/{id}/enable", id)
                .cookie(session, csrf).header("X-XSRF-TOKEN", csrf.getValue()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status", is("ACTIVE")));
        mockMvc.perform(get("/api/platform/commercial/entitlements").cookie(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].code").exists());
    }

    private Cookie register(String mobile, String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"mobile\":\"" + mobile + "\",\"verificationCode\":\"123456\",\"nickname\":\"" + nickname + "\",\"password\":\"Password123\"}"))
            .andExpect(status().isOk()).andReturn();
        return result.getResponse().getCookie("ANT_SHORT_SESSION");
    }
}
