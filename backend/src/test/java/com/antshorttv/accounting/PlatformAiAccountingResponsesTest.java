package com.antshorttv.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import com.antshorttv.points.AiPointPolicyComponentEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlatformAiAccountingResponsesTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void costComponentResponseReturnsStoredConditions() {
        AiModelPriceComponentEntity component = new AiModelPriceComponentEntity();
        component.dimensionsJson = "{\"resolution\":\"720p\",\"referenceVideo\":\"false\"}";

        var json = mapper.valueToTree(ModelPriceComponentResponse.from(component));

        assertThat(json.path("dimensions").path("resolution").asText()).isEqualTo("720p");
        assertThat(json.path("dimensions").path("referenceVideo").asText()).isEqualTo("false");
    }

    @Test
    void pointComponentResponseReturnsStoredConditions() {
        AiPointPolicyComponentEntity component = new AiPointPolicyComponentEntity();
        component.dimensionsJson = "{\"resolution\":\"1080p\",\"referenceVideo\":\"true\"}";

        var json = mapper.valueToTree(PointPolicyComponentResponse.from(component));

        assertThat(json.path("dimensions").path("resolution").asText()).isEqualTo("1080p");
        assertThat(json.path("dimensions").path("referenceVideo").asText()).isEqualTo("true");
    }

    @Test
    void legacyCostComponentsReturnAnEmptyConditionsObject() {
        var json = mapper.valueToTree(ModelPriceComponentResponse.from(new AiModelPriceComponentEntity()));

        assertThat(json.path("dimensions").isObject()).isTrue();
        assertThat(json.path("dimensions").size()).isZero();
    }

    @Test
    void legacyPointComponentsReturnAnEmptyConditionsObject() {
        var json = mapper.valueToTree(PointPolicyComponentResponse.from(new AiPointPolicyComponentEntity()));

        assertThat(json.path("dimensions").isObject()).isTrue();
        assertThat(json.path("dimensions").size()).isZero();
    }
    @Test
    void modelPointPriceHistoryReturnsStoredConditions() {
        AiModelPointPriceComponentEntity component = new AiModelPointPriceComponentEntity();
        component.dimensionsJson = "{\"resolution\":\"720p\",\"referenceVideo\":\"false\"}";

        var json = mapper.valueToTree(ModelPointPriceVersionResponse.from(
            new AiModelPointPriceVersionEntity(), List.of(component)));

        assertThat(json.path("components").get(0).path("dimensions").path("resolution").asText())
            .isEqualTo("720p");
        assertThat(json.path("components").get(0).path("dimensions").path("referenceVideo").asText())
            .isEqualTo("false");
    }

}