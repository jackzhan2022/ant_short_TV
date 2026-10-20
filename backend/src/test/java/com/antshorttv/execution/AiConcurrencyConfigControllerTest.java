package com.antshorttv.execution;

import static com.antshorttv.support.SessionTestSupport.authenticated;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antshorttv.support.SessionTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=13")
@AutoConfigureMockMvc
class AiConcurrencyConfigControllerTest extends com.antshorttv.support.RegistrationTestSupport {
    private static final String PATH = "/api/platform/ai/concurrency-config";
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private AiConcurrencyConfigService service;
    @Autowired private DataSource dataSource;

    @Test
    void persistsValidatedSettingsReportsActualPoolAndEnforcesSeparatePlatformPermissions() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        var admin = register("13800000999");
        mvc.perform(get(PATH).with(authenticated(admin.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.settings.teamTextLimit", is(5)))
            .andExpect(jsonPath("$.data.settings.teamImageLimit", is(5)))
            .andExpect(jsonPath("$.data.settings.teamVideoLimit", is(3)))
            .andExpect(jsonPath("$.data.settings.modelLimit", is(4)))
            .andExpect(jsonPath("$.data.settings.providerLimit", is(4)))
            .andExpect(jsonPath("$.data.settings.textWorkers", is(2)))
            .andExpect(jsonPath("$.data.settings.imageWorkers", is(2)))
            .andExpect(jsonPath("$.data.settings.videoSubmitWorkers", is(1)))
            .andExpect(jsonPath("$.data.settings.videoQueryWorkers", is(1)))
            .andExpect(jsonPath("$.data.databaseMaxConnections", is(dataSource.unwrap(HikariDataSource.class).getMaximumPoolSize())));
        var saved = new AiConcurrencySettings(2, 3, 1, 2, 1, 1, 2, 1, 2);
        mvc.perform(put(PATH).with(authenticated(admin.token())).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(saved)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.settings.teamTextLimit", is(2)))
            .andExpect(jsonPath("$.data.databaseMaxConnections", is(13)));
        assertThat(service.current()).isEqualTo(saved);
        mvc.perform(get(PATH).with(authenticated(admin.token())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.settings.modelLimit", is(2)));
        for (String invalid : new String[]{"{}", "{\"providerLimit\":0}",
            json.writeValueAsString(saved).replace("\"textWorkers\":1", "\"textWorkers\":201"),
            json.writeValueAsString(saved).replace("\"modelLimit\":2", "\"modelLimit\":10001")}) {
            mvc.perform(put(PATH).with(authenticated(admin.token())).contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")));
        }
        assertThat(service.current()).isEqualTo(saved);

        var owner = register("13800020701");
        mvc.perform(post("/api/tenants").with(authenticated(owner.token())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Concurrency tenant owner\",\"type\":\"STUDIO\"}"))
            .andExpect(status().isOk());
        mvc.perform(get(PATH).with(authenticated(owner.token()))).andExpect(status().isForbidden());
        mvc.perform(put(PATH).with(authenticated(owner.token())).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(saved))).andExpect(status().isForbidden());

        var restricted = register("13800020702");
        jdbc.update("""
            insert into platform_role(code,name,status,created_at,updated_at)
            values ('CONCURRENCY_READER','Concurrency reader','ACTIVE',now(),now())
            """);
        Long roleId = jdbc.queryForObject("select id from platform_role where code='CONCURRENCY_READER'", Long.class);
        jdbc.update("insert into platform_user_role(user_id,role_id,created_at) values (?,?,now())", restricted.id(), roleId);
        jdbc.update("""
            insert into platform_role_permission(role_id,permission_id,created_at)
            select ?,id,now() from platform_permission where code='PLATFORM_AI_CONCURRENCY_VIEW'
            """, roleId);
        mvc.perform(get(PATH).with(authenticated(restricted.token()))).andExpect(status().isOk());
        mvc.perform(put(PATH).with(authenticated(restricted.token())).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(saved))).andExpect(status().isForbidden());
        jdbc.update("delete from platform_role_permission where role_id=?", roleId);
        jdbc.update("""
            insert into platform_role_permission(role_id,permission_id,created_at)
            select ?,id,now() from platform_permission where code='PLATFORM_AI_CONCURRENCY_EDIT'
            """, roleId);
        mvc.perform(get(PATH).with(authenticated(restricted.token()))).andExpect(status().isForbidden());
        mvc.perform(put(PATH).with(authenticated(restricted.token())).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(saved))).andExpect(status().isOk());
    }

    private Registration register(String mobile) throws Exception {
        var result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mobile":"%s","verificationCode":"%s","nickname":"Concurrency test","password":"Password123"}
                    """.formatted(mobile, registrationVerificationCode(mvc, mobile))))
            .andExpect(status().isOk()).andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.user.id");
        return new Registration(id.longValue(), SessionTestSupport.sessionCredential(result));
    }

    private record Registration(Long id, String token) { }
}
