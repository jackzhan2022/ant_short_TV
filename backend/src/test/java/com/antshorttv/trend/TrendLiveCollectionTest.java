package com.antshorttv.trend;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfSystemProperty(named="trend.live", matches="true")
class TrendLiveCollectionTest {
    @Test @Timeout(480)
    void collectPublicSourcesIntoIsolatedPersistentDatabaseAndReadBack() throws Exception {
        Path output = Path.of("target/trend-smoke/" + Instant.now().toEpochMilli()).toAbsolutePath();
        Files.createDirectories(output);
        var ds = TrendCollectionServiceTest.database("jdbc:h2:file:" + output.resolve("trends").toString().replace('\\','/') + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        // Keep the isolated file database open between JDBC calls on Windows.
        // Reopening it for every statement can race filesystem locks during background flushes.
        var json = new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var jdbc = new JdbcTemplate(ds);
        var store = new TrendStore(jdbc, new TransactionTemplate(new DataSourceTransactionManager(ds)), json);
        var service = new TrendCollectionService(new TrendHttpClient(), List.of(new HongguoTrendParser(json),new FlexTvTrendParser(json),new ReelPulseTrendParser()), store, Clock.systemUTC(), Duration.ZERO);
        List<TrendModels.Run> runs = new ArrayList<>();
        String scope=System.getProperty("trend.live.scope","hongguo");
        if(!Set.of("hongguo","all","overseas-reserve").contains(scope)) throw new IllegalArgumentException("Unknown live collection scope");
        boolean all="all".equals(scope);
        boolean reserve="overseas-reserve".equals(scope);
        List<TrendModels.RunDiagnostics> diagnostics=new ArrayList<>();
        var sources=Arrays.stream(TrendSource.values()).filter(s -> all || (reserve
            ? Set.of("REELSHORT","DRAMABOX","GOODSHORT").contains(s.platform) : s.platform.equals("HONGGUO")))
            .sorted(Comparator.comparingInt(s -> s.sourceType.equals("THIRD_PARTY")?0:1)).toList();
        try {
        for (var source : sources) {
            var run = service.collect(source, null);
            runs.add(run);
            diagnostics.add(store.diagnostics(run.id()));
            Files.writeString(output.resolve("diagnostics.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(diagnostics));
            Files.writeString(output.resolve("runs.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(runs));
            System.out.println("TREND_LIVE " + source + " " + run.status() + " entries=" + run.entryCount() + " error=" + run.error());
            if ("SUCCEEDED".equals(run.status())) {
                var detail = store.detail(run.snapshotId(), 1, 100);
                Files.writeString(output.resolve(source.name()+".json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(detail));
                assertThat(detail.entries().records()).hasSize(run.entryCount());
                assertThat(detail.entries().records().size()).isGreaterThan(5);
            }
        }
        Files.writeString(output.resolve("coverage.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(store.coverage()));
        System.out.println("TREND_EVIDENCE " + output);
        assertThat(runs).hasSize(all?17:reserve?7:4);
        // A completed audit is not a claim that restricted platforms were successfully collected.
        assertThat(runs).allSatisfy(run -> {
            var source=TrendSource.from(run.sourceId());
            if(!source.parserKind.equals("DISCOVERY")) assertThat(run.status()).as(run.sourceId()+": "+run.error()).isEqualTo("SUCCEEDED");
            else assertThat(run.status()).as(run.sourceId()+": "+run.error()).isIn("BLOCKED","NEEDS_SOURCE");
        });
        } finally { jdbc.execute("SHUTDOWN"); }
    }
}
