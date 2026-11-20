package com.antshorttv.trend;

import static com.antshorttv.trend.TrendModels.*;
import java.net.URI;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TrendCollectionService {
    private final TrendHttpClient http;
    private final List<TrendPageParser> parsers;
    private final TrendStore store;
    private final Clock clock;
    private final Duration cooldown;

    @Autowired
    public TrendCollectionService(TrendHttpClient http, List<TrendPageParser> parsers, TrendStore store) {
        this(http, parsers, store, Clock.systemUTC(), Duration.ofMinutes(1));
    }

    TrendCollectionService(TrendHttpClient http, HongguoTrendParser parser, TrendStore store, Clock clock, Duration cooldown) {
        this(http,List.of(parser),store,clock,cooldown);
    }

    TrendCollectionService(TrendHttpClient http, List<TrendPageParser> parsers, TrendStore store, Clock clock, Duration cooldown) {
        this.http=http; this.parsers=List.copyOf(parsers); this.store=store; this.clock=clock; this.cooldown=cooldown;
    }

    // Initial rollout is manual and single-instance; no scheduled background network traffic.
    public synchronized Run collect(TrendSource source, Long operatorId) {
        Instant started = clock.instant();
        Instant last = store.lastStarted(source);
        if (last != null && started.isBefore(last.plus(cooldown))) throw new IllegalArgumentException("同一来源两次采集至少间隔60秒");
        String id = store.start(source, operatorId, started);
        String stage = "ROBOTS_FETCH";
        try {
            URI robotsUrl = source.robotsUrl();
            HttpPage robots = http.fetch(robotsUrl);
            store.capture(id, 0, robotsUrl.toString(), robots, clock.instant());
            stage = "ROBOTS_VALIDATE";
            if (robots.status() != 200 || !robots.contentType().toLowerCase(Locale.ROOT).startsWith("text/plain")) {
                throw new TrendCollectionStopped("BLOCKED", "ROBOTS_UNAVAILABLE_"+robots.status(), "无法确认 robots.txt 规则，停止采集 (HTTP " + robots.status() + ")");
            }
            var parser=parsers.stream().filter(p -> p.supports(source)).findFirst().orElse(null);
            ParsedPage metadata=null;
            var entries = new ArrayList<Entry>();
            var ids = new HashSet<String>();
            var ranks = new HashSet<Integer>();
            int totalPages = 1;
            String updatedText = null;
            for (int page = 1; page <= totalPages; page++) {
                URI url = source.url(page);
                String path = url.getRawPath() + (url.getRawQuery() == null ? "" : "?" + url.getRawQuery());
                stage = "ROBOTS_POLICY";
                if (!TrendRobotsPolicy.allowsForCollection(robots.body(), path)) throw new TrendCollectionStopped("BLOCKED", "ROBOTS_DENIED", "robots.txt 限制采集器或AI客户端访问该路径，本轮停止");
                stage = "PAGE_FETCH";
                Thread.sleep(300);
                HttpPage response = http.fetch(url);
                store.capture(id, page, url.toString(), response, clock.instant());
                stage = "PAGE_VALIDATE";
                if (response.status() != 200) throw new TrendCollectionStopped("BLOCKED", "HTTP_"+response.status(), "来源返回 HTTP " + response.status() + "，未尝试绕过或重试");
                if (!response.contentType().toLowerCase(Locale.ROOT).contains("text/html")) throw new IllegalArgumentException("来源不是 HTML 页面");
                if(parser==null) throw new TrendCollectionStopped("NEEDS_SOURCE", "NO_VERIFIED_CHART", "公开入口已检查，但尚未确认可用的短剧榜单解析规则；未将推荐列表当作榜单");
                stage = "PARSE";
                ParsedPage parsed = parser.parse(response.body(), source, page);
                if(metadata==null) metadata=parsed;
                if(parsed.sourceDate()!=null && parsed.sourceDate().isAfter(started.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate())) throw new IllegalArgumentException("来源快照日期晚于采集日期");
                if (page == 1) { totalPages = parsed.totalPages(); updatedText = parsed.sourceUpdatedText(); }
                else if (totalPages != parsed.totalPages() || !Objects.equals(updatedText, parsed.sourceUpdatedText())) {
                    throw new IllegalArgumentException("采集过程中榜单分页/更新时间变化，请稍后重试");
                }
                for (Entry entry : parsed.entries()) {
                    if(entry.title()==null||entry.title().isBlank()||entry.title().length()>500||entry.externalId()==null||entry.externalId().isBlank()||entry.externalId().length()>40||entry.rank()<1) throw new IllegalArgumentException("榜单必填字段无效");
                    if (!ids.add(entry.externalId()) || !ranks.add(entry.rank())) throw new IllegalArgumentException("跨页出现重复剧目/名次，拒绝发布不完整快照");
                    entries.add(entry);
                }
            }
            entries.sort(Comparator.comparingInt(Entry::rank));
            for (int i=0; i<entries.size(); i++) {
                if (entries.get(i).rank() != i+1) throw new IllegalArgumentException("榜位不连续，拒绝发布不完整快照");
            }
            stage = "SNAPSHOT_PUBLISH";
            String snapshotId = store.publish(source, started, updatedText, totalPages, entries, metadata, parser.version());
            return store.finish(id, "SUCCEEDED", snapshotId, entries.size(), null, clock.instant());
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            String status=ex instanceof TrendCollectionStopped stopped?stopped.status:"FAILED";
            String code=ex instanceof TrendCollectionStopped stopped?stopped.code:ex instanceof java.io.IOException?"FETCH_FAILED":"PARSE_OR_STORAGE_FAILED";
            return store.finish(id, status, null, 0, message.substring(0, Math.min(message.length(), 1000)), code, stage, clock.instant());
        }
    }
}
