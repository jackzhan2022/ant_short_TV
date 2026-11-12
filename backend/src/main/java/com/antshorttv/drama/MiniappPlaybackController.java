package com.antshorttv.drama;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.drama.DramaModels.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/miniapp")
public class MiniappPlaybackController {
    private final MiniappSessionService sessions;
    private final DramaLibraryService library;
    private final MiniappViewerService viewers;
    public MiniappPlaybackController(MiniappSessionService sessions, DramaLibraryService library, MiniappViewerService viewers) {
        this.sessions = sessions; this.library = library; this.viewers = viewers;
    }
    @PostMapping("/auth/login") public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest body) {
        return ApiResponse.success(sessions.login(body));
    }
    @PostMapping("/auth/logout") public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String auth) {
        sessions.logout(auth); return ApiResponse.ok();
    }
    @GetMapping("/dramas") public ApiResponse<List<Release>> catalog(@RequestHeader(value = "Authorization", required = false) String auth) {
        return ApiResponse.success(library.catalog(sessions.authenticate(auth)));
    }
    @GetMapping("/dramas/{id}/episodes") public ApiResponse<List<ReleaseEpisode>> episodes(@PathVariable Long id,
        @RequestHeader(value = "Authorization", required = false) String auth) {
        return ApiResponse.success(library.playbackEpisodes(sessions.authenticate(auth), id));
    }
    @GetMapping("/dramas/{id}/episodes/{episodeId}/play") public ApiResponse<PlayResponse> play(@PathVariable Long id,
        @PathVariable Long episodeId, @RequestHeader(value = "Authorization", required = false) String auth) {
        return ApiResponse.success(library.play(sessions.authenticate(auth), id, episodeId));
    }
    @GetMapping("/catalog") public ApiResponse<CatalogPage> page(
        @RequestHeader(value = "Authorization", required = false) String auth,
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
        @RequestParam(required = false) String keyword, @RequestParam(required = false) String category) {
        return ApiResponse.success(viewers.catalog(sessions.authenticate(auth), page, pageSize, keyword, category));
    }
    @GetMapping("/dramas/{id}") public ApiResponse<DramaDetail> detail(@PathVariable Long id,
        @RequestHeader(value = "Authorization", required = false) String auth) {
        return ApiResponse.success(viewers.detail(sessions.authenticate(auth), id));
    }
    @GetMapping("/history") public ApiResponse<List<WatchProgress>> history(@RequestHeader(value = "Authorization", required = false) String auth) {
        return ApiResponse.success(viewers.history(sessions.viewer(auth)));
    }
    @PutMapping("/history/{id}") public ApiResponse<Void> progress(@PathVariable Long id,
        @RequestHeader(value = "Authorization", required = false) String auth, @Valid @RequestBody ProgressRequest body) {
        viewers.saveProgress(sessions.viewer(auth), id, body); return ApiResponse.ok();
    }
    @DeleteMapping("/history") public ApiResponse<Void> clearHistory(@RequestHeader(value = "Authorization", required = false) String auth) {
        viewers.clearHistory(sessions.viewer(auth)); return ApiResponse.ok();
    }
    @PostMapping("/playback-events") public ApiResponse<Void> event(@RequestHeader(value = "Authorization", required = false) String auth,
        @Valid @RequestBody PlaybackEvent body) {
        viewers.event(sessions.authenticate(auth), body); return ApiResponse.ok();
    }
}
