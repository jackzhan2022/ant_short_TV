package com.antshorttv.trend;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

final class ForeignTrendFields {
    private ForeignTrendFields() {}
    record Metric(String text, BigDecimal value, String unit) {}

    static String externalId(String url, String platform) {
        URI uri=URI.create(url);
        String host=switch(platform) {
            case "REELSHORT" -> "www.reelshort.com";
            case "GOODSHORT" -> "www.goodshort.com";
            case "DRAMABOX" -> "www.dramabox.com";
            case "FLEXTV" -> "www.flextv.cc";
            default -> throw new IllegalArgumentException("未登记的剧目平台");
        };
        if(!"https".equals(uri.getScheme())||!host.equals(uri.getHost())||uri.getUserInfo()!=null||uri.getPort()!=-1) throw new IllegalArgumentException("剧目链接不属于预期平台");
        String regex=switch(platform) {
            case "REELSHORT" -> "^/movie/.+-([a-f0-9]{24})$";
            case "GOODSHORT" -> "^/drama/.+-(\\d{1,30})$";
            case "DRAMABOX" -> "^/drama/(\\d{1,30})/[^/]+$";
            default -> "^/movie/.+-([A-Za-z0-9]{1,40})$";
        };
        var match=Pattern.compile(regex).matcher(uri.getPath());
        if(!match.matches()) throw new IllegalArgumentException("来源剧目ID结构变化");
        return match.group(1);
    }

    static Metric views(String text) {
        var match=Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)([KMB]?)$").matcher(text);
        if(!match.matches()) return new Metric(text,null,null);
        return new Metric(text,new BigDecimal(match.group(1)),match.group(2).isEmpty()?"views":match.group(2)+" views");
    }

    static LocalDate date(String text,String prefix) {
        var match=Pattern.compile(Pattern.quote(prefix)+"\\s+([A-Za-z]+ \\d{1,2}, \\d{4})(?:\\b|$)").matcher(text);
        if(!match.find()) throw new IllegalArgumentException("来源快照日期缺失，不以采集日期代替");
        for(String pattern:new String[]{"MMM d, uuuu","MMMM d, uuuu"}) {
            try{return LocalDate.parse(match.group(1),DateTimeFormatter.ofPattern(pattern,Locale.ENGLISH));}
            catch(java.time.format.DateTimeParseException ignored) { }
        }
        throw new IllegalArgumentException("来源快照日期无法解析");
    }
}
