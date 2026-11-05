package com.antshorttv.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.antshorttv.monitoring.FeishuWebhookClient;
import com.antshorttv.support.RegistrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/** Starts real migrations, observer beans and the security chain against a fresh offline database. */
@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:operations_application;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "monitoring.enabled=false",
    "monitoring.feishu-webhook-url=",
    "ai.testing.mock-provider-enabled=true"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class OperationsApplicationIntegrationTest extends RegistrationTestSupport {
    @Autowired ApplicationContext context;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate businessJdbc;
    @Autowired OperationsDatabase operations;
    @Autowired OperationsSettingsService settings;
    @Autowired ObjectMapper json;
    @MockBean FeishuWebhookClient webhook;

    @Test void realApplicationMigratesBeforeDefaultRulesAndKeepsBusinessDataSourceWiring() {
        assertThat(context.getBeansOfType(DataSource.class)).hasSize(1);
        assertThat(context.getBeansOfType(JdbcTemplate.class)).hasSize(1);
        assertThat(operations.jdbc()).isNotSameAs(businessJdbc);
        assertThat(operations.jdbc().getDataSource()).isNotSameAs(businessJdbc.getDataSource());
        assertThat(operations.jdbc().getDataSource()).isInstanceOf(HikariDataSource.class);
        assertThat(((HikariDataSource)operations.jdbc().getDataSource()).getMaximumPoolSize()).isEqualTo(2);
        assertThat(businessJdbc.queryForObject("select count(*) from flyway_schema_history where version='174' and success=true",Integer.class)).isEqualTo(1);
        assertThat(businessJdbc.queryForObject("select count(*) from ops_alert_rule",Integer.class)).isEqualTo(11);
        assertThat(businessJdbc.queryForObject("select max(version) from ops_alert_rule",Long.class)).isZero();
        assertThat(settings.current()).containsEntry("telemetryEnabled",true).containsEntry("notificationsEnabled",false)
            .containsEntry("channelConfigured",false).containsEntry("persistentDigestEnabled",true);
        verifyNoInteractions(webhook);
    }

    @Test void anonymousExternalProbeWorksWhilePlatformRoutesFailClosed() throws Exception {
        var response=mvc.perform(get("/api/public/operations-health")).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP")).andReturn();
        var fields=new ArrayList<String>();json.readTree(response.getResponse().getContentAsByteArray()).fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("status","build");
        for(String path:new String[]{"snapshot","rules","events","settings"})
            mvc.perform(get("/api/platform/operations/"+path)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
        verifyNoInteractions(webhook);
    }

    @Test void tenantOwnerCannotReadOperationsAndBootstrappedPlatformAdminCanReadRulesPage() throws Exception {
        Cookie owner=register("13800005201","Operations Tenant Owner");
        var safe=mvc.perform(get("/api/tenants/my").cookie(owner)).andExpect(status().isOk()).andReturn();
        Cookie csrf=safe.getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(post("/api/tenants").cookie(owner,csrf).header("X-XSRF-TOKEN",csrf.getValue())
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Operations Owner Tenant\",\"type\":\"STUDIO\",\"description\":\"offline permission fixture\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/platform/operations/rules").cookie(owner)).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        Cookie admin=register("13800000999","Operations Platform Admin");
        mvc.perform(get("/api/platform/operations/rules").cookie(admin)).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data.items.length()").value(11))
            .andExpect(jsonPath("$.data.total").value(11));
        mvc.perform(get("/api/platform/operations/snapshot").cookie(admin)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.externalProbeConfigured").value(false));
        verifyNoInteractions(webhook);
    }

    private Cookie register(String mobile,String nickname) throws Exception {
        var result=mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(java.util.Map.of("mobile",mobile,"nickname",nickname,"password","Password123",
                "verificationCode",registrationVerificationCode(mvc,mobile)))))
            .andExpect(status().isOk()).andReturn();
        Cookie session=result.getResponse().getCookie("ANT_SHORT_SESSION");
        assertThat(session).isNotNull();return session;
    }
}
