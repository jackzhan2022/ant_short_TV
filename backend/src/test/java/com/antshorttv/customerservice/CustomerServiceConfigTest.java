package com.antshorttv.customerservice;

import static com.antshorttv.support.SessionTestSupport.authenticated;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.antshorttv.storage.ObjectStorageService;
import com.antshorttv.support.SessionTestSupport;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.transaction.annotation.Transactional
class CustomerServiceConfigTest extends com.antshorttv.support.RegistrationTestSupport {
    private static final String ADMIN = "/api/platform/customer-service";
    private static final String PUBLIC = "/api/public/customer-service";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;
    @MockBean ObjectStorageService storage;

    @Test
    void publicReadAndAdminUploadReplaceRemovePersistAndEnforcePermissions() throws Exception {
        mvc.perform(get(PUBLIC)).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.data.qrCodeUrl").isEmpty());
        mvc.perform(get(ADMIN)).andExpect(status().isUnauthorized());
        String member = register("13800020871");
        mvc.perform(get(ADMIN).with(authenticated(member))).andExpect(status().isForbidden());
        mvc.perform(multipart(HttpMethod.PUT, ADMIN).file(image()).with(authenticated(member)))
            .andExpect(status().isForbidden());
        mvc.perform(delete(ADMIN).with(authenticated(member))).andExpect(status().isForbidden());

        jdbc.update("insert into platform_role(code,name,status,created_at,updated_at) values ('SERVICE_READER','Service reader','ACTIVE',now(),now())");
        Long roleId = jdbc.queryForObject("select id from platform_role where code='SERVICE_READER'", Long.class);
        Long userId = jdbc.queryForObject("select id from app_user where mobile='13800020871'", Long.class);
        jdbc.update("insert into platform_user_role(user_id,role_id,created_at) values (?,?,now())", userId, roleId);
        jdbc.update("insert into platform_role_permission(role_id,permission_id,created_at) select ?,id,now() from platform_permission where code='PLATFORM_CUSTOMER_SERVICE_VIEW'", roleId);
        // Direct JDBC grants bypass MyBatis' transaction-local query cache in this test.
        sqlSession.clearCache();
        mvc.perform(get(ADMIN).with(authenticated(member))).andExpect(status().isOk());
        mvc.perform(multipart(HttpMethod.PUT, ADMIN).file(image()).with(authenticated(member))).andExpect(status().isForbidden());
        mvc.perform(delete(ADMIN).with(authenticated(member))).andExpect(status().isForbidden());

        String admin = register("13800000999");
        when(storage.publicUrl(anyString())).thenAnswer(call -> "https://cdn.example/" + call.getArgument(0));
        mvc.perform(multipart(HttpMethod.PUT, ADMIN).file(image()).with(authenticated(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.qrCodeUrl").isNotEmpty());
        String first = jdbc.queryForObject("select qr_code_path from customer_service_config where id=1", String.class);
        assertThat(first).startsWith("platform/customer-service/").endsWith(".png");
        mvc.perform(get(PUBLIC)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.qrCodeUrl").value("https://cdn.example/" + first));
        mvc.perform(multipart(HttpMethod.PUT, ADMIN).file(image()).with(authenticated(admin)))
            .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select qr_code_path from customer_service_config where id=1", String.class))
            .isNotEqualTo(first);
        mvc.perform(delete(ADMIN).with(authenticated(admin))).andExpect(status().isOk());
        mvc.perform(get(PUBLIC)).andExpect(status().isOk()).andExpect(jsonPath("$.data.qrCodeUrl").isEmpty());
    }

    @Test
    void rejectsDisguisedEmptyOversizedAndOverDimensionedImagesWithoutSaving() throws Exception {
        String admin = register("13800000999");
        for (var file : new MockMultipartFile[]{
            new MockMultipartFile("file", "qr.png", "image/png", "<svg>bad</svg>".getBytes()),
            new MockMultipartFile("file", "qr.png", "image/png", new byte[0]),
            new MockMultipartFile("file", "qr.png", "image/png", new byte[2 * 1024 * 1024 + 1]),
            png(4097, 1)
        }) {
            mvc.perform(multipart(HttpMethod.PUT, ADMIN).file(file).with(authenticated(admin)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        }
        assertThat(jdbc.queryForObject("select qr_code_path from customer_service_config where id=1", String.class)).isNull();
    }

    @Test
    void acceptsRealJpegAndDeterminesFormatFromBytes() throws Exception {
        String admin = register("13800000999");
        when(storage.publicUrl(anyString())).thenAnswer(call -> "https://cdn.example/" + call.getArgument(0));
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB), "jpeg", bytes);
        var file = new MockMultipartFile("file", "mislabelled.png", "image/png", bytes.toByteArray());
        mvc.perform(multipart(HttpMethod.PUT, ADMIN).file(file).with(authenticated(admin)))
            .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select qr_code_path from customer_service_config where id=1", String.class)).endsWith(".jpg");
        org.mockito.Mockito.verify(storage).upload(anyString(), org.mockito.ArgumentMatchers.eq(bytes.toByteArray()), org.mockito.ArgumentMatchers.eq("image/jpeg"));
    }

    private MockMultipartFile image() throws Exception { return png(128, 128); }
    private MockMultipartFile png(int width, int height) throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return new MockMultipartFile("file", "qr.png", "image/png", bytes.toByteArray());
    }
    private String register(String mobile) throws Exception {
        var result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
            {"mobile":"%s","verificationCode":"%s","nickname":"Service test","password":"Password123"}
            """.formatted(mobile, registrationVerificationCode(mvc, mobile))))
            .andExpect(status().isOk()).andReturn();
        return SessionTestSupport.sessionCredential(result);
    }
}
