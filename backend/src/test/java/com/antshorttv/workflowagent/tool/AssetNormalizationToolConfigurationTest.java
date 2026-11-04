package com.antshorttv.workflowagent.tool;

import static org.assertj.core.api.Assertions.assertThatCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AssetNormalizationToolConfigurationTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void acceptsSourceReferencesAndLegacyLiteralQuotes() throws Exception {
        var schema = new AssetNormalizationToolConfiguration().saveNormalization(
            org.mockito.Mockito.mock(com.antshorttv.script.AssetNormalizationService.class),json).inputSchema();
        for (String evidence : new String[]{
            "{\"episodeId\":70,\"quoteRef\":{\"segmentId\":\"S0001\"}}",
            "{\"episodeId\":70,\"quote\":\"水龙头哗哗流水\"}"}) {
            var request=json.readTree("""
                {"groups":[{"assetType":"PROP","retainId":1,"memberIds":[1],"evidence":[%s]}],
                 "splits":[{"assetType":"PROP","sourceId":1,"targetId":null,"newName":"水龙头","bindingIds":[1],"evidence":[%s]}]}
                """.formatted(evidence,evidence));
            assertThatCode(()->new WorkflowToolSchemaValidator().validate(schema,request)).doesNotThrowAnyException();
        }
    }
}
