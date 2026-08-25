package com.antshorttv.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.common.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AiModelRouterTest {

    @Autowired
    private AiModelRouter router;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long providerId;

    @BeforeEach
    void enableOpenAiProvider() {
        providerId = jdbcTemplate.queryForObject(
            "select id from ai_provider where code = 'OpenAI' limit 1",
            Long.class
        );
        jdbcTemplate.update("update ai_provider set status = 'ENABLED' where id = ?", providerId);
        jdbcTemplate.update("update ai_provider_config set status = 'ENABLED' where provider_id = ?", providerId);
    }

    @Test
    void rejectsExplicitModelWithoutEnabledCapability() {
        Long modelId = insertModel("ROUTER_NO_CAPABILITY", "TEXT", false, 990);

        assertThatThrownBy(() -> router.route(modelId, "TEXT"))
            .isInstanceOf(AiGatewayException.class)
            .extracting(exception -> ((AiGatewayException) exception).getErrorCode())
            .isEqualTo(ErrorCode.AI_MODEL_NOT_FOUND);
    }

    @Test
    void rejectsExplicitModelWithDisabledCapability() {
        Long modelId = insertModel("ROUTER_DISABLED_CAPABILITY", "IMAGE", false, 991);
        insertCapability(modelId, "IMAGE_GENERATION", "DISABLED");

        assertThatThrownBy(() -> router.route(modelId, "IMAGE"))
            .isInstanceOf(AiGatewayException.class)
            .extracting(exception -> ((AiGatewayException) exception).getErrorCode())
            .isEqualTo(ErrorCode.AI_MODEL_NOT_FOUND);
    }

    @Test
    void defaultSelectionSkipsModelWithoutRequestedCapability() {
        jdbcTemplate.update("update ai_model set is_default = false, status = 'DISABLED' where service_type = 'TEXT'");
        Long unavailableId = insertModel("ROUTER_DEFAULT_NO_CAPABILITY", "TEXT", true, 1000);
        Long availableId = insertModel("ROUTER_FALLBACK_WITH_CAPABILITY", "TEXT", false, 999);
        insertCapability(availableId, "TEXT_GENERATION", "ENABLED");

        AiModelRoute route = router.route(null, "TEXT");

        assertThat(route.model().getId()).isEqualTo(availableId);
        assertThat(route.model().getId()).isNotEqualTo(unavailableId);
    }

    private Long insertModel(String code, String serviceType, boolean isDefault, int sort) {
        jdbcTemplate.update(
            """
                insert into ai_model
                  (provider_id, code, name, model_code, service_type, status, is_default, sort, created_at, updated_at)
                values (?, ?, ?, ?, ?, 'ENABLED', ?, ?, now(), now())
                """,
            providerId, code, code, code.toLowerCase(), serviceType, isDefault, sort
        );
        return jdbcTemplate.queryForObject("select id from ai_model where code = ?", Long.class, code);
    }

    private void insertCapability(Long modelId, String capability, String status) {
        jdbcTemplate.update(
            """
                insert into ai_model_capability
                  (model_id, capability, status, created_at, updated_at)
                values (?, ?, ?, now(), now())
                """,
            modelId, capability, status
        );
    }
}
