package com.antshorttv.trend;

import static com.antshorttv.trend.TrendModels.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

@Component
public class FlexTvTrendParser implements TrendPageParser {
    private final ObjectMapper json;
    public FlexTvTrendParser(ObjectMapper json) { this.json=json; }
    public boolean supports(TrendSource source) { return source.parserKind.equals("FLEXTV"); }
    public String version() { return "flextv-jsonld-v1"; }

    public ParsedPage parse(String html,TrendSource source,int page) {
        if(!supports(source)||page!=1) throw new IllegalArgumentException("FlexTV仅采集已确认的公开第一页");
        try {
            var doc=Jsoup.parse(html);
            var heading=doc.selectFirst("h1");
            if(heading==null||!heading.text().equals("Top in FlexTV Verticals")) throw new IllegalArgumentException("FlexTV专区标题变化");
            Integer total=null;
            for(var h:doc.select("h2")) {
                var match=Pattern.compile("About ([0-9]+) results").matcher(h.text());
                if(match.matches()) total=Integer.valueOf(match.group(1));
            }
            var entries=new ArrayList<Entry>();
            boolean found=false;
            for(var script:doc.select("script[type=application/ld+json]")) {
                var data=json.readTree(script.data());
                if(!"CollectionPage".equals(data.path("@type").asText())||!source.url(1).toString().equals(data.path("url").asText())) continue;
                if(found) throw new IllegalArgumentException("FlexTV出现多个同名专区数据");
                found=true;
                var list=data.path("mainEntity").path("itemListElement");
                if(!list.isArray()||list.isEmpty()) throw new IllegalArgumentException("FlexTV公开条目为空");
                for(var item:list) {
                    String url=item.path("url").asText();
                    BigDecimal count=null;
                    for(var statistic:item.path("interactionStatistic")) {
                        if("https://schema.org/WatchAction".equals(statistic.path("interactionType").asText())
                            && statistic.path("userInteractionCount").isNumber()) count=statistic.path("userInteractionCount").decimalValue();
                    }
                    entries.add(new Entry(item.path("position").asInt(-1),ForeignTrendFields.externalId(url,source.platform),
                        item.path("name").asText(),"UNKNOWN",url,item.path("image").asText(""),
                        count==null?"":count.toPlainString()+" views",count,count==null?null:"views",List.of(),"PLATFORM_VIEWS",null));
                }
            }
            if(!found||entries.isEmpty()) throw new IllegalArgumentException("FlexTV结构化榜单缺失");
            if(total!=null&&total<entries.size()) throw new IllegalArgumentException("FlexTV总数与可见条目矛盾");
            return new ParsedPage(1,1,"","",List.copyOf(entries),null,total,"PARTIAL_PUBLIC_PAGE");
        } catch(IllegalArgumentException ex){throw ex;}
        catch(Exception ex){throw new IllegalArgumentException("FlexTV公开数据解析失败",ex);}
    }
}
