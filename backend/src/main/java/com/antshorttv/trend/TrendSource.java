package com.antshorttv.trend;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;

public enum TrendSource {
    HONGGUO_HOT("hot-drama", "红果综合热播榜", "MIXED"),
    HONGGUO_REAL("hot-real-drama", "红果热播真人剧榜", "LIVE_ACTION"),
    HONGGUO_AI("hot-ai-drama", "红果热播AI剧榜", "AI"),
    HONGGUO_COMIC("hot-comic-drama", "红果热播漫剧榜", "COMIC"),
    DOUYIN_PUBLIC("DOUYIN", "抖音公开入口（待确认榜单）", "https://www.douyin.com/", "OFFICIAL", "zh-CN", "UNVERIFIED", "DISCOVERY", ""),
    KUAISHOU_PUBLIC("KUAISHOU", "快手公开入口（待确认榜单）", "https://www2.kuaishou.com/", "OFFICIAL", "zh-CN", "UNVERIFIED", "DISCOVERY", ""),
    TENCENT_PUBLIC("TENCENT", "腾讯视频公开入口（待确认短剧榜单）", "https://v.qq.com/", "OFFICIAL", "zh-CN", "UNVERIFIED", "DISCOVERY", ""),
    IQIYI_PUBLIC("IQIYI", "爱奇艺公开榜单入口（待确认短剧分区）", "https://www.iqiyi.com/rank", "OFFICIAL", "zh-CN", "UNVERIFIED", "DISCOVERY", ""),
    YOUKU_PUBLIC("YOUKU", "优酷公开入口（待确认榜单）", "https://www.youku.com/", "OFFICIAL", "zh-CN", "UNVERIFIED", "DISCOVERY", ""),
    REELSHORT_TOP("REELSHORT", "ReelShort 官方TOP专区", "https://www.reelshort.com/shelf/top-short-movies-dramas-51002892", "OFFICIAL", "en", "SECTION_ORDER", "DISCOVERY", ""),
    DRAMABOX_TOP("DRAMABOX", "DramaBox 官方公开入口", "https://www.dramabox.com/", "OFFICIAL", "en", "UNVERIFIED", "DISCOVERY", ""),
    GOODSHORT_TOP("GOODSHORT", "GoodShort Top 官方专区", "https://www.goodshort.com/channel/Top-in-GoodShort", "OFFICIAL", "en", "SECTION_ORDER", "DISCOVERY", ""),
    FLEXTV_TOP("FLEXTV", "FlexTV Top 官方专区", "https://www.flextv.cc/drama/top-in-flextv-10604", "OFFICIAL", "en", "SECTION_ORDER", "FLEXTV", ""),
    REELPULSE_REELSHORT_TOP("REELSHORT", "ReelShort Top · ReelPulse快照", "https://reelpulse.net/trends", "THIRD_PARTY", "en", "SECTION_ORDER", "REELPULSE", "ReelShort Top"),
    REELPULSE_GOODSHORT_TOP("GOODSHORT", "GoodShort Top · ReelPulse快照", "https://reelpulse.net/trends", "THIRD_PARTY", "en", "SECTION_ORDER", "REELPULSE", "GoodShort Top"),
    REELPULSE_GOODSHORT_TRENDING("GOODSHORT", "GoodShort Most Trending · ReelPulse快照", "https://reelpulse.net/trends", "THIRD_PARTY", "en", "SECTION_ORDER", "REELPULSE", "GoodShort Most Trending"),
    REELPULSE_DRAMABOX_TOP("DRAMABOX", "DramaBox Top Hits · ReelPulse快照", "https://reelpulse.net/guides/best-dramabox-shows", "THIRD_PARTY", "en", "SECTION_ORDER", "REELPULSE", "DramaBox official Top Hits");

    public final String slug, title, contentType, platform, market, sourceType, language, rankingKind, parserKind, sectionHeading;
    private final URI origin;

    TrendSource(String slug, String title, String contentType) {
        this.slug=slug; this.title=title; this.contentType=contentType; this.platform="HONGGUO";
        this.market="DOMESTIC"; this.sourceType="OFFICIAL"; this.language="zh-CN";
        this.rankingKind="RANK"; this.parserKind="HONGGUO"; this.sectionHeading="";
        this.origin=URI.create("https://hongguoduanju.com/rank/"+slug);
    }

    TrendSource(String platform, String title, String url, String sourceType, String language,
        String rankingKind, String parserKind, String sectionHeading) {
        this.slug=""; this.title=title; this.contentType="UNKNOWN"; this.platform=platform;
        this.market=Set.of("DOUYIN","KUAISHOU","TENCENT","IQIYI","YOUKU").contains(platform)?"DOMESTIC":"OVERSEAS";
        this.sourceType=sourceType; this.language=language; this.rankingKind=rankingKind;
        this.parserKind=parserKind; this.sectionHeading=sectionHeading; this.origin=URI.create(url);
    }

    public URI url(int page) {
        if (page < 1 || page > (parserKind.equals("HONGGUO") ? 10 : 1)) throw new IllegalArgumentException("该来源不支持此分页地址");
        return page == 1 ? origin : URI.create(origin+"?page="+page);
    }

    public URI robotsUrl() { return URI.create("https://"+origin.getHost()+"/robots.txt"); }

    public TrendModels.SourceInfo info() {
        return new TrendModels.SourceInfo(name(),title,market,platform,contentType,sourceType,origin.toString(),language,rankingKind,parserKind);
    }

    public static boolean allowedRequest(URI uri) {
        if (!"https".equals(uri.getScheme()) || uri.getPort()!=-1 || uri.getUserInfo()!=null || uri.getFragment()!=null) return false;
        return Arrays.stream(values()).anyMatch(source -> {
            if (source.robotsUrl().equals(uri) || source.url(1).equals(uri)) return true;
            if (source.parserKind.equals("HONGGUO")) for(int page=2;page<=10;page++) if(source.url(page).equals(uri)) return true;
            return false;
        });
    }

    public static TrendSource from(String code) {
        try { return valueOf(code); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("不支持的数据源: " + code); }
    }
}
