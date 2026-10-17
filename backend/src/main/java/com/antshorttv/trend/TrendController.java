package com.antshorttv.trend;

import static com.antshorttv.trend.TrendModels.*;
import com.antshorttv.common.ApiResponse;
import com.antshorttv.platform.RequirePlatformPermission;
import com.antshorttv.security.CurrentPrincipal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class TrendController {
    private final TrendCollectionService collections;
    private final TrendStore store;
    private final CurrentPrincipal principal;

    public TrendController(TrendCollectionService collections, TrendStore store, CurrentPrincipal principal) {
        this.collections = collections; this.store = store; this.principal = principal;
    }

    @GetMapping("/short-drama-trends/sources")
    public ApiResponse<List<SourceInfo>> sources() {
        principal.require();
        return ApiResponse.success(Arrays.stream(TrendSource.values()).map(TrendSource::info).toList());
    }

    @GetMapping("/short-drama-trends/snapshots")
    public ApiResponse<Page<Snapshot>> snapshots(
        @RequestParam(required=false) String market,
        @RequestParam(required=false) String platform,
        @RequestParam(required=false) String contentType,
        @RequestParam(required=false) String sourceType,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue="1") int page,
        @RequestParam(defaultValue="20") int pageSize
    ) {
        principal.require();
        return ApiResponse.success(store.query(new Query(market, platform, contentType, sourceType, from, to, page, pageSize)));
    }

    @GetMapping("/short-drama-trends/snapshots/{id}")
    public ApiResponse<SnapshotDetail> detail(@PathVariable String id,
        @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int pageSize) {
        principal.require();
        return ApiResponse.success(store.detail(id, page, pageSize));
    }

    @PostMapping("/platform/short-drama-trends/collections/{source}")
    @RequirePlatformPermission("PLATFORM_TREND_COLLECT")
    public ApiResponse<Run> collect(@PathVariable String source) {
        return ApiResponse.success(collections.collect(TrendSource.from(source), principal.require().userId()));
    }

    @GetMapping("/platform/short-drama-trends/collections/{id}")
    @RequirePlatformPermission("PLATFORM_TREND_COLLECT")
    public ApiResponse<Run> run(@PathVariable String id) {
        return ApiResponse.success(store.run(id));
    }

    @GetMapping("/platform/short-drama-trends/collections/{id}/diagnostics")
    @RequirePlatformPermission("PLATFORM_TREND_COLLECT")
    public ApiResponse<RunDiagnostics> diagnostics(@PathVariable String id) {
        return ApiResponse.success(store.diagnostics(id));
    }

    @GetMapping("/platform/short-drama-trends/coverage")
    @RequirePlatformPermission("PLATFORM_TREND_COLLECT")
    public ApiResponse<List<SourceCoverage>> coverage() {
        return ApiResponse.success(store.coverage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.fail("VALIDATION_ERROR", exception.getMessage()));
    }
}
