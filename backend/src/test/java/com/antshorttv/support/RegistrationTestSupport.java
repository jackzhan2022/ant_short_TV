package com.antshorttv.support;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antshorttv.auth.SmsSender;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

public abstract class RegistrationTestSupport extends OfflineMediaTestSupport {

    @MockBean(name = "tencentCloudSmsSender")
    private SmsSender smsSender;

    protected String registrationVerificationCode(MockMvc mockMvc, String mobile) throws Exception {
        mockMvc.perform(post("/api/auth/verification-code/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mobile":"%s"}
                    """.formatted(mobile)))
            .andExpect(status().isOk());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(smsSender).sendRegistrationVerificationCode(eq(mobile), code.capture());
        return code.getValue();
    }
}
