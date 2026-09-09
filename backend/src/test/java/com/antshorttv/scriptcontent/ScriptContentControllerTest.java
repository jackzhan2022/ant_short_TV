package com.antshorttv.scriptcontent;

import static org.hamcrest.Matchers.is;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antshorttv.support.SessionTestSupport;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ScriptContentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void parsesDocxWithoutPersistingReviewData() throws Exception {
        String token = registerUser("13800019001", "剧本导入用户");
        Long tenantId = createTenant(token, "剧本导入团队");
        Long projectsBefore = count("review_project");
        Long versionsBefore = count("review_script_version");

        mockMvc.perform(multipart("/api/script-content/parse")
                .file(new MockMultipartFile(
                    "file",
                    "story.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    docxBytes("第一集", "开场")
                ))
                .with(SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.fileName", is("story.docx")))
            .andExpect(jsonPath("$.data.content", is("第一集\n开场")));

        assertThat(count("review_project")).isEqualTo(projectsBefore);
        assertThat(count("review_script_version")).isEqualTo(versionsBefore);
    }

    @Test
    void rejectsUnsupportedAndEmptyFiles() throws Exception {
        String token = registerUser("13800019002", "剧本校验用户");
        Long tenantId = createTenant(token, "剧本校验团队");

        mockMvc.perform(multipart("/api/script-content/parse")
                .file(new MockMultipartFile("file", "story.pdf", "application/pdf", "pdf".getBytes()))
                .with(SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorMessage", is("仅支持 txt、md、docx 文件。")));

        mockMvc.perform(multipart("/api/script-content/parse")
                .file(new MockMultipartFile("file", "story.docx", "application/octet-stream", new byte[0]))
                .with(SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorMessage", is("请选择非空剧本文件。")));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(multipart("/api/script-content/parse")
                .file(new MockMultipartFile("file", "story.txt", MediaType.TEXT_PLAIN_VALUE, "content".getBytes()))
                .with(csrf()))
            .andExpect(status().isUnauthorized());
    }

    private byte[] docxBytes(String... paragraphs) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String paragraph : paragraphs) {
                document.createParagraph().createRun().setText(paragraph);
            }
            document.write(output);
            return output.toByteArray();
        }
    }

    private String registerUser(String mobile, String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mobile":"%s","verificationCode":"123456","nickname":"%s","password":"Password123"}
                    """.formatted(mobile, nickname)))
            .andExpect(status().isOk())
            .andReturn();
        return SessionTestSupport.sessionCredential(result);
    }

    private Long createTenant(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tenants")
                .with(SessionTestSupport.authenticated(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","type":"STUDIO","description":"剧本内容解析测试"}
                    """.formatted(name)))
            .andExpect(status().isOk())
            .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private Long count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
    }
}
