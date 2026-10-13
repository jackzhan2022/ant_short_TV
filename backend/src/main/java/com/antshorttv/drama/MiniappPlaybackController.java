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
    public MiniappPlaybackController(MiniappSessionService sessions, DramaLibraryService library) {
        this.sessions = sessions; this.library = library;
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
}
