package com.antshorttv.trend;

import static com.antshorttv.trend.TrendModels.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class HongguoTrendParser implements TrendPageParser {
    @Override public boolean supports(TrendSource source) { return source.parserKind.equals("HONGGUO"); }
    @Override public String version() { return VERSION; }
    public static final String VERSION = "hongguo-ssr-v1";
    private static final Pattern ROUTER = Pattern.compile("_ROUTER_DATA\\s*=\\s*(\\{)");
    private static final Pattern DEFERRED = Pattern.compile("data-fn-args=\"([^\"]+)\"");
    private static final Pattern HEAT = Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)(万热度|亿热度|热度)$");
    private final ObjectMapper json;

    public HongguoTrendParser(ObjectMapper json) { this.json = json; }

    public ParsedPage parse(String html, TrendSource source, int expectedPage) {
        try {
            var routerMatch = ROUTER.matcher(html);
            if (!routerMatch.find()) throw invalid("缺少 SSR 数据");
            // Read a JSON value only. Never evaluate the surrounding JavaScript.
            JsonNode router;
            try (var input = json.createParser(html.substring(routerMatch.start(1)))) {
                router = json.readTree(input);
            }
            String routeKey = "rank_" + source.slug + "/page";
            var page = router.path("loaderData").path(routeKey);
            if (page.path("pageNum").asInt(-1) != expectedPage) throw invalid("页码与请求不一致");
            JsonNode content = page.path("content");
            if (!content.has("rankList")) {
                var deferred = DEFERRED.matcher(html);
                while (deferred.find()) {
                    var args = json.readTree(HtmlUtils.htmlUnescape(deferred.group(1)));
                    if (!args.isArray() || !routeKey.equals(args.path(0).asText())) continue;
                    for (var item : args.path(1)) {
                        if ("content".equals(item.path("key").asText())
                            && "p".equals(item.path("routerDataFnName").asText())) {
                            content = json.readTree(item.path("routerDataFnArgs").path(0).asText());
                        }
                    }
                }
            }
            if (content != null && !content.has("isSuccess")) content = renderedContent(html, source, expectedPage);
            if (content == null || !content.path("isSuccess").asBoolean()) throw invalid("来源未返回成功榜单");
            var paging = content.path("pagination");
            int totalPages = paging.path("totalPages").asInt(-1);
            if (paging.path("pageNum").asInt(-1) != expectedPage || totalPages < expectedPage || totalPages > 10) {
                throw invalid("分页结构不符合约定");
            }
            String next = paging.path("nextUrl").asText("");
            if (expectedPage < totalPages && !source.url(expectedPage + 1).toString().equals(next)) {
                throw invalid("下一页地址不符合固定来源约定");
            }
            var rows = content.path("rankList");
            if (!rows.isArray() || rows.isEmpty() || rows.size() > 100) throw invalid("条目为空或超过单页上限");
            List<Entry> entries = new ArrayList<>();
            var ranks = new HashSet<Integer>();
            var ids = new HashSet<String>();
            for (var row : rows) {
                int rank = row.path("rank").asInt(-1);
                String id = row.path("seriesId").asText("");
                String title = row.path("title").asText("").trim();
                if (rank < 1 || !id.matches("[0-9]{1,30}") || title.isEmpty() || title.length() > 500
                    || !ranks.add(rank) || !ids.add(id)) throw invalid("剧目编号、标题或榜位无效/重复");
                String metric = row.path("heatText").asText("");
                var heat = HEAT.matcher(metric);
                BigDecimal value = null;
                String unit = null;
                if (heat.matches()) { value = new BigDecimal(heat.group(1)); unit = heat.group(2); }
                var tags = new ArrayList<String>();
                if (row.path("tags").isArray()) for (var tag : row.path("tags")) if (tag.isTextual()) tags.add(tag.asText());
                String type = "MIXED".equals(source.contentType) ? "UNKNOWN" : source.contentType;
                String cover = row.path("cover").asText("");
                if (!cover.isBlank() && !"https".equals(URI.create(cover).getScheme())) cover = "";
                entries.add(new Entry(rank, id, title, type,
                    "https://hongguoduanju.com/detail?series_id=" + id, cover,
                    metric, value, unit, List.copyOf(tags)));
            }
            return new ParsedPage(expectedPage, totalPages, next,
                page.path("updatedText").asText(""), List.copyOf(entries));
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalid("JSON 结构变化或无法解析");
        }
    }

    // Some successful SSR responses render the complete list but leave hydration as a pending
    // promise. Read the very same visible ranking DOM, not an undocumented private API.
    private JsonNode renderedContent(String html, TrendSource source, int page) {
        var doc = Jsoup.parse(html, source.url(page).toString());
        var pagination = doc.selectFirst("nav[aria-label=榜单分页]");
        if (pagination == null) throw invalid("缺少公开分页结构");
        int current = Integer.parseInt(required(pagination, "[aria-current=page]").text());
        if (current != page) throw invalid("可见分页与请求不一致");
        int total = current;
        for (var link : pagination.select("a[aria-label^=第]")) {
            int number = Integer.parseInt(link.text());
            if (!source.url(number).toString().equals(link.absUrl("href"))) throw invalid("可见分页地址变化");
            total = Math.max(total, number);
        }
        var next = pagination.selectFirst("a[rel=next]");
        var content = json.createObjectNode();
        content.put("isSuccess", true);
        var paging = content.putObject("pagination");
        paging.put("pageNum", current).put("totalPages", total).put("nextUrl", next == null ? "" : next.absUrl("href"));
        var rows = content.putArray("rankList");
        for (var article : doc.select("ol[aria-label] > li > article[aria-labelledby^=rank-title-]")) {
            String id = article.attr("aria-labelledby").substring("rank-title-".length());
            var row = rows.addObject();
            row.put("seriesId", id);
            row.put("rank", Integer.parseInt(required(article, "[class^=pc-badge-number-]").text()));
            row.put("title", required(article, "h2[id=rank-title-" + id + "]").text());
            row.put("heatText", required(article, "p[class^=pc-metrics-]").text());
            var cover = article.selectFirst("img[src]");
            row.put("cover", cover == null ? "" : cover.absUrl("src"));
            var tags = row.putArray("tags");
            article.select("p[class^=pc-categories-] > span").forEach(tag -> tags.add(tag.text()));
        }
        return content;
    }

    private static Element required(Element parent, String selector) {
        var found = parent.selectFirst(selector);
        if (found == null) throw invalid("可见榜单字段缺失: " + selector);
        return found;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("红果榜单解析失败：" + message);
    }
}
