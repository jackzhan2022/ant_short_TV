package com.antshorttv.style;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.antshorttv.storage.ObjectStorageService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class StyleLibraryServiceTest {

    @Autowired
    private StyleLibraryService styleLibraryService;

    @Test
    void listsPublicStylesInDeterministicOrder() {
        var styles = styleLibraryService.list(null, null);

        assertThat(styles).hasSize(139);
        assertThat(styles.get(0).externalId()).isEqualTo("864621266010645040");
        assertThat(styles.get(0).category()).isEqualTo("3D风格");
        assertThat(styles.get(0).storagePath()).isEqualTo("style-library/public/864621266010645040/cover.png");
        assertThat(styles.get(0).imageUrl()).isEqualTo("/api/style-library/images/864621266010645040");
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

        assertThat(path).isEqualTo("style-library/public/864621266010645040/cover.png");
        assertThat(path).doesNotContain("materials/");
    }

    @Test
    void transfersSourceImageToObjectStorage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/style.png", exchange -> {
            byte[] body = "image-bytes".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
        StyleLibraryImageStorage storage = new StyleLibraryImageStorage(objectStorageService);

        try {
            storage.transfer(
                "864621266010645040",
                "http://127.0.0.1:%d/style.png".formatted(server.getAddress().getPort())
            );
        } finally {
            server.stop(0);
        }

        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(objectStorageService).upload(
            eq("style-library/public/864621266010645040/cover.png"),
            bytes.capture(),
            eq("image/png")
        );
        assertThat(bytes.getValue()).containsExactly("image-bytes".getBytes());
    }
}
