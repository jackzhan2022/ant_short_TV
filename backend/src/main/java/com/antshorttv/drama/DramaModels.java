package com.antshorttv.drama;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class DramaModels {
    private DramaModels() {}

    @TableName("library_drama")
    public static class Drama {
        @TableId(type = IdType.AUTO) public Long id;
        public Long tenantId;
        public String title;
        public String description;
        public String coverUrl;
        public String category;
        public String completionStatus;
        public Integer totalEpisodes;
        @TableField(exist = false) public Long episodeCount;
        @TableField(exist = false) public Long publishedAppCount;
        public LocalDateTime deletedAt;
        public LocalDateTime createdAt;
    }

    @TableName("library_episode")
    public static class Episode {
        @TableId(type = IdType.AUTO) public Long id;
        public Long tenantId;
        public Long dramaId;
        public Integer episodeNo;
        public String title;
        public String sourceType;
        public Long sourceProjectId;
        public Long sourceVersionId;
        @JsonIgnore public String storagePath;
        public Long fileSize;
        @JsonIgnore public String sourceUploadHash;
        public String posterUrl;
        public Double durationSeconds;
        public Integer width;
        public Integer height;
        public LocalDateTime deletedAt;
        public LocalDateTime createdAt;
    }

    @TableName("team_miniapp")
    public static class Miniapp {
        @TableId(type = IdType.AUTO) public Long id;
        public Long tenantId;
        public String appid;
        public String name;
        public String bindingType;
        public String authStatus;
        public Boolean enabled;
        public LocalDateTime createdAt;
    }

    @TableName("miniapp_drama_release")
    public static class Release {
        @TableId(type = IdType.AUTO) public Long id;
        public Long tenantId;
        public Long miniappId;
        public Long dramaId;
        public String title;
        public String description;
        public String coverUrl;
        public String category;
        public String completionStatus;
        public Integer totalEpisodes;
        public Integer sortOrder;
        public Boolean recommended;
        public Integer episodeCount;
        public Boolean published;
        public LocalDateTime createdAt;
    }

    @TableName("miniapp_release_episode")
    public static class ReleaseEpisode {
        @TableId(type = IdType.AUTO) public Long id;
        public Long releaseId;
        public Long episodeId;
        public Integer episodeNo;
        public String title;
        @JsonIgnore public String storagePath;
        public Long sourceVersionId;
        public String posterUrl;
        public Double durationSeconds;
        public Integer width;
        public Integer height;
    }

    @TableName("miniapp_viewer_session")
    public static class ViewerSession {
        @TableId(type = IdType.AUTO) public Long id;
        public Long miniappId;
        @JsonIgnore public String tokenHash;
        @JsonIgnore public String openid;
        public LocalDateTime expiresAt;
    }

    public record DramaRequest(@NotBlank @Size(max = 200) String title,
        @Size(max = 4000) String description,
        @Size(max = 1024) @Pattern(regexp = "https://[^\\s]+|^$", message = "封面须为 HTTPS 地址") String coverUrl,
        @Size(max = 40) String category,
        @Pattern(regexp = "ONGOING|COMPLETED") String completionStatus,
        @Min(1) @Max(10000) Integer totalEpisodes) {
        public DramaRequest(String title, String description) { this(title, description, null, null, "ONGOING", null); }
    }
    public record EpisodeUpdate(@NotBlank @Size(max = 200) String title,
        @Size(max = 1024) @Pattern(regexp = "https://[^\\s]+|^$") String posterUrl) {}
    public record EpisodeRequest(@NotNull @Positive Integer episodeNo,
                                 @NotBlank @Size(max = 200) String title,
                                 @Size(max = 100) String uploadSessionToken,
                                 @Positive Long projectId, @Positive Long sourceVersionId) {}
    public record BindingRequest(@NotBlank @Pattern(regexp = "wx[0-9a-fA-F]{16}") String appid,
                                 @NotBlank @Size(max = 100) String name) {}
    public record BindingUpdate(@NotNull Boolean enabled) {}
    public record PublishRequest(@NotEmpty @Size(max = 500) List<@NotNull @Positive Long> episodeIds,
        @Min(-100000) @Max(100000) Integer sortOrder, Boolean recommended) {
        public PublishRequest(List<Long> episodeIds) { this(episodeIds, null, null); }
    }
    public record Preflight(boolean valid, List<String> errors, List<String> warnings,
        List<Integer> added, List<Integer> removed, List<Integer> changed, boolean metadataChanged, Long previousReleaseId) {}
    public record CatalogPage(List<Release> items, long total, int page, int pageSize, List<String> categories, String version) {}
    public record DramaDetail(Release drama, List<ReleaseEpisode> episodes) {}
    public record Viewer(Miniapp app, @JsonIgnore String openid) {}
    @TableName("miniapp_watch_progress")
    public static class WatchProgress {
        @TableId(type = IdType.AUTO) @JsonIgnore public Long id;
        @JsonIgnore public Long miniappId;
        @JsonIgnore public String openid;
        public Long dramaId;
        public Long episodeId;
        public Double positionSeconds;
        public Double durationSeconds;
        public Boolean completed;
        @TableField("client_sequence") @JsonIgnore public Long sequence;
        @JsonIgnore public String playbackSession;
        public LocalDateTime updatedAt;
        @TableField(exist = false) public Integer episodeNo;
        @TableField(exist = false) public String title;
        @TableField(exist = false) public String episodeTitle;
        @TableField(exist = false) public String coverUrl;
        @TableField(exist = false) public Long releaseId;
    }
    public record ProgressRequest(@NotNull @Positive Long episodeId,
        @NotNull @DecimalMin("0") @DecimalMax("86400") Double positionSeconds,
        @NotNull @DecimalMin("0") @DecimalMax("86400") Double durationSeconds,
        @NotNull Boolean completed, @NotNull @PositiveOrZero Long sequence,
        @NotBlank @Size(max = 64) String playbackSession) {}
    public record PlaybackEvent(@NotNull @Positive Long dramaId, @NotNull @Positive Long episodeId,
        @NotNull @Pattern(regexp = "FIRST_FRAME|ERROR|STALL") String event,
        @PositiveOrZero @Max(86400000) Long elapsedMs) {}
    public record LoginRequest(@NotBlank @Pattern(regexp = "wx[0-9a-fA-F]{16}") String appid,
                               @NotBlank @Size(max = 256) String code) {}
    public record LoginResponse(String accessToken, String tokenType, LocalDateTime expiresAt) {}
    public record PlayResponse(Long dramaId, Long episodeId, String url, java.time.Instant expiresAt) {}
}
