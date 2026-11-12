package com.antshorttv.drama;

import com.antshorttv.drama.DramaModels.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MiniappViewerService {
    private final DramaLibraryService library;
    private final ReleaseMapper releases;
    private final WatchProgressMapper progress;
    private final MeterRegistry metrics;
    public MiniappViewerService(DramaLibraryService library, ReleaseMapper releases, WatchProgressMapper progress, MeterRegistry metrics) {
        this.library = library; this.releases = releases; this.progress = progress; this.metrics = metrics;
    }

    public CatalogPage catalog(Miniapp app, int page, int pageSize, String keyword, String category) {
        if (page < 1 || page > 100000 || pageSize < 1 || pageSize > 50
            || keyword != null && keyword.length() > 200 || category != null && category.length() > 40)
            throw DramaLibraryService.invalid("目录分页或搜索参数无效。");
        QueryWrapper<Release> filter = scoped(app);
        if (keyword != null && !keyword.isBlank()) {
            // Escape SQL LIKE wildcards so searches remain literal titles.
            filter.apply("title like {0} escape '!'", "%" + keyword.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        }
        if (category != null && !category.isBlank()) filter.eq("category", category.trim());
        long total = releases.selectCount(filter);
        List<Release> items = releases.selectList(filter.orderByAsc("sort_order").orderByDesc("recommended", "id")
            .last("limit " + pageSize + " offset " + ((long)(page - 1) * pageSize)));
        List<String> categories = releases.<Object>selectObjs(scoped(app).select("distinct category")
            .isNotNull("category").ne("category", "").orderByAsc("category")).stream().map(Object::toString).toList();
        List<Object> ids = releases.selectObjs(scoped(app).select("id").orderByAsc("id"));
        String version = MiniappSessionService.hash(ids.toString());
        return new CatalogPage(items, total, page, pageSize, categories, version);
    }

    public DramaDetail detail(Miniapp app, Long dramaId) {
        Release release = library.published(app, dramaId);
        return new DramaDetail(release, library.playbackEpisodes(app, dramaId));
    }

    public List<WatchProgress> history(Viewer viewer) { return progress.history(viewer.app().id, viewer.openid()); }

    @Transactional
    public void saveProgress(Viewer viewer, Long dramaId, ProgressRequest body) {
        ReleaseEpisode episode = library.playbackEpisodes(viewer.app(), dramaId).stream()
            .filter(e -> e.episodeId.equals(body.episodeId())).findFirst().orElseThrow(DramaLibraryService::missing);
        if (!Double.isFinite(body.positionSeconds()) || !Double.isFinite(body.durationSeconds()))
            throw DramaLibraryService.invalid("播放进度无效。");
        double duration = episode.durationSeconds == null ? Math.min(body.durationSeconds(), 86400) : episode.durationSeconds;
        double position = Math.max(0, Math.min(body.positionSeconds(), duration));
        progress.ensure(viewer.app().id, viewer.openid(), dramaId, body.episodeId());
        WatchProgress row = progress.lock(viewer.app().id, viewer.openid(), dramaId);
        if (body.playbackSession().equals(row.playbackSession) && body.sequence() <= row.sequence) return;
        row.episodeId = body.episodeId(); row.positionSeconds = position; row.durationSeconds = duration;
        row.completed = Boolean.TRUE.equals(body.completed()) && duration > 0 && position >= duration - 2;
        row.sequence = body.sequence(); row.playbackSession = body.playbackSession(); row.updatedAt = LocalDateTime.now();
        progress.updateById(row);
    }

    public void clearHistory(Viewer viewer) {
        progress.delete(new QueryWrapper<WatchProgress>().eq("miniapp_id", viewer.app().id).eq("openid", viewer.openid()));
    }

    public void event(Miniapp app, PlaybackEvent body) {
        boolean available = library.playbackEpisodes(app, body.dramaId()).stream().anyMatch(e -> e.episodeId.equals(body.episodeId()));
        if (!available) throw DramaLibraryService.missing();
        String[] tags = {"miniapp", app.id.toString(), "event", body.event()};
        metrics.counter("miniapp.playback.events", tags).increment();
        if (body.elapsedMs() != null) metrics.timer("miniapp.playback.elapsed", tags).record(body.elapsedMs(), TimeUnit.MILLISECONDS);
    }

    private QueryWrapper<Release> scoped(Miniapp app) {
        return new QueryWrapper<Release>().eq("tenant_id", app.tenantId).eq("miniapp_id", app.id).eq("published", true);
    }
}
