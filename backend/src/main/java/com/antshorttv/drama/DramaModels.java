package com.antshorttv.drama;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
                               @Size(max = 4000) String description) {}
    public record EpisodeRequest(@NotNull @Positive Integer episodeNo,
                                 @NotBlank @Size(max = 200) String title,
                                 @Size(max = 100) String uploadSessionToken,
                                 @Positive Long projectId, @Positive Long sourceVersionId) {}
    public record BindingRequest(@NotBlank @Pattern(regexp = "wx[0-9a-fA-F]{16}") String appid,
                                 @NotBlank @Size(max = 100) String name) {}
    public record BindingUpdate(@NotNull Boolean enabled) {}
    public record PublishRequest(@NotEmpty @Size(max = 500) List<@NotNull @Positive Long> episodeIds) {}
    public record LoginRequest(@NotBlank @Pattern(regexp = "wx[0-9a-fA-F]{16}") String appid,
                               @NotBlank @Size(max = 256) String code) {}
    public record LoginResponse(String accessToken, String tokenType, LocalDateTime expiresAt) {}
    public record PlayResponse(Long dramaId, Long episodeId, String url, java.time.Instant expiresAt) {}
}
