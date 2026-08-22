package com.antshorttv.style;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.storage.ObjectStorageService;
import com.sun.net.httpserver.HttpServer;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class StyleLibraryServiceTest {

    @Autowired
    private StyleLibraryService styleLibraryService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private ObjectStorageService objectStorageService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        when(objectStorageService.publicUrl(anyString()))
            .thenAnswer(invocation -> "https://minio.aixmax.cn/ant-short-tv/" + invocation.getArgument(0) + "?X-Amz-Signature=test");
    }

    @Test
    void listsPublicStylesInDeterministicOrder() {
        var styles = styleLibraryService.list(null, null);

        assertThat(styles).hasSize(139);
        assertThat(styles.get(0).externalId()).isEqualTo("864621266010645040");
        assertThat(styles.get(0).category()).isEqualTo("3D风格");
        assertThat(styles.get(0).storagePath()).isEqualTo("style-library/public/864621266010645040/cover-compressed.jpg");
        assertThat(styles.get(0).imageUrl())
            .startsWith("https://minio.aixmax.cn/ant-short-tv/style-library/public/864621266010645040/cover-compressed.jpg?");
        assertThat(styles.get(0).imageUrl()).contains("X-Amz-Signature=");
        assertThat(styles.get(0).imageUrl()).doesNotContain("/console/api/v1/download-shared-object/");
    }

    @Test
    void filtersByCategoryAndKeyword() {
        var categoryMatches = styleLibraryService.list("3D风格", null);
        var keywordMatches = styleLibraryService.list(null, "赛博朋克");
        var emptyMatches = styleLibraryService.list("3D风格", "不存在的风格");

        assertThat(categoryMatches).hasSize(29);
        assertThat(categoryMatches).allSatisfy(style -> assertThat(style.category()).isEqualTo("3D风格"));
        assertThat(keywordMatches).extracting(StyleLibraryResponse::name)
            .anySatisfy(name -> assertThat(name).contains("赛博朋克"));
        assertThat(emptyMatches).isEmpty();
    }

    @Test
    void derivesObjectStoragePathWithoutProjectMaterialScope() {
        String path = StyleLibraryImageStorage.storagePath("864621266010645040", "https://example.com/a.png");

        assertThat(path).isEqualTo("style-library/public/864621266010645040/cover-compressed.jpg");
        assertThat(path).doesNotContain("materials/");
    }

    @Test
    void transfersSourceImageToObjectStorage() throws Exception {
        BufferedImage source = new BufferedImage(2048, 1152, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                source.setRGB(x, y, new Color((x * 17 + y) % 256, (x + y * 13) % 256, (x * 3 + y * 5) % 256).getRGB());
            }
        }
        ByteArrayOutputStream sourceBytes = new ByteArrayOutputStream();
        ImageIO.write(source, "png", sourceBytes);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/style.png", exchange -> {
            byte[] body = sourceBytes.toByteArray();
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        ObjectStorageService storageService = mock(ObjectStorageService.class);
        StyleLibraryImageStorage storage = new StyleLibraryImageStorage(storageService);

        try {
            storage.transfer(
                "864621266010645040",
                "http://127.0.0.1:%d/style.png".formatted(server.getAddress().getPort())
            );
        } finally {
            server.stop(0);
        }

        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(storageService).upload(
            eq("style-library/public/864621266010645040/cover-compressed.jpg"),
            bytes.capture(),
            eq("image/jpeg")
        );
        BufferedImage compressed = ImageIO.read(new java.io.ByteArrayInputStream(bytes.getValue()));
        assertThat(compressed.getWidth()).isEqualTo(1280);
        assertThat(compressed.getHeight()).isEqualTo(720);
        assertThat(bytes.getValue().length).isLessThan(sourceBytes.size());
    }
}
