package com.antshorttv.trend;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HongguoTrendParserTest {
    private final HongguoTrendParser parser = new HongguoTrendParser(new ObjectMapper());

    static String fixture() throws Exception {
        return Files.readString(Path.of("src/test/resources/trend/hongguo-rank.html"));
    }

    @Test void parsesDeferredJsonWithoutExecutingScriptsOrTruncatingToFive() throws Exception {
        var page = parser.parse(fixture(), TrendSource.HONGGUO_HOT, 1);
        assertThat(page.entries()).hasSize(20);
        assertThat(page.totalPages()).isEqualTo(5);
        assertThat(page.entries().get(0).title()).isEqualTo("聚宝仙盆之杂灵根才是真BOSS第十三季");
        assertThat(page.entries().get(0).metricText()).isEqualTo("8023万热度");
        assertThat(page.entries().get(0).metricValue()).isEqualByComparingTo("8023");
        assertThat(page.entries().get(0).metricUnit()).isEqualTo("万热度");
        assertThat(page.entries().get(0).contentType()).isEqualTo("UNKNOWN");
        assertThat(page.sourceUpdatedText()).contains("10月9日");
    }

    @Test void readsRenderedRankingWhenSsrStreamOnlyContainsAPendingPromise() throws Exception {
        var html = Files.readString(Path.of("src/test/resources/trend/hongguo-rendered-rank.html"));
        var result = parser.parse(html, TrendSource.HONGGUO_REAL, 1);
        assertThat(result.entries()).hasSize(20);
        assertThat(result.totalPages()).isEqualTo(5);
        assertThat(result.entries().get(0).title()).isEqualTo("首辅娇娘");
        assertThat(result.entries().get(0).contentType()).isEqualTo("LIVE_ACTION");
        assertThat(result.entries().get(0).metricText()).isEqualTo("6721万热度");
    }

    @Test void refusesMissingOrChangedStructureInsteadOfPublishingAnEmptySuccess() {
        assertThatThrownBy(() -> parser.parse("<html>captcha</html>", TrendSource.HONGGUO_HOT, 1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void refusesWrongPageAndMissingRanks() throws Exception {
        assertThatThrownBy(() -> parser.parse(fixture(), TrendSource.HONGGUO_HOT, 2))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse(fixture().replace("\\&quot;rank\\&quot;:1,", ""), TrendSource.HONGGUO_HOT, 1))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
