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
    @org.springframework.beans.factory.annotation.Autowired
    private DramaMediaInspector inspector;

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
        List<Drama> rows = dramas.selectList(new QueryWrapper<Drama>().eq("tenant_id", tenantId)
            .isNull("deleted_at").orderByDesc("id").last("limit 500"));
        // Aggregate once for the entire library, rather than one count per table row.
        Map<Long, Long> counts = new HashMap<>(), apps = new HashMap<>();
        episodes.selectMaps(new QueryWrapper<Episode>().select("drama_id", "count(*) as cnt")
            .eq("tenant_id", tenantId).isNull("deleted_at").groupBy("drama_id"))
            .forEach(r -> counts.put(((Number)r.get("drama_id")).longValue(), ((Number)r.get("cnt")).longValue()));
        releases.selectMaps(new QueryWrapper<Release>().select("drama_id", "count(*) as cnt")
            .eq("tenant_id", tenantId).eq("published", true).groupBy("drama_id"))
            .forEach(r -> apps.put(((Number)r.get("drama_id")).longValue(), ((Number)r.get("cnt")).longValue()));
        rows.forEach(r -> { r.episodeCount = counts.getOrDefault(r.id, 0L); r.publishedAppCount = apps.getOrDefault(r.id, 0L); });
        return rows;
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
        metadata(drama, body);
        dramas.insert(drama); return drama;
    }

    @Transactional
    public Drama update(Long tenantId, Long id, DramaRequest body) {
        Drama drama = lockDrama(tenantId, id);
        drama.title = body.title().trim(); drama.description = body.description();
        metadata(drama, body);
        dramas.update(null, new UpdateWrapper<Drama>().eq("id", id).eq("tenant_id", tenantId)
            .set("title", drama.title).set("description", drama.description).set("cover_url", drama.coverUrl)
            .set("category", drama.category).set("completion_status", drama.completionStatus).set("total_episodes", drama.totalEpisodes));
        return drama;
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
        String uploadHash = local ? MiniappSessionService.hash(body.uploadSessionToken()) : null;
        if (local) {
            Episode existing = episodes.selectOne(new QueryWrapper<Episode>().eq("tenant_id", tenantId)
                .eq("drama_id", dramaId).eq("source_upload_hash", uploadHash).isNull("deleted_at"));
            if (existing != null) {
                if (!Objects.equals(existing.episodeNo, body.episodeNo()) || !Objects.equals(existing.title, body.title().trim()))
                    throw invalid("该上传已导入，请编辑现有单集或选择新文件。");
                return existing;
            }
        }
        if (episodes.selectCount(new QueryWrapper<Episode>().eq("tenant_id", tenantId)
            .eq("drama_id", dramaId).eq("episode_no", body.episodeNo()).isNull("deleted_at")) > 0) {
            throw invalid("单集序号已存在；请删除未发布版本后重新导入。");
        }
        Episode episode = new Episode(); episode.tenantId = tenantId; episode.dramaId = dramaId;
        episode.episodeNo = body.episodeNo(); episode.title = body.title().trim();
        episode.createdAt = LocalDateTime.now();
        episode.sourceUploadHash = uploadHash;
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
        if (inspector != null) {
            try { inspector.inspect(episode); }
            catch (com.antshorttv.common.BusinessException ex) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("剧库视频元数据检测失败，待重新检测: episodeNo={}", episode.episodeNo);
            }
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
    public Episode updateEpisode(Long tenantId, Long dramaId, Long episodeId, EpisodeUpdate body) {
        lockDrama(tenantId, dramaId);
        Episode episode = requireEpisode(tenantId, dramaId, episodeId);
        episode.title = body.title().trim();
        episodes.update(null, new UpdateWrapper<Episode>().eq("id", episode.id)
            .set("title", episode.title).set("poster_url", body.posterUrl()));
        episode.posterUrl = body.posterUrl(); return episode;
    }

    @Transactional
    public Episode inspectEpisode(Long tenantId, Long dramaId, Long episodeId) {
        lockDrama(tenantId, dramaId);
        Episode episode = requireEpisode(tenantId, dramaId, episodeId);
        requireVideo(storage.metadata(episode.storagePath)); inspector.inspect(episode);
        episodes.updateById(episode); return episode;
    }

    public PlayResponse preview(Long tenantId, Long dramaId, Long episodeId) {
        get(tenantId, dramaId); Episode episode = requireEpisode(tenantId, dramaId, episodeId);
        Instant expires = Instant.now().plusSeconds(300);
        return new PlayResponse(dramaId, episodeId, storage.cdnUrl(episode.storagePath, expires), expires);
    }

    private Episode requireEpisode(Long tenantId, Long dramaId, Long episodeId) {
        Episode episode = episodes.selectOne(new QueryWrapper<Episode>().eq("tenant_id", tenantId)
            .eq("drama_id", dramaId).eq("id", episodeId).isNull("deleted_at"));
        if (episode == null) throw missing(); return episode;
    }

    public Preflight preflight(Long tenantId, Long appId, Long dramaId, PublishRequest body) {
        return inspectRelease(get(tenantId, dramaId), requireApp(tenantId, appId), body);
    }

    private Preflight inspectRelease(Drama drama, Miniapp app, PublishRequest body) {
        List<String> errors = new ArrayList<>(), warnings = new ArrayList<>();
        if (!Boolean.TRUE.equals(app.enabled)) errors.add("小程序已停用。");
        if ("OFFICIAL".equals(app.bindingType)) component.requireAuthorized(app);
        if (drama.coverUrl == null || !drama.coverUrl.matches("https://[^\\s]+")) errors.add("请填写 HTTPS 竖版封面地址。");
        if (drama.category == null || drama.category.isBlank()) errors.add("请填写题材分类。");
        if (drama.totalEpisodes == null || drama.totalEpisodes < 1) errors.add("请填写计划总集数。");
        if (!Set.of("ONGOING", "COMPLETED").contains(Objects.toString(drama.completionStatus, ""))) errors.add("请填写更新状态。");
        List<Long> ids = body.episodeIds();
        if (ids == null || ids.isEmpty() || ids.size() > 500) throw invalid("请选择1至500个单集。");
        if (new HashSet<>(ids).size() != ids.size()) errors.add("不能重复选择单集。");
        List<Episode> selected = episodes.selectList(new QueryWrapper<Episode>().eq("tenant_id", drama.tenantId)
            .eq("drama_id", drama.id).in("id", ids).isNull("deleted_at").orderByAsc("episode_no", "id"));
        if (selected.size() != ids.size()) errors.add("所选单集不存在或已删除。");
        Set<Integer> numbers = new HashSet<>();
        for (Episode episode : selected) {
            if (episode.episodeNo == null || episode.episodeNo < 1 || !numbers.add(episode.episodeNo)) errors.add("集号无效或重复。");
            if (drama.totalEpisodes != null && episode.episodeNo != null && episode.episodeNo > drama.totalEpisodes) errors.add("第" + episode.episodeNo + "集超过计划总集数。");
            if (episode.title == null || episode.title.isBlank()) errors.add("第" + episode.episodeNo + "集缺少标题。");
            if (episode.durationSeconds == null || !Double.isFinite(episode.durationSeconds) || episode.durationSeconds <= 0
                || episode.width == null || episode.width <= 0 || episode.height == null || episode.height <= 0)
                errors.add("第" + episode.episodeNo + "集缺少有效媒体信息，请重新检测。");
            try { requireVideo(storage.metadata(episode.storagePath)); }
            catch (com.antshorttv.common.BusinessException ex) { errors.add("第" + episode.episodeNo + "集视频不可用。"); }
        }
        List<Integer> sorted = numbers.stream().sorted().toList();
        if (!sorted.isEmpty() && (sorted.get(0) != 1 || sorted.get(sorted.size() - 1) != sorted.size()))
            warnings.add("当前发布目录存在跳集，播放将严格按已发布集号顺序进行。");
        if ("COMPLETED".equals(drama.completionStatus) && !Objects.equals(drama.totalEpisodes, selected.size()))
            warnings.add("已标记完结，但所选发布集数与计划总集数不同。");
        Release previous = releases.selectOne(new QueryWrapper<Release>().eq("tenant_id", drama.tenantId)
            .eq("miniapp_id", app.id).eq("drama_id", drama.id).eq("published", true));
        List<ReleaseEpisode> before = previous == null ? List.of() : releaseEpisodes.selectList(
            new QueryWrapper<ReleaseEpisode>().eq("release_id", previous.id));
        Map<Long, ReleaseEpisode> old = new HashMap<>(); before.forEach(e -> old.put(e.episodeId, e));
        Set<Long> nextIds = new HashSet<>(ids);
        List<Integer> added = selected.stream().filter(e -> !old.containsKey(e.id)).map(e -> e.episodeNo).toList();
        List<Integer> removed = before.stream().filter(e -> !nextIds.contains(e.episodeId)).map(e -> e.episodeNo).sorted().toList();
        List<Integer> changed = selected.stream().filter(e -> old.containsKey(e.id) && episodeChanged(e, old.get(e.id))).map(e -> e.episodeNo).toList();
        boolean metadataChanged = previous == null || !Objects.equals(drama.title, previous.title)
            || !Objects.equals(drama.description, previous.description) || !Objects.equals(drama.coverUrl, previous.coverUrl)
            || !Objects.equals(drama.category, previous.category) || !Objects.equals(drama.totalEpisodes, previous.totalEpisodes)
            || !Objects.equals(drama.completionStatus, previous.completionStatus)
            || body.sortOrder() != null && !Objects.equals(body.sortOrder(), previous.sortOrder)
            || body.recommended() != null && !Objects.equals(body.recommended(), previous.recommended);
        return new Preflight(errors.isEmpty(), errors, warnings, added, removed, changed, metadataChanged, previous == null ? null : previous.id);
    }

    private boolean episodeChanged(Episode next, ReleaseEpisode old) {
        return !Objects.equals(next.title, old.title) || !Objects.equals(next.episodeNo, old.episodeNo)
            || !Objects.equals(next.posterUrl, old.posterUrl) || !Objects.equals(next.storagePath, old.storagePath)
            || !Objects.equals(next.durationSeconds, old.durationSeconds) || !Objects.equals(next.width, old.width)
            || !Objects.equals(next.height, old.height);
    }

    private void metadata(Drama drama, DramaRequest body) {
        drama.coverUrl = body.coverUrl(); drama.category = body.category() == null ? null : body.category().trim();
        drama.completionStatus = body.completionStatus() == null ? "ONGOING" : body.completionStatus(); drama.totalEpisodes = body.totalEpisodes();
    }

    @Transactional
    public Release publish(Long tenantId, Long appId, Long dramaId, PublishRequest body) {
        if (component != null) component.lockLifecycle();
        Drama drama = lockDrama(tenantId, dramaId);
        Miniapp app = miniapps.lock(tenantId, appId);
        if (app == null) throw missing();
        if (!Boolean.TRUE.equals(app.enabled)) throw invalid("小程序已停用。");
        if ("OFFICIAL".equals(app.bindingType)) component.requireAuthorized(app);
        Preflight preflight = inspectRelease(drama, app, body);
        if (!preflight.valid()) throw invalid(String.join(" ", preflight.errors()));
        if (new HashSet<>(body.episodeIds()).size() != body.episodeIds().size()) throw invalid("不能重复选择单集。");
        List<Episode> selected = episodes.selectList(new QueryWrapper<Episode>().eq("tenant_id", tenantId)
            .eq("drama_id", dramaId).in("id", body.episodeIds()).isNull("deleted_at").orderByAsc("episode_no"));
        if (selected.size() != body.episodeIds().size()) throw missing();
        for (Episode episode : selected) requireVideo(storage.metadata(episode.storagePath));
        unpublishRows(tenantId, appId, dramaId);
        Release release = new Release(); release.tenantId = tenantId; release.miniappId = appId;
        release.dramaId = dramaId; release.title = drama.title; release.description = drama.description;
        release.coverUrl = drama.coverUrl; release.category = drama.category; release.completionStatus = drama.completionStatus;
        release.totalEpisodes = drama.totalEpisodes; release.episodeCount = selected.size();
        Release previous = preflight.previousReleaseId() == null ? null : releases.selectById(preflight.previousReleaseId());
        release.sortOrder = body.sortOrder() != null ? body.sortOrder() : previous == null ? 0 : previous.sortOrder;
        release.recommended = body.recommended() != null ? body.recommended() : previous != null && Boolean.TRUE.equals(previous.recommended);
        release.published = true; release.createdAt = LocalDateTime.now(); releases.insert(release);
        for (Episode episode : selected) {
            ReleaseEpisode item = new ReleaseEpisode(); item.releaseId = release.id; item.episodeId = episode.id;
            item.episodeNo = episode.episodeNo; item.title = episode.title; item.storagePath = episode.storagePath;
            item.sourceVersionId = episode.sourceVersionId; item.posterUrl = episode.posterUrl;
            item.durationSeconds = episode.durationSeconds; item.width = episode.width; item.height = episode.height;
            releaseEpisodes.insert(item);
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

    Release published(Miniapp app, Long dramaId) {
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
