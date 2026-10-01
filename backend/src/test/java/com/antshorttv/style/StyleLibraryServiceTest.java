package com.antshorttv.style;

import static org.assertj.core.api.Assertions.assertThat;
import com.antshorttv.storage.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class StyleLibraryServiceTest {

    @Autowired
    private StyleLibraryService styleLibraryService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private ObjectStorageService objectStorageService;

    @Test
    void listsPublicStylesInDeterministicOrder() {
        var styles = styleLibraryService.list(null, null);

        assertThat(styles).hasSize(139);
        assertThat(styles.get(0).externalId()).isEqualTo("864621266010645040");
        assertThat(styles.get(0).category()).isEqualTo("3D风格");
        assertThat(styles.get(0).storagePath())
            .isEqualTo("style-library/public/864621266010645040/cover-compressed.jpg");
        assertThat(styles.get(0).imageUrl())
            .isEqualTo("/api/style-library/images/864621266010645040");
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

}
