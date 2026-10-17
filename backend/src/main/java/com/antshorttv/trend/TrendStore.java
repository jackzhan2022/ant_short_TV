package com.antshorttv.trend;

import static com.antshorttv.trend.TrendModels.*;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class TrendStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ObjectMapper json;

    public TrendStore(JdbcTemplate jdbc, TransactionTemplate transaction, ObjectMapper json) {
        this.jdbc = jdbc; this.transaction = transaction; this.json = json;
    }

    public Instant lastStarted(TrendSource source) {
        LocalDateTime value = jdbc.queryForObject("select max(started_at) from trend_collection_run where source_id=?", LocalDateTime.class, source.name());
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    public String start(TrendSource source, Long operatorId, Instant now) {
        String id = UUID.randomUUID().toString();
        jdbc.update("insert into trend_collection_run(id, source_id, operator_id, status, started_at) values(?,?,?,'RUNNING',?)",
            id, source.name(), operatorId, LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        return id;
    }

    public void capture(String runId, int page, String url, HttpPage response, Instant at) {
        jdbc.update("insert into trend_collection_page(run_id,page_number,url,http_status,content_type,fetched_at,body,diagnostics_json) values(?,?,?,?,?,?,?,?)",
            runId, page, url, response.status(), response.contentType(), LocalDateTime.ofInstant(at, ZoneOffset.UTC), response.body(), serialize(TrendRequestDiagnostics.inspect(page,url,response,at)));
    }

    public Run finish(String id, String status, String snapshotId, int count, String error, Instant now) {
        return finish(id,status,snapshotId,count,error,null,now);
    }

    public Run finish(String id, String status, String snapshotId, int count, String error, String errorCode, Instant now) {
        return finish(id,status,snapshotId,count,error,errorCode,null,now);
    }

    public Run finish(String id, String status, String snapshotId, int count, String error, String errorCode, String failureStage, Instant now) {
        jdbc.update("update trend_collection_run set status=?, snapshot_id=?, entry_count=?, error_message=?, error_code=?, failure_stage=?, finished_at=? where id=?",
            status, snapshotId, count, error, errorCode, failureStage, LocalDateTime.ofInstant(now, ZoneOffset.UTC), id);
        return run(id);
    }

    public Run run(String id) {
        var found = jdbc.query("select * from trend_collection_run where id=?", (rs, n) -> {
            LocalDateTime finished = rs.getObject("finished_at", LocalDateTime.class);
            return new Run(rs.getString("id"), rs.getString("source_id"), rs.getString("status"),
                rs.getObject("started_at", LocalDateTime.class).toInstant(ZoneOffset.UTC), finished == null ? null : finished.toInstant(ZoneOffset.UTC),
                rs.getString("snapshot_id"), rs.getInt("entry_count"), rs.getString("error_message"), rs.getString("error_code"), rs.getString("failure_stage"));
        }, id);
        if (found.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "采集记录不存在");
        return found.get(0);
    }

    public RunDiagnostics diagnostics(String id) {
        Run run=run(id);
        var requests=jdbc.query("select * from trend_collection_page where run_id=? order by page_number", (rs,n) -> {
            String encoded=rs.getString("diagnostics_json");
            if(encoded!=null) {
                try { return json.readValue(encoded,RequestDiagnostic.class); }
                catch (Exception ex) { throw new IllegalStateException("已保存的请求诊断格式错误",ex); }
            }
            // Historical captures have no headers or timing. Keep these missing, never manufacture them.
            return TrendRequestDiagnostics.inspect(rs.getInt("page_number"),rs.getString("url"),
                new HttpPage(rs.getInt("http_status"),rs.getString("content_type"),rs.getString("body")),
                rs.getObject("fetched_at",LocalDateTime.class).toInstant(ZoneOffset.UTC));
        },id);
        return new RunDiagnostics(run,requests);
    }

    public String publish(TrendSource source, Instant observedAt, String updatedText, int pages, List<Entry> entries, ParsedPage metadata, String parserVersion) {
        String serialized = serialize(Arrays.asList(entries, metadata.sourceDate(), metadata.coverage(), metadata.reportedTotal(), parserVersion));
        String digest;
        try { digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(serialized.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException("无法计算快照指纹", ex); }
        LocalDate date = observedAt.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
        try {
            return transaction.execute(status -> {
                String existing = existing(source, date, digest);
                if (existing != null) return existing;
                String id = UUID.randomUUID().toString();
                jdbc.update("""
                    insert into trend_snapshot(id,source_id,title,market,platform,content_type,source_type,source_url,
                    observed_date,observed_at,source_updated_text,page_count,entry_count,fingerprint,parser_version,
                    source_date,language,ranking_kind,coverage,reported_total)
                    values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, id, source.name(), source.title, source.market, source.platform, source.contentType, source.sourceType, source.url(1).toString(),
                    java.sql.Date.valueOf(date), LocalDateTime.ofInstant(observedAt, ZoneOffset.UTC), updatedText, pages, entries.size(), digest, parserVersion, metadata.sourceDate(), source.language, source.rankingKind, metadata.coverage(), metadata.reportedTotal());
                for (Entry entry : entries) {
                    jdbc.update("""
                        insert into trend_snapshot_entry(snapshot_id,rank_position,external_id,title,content_type,
                        detail_url,cover_url,metric_text,metric_value,metric_unit,tags_json,metric_kind,episode_count) values(?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """, id, entry.rank(), entry.externalId(), entry.title(), entry.contentType(), entry.detailUrl(),
                        entry.coverUrl(), entry.metricText(), entry.metricValue(), entry.metricUnit(), serialize(entry.tags()), entry.metricKind(), entry.episodeCount());
                }
                return id;
            });
        } catch (DuplicateKeyException ex) {
            String existing = existing(source, date, digest);
            if (existing != null) return existing;
            throw ex;
        }
    }

    private String existing(TrendSource source, LocalDate date, String digest) {
        var ids = jdbc.queryForList("select id from trend_snapshot where source_id=? and observed_date=? and fingerprint=?",
            String.class, source.name(), java.sql.Date.valueOf(date), digest);
        return ids.isEmpty() ? null : ids.get(0);
    }

    public Page<Snapshot> query(Query query) {
        validatePage(query.page(), query.pageSize());
        if (query.from() != null && query.to() != null && query.from().isAfter(query.to())) throw new IllegalArgumentException("开始日期不能晚于结束日期");
        StringBuilder where = new StringBuilder(" where 1=1");
        List<Object> args = new ArrayList<>();
        filter(where, args, "market", query.market(), Set.of("DOMESTIC", "OVERSEAS"));
        filter(where, args, "platform", query.platform(), Set.of("HONGGUO", "DOUYIN", "KUAISHOU", "TENCENT", "IQIYI", "YOUKU", "REELSHORT", "DRAMABOX", "GOODSHORT", "FLEXTV"));
        filter(where, args, "content_type", query.contentType(), Set.of("MIXED", "LIVE_ACTION", "AI", "COMIC", "UNKNOWN"));
        filter(where, args, "source_type", query.sourceType(), Set.of("OFFICIAL", "THIRD_PARTY"));
        if (query.from() != null) { where.append(" and observed_date>=?"); args.add(java.sql.Date.valueOf(query.from())); }
        if (query.to() != null) { where.append(" and observed_date<=?"); args.add(java.sql.Date.valueOf(query.to())); }
        long total = Objects.requireNonNull(jdbc.queryForObject("select count(*) from trend_snapshot" + where, Long.class, args.toArray()));
        args.add(query.pageSize()); args.add((query.page()-1)*query.pageSize());
        var records = jdbc.query("select * from trend_snapshot" + where + " order by observed_at desc,id desc limit ? offset ?", this::snapshot, args.toArray());
        return new Page<>(records, total, query.page(), query.pageSize());
    }

    public SnapshotDetail detail(String id, int page, int pageSize) {
        validatePage(page, pageSize);
        var snapshots = jdbc.query("select * from trend_snapshot where id=?", this::snapshot, id);
        if (snapshots.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "趋势快照不存在");
        var entries = jdbc.query("select * from trend_snapshot_entry where snapshot_id=? order by rank_position limit ? offset ?", (rs, n) ->
            new Entry(rs.getInt("rank_position"), rs.getString("external_id"), rs.getString("title"), rs.getString("content_type"),
                rs.getString("detail_url"), rs.getString("cover_url"), rs.getString("metric_text"), rs.getBigDecimal("metric_value"),
                rs.getString("metric_unit"), tags(rs.getString("tags_json")), rs.getString("metric_kind"), rs.getObject("episode_count",Integer.class)), id, pageSize, (page-1)*pageSize);
        return new SnapshotDetail(snapshots.get(0), new Page<>(entries, snapshots.get(0).entryCount(), page, pageSize));
    }

    private Snapshot snapshot(ResultSet rs, int n) throws SQLException {
        return new Snapshot(rs.getString("id"),rs.getString("source_id"),rs.getString("title"),rs.getString("market"),
            rs.getString("platform"),rs.getString("content_type"),rs.getString("source_type"),rs.getString("source_url"),
            rs.getDate("observed_date").toLocalDate(),rs.getObject("observed_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),rs.getString("source_updated_text"),
            rs.getInt("page_count"),rs.getInt("entry_count"),rs.getString("fingerprint"),rs.getString("parser_version"),
            rs.getObject("source_date",LocalDate.class),rs.getString("language"),rs.getString("ranking_kind"),rs.getString("coverage"),rs.getObject("reported_total",Integer.class));
    }

    public List<SourceCoverage> coverage() {
        return Arrays.stream(TrendSource.values()).map(source -> {
            var runs=jdbc.queryForList("select id from trend_collection_run where source_id=? order by started_at desc,id desc limit 1",String.class,source.name());
            var snapshots=jdbc.query("select * from trend_snapshot where source_id=? order by observed_at desc,id desc limit 1",this::snapshot,source.name());
            return new SourceCoverage(source.info(),runs.isEmpty()?null:run(runs.get(0)),snapshots.isEmpty()?null:snapshots.get(0));
        }).toList();
    }

    private static void filter(StringBuilder where, List<Object> args, String column, String value, Set<String> allowed) {
        if (value == null || value.isBlank()) return;
        if (!allowed.contains(value)) throw new IllegalArgumentException("不支持的筛选项: " + column);
        where.append(" and ").append(column).append("=?"); args.add(value);
    }

    private static void validatePage(int page, int size) {
        if (page < 1 || page > 100000 || size < 1 || size > 100) throw new IllegalArgumentException("page 必须为 1–100000，pageSize 必须为 1–100");
    }

    private String serialize(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException("趋势数据序列化失败", ex); }
    }

    private List<String> tags(String value) {
        try { return json.readValue(value, new TypeReference<List<String>>() {}); }
        catch (Exception ex) { throw new IllegalStateException("已保存的趋势标签格式错误", ex); }
    }
}
