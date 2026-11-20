package com.antshorttv.trend;

import static com.antshorttv.trend.TrendModels.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Component
public class ReelPulseTrendParser implements TrendPageParser {
    public boolean supports(TrendSource source) { return source.parserKind.equals("REELPULSE"); }
    public String version() { return "reelpulse-public-chart-v1"; }

    public ParsedPage parse(String html,TrendSource source,int page) {
        if(!supports(source)||page!=1) throw new IllegalArgumentException("ReelPulse仅采集公开日期快照");
        var doc=Jsoup.parse(html,source.url(1).toString());
        var matches=doc.select("h2").stream().filter(h->h.text().equals(source.sectionHeading)).toList();
        if(matches.size()!=1) throw new IllegalArgumentException("目标榜单专区缺失或不唯一");
        var heading=matches.get(0);
        var section=heading.closest("section");
        if(section==null) throw new IllegalArgumentException("榜单没有独立专区");
        boolean drama=source==TrendSource.REELPULSE_DRAMABOX_TOP;
        LocalDate date=ForeignTrendFields.date(drama?section.text():heading.parent().text(),drama?"Checked":"Captured");
        List<Entry> entries=drama?dramaBox(section,source):table(section,source);
        if(entries.size()!=10) throw new IllegalArgumentException("ReelPulse公开TOP10条目数量变化，待核验");
        return new ParsedPage(1,1,"",(drama?"Checked ":"Captured ")+date,List.copyOf(entries),date,null,"THIRD_PARTY_TOP10");
    }

    private List<Entry> table(Element section,TrendSource source) {
        var tables=section.select("table");
        if(tables.size()!=1||!tables.get(0).select("thead th").eachText().equals(List.of("#","Move","Title","Eps","Views"))) {
            throw new IllegalArgumentException("ReelPulse表格列结构变化");
        }
        var entries=new ArrayList<Entry>();
        for(var row:tables.get(0).select("tbody tr")) {
            var cells=row.select("td");
            if(cells.size()!=5) throw new IllegalArgumentException("榜单列数异常");
            var link=cells.get(2).selectFirst("a[href]");
            if(link==null) throw new IllegalArgumentException("缺少剧目来源链接");
            String url=link.absUrl("href");
            var metric=ForeignTrendFields.views(cells.get(4).text());
            var tags=cells.get(2).selectFirst("p");
            entries.add(new Entry(Integer.parseInt(cells.get(0).text()),ForeignTrendFields.externalId(url,source.platform),link.text(),
                "UNKNOWN",url,"",metric.text(),metric.value(),metric.unit(),
                tags==null?List.of():Arrays.stream(tags.text().split(" · ")).map(String::trim).toList(),
                "PLATFORM_VIEWS",Integer.valueOf(cells.get(3).text())));
        }
        return entries;
    }

    private List<Entry> dramaBox(Element section,TrendSource source) {
        var entries=new ArrayList<Entry>();
        for(var h:section.select("h3")) {
            var link=h.selectFirst("a[href]");
            var episode=h.parent().selectFirst("p");
            var rank=h.parent().parent().selectFirst("span");
            if(link==null||episode==null||rank==null) throw new IllegalArgumentException("DramaBox日期快照字段缺失");
            var match=Pattern.compile("([0-9]+) episodes").matcher(episode.text());
            if(!match.matches()) throw new IllegalArgumentException("DramaBox集数字段变化");
            int count=Integer.parseInt(match.group(1));
            String url=link.absUrl("href");
            entries.add(new Entry(Integer.parseInt(rank.text()),ForeignTrendFields.externalId(url,source.platform),link.text(),
                "UNKNOWN",url,"",episode.text(),BigDecimal.valueOf(count),"episodes",List.of(),"EPISODE_COUNT",count));
        }
        return entries;
    }
}
