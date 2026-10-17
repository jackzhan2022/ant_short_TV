package com.antshorttv.trend;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class TrendModels {
    private TrendModels() {}

    public record Entry(int rank, String externalId, String title, String contentType,
        String detailUrl, String coverUrl, String metricText, BigDecimal metricValue,
        String metricUnit, List<String> tags, String metricKind, Integer episodeCount) {
        public Entry(int rank,String externalId,String title,String contentType,String detailUrl,String coverUrl,
            String metricText,BigDecimal metricValue,String metricUnit,List<String> tags) {
            this(rank,externalId,title,contentType,detailUrl,coverUrl,metricText,metricValue,metricUnit,tags,"PLATFORM_HEAT",null);
        }
    }
    public record ParsedPage(int page, int totalPages, String nextUrl, String sourceUpdatedText,
        List<Entry> entries, LocalDate sourceDate, Integer reportedTotal, String coverage) {
        public ParsedPage(int page,int totalPages,String nextUrl,String sourceUpdatedText,List<Entry> entries) {
            this(page,totalPages,nextUrl,sourceUpdatedText,entries,null,null,"FULL_PUBLIC_PAGES");
        }
    }
    public record HttpPage(int status, String contentType, String body, Map<String,String> headers, Long elapsedMillis) {
        public HttpPage { headers = TrendRequestDiagnostics.safeHeaders(headers); }
        public HttpPage(int status, String contentType, String body) { this(status,contentType,body,Map.of(),null); }
    }
    public record RequestDiagnostic(int pageNumber, String stage, String url, int httpStatus,
        String contentType, Instant fetchedAt, Long elapsedMillis, Map<String,String> headers,
        String provider, String cause, String requestId) {}
    public record RunDiagnostics(Run run, List<RequestDiagnostic> requests) {}
    public record Run(String id, String sourceId, String status, Instant startedAt, Instant finishedAt,
        String snapshotId, int entryCount, String error, String errorCode, String failureStage) {
        public Run(String id,String sourceId,String status,Instant startedAt,Instant finishedAt,String snapshotId,int entryCount,String error,String errorCode) {
            this(id,sourceId,status,startedAt,finishedAt,snapshotId,entryCount,error,errorCode,null);
        }
        public Run(String id,String sourceId,String status,Instant startedAt,Instant finishedAt,String snapshotId,int entryCount,String error) {
            this(id,sourceId,status,startedAt,finishedAt,snapshotId,entryCount,error,null);
        }
    }
    public record Snapshot(String id, String sourceId, String title, String market, String platform,
        String contentType, String sourceType, String sourceUrl, LocalDate observedDate,
        Instant observedAt, String sourceUpdatedText, int pageCount, int entryCount,
        String fingerprint, String parserVersion, LocalDate sourceDate, String language,
        String rankingKind, String coverage, Integer reportedTotal) {}
    public record Page<T>(List<T> records, long total, int page, int pageSize) {}
    public record SnapshotDetail(Snapshot snapshot, Page<Entry> entries) {}
    public record Query(String market, String platform, String contentType, String sourceType,
        LocalDate from, LocalDate to, int page, int pageSize) {}
    public record SourceInfo(String id, String title, String market, String platform,
        String contentType, String sourceType, String url, String language, String rankingKind, String parserKind) {}
    public record SourceCoverage(SourceInfo source, Run latestRun, Snapshot latestSnapshot) {}
}
