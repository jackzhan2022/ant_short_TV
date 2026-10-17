package com.antshorttv.trend;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ForeignTrendParserTest {
    String fixture(String file) throws Exception { return Files.readString(Path.of("src/test/resources/trend/"+file)); }

    @Test void flexTvCapturesAllVisibleItemsButDoesNotClaimTheFullHundred() throws Exception {
        var page = new FlexTvTrendParser(new ObjectMapper()).parse(fixture("flextv-top.html"), TrendSource.FLEXTV_TOP, 1);
        assertThat(page.entries()).hasSize(18);
        assertThat(page.reportedTotal()).isEqualTo(100);
        assertThat(page.coverage()).isEqualTo("PARTIAL_PUBLIC_PAGE");
        assertThat(page.sourceDate()).isNull();
        assertThat(page.entries().get(0).externalId()).isEqualTo("BxZ0XB7ZQ7");
        assertThat(page.entries().get(0).metricValue()).isEqualByComparingTo("1379597");
        assertThat(page.entries().get(0).metricKind()).isEqualTo("PLATFORM_VIEWS");
        assertThat(page.entries().get(0).contentType()).isEqualTo("UNKNOWN");
    }

    @Test void reelPulseReadsOnlyTheRequestedChartAndItsOwnCaptureDate() throws Exception {
        var parser = new ReelPulseTrendParser();
        var page = parser.parse(fixture("reelpulse-trends.html"), TrendSource.REELPULSE_REELSHORT_TOP, 1);
        assertThat(page.entries()).hasSize(10);
        assertThat(page.entries().get(0).title()).isEqualTo("Just One Kiss");
        assertThat(page.entries().get(0).metricText()).isEqualTo("26M");
        assertThat(page.entries().get(0).metricValue()).isEqualByComparingTo("26");
        assertThat(page.entries().get(0).metricUnit()).isEqualTo("M views");
        assertThat(page.entries().get(0).episodeCount()).isEqualTo(72);
        assertThat(page.sourceDate()).isEqualTo(LocalDate.of(2026,10,9));
        assertThat(page.coverage()).isEqualTo("THIRD_PARTY_TOP10");
        assertThat(parser.parse(fixture("reelpulse-trends.html"), TrendSource.REELPULSE_GOODSHORT_TOP,1).entries().get(0).externalId()).isEqualTo("31000835181");
        assertThat(parser.parse(fixture("reelpulse-trends.html"), TrendSource.REELPULSE_GOODSHORT_TRENDING,1).entries().get(0).externalId()).isEqualTo("31001792509");
    }

    @Test void dramaBoxExcludesEditorsPicksAndDoesNotFabricateViewCounts() throws Exception {
        var page = new ReelPulseTrendParser().parse(fixture("reelpulse-dramabox.html"), TrendSource.REELPULSE_DRAMABOX_TOP,1);
        assertThat(page.entries()).hasSize(10);
        assertThat(page.entries().get(0).title()).isEqualTo("Love Is A Dangerous Game");
        assertThat(page.entries().get(0).metricKind()).isEqualTo("EPISODE_COUNT");
        assertThat(page.entries().get(0).episodeCount()).isEqualTo(43);
        assertThat(page.sourceDate()).isEqualTo(LocalDate.of(2026,10,5));
    }

    @Test void refusesWrongSectionsMissingCaptureDatesAndUnsafeDetailLinks() throws Exception {
        var parser = new ReelPulseTrendParser();
        assertThatThrownBy(() -> parser.parse("<h1>Editor picks</h1>",TrendSource.REELPULSE_DRAMABOX_TOP,1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse(fixture("reelpulse-trends.html").replace("Oct 9, 2026","Unknown"),TrendSource.REELPULSE_REELSHORT_TOP,1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse(fixture("reelpulse-trends.html").replace("https://www.reelshort.com/movie/just-one-kiss", "https://evil.example/movie/just-one-kiss"),TrendSource.REELPULSE_REELSHORT_TOP,1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void sourceRegistryCoversTenPlatformsAndNeverAcceptsArbitraryUrlOverrides() {
        assertThat(java.util.Arrays.stream(TrendSource.values()).map(s->s.platform).distinct()).hasSize(10);
        assertThat(TrendSource.REELPULSE_DRAMABOX_TOP.sourceType).isEqualTo("THIRD_PARTY");
        assertThat(TrendSource.REELPULSE_DRAMABOX_TOP.market).isEqualTo("OVERSEAS");
        assertThat(TrendSource.FLEXTV_TOP.rankingKind).isEqualTo("SECTION_ORDER");
        assertThatThrownBy(()->TrendSource.FLEXTV_TOP.url(2)).isInstanceOf(IllegalArgumentException.class);
    }
}
