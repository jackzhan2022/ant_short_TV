package com.antshorttv.video;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VideoReferenceDiagnosticsTest {
    @ParameterizedTest
    @ValueSource(strings = {"VOLCENGINE_ARK", "VOLCENGINE_ARK_XINJI"})
    void resolvesRejectedReferenceImagesForBothArkSuppliers(String providerCode) {
        var diagnostics = VideoReferenceDiagnostics.from(providerCode, null,
            "InputImageSensitiveContentDetected.PrivacyInformation content[1]", "/image/first-frame");
        assertThat(diagnostics.at("/failedImages/0/contentIndex").asInt()).isEqualTo(1);
        assertThat(diagnostics.at("/failedImages/0/imageUrl").asText()).isEqualTo("/image/first-frame");
    }
}
