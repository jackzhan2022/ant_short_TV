import DownloadButton from '@/pages/downloads/DownloadButton';
import { AudioOutlined, DownloadOutlined, FolderFilled, PlayCircleFilled } from '@ant-design/icons';
import { history, useLocation, useModel, useParams } from '@umijs/max';
import { Alert, Button, Checkbox, Empty, Input, Modal, Pagination, Tabs } from 'antd';
import { useEffect, useRef, useState } from 'react';
import LazyMediaImage from '@/components/LazyMediaImage';
import { getCurrentTenantId } from '@/services/account-team/auth';
import { queryProjectMetadata, queryProjects } from '@/services/account-team/project';
import type { Project } from '@/services/account-team/types';
import { type AssetLibraryItem, downloadAssetImage, downloadAssetImages, queryProjectAssetLibrary } from './service';
import styles from './index.module.css';

const categories = [
  { key: 'ALL', label: '全部' },
  { key: 'CHARACTER', label: '角色' }, { key: 'SCENE', label: '场景' },
  { key: 'PROP', label: '道具' }, { key: 'VIDEO_FRAME', label: '视频帧' }, { key: 'OTHER', label: '其他' },
];
const dateText = (value?: string) => value ? value.replace('T', ' ').slice(0, 16) : '暂无更新时间';
const durationText = (seconds: number) => `${Math.floor(seconds / 60)}:${String(Math.floor(seconds % 60)).padStart(2, '0')}`;
const mediaLabels = { IMAGE: '图片', VIDEO: '视频', AUDIO: '音频' };

export default function AssetLibrary() {
  const { initialState } = useModel('@@initialState');
  const tenantId = initialState?.currentTenantId || getCurrentTenantId();
  return <ProjectAssetLibrary key={tenantId || 'no-team'} tenantId={tenantId} />;
}

function ProjectAssetLibrary({ tenantId }: { tenantId?: number }) {
  const { projectId: routeProjectId } = useParams<{ projectId?: string }>();
  const projectId = routeProjectId ? Number(routeProjectId) : undefined;
  const location = useLocation();
  const search = new URLSearchParams(location.search);
  const mediaType = search.get('type') === 'VIDEO' ? 'VIDEO' : search.get('type') === 'AUDIO' ? 'AUDIO' : 'IMAGE';
  const category = categories.some((item) => item.key === search.get('category')) ? search.get('category') || 'ALL' : 'ALL';
  const keyword = search.get('keyword') || '';
  const pageValue = Number(search.get('page'));
  const page = Number.isSafeInteger(pageValue) && pageValue > 0 ? pageValue : 1;
  const episodeValue = Number(search.get('episode'));
  const episodeNo = Number.isSafeInteger(episodeValue) && episodeValue > 0 ? episodeValue : undefined;
  const [inputKeyword, setInputKeyword] = useState(keyword);
  const [projects, setProjects] = useState<Project[]>([]);
  const [project, setProject] = useState<Project>();
  const [items, setItems] = useState<AssetLibraryItem[]>([]);
  const [total, setTotal] = useState(0);
  const [counts, setCounts] = useState<Record<string, number>>({});
  const [episodes, setEpisodes] = useState<number[]>([]);
  const [loading, setLoading] = useState(true);
  const [metadataFailed, setMetadataFailed] = useState(false);
  const [failed, setFailed] = useState(false);
  const [retry, setRetry] = useState(0);
  const [preview, setPreview] = useState<AssetLibraryItem>();
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [downloading, setDownloading] = useState(false);
  const [downloadError, setDownloadError] = useState<string>();
  const downloadController = useRef<AbortController | null>(null);
  const playingAudio = useRef<HTMLAudioElement | null>(null);
  const canViewImages = Boolean(project?.effectivePermissions.includes('MENU:ASSET_LIBRARY'));
  const canViewVideos = canViewImages;
  const denied = Boolean(project && !(mediaType === 'IMAGE' ? canViewImages : canViewVideos));
  const invalidProject = routeProjectId !== undefined && (!Number.isSafeInteger(projectId) || !projectId || projectId < 1);

  const pageImageIds = items.filter((item) => item.resourceType === 'AI_IMAGE_RESULT' && item.downloadUrl).map((item) => item.id);
  const selectedOnPage = pageImageIds.filter((id) => selectedIds.includes(id)).length;
  const selectImages = (ids: number[], checked: boolean) => {
    const next = checked ? [...new Set([...selectedIds, ...ids])] : selectedIds.filter((id) => !ids.includes(id));
    if (next.length > 100) { setDownloadError('单次最多选择 100 张图片。'); return; }
    setSelectedIds(next);
    setDownloadError(undefined);
  };
  const download = async (resultId?: number) => {
    if (!projectId || downloadController.current || (!resultId && !selectedIds.length)) return;
    const controller = new AbortController();
    downloadController.current = controller;
    setDownloading(true);
    setDownloadError(undefined);
    try {
      if (resultId) await downloadAssetImage(projectId, resultId, controller.signal);
      else await downloadAssetImages(projectId, selectedIds, controller.signal);
    } catch (error) {
      if (!controller.signal.aborted) setDownloadError(error instanceof Error ? error.message : '下载失败，请重试。');
    } finally {
      if (downloadController.current === controller) {
        downloadController.current = null;
        setDownloading(false);
      }
    }
  };

  const navigate = (values: Record<string, string | number | undefined>, destination = projectId) => {
    const params = new URLSearchParams(location.search);
    for (const [key, value] of Object.entries(values)) {
      if (value === undefined || value === '') params.delete(key);
      else params.set(key, String(value));
    }
    const path = destination ? `/asset-library/projects/${destination}` : '/asset-library';
    history.push(`${path}${params.size ? `?${params}` : ''}`);
  };

  useEffect(() => {
    setSelectedIds([]);
    setDownloadError(undefined);
    setDownloading(false);
    return () => { downloadController.current?.abort(); downloadController.current = null; };
  }, [tenantId, projectId, mediaType, category, keyword]);
  useEffect(() => { setInputKeyword(keyword); }, [keyword, projectId, mediaType, category]);
  useEffect(() => { setPreview(undefined); }, [tenantId, projectId, mediaType, category, keyword, page, episodeNo]);
  useEffect(() => {
    setProject(undefined);
    setMetadataFailed(false);
    if (!tenantId || !projectId || invalidProject) return;
    let active = true;
    void queryProjectMetadata(projectId).then((response) => {
      if (!active) return;
      if (!response.success) throw new Error(response.errorMessage);
      setProject(response.data);
    }).catch(() => { if (active) setMetadataFailed(true); });
    return () => { active = false; };
  }, [tenantId, projectId, invalidProject, retry]);

  useEffect(() => {
    setItems([]);
    setProjects([]);
    setCounts({});
    setEpisodes([]);
    setTotal(0);
    setFailed(false);
    if (!tenantId || invalidProject || metadataFailed || denied) { setLoading(false); return; }
    if (projectId && !project) { setLoading(true); return; }
    const controller = new AbortController();
    setLoading(true);
    const load = async () => {
      try {
        if (projectId) {
          const response = await queryProjectAssetLibrary(projectId, {
            mediaType, category: mediaType === 'IMAGE' ? category : undefined,
            episodeNo: mediaType === 'VIDEO' ? episodeNo : undefined,
            keyword: keyword || undefined, current: page, pageSize: 24,
          }, controller.signal);
          if (controller.signal.aborted) return;
          if (!response.success) throw new Error(response.errorMessage);
          setItems(response.data.items.data);
          setTotal(response.data.items.total);
          setCounts(response.data.categoryCounts);
          setEpisodes(response.data.episodeNumbers);
        } else {
          const response = await queryProjects({ current: page, pageSize: 24, keyword: keyword || undefined }, controller.signal);
          if (controller.signal.aborted) return;
          if (!response.success) throw new Error(response.errorMessage);
          setProjects(response.data.data);
          setTotal(response.data.total);
        }
      } catch {
        if (!controller.signal.aborted) setFailed(true);
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    };
    void load();
    return () => controller.abort();
  }, [tenantId, projectId, project, invalidProject, metadataFailed, denied, mediaType, category, keyword, page, episodeNo, retry]);

  return (
    <main className={styles.page} aria-label="项目资产库">
      <header className={styles.heading}>
        <div><h1>资产库</h1><p>按项目查找和预览图片、视频、音频资产</p></div>
        {project ? <Button onClick={() => history.push(`/projects/${project.id}/production-workbench/${mediaType === 'VIDEO' ? 'video' : 'settings'}`)}>进入项目</Button> : null}
      </header>
      <Tabs
        activeKey={mediaType}
        items={[{ key: 'IMAGE', label: '图片', disabled: Boolean(project && !canViewImages) }, { key: 'VIDEO', label: '视频', disabled: Boolean(project && !canViewVideos) }, { key: 'AUDIO', label: '音频', disabled: Boolean(project && !canViewImages) }]}
        onChange={(type) => navigate({ type, keyword: undefined, episode: undefined, page: undefined })}
      />
      {projectId ? (
        <nav className={styles.breadcrumb} aria-label="资产库导航">
          <button type="button" onClick={() => {
            const params = new URLSearchParams({ type: mediaType });
            history.push(`/asset-library?${params}`);
          }}>{mediaLabels[mediaType]}资产库</button>
          <span aria-hidden="true">›</span><span>{project?.name || '项目资产'}</span>
        </nav>
      ) : null}
      {mediaType === 'IMAGE' ? (
        <section className={styles.categories} aria-label="图片分类">
          {categories.map((item) => (
            <button key={item.key} type="button" aria-pressed={category === item.key}
              onClick={() => navigate({ category: item.key, page: undefined, keyword: undefined })}>
              {item.label}{project && counts[item.key] !== undefined ? <span className={styles.categoryCount}>{counts[item.key]}</span> : null}
            </button>
          ))}
        </section>
      ) : null}
      <div className={styles.toolbar}>
        <Input.Search
          aria-label={projectId ? '搜索资产' : '搜索项目'}
          placeholder={projectId ? '搜索资产名称' : '搜索项目名称'}
          value={inputKeyword}
          allowClear
          onChange={(event) => {
            setInputKeyword(event.target.value);
            if (!event.target.value && keyword) navigate({ keyword: undefined, page: undefined });
          }}
          onSearch={(value) => navigate({ keyword: value.trim(), page: undefined })}
        />
        {projectId && mediaType === 'VIDEO' ? (
          <select aria-label="筛选剧集" value={episodeNo || ''} onChange={(event) => navigate({ episode: event.target.value || undefined, page: undefined })}>
            <option value="">全部剧集</option>
            {episodes.map((number) => <option key={number} value={number}>第 {number} 集</option>)}
          </select>
        ) : null}
      </div>
      <div className={styles.sectionHeading}>
        <span>{projectId ? `${mediaLabels[mediaType]}素材` : '项目文件夹'}</span>
        <span className={styles.total}>共 {total} {projectId ? '项资产' : '个项目'}</span>
      </div>
      {projectId && mediaType === 'IMAGE' && canViewImages && !metadataFailed && !denied ? (
        <div className={styles.batchActions}>
          <Checkbox checked={pageImageIds.length > 0 && selectedOnPage === pageImageIds.length}
            indeterminate={selectedOnPage > 0 && selectedOnPage < pageImageIds.length}
            disabled={loading || downloading || !pageImageIds.length}
            onChange={(event) => selectImages(pageImageIds, event.target.checked)}>全选当前页</Checkbox>
          <span aria-live="polite">已选 {selectedIds.length} 张</span>
          <Button type="text" disabled={!selectedIds.length || downloading} onClick={() => setSelectedIds([])}>清空选择</Button>
          <Button aria-label="批量下载" icon={<DownloadOutlined aria-hidden="true" />} loading={downloading} disabled={!selectedIds.length || downloading}
            onClick={() => void download()}>批量下载</Button>
          <span className={styles.total}>支持跨页选择，单次最多 100 张</span>
        </div>
      ) : null}
      {downloadError && !preview ? <Alert type="error" showIcon title={downloadError} className={styles.downloadError} /> : null}
      {!tenantId ? <Empty description="请先选择当前团队" />
        : invalidProject || metadataFailed || failed ? <div className={styles.empty}><Empty description={metadataFailed ? '项目不存在或无权访问' : '资产加载失败'} /><Button aria-label="重试" onClick={() => setRetry((value) => value + 1)}>重试</Button></div>
        : denied ? <Empty description="暂无查看此类资产的权限" />
        : loading ? <div role="status" className={styles.loading}>正在加载资产库…</div>
        : !total ? <Empty description={keyword ? '没有找到匹配的内容' : projectId ? '项目暂无此类资产' : '暂无可访问的项目'} />
        : projectId && mediaType === 'AUDIO' ? <div className={styles.audioList}>
          {items.map((item) => <article className={styles.audioRow} key={`${item.resourceType}-${item.id}`}>
            <div className={styles.audioIcon}><AudioOutlined /></div>
            <div className={styles.audioInfo}><strong>{item.name}</strong><div>{item.sourceType} · {dateText(item.updatedAt)} 更新{item.durationSeconds ? ` · ${durationText(item.durationSeconds)}` : ''}</div></div>
            {/* biome-ignore lint/a11y/useMediaCaption: Source audio assets do not have transcript files. */}
            <audio src={item.previewUrl} controls preload="none" aria-label={`${item.name}音频预览`} onPlay={(event) => {
              if (playingAudio.current && playingAudio.current !== event.currentTarget) playingAudio.current.pause();
              playingAudio.current = event.currentTarget;
            }} />
          </article>)}
        </div> : projectId ? (
          <div className={`${styles.mediaGrid} ${mediaType === 'VIDEO' ? styles.videoGrid : ''}`}>
            {items.map((item) => (
              <article key={`${item.resourceType}-${item.id}`} className={`${styles.card} ${selectedIds.includes(item.id) ? styles.selectedCard : ''}`}>
                {mediaType === 'IMAGE' && item.resourceType === 'AI_IMAGE_RESULT' && item.downloadUrl ? (
                  <Checkbox className={styles.imageCheckbox} aria-label={`选择${item.name}`} checked={selectedIds.includes(item.id)}
                    disabled={downloading} onChange={(event) => selectImages([item.id], event.target.checked)} />
                ) : null}
                <button type="button" className={styles.cardEntry} aria-label={`预览${item.name}`} onClick={() => setPreview(item)}>
                  <div className={styles.cover}>
                    <LazyMediaImage native src={item.thumbnailUrl} alt={item.name} height="100%" imageStyle={{ objectFit: mediaType === 'IMAGE' ? 'contain' : 'cover' }}
                      placeholderFallback={<span className={styles.placeholder}>{mediaType === 'VIDEO' ? '视频' : '图片'}</span>} />
                    {mediaType === 'VIDEO' ? <PlayCircleFilled className={styles.play} /> : null}
                    {item.durationSeconds ? <span className={styles.duration}>{durationText(item.durationSeconds)}</span> : null}
                  </div>
                  <div className={styles.cardBody}>
                    <div className={styles.cardTitle}><span title={item.name}>{item.name}</span><em>{item.sourceType}</em></div>
                    <div className={styles.cardMeta}><time>{dateText(item.updatedAt)} 更新</time>{item.current ? <span>当前使用</span> : null}</div>
                  </div>
                </button>
              </article>
            ))}
          </div>
        ) : (
          <div className={styles.folderGrid}>
            {projects.map((item) => (
              <article key={item.id} className={styles.card}>
                <button type="button" className={styles.cardEntry} aria-label={`打开${item.name}资产库`}
                  onClick={() => navigate({ keyword: undefined, page: undefined, episode: undefined }, item.id)}>
                  <div className={`${styles.cover} ${styles.folderCover}`}><FolderFilled /></div>
                  <div className={styles.cardBody}>
                    <div className={styles.cardTitle}><span title={item.name}>{item.name}</span><em>项目创建</em></div>
                    <div className={styles.cardMeta}><time>{dateText(item.updatedAt)} 更新</time></div>
                  </div>
                </button>
              </article>
            ))}
          </div>
        )}
      {total > 24 ? <Pagination className={styles.pagination} current={page} pageSize={24} total={total} showSizeChanger={false} onChange={(next) => navigate({ page: next })} /> : null}
      {preview ? <Modal open title={preview.name} width="min(1100px, calc(100vw - 32px))"
        onCancel={() => setPreview(undefined)}
        footer={preview?.downloadUrl ? preview.resourceType === 'AI_IMAGE_RESULT' ? (
          <Button aria-label="下载原文件" icon={<DownloadOutlined aria-hidden="true" />} loading={downloading} disabled={downloading}
            onClick={() => void download(preview.id)}>下载原文件</Button>
        ) : <DownloadButton type="default" name={preview.name} sources={[{ kind: 'VIDEO', projectId, id: preview.id }]}>下载原文件</DownloadButton> : null}>
        {downloadError ? <Alert type="error" showIcon title={downloadError} className={styles.downloadError} /> : null}
        {preview ? <div className={styles.preview}>
          {preview.resourceType === 'AI_IMAGE_RESULT' ? (
            <LazyMediaImage native src={preview.previewUrl} alt={`${preview.name}大图`} height="min(66vh, 720px)" imageStyle={{ objectFit: 'contain' }} />
          ) : (
            // biome-ignore lint/a11y/useMediaCaption: Source videos do not have separate caption files.
            <video key={`${preview.resourceType}-${preview.id}`} src={preview.previewUrl} poster={preview.thumbnailUrl} controls autoPlay playsInline preload="metadata" aria-label={`${preview.name}视频预览`} />
          )}
        </div> : null}
      </Modal> : null}
    </main>
  );
}
