package com.antshorttv.drama;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.drama.DramaModels.*;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.storage.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DramaLibraryService {
    private final DramaMapper dramas;
    private final LibraryEpisodeMapper episodes;
    private final MiniappMapper miniapps;
    private final ReleaseMapper releases;
    private final ReleaseEpisodeMapper releaseEpisodes;
    private final DramaSourceMapper sources;
    private final MediaUploadSessionService uploads;
    private final ObjectStorageService storage;
    private final ObjectStorageKeyFactory keys;
    private final ProjectPermissionGuard projects;
    private final MiniappOwnership ownership;
    @org.springframework.beans.factory.annotation.Autowired
    private WechatComponentService component;

    public DramaLibraryService(DramaMapper dramas, LibraryEpisodeMapper episodes, MiniappMapper miniapps,
        ReleaseMapper releases, ReleaseEpisodeMapper releaseEpisodes, DramaSourceMapper sources,
        MediaUploadSessionService uploads, ObjectStorageService storage, ObjectStorageKeyFactory keys,
        ProjectPermissionGuard projects, MiniappOwnership ownership) {
        this.dramas = dramas; this.episodes = episodes; this.miniapps = miniapps;
        this.releases = releases; this.releaseEpisodes = releaseEpisodes; this.sources = sources;
        this.uploads = uploads; this.storage = storage; this.keys = keys; this.projects = projects;
        this.ownership = ownership;
    }

    public List<Drama> list(Long tenantId) {
        return dramas.selectList(new QueryWrapper<Drama>().eq("tenant_id", tenantId)
            .isNull("deleted_at").orderByDesc("id").last("limit 500"));
    }

    public Drama get(Long tenantId, Long id) {
        Drama drama = dramas.selectOne(new QueryWrapper<Drama>().eq("tenant_id", tenantId)
            .eq("id", id).isNull("deleted_at"));
        if (drama == null) throw missing();
        return drama;
    }

    @Transactional
    public Drama create(Long tenantId, DramaRequest body) {
        Drama drama = new Drama(); drama.tenantId = tenantId; drama.title = body.title().trim();
        drama.description = body.description(); drama.createdAt = LocalDateTime.now();
        dramas.insert(drama); return drama;
    }

    @Transactional
    public Drama update(Long tenantId, Long id, DramaRequest body) {
        Drama drama = lockDrama(tenantId, id);
        drama.title = body.title().trim(); drama.description = body.description();
        dramas.updateById(drama); return drama;
    }

    @Transactional
    public void delete(Long tenantId, Long id) {
        Drama drama = lockDrama(tenantId, id);
        if (releases.selectCount(new QueryWrapper<Release>().eq("tenant_id", tenantId)
            .eq("drama_id", id).eq("published", true)) > 0) throw inUse();
        drama.deletedAt = LocalDateTime.now(); dramas.updateById(drama);
        episodes.update(null, new UpdateWrapper<Episode>().eq("tenant_id", tenantId)
            .eq("drama_id", id).set("deleted_at", drama.deletedAt));
    }

    public List<Episode> episodes(Long tenantId, Long dramaId) {
        get(tenantId, dramaId);
        return episodes.selectList(new QueryWrapper<Episode>().eq("tenant_id", tenantId)
            .eq("drama_id", dramaId).isNull("deleted_at").orderByAsc("episode_no", "id"));
    }

    @Transactional
    public Episode importEpisode(Long tenantId, Long userId, Long dramaId, EpisodeRequest body) {
        lockDrama(tenantId, dramaId);
        boolean local = body.uploadSessionToken() != null && !body.uploadSessionToken().isBlank();
        boolean project = body.projectId() != null && body.sourceVersionId() != null;
        if (local == project || local && (body.projectId() != null || body.sourceVersionId() != null)) {
            throw invalid("必须选择且仅选择本地上传或项目成片来源。");
        }
        if (episodes.selectCount(new QueryWrapper<Episode>().eq("tenant_id", tenantId)
            .eq("drama_id", dramaId).eq("episode_no", body.episodeNo()).isNull("deleted_at")) > 0) {
            throw invalid("单集序号已存在；请删除未发布版本后重新导入。");
        }
        Episode episode = new Episode(); episode.tenantId = tenantId; episode.dramaId = dramaId;
        episode.episodeNo = body.episodeNo(); episode.title = body.title().trim();
        episode.createdAt = LocalDateTime.now();
        if (local) {
            VerifiedMediaUpload upload = uploads.requireCompleted(userId, tenantId, null, body.uploadSessionToken());
            requireVideo(storage.metadata(upload.objectKey()));
            // 剧库独立固化原件，避免上传会话过期清理影响播放。
            String ext = upload.objectKey().substring(upload.objectKey().lastIndexOf('.') + 1);
            String target = keys.tenantOriginal(tenantId, "drama", dramaId, UUID.randomUUID().toString(), LocalDate.now(), ext);
            StoredObject copied = storage.copyCompletedUploadOriginal(
                new StoredObject(upload.objectKey(), upload.size(), upload.contentType(), upload.eTag(), null), target);
            episode.storagePath = copied.key(); episode.fileSize = copied.size(); episode.sourceType = "LOCAL_UPLOAD";
        } else {
            projects.require(tenantId, body.projectId(), "EPISODE_VERSION:DOWNLOAD");
            var version = sources.lock(tenantId, body.projectId(), body.sourceVersionId());
            if (version == null) throw missing();
            if (version.storagePath == null || !version.storagePath.startsWith("materials/" + tenantId + "/" + body.projectId() + "/")) {
                throw invalid("成片尚未迁移到本团队的私有对象存储，不能导入。");
            }
            StoredObject object = storage.metadata(version.storagePath); requireVideo(object);
            episode.storagePath = object.key(); episode.fileSize = object.size(); episode.sourceType = "PROJECT_VERSION";
            episode.sourceProjectId = body.projectId(); episode.sourceVersionId = version.id;
        }
        episodes.insert(episode); return episode;
    }

    @Transactional
    public void deleteEpisode(Long tenantId, Long dramaId, Long episodeId) {
        lockDrama(tenantId, dramaId);
        Episode episode = episodes.selectOne(new QueryWrapper<Episode>().eq("tenant_id", tenantId)
            .eq("drama_id", dramaId).eq("id", episodeId).isNull("deleted_at"));
        if (episode == null) throw missing();
        List<Long> published = releases.selectList(new QueryWrapper<Release>().eq("tenant_id", tenantId)
            .eq("drama_id", dramaId).eq("published", true)).stream().map(r -> r.id).toList();
        if (!published.isEmpty() && releaseEpisodes.selectCount(new QueryWrapper<ReleaseEpisode>()
            .in("release_id", published).eq("episode_id", episodeId)) > 0) throw inUse();
        episode.deletedAt = LocalDateTime.now(); episodes.updateById(episode);
    }

    public List<Miniapp> miniapps(Long tenantId) {
        return miniapps.selectList(new QueryWrapper<Miniapp>().eq("tenant_id", tenantId).orderByAsc("id"));
    }

    @Transactional
    public Miniapp bind(Long tenantId, BindingRequest body) {
        ownership.require(body.appid(), tenantId);
        // appid 全局唯一归属，手工绑定不是微信官方授权；secret 只允许在服务端配置。
        if (miniapps.selectCount(new QueryWrapper<Miniapp>().eq("appid", body.appid())) > 0) {
            throw invalid("该小程序已绑定，不能重复或跨团队绑定。");
        }
        Miniapp app = new Miniapp(); app.tenantId = tenantId; app.appid = body.appid();
        app.name = body.name().trim(); app.bindingType = "MANUAL"; app.enabled = true;
        app.createdAt = LocalDateTime.now(); miniapps.insert(app); return app;
    }

    @Transactional
    public Miniapp enable(Long tenantId, Long id, boolean enabled) {
        if (component != null) component.lockLifecycle();
        Miniapp app = miniapps.lock(tenantId, id); if (app == null) throw missing();
        if (enabled && "OFFICIAL".equals(app.bindingType)) component.requireAuthorized(app);
        app.enabled = enabled; miniapps.updateById(app); return app;
    }

    public List<Release> releases(Long tenantId, Long appId) {
        requireApp(tenantId, appId);
        return releases.selectList(new QueryWrapper<Release>().eq("tenant_id", tenantId).eq("miniapp_id", appId).orderByDesc("id"));
    }

    @Transactional
    public Release publish(Long tenantId, Long appId, Long dramaId, PublishRequest body) {
        if (component != null) component.lockLifecycle();
        Drama drama = lockDrama(tenantId, dramaId);
        Miniapp app = miniapps.lock(tenantId, appId);
        if (app == null) throw missing();
        if (!Boolean.TRUE.equals(app.enabled)) throw invalid("小程序已停用。");
        if ("OFFICIAL".equals(app.bindingType)) component.requireAuthorized(app);
        if (new HashSet<>(body.episodeIds()).size() != body.episodeIds().size()) throw invalid("不能重复选择单集。");
        List<Episode> selected = episodes.selectList(new QueryWrapper<Episode>().eq("tenant_id", tenantId)
            .eq("drama_id", dramaId).in("id", body.episodeIds()).isNull("deleted_at").orderByAsc("episode_no"));
        if (selected.size() != body.episodeIds().size()) throw missing();
        for (Episode episode : selected) requireVideo(storage.metadata(episode.storagePath));
        unpublishRows(tenantId, appId, dramaId);
        Release release = new Release(); release.tenantId = tenantId; release.miniappId = appId;
        release.dramaId = dramaId; release.title = drama.title; release.description = drama.description;
        release.published = true; release.createdAt = LocalDateTime.now(); releases.insert(release);
        for (Episode episode : selected) {
            ReleaseEpisode item = new ReleaseEpisode(); item.releaseId = release.id; item.episodeId = episode.id;
            item.episodeNo = episode.episodeNo; item.title = episode.title; item.storagePath = episode.storagePath;
            item.sourceVersionId = episode.sourceVersionId; releaseEpisodes.insert(item);
        }
        return release;
    }

    @Transactional
    public void unpublish(Long tenantId, Long appId, Long dramaId) {
        lockDrama(tenantId, dramaId);
        if (miniapps.lock(tenantId, appId) == null) throw missing();
        unpublishRows(tenantId, appId, dramaId);
    }

    public List<Release> catalog(Miniapp app) {
        return releases.selectList(new QueryWrapper<Release>().eq("tenant_id", app.tenantId)
            .eq("miniapp_id", app.id).eq("published", true).orderByDesc("id").last("limit 500"));
    }

    public List<ReleaseEpisode> playbackEpisodes(Miniapp app, Long dramaId) {
        Release release = published(app, dramaId);
        return releaseEpisodes.selectList(new QueryWrapper<ReleaseEpisode>().eq("release_id", release.id).orderByAsc("episode_no"));
    }

    @Transactional
    public PlayResponse play(Miniapp app, Long dramaId, Long episodeId) {
        if ("OFFICIAL".equals(app.bindingType)) {
            component.lockLifecycle();
            Miniapp current = requireApp(app.tenantId, app.id);
            if (!Boolean.TRUE.equals(current.enabled)) throw WechatMiniappIdentity.unauthorized();
            component.requireAuthorized(current);
        }
        Release release = published(app, dramaId);
        ReleaseEpisode item = releaseEpisodes.selectOne(new QueryWrapper<ReleaseEpisode>()
            .eq("release_id", release.id).eq("episode_id", episodeId));
        if (item == null) throw missing();
        Instant expires = Instant.now().plusSeconds(300);
        return new PlayResponse(dramaId, episodeId, storage.cdnUrl(item.storagePath, expires), expires);
    }

    private Release published(Miniapp app, Long dramaId) {
        Release release = releases.selectOne(new QueryWrapper<Release>().eq("tenant_id", app.tenantId)
            .eq("miniapp_id", app.id).eq("drama_id", dramaId).eq("published", true));
        if (release == null) throw missing(); return release;
    }

    private void unpublishRows(Long tenantId, Long appId, Long dramaId) {
        releases.update(null, new UpdateWrapper<Release>().eq("tenant_id", tenantId)
            .eq("miniapp_id", appId).eq("drama_id", dramaId).eq("published", true).set("published", false));
    }

    private Miniapp requireApp(Long tenantId, Long id) {
        Miniapp app = miniapps.selectOne(new QueryWrapper<Miniapp>().eq("tenant_id", tenantId).eq("id", id));
        if (app == null) throw missing(); return app;
    }

    private Drama lockDrama(Long tenantId, Long id) {
        Drama drama = dramas.lock(tenantId, id); if (drama == null) throw missing(); return drama;
    }

    private void requireVideo(StoredObject object) {
        if (object == null || object.size() <= 0 || object.contentType() == null || !object.contentType().startsWith("video/")) {
            throw invalid("来源必须是已完成校验的非空视频对象。");
        }
    }
    static BusinessException missing() { return new BusinessException(ErrorCode.NOT_FOUND, "剧库资源不存在。"); }
    static BusinessException invalid(String message) { return new BusinessException(ErrorCode.VALIDATION_ERROR, message); }
    static BusinessException inUse() { return invalid("资源已上架引用，请先对所有小程序下架。"); }
}
