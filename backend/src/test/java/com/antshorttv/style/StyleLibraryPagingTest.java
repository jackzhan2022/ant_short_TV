package com.antshorttv.style;

import static org.assertj.core.api.Assertions.assertThat;
import com.antshorttv.storage.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class StyleLibraryPagingTest {
    @Autowired private StyleLibraryService service;
    @org.springframework.boot.test.mock.mockito.MockBean private ObjectStorageService storage;

    @Test
    void pagesDatabaseRowsAndKeepsAllCategoriesIndependentOfPage() {
        var first = service.list(null, null, 1, 20);
        var second = service.list(null, null, 2, 20);
        assertThat(first.total()).isEqualTo(139);
        assertThat(first.data()).hasSize(20);
        assertThat(second.data()).hasSize(20);
        assertThat(second.data()).extracting(StyleLibraryResponse::id)
            .doesNotContainAnyElementsOf(first.data().stream().map(StyleLibraryResponse::id).toList());
        assertThat(service.categories()).contains("3D风格");
        assertThat(service.list(null, null, Integer.MAX_VALUE, 100).data()).isEmpty();
        assertThat(service.list("3D风格", null, 1, 1000).total()).isEqualTo(29);
        assertThat(service.list(null, null, 0, 0).pageSize()).isEqualTo(20);
    }
}
