package com.antshorttv.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class WebpImageDecodingTest {
    @Test
    void decodesWebpOriginalWithTheSameImageIoApiAsImageIngestion() throws Exception {
        byte[] bytes = Base64.getDecoder().decode(
            "UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA"
        );
        var image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(1);
        assertThat(image.getHeight()).isEqualTo(1);
    }
}
