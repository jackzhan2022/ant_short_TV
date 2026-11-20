package com.antshorttv.trend;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.antshorttv.support.RegistrationTestSupport;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TrendSecurityIntegrationTest extends RegistrationTestSupport {
    @Autowired MockMvc mvc;
    @MockBean TrendCollectionService collections;
    @Autowired TrendStore store;

    @Test void authenticatedReadersCannotCollectButPlatformAdminCanWithCsrf() throws Exception {
        mvc.perform(get("/api/short-drama-trends/sources")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/platform/short-drama-trends/collections/test/diagnostics")).andExpect(status().isUnauthorized());
        Cookie reader = register("13800000787");
        Cookie readerCsrf = mvc.perform(get("/api/short-drama-trends/sources").cookie(reader))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(17))
            .andReturn().getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(get("/api/short-drama-trends/snapshots").cookie(reader))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(post("/api/platform/short-drama-trends/collections/HONGGUO_HOT")
            .cookie(reader,readerCsrf).header("X-XSRF-TOKEN",readerCsrf.getValue()))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/platform/short-drama-trends/collections/test").cookie(reader))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/platform/short-drama-trends/collections/test/diagnostics").cookie(reader))
            .andExpect(status().isForbidden());
        verifyNoInteractions(collections);

        Cookie admin = register("13800000999");
        Cookie adminCsrf = mvc.perform(get("/api/short-drama-trends/sources").cookie(admin))
            .andExpect(status().isOk()).andReturn().getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(post("/api/platform/short-drama-trends/collections/HONGGUO_HOT").cookie(admin))
            .andExpect(status().isForbidden());
        when(collections.collect(eq(TrendSource.HONGGUO_HOT), anyLong())).thenReturn(
            new TrendModels.Run("run-test","HONGGUO_HOT","SUCCEEDED",Instant.now(),Instant.now(),"snapshot-test",100,null));
        mvc.perform(post("/api/platform/short-drama-trends/collections/HONGGUO_HOT")
            .cookie(admin,adminCsrf).header("X-XSRF-TOKEN",adminCsrf.getValue()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.entryCount").value(100));
        verify(collections, times(1)).collect(eq(TrendSource.HONGGUO_HOT), anyLong());
        String diagnosticRun=store.start(TrendSource.REELSHORT_TOP,null,Instant.now());
        store.capture(diagnosticRun,0,TrendSource.REELSHORT_TOP.robotsUrl().toString(),
            new TrendModels.HttpPage(403,"text/html","<h1>Forbidden</h1>"),Instant.now());
        store.finish(diagnosticRun,"BLOCKED",null,0,"无法确认 robots.txt","ROBOTS_UNAVAILABLE_403","ROBOTS_VALIDATE",Instant.now());
        mvc.perform(get("/api/platform/short-drama-trends/collections/"+diagnosticRun+"/diagnostics").cookie(admin))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.run.failureStage").value("ROBOTS_VALIDATE"))
            .andExpect(jsonPath("$.data.requests[0].httpStatus").value(403))
            .andExpect(jsonPath("$.data.requests[0].body").doesNotExist());
        mvc.perform(get("/api/platform/short-drama-trends/collections/missing/diagnostics").cookie(admin))
            .andExpect(status().isNotFound());
    }

    private Cookie register(String mobile) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"mobile":"%s","verificationCode":"%s","nickname":"趋势测试","password":"Password123"}
                """.formatted(mobile,registrationVerificationCode(mvc,mobile))))
            .andExpect(status().isOk()).andReturn().getResponse().getCookie("ANT_SHORT_SESSION");
    }
}
