package com.antshorttv.trend;

public interface TrendPageParser {
    boolean supports(TrendSource source);
    String version();
    TrendModels.ParsedPage parse(String html, TrendSource source, int page);
}
