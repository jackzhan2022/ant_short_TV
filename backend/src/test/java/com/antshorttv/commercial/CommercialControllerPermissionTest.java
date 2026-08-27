package com.antshorttv.commercial;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    }

    @Test
    void platformAdministratorCanViewPackageManagementApi() throws Exception {
        Cookie session = register("13800000999", "Platform Commercial Admin");
        mockMvc.perform(get("/api/platform/commercial/packages").cookie(session))
            .andExpect(status().isOk());
    }

    private Cookie register(String mobile, String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"mobile\":\"" + mobile + "\",\"verificationCode\":\"123456\",\"nickname\":\"" + nickname + "\",\"password\":\"Password123\"}"))
            .andExpect(status().isOk()).andReturn();
        return result.getResponse().getCookie("ANT_SHORT_SESSION");
    }
}
