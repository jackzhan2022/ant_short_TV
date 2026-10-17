package com.antshorttv.trend;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static com.antshorttv.trend.TrendModels.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.*;
import java.util.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.HtmlUtils;

class TrendCollectionServiceTest {
    JdbcTemplate jdbc;
    TrendStore store;
    TrendHttpClient client;
    TrendCollectionService service;
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    final Clock clock = Clock.fixed(Instant.parse("2026-10-09T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach void setup() throws Exception {
        var ds = database("jdbc:h2:mem:trend_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        store = new TrendStore(jdbc, new TransactionTemplate(new DataSourceTransactionManager(ds)), json);
        client = mock(TrendHttpClient.class);
        when(client.fetch(any())).thenAnswer(inv -> {
            URI uri = inv.getArgument(0);
            if (uri.getPath().equals("/robots.txt")) return new HttpPage(200, "text/plain", "User-agent: *\nAllow: /rank/\nDisallow: /player/");
            int page = uri.getQuery() == null ? 1 : Integer.parseInt(uri.getQuery().substring(5));
            return new HttpPage(200, "text/html", pageHtml(page));
        });
        service = new TrendCollectionService(client, new HongguoTrendParser(json), store, clock, Duration.ZERO);
    }

    static JdbcDataSource database(String url) {
        var ds = new JdbcDataSource(); ds.setURL(url);
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("create table if not exists platform_permission (id bigint auto_increment primary key, code varchar(100), name varchar(100), resource varchar(100), action varchar(100), created_at datetime, updated_at datetime)");
        jdbc.execute("create table if not exists platform_role (id bigint primary key, code varchar(100))");
        jdbc.execute("create table if not exists platform_role_permission (role_id bigint, permission_id bigint, created_at datetime)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V134__short_drama_trend_collection.sql"), new ClassPathResource("db/migration/V135__multi_platform_trend_metadata.sql"), new ClassPathResource("db/migration/V136__trend_request_diagnostics.sql")).execute(ds);
        return ds;
    }

    String pageHtml(int page) throws Exception {
        var entries = new ArrayList<Map<String,Object>>();
        for (int n = 1; n <= 6; n++) {
            int rank = (page-1)*6+n;
            entries.add(Map.of("rank", rank, "seriesId", String.valueOf(1000+rank), "title", "示例剧"+rank, "heatText", "1000万热度", "tags", List.of("剧情")));
        }
        var content = Map.of("isSuccess", true, "rankList", entries, "pagination", Map.of("pageNum", page, "totalPages", 2,
            "nextUrl", page == 1 ? TrendSource.HONGGUO_HOT.url(2).toString() : ""));
        var route = Map.of("loaderData", Map.of("rank_hot-drama/page", Map.of("pageNum", page, "updatedText", "10月9日已更新", "content", content)));
        return "<script>_ROUTER_DATA = " + json.writeValueAsString(route) + ";</script>";
    }

    Query query(String type) { return new Query("DOMESTIC", "HONGGUO", type, "OFFICIAL", LocalDate.parse("2026-10-09"), LocalDate.parse("2026-10-09"), 1, 20); }

    @Test void collectsAllPagesAndPersistsQueryableImmutableSnapshot() {
        var run = service.collect(TrendSource.HONGGUO_HOT, 42L);
        assertThat(run.status()).isEqualTo("SUCCEEDED");
        assertThat(run.entryCount()).isEqualTo(12);
        var snapshots = store.query(query("MIXED"));
        assertThat(snapshots.total()).isEqualTo(1);
        var detail = store.detail(run.snapshotId(), 2, 5);
        assertThat(detail.entries().total()).isEqualTo(12);
        assertThat(detail.entries().records()).hasSize(5);
        assertThat(detail.entries().records().get(0).rank()).isEqualTo(6);
        assertThat(detail.snapshot().observedDate()).isEqualTo(LocalDate.of(2026,10,9));
        assertThat(jdbc.queryForObject("select count(*) from trend_collection_page where run_id=?", Integer.class, run.id())).isEqualTo(3);
        assertThat(store.query(query("AI")).records()).isEmpty();
        assertThat(store.query(new Query("OVERSEAS", null, null, null, null, null, 1, 20)).records()).isEmpty();
    }

    @Test void sameDaySameContentReusesSnapshotButRetainsEveryCollectionRun() {
        var first = service.collect(TrendSource.HONGGUO_HOT, 42L);
        var second = service.collect(TrendSource.HONGGUO_HOT, 42L);
        assertThat(second.snapshotId()).isEqualTo(first.snapshotId());
        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(store.query(query(null)).total()).isEqualTo(1);
    }

    @Test void failedLaterPageKeepsRawEvidenceAndDoesNotReplaceExistingSnapshot() throws Exception {
        var first = service.collect(TrendSource.HONGGUO_HOT, 42L);
        when(client.fetch(TrendSource.HONGGUO_HOT.url(2))).thenReturn(new HttpPage(403, "text/html", "Access denied"));
        var failed = service.collect(TrendSource.HONGGUO_HOT, 42L);
        assertThat(failed.status()).isEqualTo("BLOCKED");
        assertThat(failed.error()).contains("403");
        assertThat(failed.snapshotId()).isNull();
        assertThat(store.query(query(null)).records().get(0).id()).isEqualTo(first.snapshotId());
        assertThat(jdbc.queryForObject("select body from trend_collection_page where run_id=? and page_number=2", String.class, failed.id())).isEqualTo("Access denied");
    }

    @Test void robotsDenialStopsBeforeFetchingTheRanking() throws Exception {
        when(client.fetch(URI.create("https://hongguoduanju.com/robots.txt"))).thenReturn(new HttpPage(200, "text/plain", "User-agent: *\nDisallow: /rank/"));
        assertThat(service.collect(TrendSource.HONGGUO_HOT, 42L).status()).isEqualTo("BLOCKED");
        verify(client, never()).fetch(TrendSource.HONGGUO_HOT.url(1));
    }

    @Test void duplicatePageEntriesNeverPublishAnIncompleteSnapshot() throws Exception {
        when(client.fetch(TrendSource.HONGGUO_HOT.url(2))).thenReturn(new HttpPage(200, "text/html", pageHtml(1).replace("\"pageNum\":1", "\"pageNum\":2")));
        assertThat(service.collect(TrendSource.HONGGUO_HOT, 42L).status()).isEqualTo("FAILED");
        assertThat(store.query(query(null)).total()).isZero();
    }

    @Test void cooldownRejectsRepeatedTriggersAndValidationRejectsUnboundedQueries() {
        var limited = new TrendCollectionService(client, new HongguoTrendParser(json), store, clock, Duration.ofMinutes(1));
        limited.collect(TrendSource.HONGGUO_HOT, 42L);
        assertThatThrownBy(() -> limited.collect(TrendSource.HONGGUO_HOT, 42L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.detail("missing", 0, 5000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.query(new Query(null, null, null, null, LocalDate.now(), LocalDate.now().minusDays(1), 1, 20))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void storedInstantsRemainStableAcrossServerTimezones() {
        var original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            var run = service.collect(TrendSource.HONGGUO_HOT, 42L);
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            assertThat(store.run(run.id()).startedAt()).isEqualTo(clock.instant());
            assertThat(store.detail(run.snapshotId(), 1, 20).snapshot().observedAt()).isEqualTo(clock.instant());
        } finally { TimeZone.setDefault(original); }
    }

    @Test void robotsRulesRespectSpecificAgentsWildcardsAndAllowPrecedence() {
        assertThat(TrendRobotsPolicy.allows("User-agent: *\nDisallow: /rank/*\nAllow: /rank/hot-drama$", "/rank/hot-drama")).isTrue();
        assertThat(TrendRobotsPolicy.allows("User-agent: *\nDisallow: /rank/*\nAllow: /rank/hot-drama$", "/rank/hot-drama?page=2")).isFalse();
        assertThat(TrendRobotsPolicy.allows("User-agent: *\nAllow: /\nUser-agent: AntShortTVTrendCollector\nDisallow: /", "/rank/hot-drama")).isFalse();
    }
    @Test void foreignSnapshotPersistsActualSourceDateCoverageAndMetricKind() throws Exception {
        var source=TrendSource.REELPULSE_DRAMABOX_TOP;
        when(client.fetch(source.robotsUrl())).thenReturn(new HttpPage(200,"text/plain","User-agent: *\nAllow: /"));
        when(client.fetch(source.url(1))).thenReturn(new HttpPage(200,"text/html",java.nio.file.Files.readString(java.nio.file.Path.of("src/test/resources/trend/reelpulse-dramabox.html"))));
        var foreign=new TrendCollectionService(client,List.of(new HongguoTrendParser(json),new ReelPulseTrendParser(),new FlexTvTrendParser(json)),store,clock,Duration.ZERO);
        var run=foreign.collect(source,42L);
        assertThat(run.status()).as(run.error()).isEqualTo("SUCCEEDED");
        var detail=store.detail(run.snapshotId(),1,20);
        assertThat(run.failureStage()).isNull();
        var repeat=foreign.collect(source,42L);
        assertThat(repeat.snapshotId()).isEqualTo(run.snapshotId());
        assertThat(detail.snapshot().platform()).isEqualTo("DRAMABOX");
        assertThat(detail.snapshot().sourceType()).isEqualTo("THIRD_PARTY");
        assertThat(detail.snapshot().market()).isEqualTo("OVERSEAS");
        assertThat(detail.snapshot().sourceDate()).isEqualTo(LocalDate.of(2026,10,5));
        assertThat(detail.snapshot().observedDate()).isEqualTo(LocalDate.of(2026,10,9));
        assertThat(detail.snapshot().coverage()).isEqualTo("THIRD_PARTY_TOP10");
        assertThat(detail.snapshot().language()).isEqualTo("en");
        assertThat(detail.entries().records().get(0).episodeCount()).isEqualTo(43);
        assertThat(detail.entries().records().get(0).metricKind()).isEqualTo("EPISODE_COUNT");
        assertThat(store.coverage()).hasSize(17);
        assertThat(store.query(new Query("OVERSEAS","DRAMABOX",null,"THIRD_PARTY",null,null,1,20)).total()).isEqualTo(1);
        assertThat(store.query(new Query("OVERSEAS","DRAMABOX",null,"OFFICIAL",null,null,1,20)).total()).isZero();
    }

    @Test void partialOfficialPageRetainsItsReportedTotalWithoutFillingMissingEntries() throws Exception {
        var source=TrendSource.FLEXTV_TOP;
        when(client.fetch(source.robotsUrl())).thenReturn(new HttpPage(200,"text/plain","User-agent: *\nAllow: /"));
        when(client.fetch(source.url(1))).thenReturn(new HttpPage(200,"text/html",java.nio.file.Files.readString(java.nio.file.Path.of("src/test/resources/trend/flextv-top.html"))));
        var foreign=new TrendCollectionService(client,List.of(new FlexTvTrendParser(json)),store,clock,Duration.ZERO);
        var run=foreign.collect(source,42L);
        assertThat(run.status()).as(run.error()).isEqualTo("SUCCEEDED");
        var detail=store.detail(run.snapshotId(),1,100);
        assertThat(detail.entries().total()).isEqualTo(18);
        assertThat(detail.snapshot().reportedTotal()).isEqualTo(100);
        assertThat(detail.snapshot().coverage()).isEqualTo("PARTIAL_PUBLIC_PAGE");
        assertThat(detail.snapshot().rankingKind()).isEqualTo("SECTION_ORDER");
    }

    @Test void restrictedOfficialSourceNeverSilentlyBecomesAThirdPartySuccess() throws Exception {
        var source=TrendSource.GOODSHORT_TOP;
        when(client.fetch(source.robotsUrl())).thenReturn(new HttpPage(403,"text/html","Forbidden"));
        var result=service.collect(source,42L);
        assertThat(result.status()).isEqualTo("BLOCKED");
        assertThat(result.errorCode()).isEqualTo("ROBOTS_UNAVAILABLE_403");
        assertThat(result.snapshotId()).isNull();
        verify(client,never()).fetch(source.url(1));
        verify(client,never()).fetch(TrendSource.REELPULSE_GOODSHORT_TOP.url(1));
    }

    @Test void explicitAiRobotOptOutIsNotBypassedByTheCustomCollectorName() throws Exception {
        var source=TrendSource.IQIYI_PUBLIC;
        when(client.fetch(source.robotsUrl())).thenReturn(new HttpPage(200,"text/plain","User-agent: ChatGPT-User\nDisallow: /\nUser-agent: *\nAllow: /"));
        var result=service.collect(source,42L);
        assertThat(result.status()).isEqualTo("BLOCKED");
        assertThat(result.errorCode()).isEqualTo("ROBOTS_DENIED");
        verify(client,never()).fetch(source.url(1));
    }

    @Test void accessibleHomePageWithoutAVerifiedChartIsADiscoveryGapNotRankingData() throws Exception {
        var source=TrendSource.DOUYIN_PUBLIC;
        when(client.fetch(source.robotsUrl())).thenReturn(new HttpPage(200,"text/plain","User-agent: *\nAllow: /"));
        when(client.fetch(source.url(1))).thenReturn(new HttpPage(200,"text/html","<html><h1>抖音</h1></html>"));
        var result=service.collect(source,42L);
        assertThat(result.status()).isEqualTo("NEEDS_SOURCE");
        assertThat(result.errorCode()).isEqualTo("NO_VERIFIED_CHART");
        assertThat(result.snapshotId()).isNull();
        assertThat(store.coverage().stream().filter(c->c.source().id().equals(source.name())).findFirst().orElseThrow().latestRun().id()).isEqualTo(result.id());
    }

    @Test void official403PersistsSanitizedDiagnosticsAndFailureStage() throws Exception {
        var source=TrendSource.REELSHORT_TOP;
        when(client.fetch(source.robotsUrl())).thenReturn(new HttpPage(403,"text/html",
            "The Amazon CloudFront distribution is configured to block access from your country.",
            Map.of("Server","CloudFront","X-Amz-Cf-Id","request-123","Set-Cookie","secret"),45L));
        var run=service.collect(source,42L);
        assertThat(run.failureStage()).isEqualTo("ROBOTS_VALIDATE");
        assertThat(run.errorCode()).isEqualTo("ROBOTS_UNAVAILABLE_403");
        var diagnostics=store.diagnostics(run.id());
        assertThat(diagnostics.requests()).hasSize(1);
        var request=diagnostics.requests().get(0);
        assertThat(request.cause()).isEqualTo("GEO_RESTRICTED");
        assertThat(request.elapsedMillis()).isEqualTo(45L);
        assertThat(request.requestId()).isEqualTo("request-123");
        assertThat(request.headers()).doesNotContainKey("set-cookie");
        assertThat(json.writeValueAsString(diagnostics)).doesNotContain("secret", "<html>");
        verify(client,never()).fetch(source.url(1));
    }

    @Test void transportFailureKeepsStageWithoutInventingAnHttpResponse() throws Exception {
        when(client.fetch(TrendSource.DRAMABOX_TOP.robotsUrl())).thenThrow(new java.io.IOException("timeout"));
        var run=service.collect(TrendSource.DRAMABOX_TOP,42L);
        assertThat(run.failureStage()).isEqualTo("ROBOTS_FETCH");
        assertThat(run.errorCode()).isEqualTo("FETCH_FAILED");
        assertThat(store.diagnostics(run.id()).requests()).isEmpty();
    }

    @Test void pageFailureIsNotReportedAsRobotsFailure() throws Exception {
        when(client.fetch(TrendSource.HONGGUO_HOT.url(1))).thenReturn(new HttpPage(403,"text/html","Forbidden"));
        var run=service.collect(TrendSource.HONGGUO_HOT,42L);
        assertThat(run.failureStage()).isEqualTo("PAGE_VALIDATE");
        assertThat(store.diagnostics(run.id()).requests()).hasSize(2);
        assertThat(store.diagnostics(run.id()).requests().get(1).stage()).isEqualTo("PAGE_FETCH");
    }

    @Test void historicalCapturesRemainReadableWithoutFabricatedHeadersOrTiming() throws Exception {
        String id=store.start(TrendSource.REELSHORT_TOP,null,clock.instant());
        store.capture(id,0,TrendSource.REELSHORT_TOP.robotsUrl().toString(),new HttpPage(403,"text/html","Forbidden"),clock.instant());
        jdbc.update("update trend_collection_page set diagnostics_json=null where run_id=?",id);
        var diagnostic=store.diagnostics(id).requests().get(0);
        assertThat(diagnostic.headers()).isEmpty();
        assertThat(diagnostic.elapsedMillis()).isNull();
        assertThat(store.diagnostics(id).run().failureStage()).isNull();
        assertThatThrownBy(()->store.diagnostics("missing")).isInstanceOf(com.antshorttv.common.BusinessException.class);
    }

}
