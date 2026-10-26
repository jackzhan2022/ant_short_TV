package com.antshorttv.material;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.MediaPage;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system-audio")
public class SystemAudioLibraryController {
    private final SystemAudioLibraryService service;
    public SystemAudioLibraryController(SystemAudioLibraryService service) { this.service = service; }

    @GetMapping
    public ApiResponse<MediaPage<SystemAudioLibraryService.Audio>> list(@RequestParam(required=false) String keyword,
        @RequestParam(required=false) String gender, @RequestParam(required=false) String ageGroup,
        @RequestParam(required=false) Integer current, @RequestParam(required=false) Integer pageSize) {
        return ApiResponse.success(service.list(keyword, gender, ageGroup, current, pageSize));
    }

    @GetMapping("/{id}/playback")
    public ResponseEntity<Void> playback(@PathVariable Long id) {
        return ResponseEntity.status(302).header("Cache-Control", "no-store")
            .location(URI.create(service.playback(id).url())).build();
    }
}
