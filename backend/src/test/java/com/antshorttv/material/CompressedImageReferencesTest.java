package com.antshorttv.material;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

public class CompressedImageReferencesTest {
    @Test
    void resolvesOriginalIdentityToAuthorizedCompressedEntry() {
        assertThat(CompressedImageReferences.thumbnail("/api/projects/4/ai-image-results/19/download"))
            .isEqualTo("/api/projects/4/ai-image-results/19/thumbnail");
        assertThat(CompressedImageReferences.thumbnail("/api/projects/4/ai-image-results/19/display"))
            .isEqualTo("/api/projects/4/ai-image-results/19/thumbnail");
    }

    @Test
    void unknownOriginalNeverBecomesACoverFallback() {
        assertThat(CompressedImageReferences.thumbnail("https://provider.example/original.png")).isNull();
        assertThat(CompressedImageReferences.thumbnail("data:image/png;base64,abc")).isNull();
        assertThat(CompressedImageReferences.thumbnail(null)).isNull();
    }

    public static void main(String[] args) {
        var tests = new CompressedImageReferencesTest();
        tests.resolvesOriginalIdentityToAuthorizedCompressedEntry();
        tests.unknownOriginalNeverBecomesACoverFallback();
    }
}
