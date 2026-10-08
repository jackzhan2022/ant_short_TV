import {
  CheckCircleOutlined,
  DownloadOutlined,
  EditOutlined,
  LeftOutlined,
  LoadingOutlined,
  ReloadOutlined,
  RightOutlined,
  VideoCameraOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import { App, Button, Drawer, Modal, Select, Spin } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { getCurrentTenantId } from '@/services/account-team/auth';
import ShotProductionWorkspace from './ShotProductionWorkspace';
import StableImage from './StableImage';
import {
  type AiVideoTask,
  createEpisodeComposeTask,
  downloadEpisodeVideoVersion,
  type EpisodeComposeTask,
  type EpisodeVideoVersion,
  queryEpisodeComposeTasks,
  queryEpisodeVideoVersions,
  queryStoryboardMedia,
  type StoryboardWorkspacePage,
} from './service';
import VideoPreviewPlayer from './VideoPreviewPlayer';
import {
  formatVideoTime,
  getShotState,
  loadVideoEpisode,
  storyboardEditPath,
} from './video-workbench-state';
import './video-workbench.css';

type Props = { projectId: number; canEdit?: boolean };
type Selection = { episodeNo: number; storyboardId?: number };
const runningStatuses = new Set([
  'PENDING_VALIDATION',
  'PENDING',
  'PROCESSING',
  'SUBMITTING',
  'GENERATING',
]);
const composeLabels: Record<string, string> = {
  PENDING_VALIDATION: '等待校验',
  VALIDATION_FAILED: '校验失败',
  PENDING: '等待合成',
  PROCESSING: '合成中',
  SUCCEEDED: '合成成功',
  FAILED: '合成失败',
  CANCELED: '已取消',
};

function restoreSelection(key: string): Selection | undefined {
  try {
    const value = JSON.parse(sessionStorage.getItem(key) || 'null');
    return value && Number.isSafeInteger(value.episodeNo) && value.episodeNo > 0
      ? value
      : undefined;
  } catch {
    return undefined;
  }
}

export default function VideoWorkbench({ projectId, canEdit = false }: Props) {
  const { message } = App.useApp();
  const selectionKey = `video-workbench:${getCurrentTenantId()}:${projectId}`;
  const [initialSelection] = useState(() => restoreSelection(selectionKey));
  const [episodeNo, setEpisodeNo] = useState<number | undefined>(
    initialSelection?.episodeNo,
  );
  const [selectedId, setSelectedId] = useState<number | undefined>(
    initialSelection?.storyboardId,
  );
  const [workspace, setWorkspace] = useState<StoryboardWorkspacePage>();
  const [videoTasks, setVideoTasks] = useState<AiVideoTask[]>([]);
  const [versions, setVersions] = useState<EpisodeVideoVersion[]>([]);
  const [composeTasks, setComposeTasks] = useState<EpisodeComposeTask[]>([]);
  const [versionId, setVersionId] = useState<number>();
  const [mode, setMode] = useState<'shot' | 'episode'>('shot');
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');
  const [metadataError, setMetadataError] = useState('');
  const [metadataReady, setMetadataReady] = useState(false);
  const [refresh, setRefresh] = useState(0);
  const [filter, setFilter] = useState('all');
  const [recordsOpen, setRecordsOpen] = useState(false);
  const [composeOpen, setComposeOpen] = useState(false);
  const [composing, setComposing] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const requestId = useRef(0);
  const loadedEpisode = useRef<number | undefined>(undefined);
  const episodeRef = useRef(episodeNo);
  episodeRef.current = episodeNo;
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  useEffect(() => {
    const id = ++requestId.current;
    let canceled = false;
    const obsolete = () => canceled || requestId.current !== id;
    const isInitial =
      loadedEpisode.current === undefined ||
      (episodeNo !== undefined && loadedEpisode.current !== episodeNo);
    if (isInitial) {
      setLoading(true);
      setMetadataReady(false);
    }
    setRefreshing(true);
    setError('');
    const load = async () => {
      const data = await loadVideoEpisode(projectId, episodeNo, obsolete);
      if (obsolete()) return;
      loadedEpisode.current = data.episodeNo;
      setWorkspace(data);
      setSelectedId((previous) =>
        data.storyboards.some((shot) => shot.id === previous)
          ? previous
          : data.storyboards[0]?.id,
      );
      setLoading(false);
      const ids = data.storyboards.map((shot) => shot.id);
      const batches: number[][] = [];
      for (let index = 0; index < ids.length; index += 50)
        batches.push(ids.slice(index, index + 50));
      const results = await Promise.allSettled([
        Promise.all(
          batches.map((batch) => queryStoryboardMedia(projectId, batch)),
        ),
        queryEpisodeVideoVersions(projectId, data.episodeNo, {
          current: 1,
          pageSize: 100,
        }),
        queryEpisodeComposeTasks(projectId, {
          episodeNo: data.episodeNo,
          current: 1,
          pageSize: 20,
        }),
      ]);
      if (obsolete()) return;
      const [media, versionResponse, taskResponse] = results;
      if (media.status === 'fulfilled')
        setVideoTasks(
          media.value.flatMap((response) => response.data.videoTasks || []),
        );
      if (versionResponse.status === 'fulfilled') {
        const nextVersions = versionResponse.value.data.data.filter(
          (version) => !!version.videoUrl,
        );
        setVersions(nextVersions);
        setVersionId((previous) =>
          nextVersions.some((version) => version.id === previous)
            ? previous
            : (
                nextVersions.find((version) => version.current) ||
                nextVersions[0]
              )?.id,
        );
        if (!nextVersions.length) setMode('shot');
      }
      if (taskResponse.status === 'fulfilled')
        setComposeTasks(taskResponse.value.data.data);
      const failed = results.some((result) => result.status === 'rejected');
      setMetadataError(
        failed ? '部分任务状态或成片版本加载失败，请刷新重试。' : '',
      );
      setMetadataReady(!failed);
    };
    void load()
      .catch(() => {
        if (!obsolete()) {
          setError('分镜加载失败，请重新加载。');
          setMetadataReady(false);
        }
      })
      .finally(() => {
        if (!obsolete()) {
          setLoading(false);
          setRefreshing(false);
        }
      });
    return () => {
      canceled = true;
    };
  }, [projectId, episodeNo, refresh]);

  const activeEpisode = episodeNo ?? workspace?.episodeNo;
  const currentWorkspace =
    workspace?.episodeNo === activeEpisode ? workspace : undefined;
  const shots = currentWorkspace?.storyboards || [];
  const selectedIndex = shots.findIndex((shot) => shot.id === selectedId);
  const selected = shots[selectedIndex];
  const selectedState = selected
    ? getShotState(selected, videoTasks)
    : undefined;
  const version = versions.find((item) => item.id === versionId);
  const playableCount = shots.filter(
    (shot) => !!getShotState(shot, videoTasks).src,
  ).length;
  const busy = composeTasks.some((task) => runningStatuses.has(task.status));
  const shouldPoll =
    busy || videoTasks.some((task) => runningStatuses.has(task.status));
  useEffect(() => {
    if (!shouldPoll || refreshing) return;
    const timer = window.setInterval(() => {
      if (!document.hidden) setRefresh((value) => value + 1);
    }, 8000);
    return () => window.clearInterval(timer);
  }, [shouldPoll, refreshing]);

  useEffect(() => {
    if (!currentWorkspace || loading) return;
    try {
      sessionStorage.setItem(
        selectionKey,
        JSON.stringify({
          episodeNo: currentWorkspace.episodeNo,
          storyboardId: selectedId,
        }),
      );
    } catch {
      /* Storage may be disabled. Navigation still works. */
    }
  }, [currentWorkspace, selectedId, selectionKey, loading]);

  const selectEpisode = (next: number) => {
    if (next === activeEpisode) return;
    ++requestId.current;
    setEpisodeNo(next);
    setSelectedId(undefined);
    setMode('shot');
    setFilter('all');
    setLoading(true);
    setVersions([]);
    setVersionId(undefined);
    setVideoTasks([]);
    setComposeTasks([]);
    setMetadataReady(false);
    setMetadataError('');
    setError('');
    setComposeOpen(false);
  };
  const edit = () => {
    if (selected)
      history.push(storyboardEditPath(projectId, selected, selectedIndex));
  };
  const chooseShot = (id: number) => {
    setSelectedId(id);
    setMode('shot');
  };
  const canCompose =
    canEdit &&
    !!shots.length &&
    playableCount === shots.length &&
    metadataReady &&
    !loading &&
    !refreshing &&
    !error &&
    !busy;
  const startComposition = async () => {
    if (!canCompose || !activeEpisode || composing) return;
    const submittedEpisode = activeEpisode;
    setComposing(true);
    try {
      const response = await createEpisodeComposeTask(projectId, {
        episodeNo: submittedEpisode,
        outputFormat: 'mp4',
        generateCover: true,
      });
      if (
        !mounted.current ||
        (episodeRef.current !== undefined &&
          episodeRef.current !== submittedEpisode)
      )
        return;
      const task = response.data;
      if (['VALIDATION_FAILED', 'FAILED', 'CANCELED'].includes(task.status)) {
        message.error(task.errorMessage || composeLabels[task.status]);
      } else {
        message.success(
          task.status === 'SUCCEEDED' ? '整集合成完成' : '整集合成任务已创建',
        );
      }
      setComposeTasks((previous) => [
        task,
        ...previous.filter((item) => item.id !== task.id),
      ]);
      setComposeOpen(false);
      setRefresh((value) => value + 1);
    } catch {
      if (mounted.current)
        message.error('整集合成失败，请查看任务记录后重试。');
    } finally {
      if (mounted.current) setComposing(false);
    }
  };
  const download = async () => {
    if (!version || downloading) return;
    setDownloading(true);
    try {
      await downloadEpisodeVideoVersion(projectId, version.id);
    } catch {
      if (mounted.current) message.error('成片下载失败，请重试。');
    } finally {
      if (mounted.current) setDownloading(false);
    }
  };
  const currentEpisode = workspace?.episodes.find(
    (episode) => episode.episodeNo === activeEpisode,
  );
  const visibleShots = shots.filter(
    (shot) =>
      filter === 'all' || getShotState(shot, videoTasks).kind === filter,
  );
  const totalDuration = shots.reduce(
    (total, shot) => total + Math.max(0, shot.durationSeconds || 0),
    0,
  );
  const src = mode === 'episode' ? version?.videoUrl : selectedState?.src;
  const emptyPreview = selected ? (
    <div className="video-empty-preview">
      {selected.firstFrameUrl && (
        <StableImage
          src={selected.firstFrameUrl}
          alt={`分镜${selected.shotNo}首帧`}
          style={{ position: 'absolute', inset: 0 }}
          imageStyle={{ objectFit: 'contain', width: '100%', height: '100%' }}
        />
      )}
      <div className="video-empty-message">
        {selectedState?.kind === 'generating' ? (
          <LoadingOutlined />
        ) : (
          <VideoCameraOutlined />
        )}
        <strong>
          {selectedState?.kind === 'generating'
            ? '分镜视频生成中'
            : selectedState?.kind === 'failed'
              ? '分镜视频生成失败'
              : '该分镜尚未生成视频'}
        </strong>
        <span>
          {selectedState?.kind === 'generating'
            ? '生成完成后自动更新，可先查看其他分镜'
            : selectedState?.error || '点击「编辑分镜」前往分镜页面生成视频'}
        </span>
      </div>
    </div>
  ) : (
    <div className="video-empty-message">
      <VideoCameraOutlined />
      <strong>本集暂无分镜</strong>
      <span>请先在分镜页面完成拆解</span>
      <Button
        onClick={() =>
          history.push(
            `/projects/${projectId}/production-workbench/storyboard${activeEpisode ? `?episodeNo=${activeEpisode}` : ''}`,
          )
        }
      >
        前往分镜页面
      </Button>
    </div>
  );

  return (
    <div className="video-workbench">
      <nav className="video-episode-rail" aria-label="集数选择">
        <span className="video-episode-caption">集数</span>
        <div className="video-episode-list">
          {(workspace?.episodes || []).map((episode) => (
            <button
              type="button"
              key={episode.episodeNo}
              aria-label={`第${episode.episodeNo}集 ${episode.title}`}
              aria-pressed={episode.episodeNo === activeEpisode}
              title={`第${episode.episodeNo}集 · ${episode.title}`}
              onClick={() => selectEpisode(episode.episodeNo)}
            >
              {String(episode.episodeNo).padStart(2, '0')}
            </button>
          ))}
        </div>
      </nav>
      <main className="video-workbench-main">
        <header className="video-workbench-toolbar">
          <div className="video-episode-info">
            <h1>
              {activeEpisode
                ? `第 ${String(activeEpisode).padStart(2, '0')} 集`
                : '视频工作台'}
            </h1>
            <span className="video-episode-title">{currentEpisode?.title}</span>
            {!loading && (
              <>
                <span className="video-shot-count">{shots.length} 个分镜</span>
                <span className="video-ready-count">
                  <CheckCircleOutlined /> {playableCount} / {shots.length}{' '}
                  视频就绪
                </span>
              </>
            )}
          </div>
          <div className="video-workbench-actions">
            <Button
              aria-label="刷新视频状态"
              title="刷新视频状态"
              icon={<ReloadOutlined />}
              loading={refreshing}
              onClick={() => setRefresh((value) => value + 1)}
            />
            <Button onClick={() => setRecordsOpen(true)}>任务记录</Button>
            <Button
              aria-label="下载成片"
              icon={<DownloadOutlined />}
              disabled={!version || loading}
              loading={downloading}
              onClick={() => void download()}
            >
              下载成片
            </Button>
            <Button
              type="primary"
              disabled={!canCompose || composing}
              loading={composing}
              title={
                !canEdit
                  ? '当前账号无编辑权限'
                  : playableCount < shots.length
                    ? '本集所有分镜视频就绪后可合成'
                    : undefined
              }
              onClick={() => setComposeOpen(true)}
            >
              {busy ? '整集合成中' : '合成整集'}
            </Button>
          </div>
        </header>
        {metadataError && (
          <div className="video-status-notice" role="alert">
            {metadataError}
            <button
              type="button"
              onClick={() => setRefresh((value) => value + 1)}
            >
              刷新
            </button>
          </div>
        )}
        {error ? (
          <div className="video-workbench-placeholder" role="alert">
            <p>{error}</p>
            <Button onClick={() => setRefresh((value) => value + 1)}>
              重新加载
            </Button>
          </div>
        ) : loading ? (
          <div className="video-workbench-placeholder" role="status">
            <Spin />
            <p>正在加载本集分镜…</p>
          </div>
        ) : (
          <>
            <VideoPreviewPlayer
              active={!recordsOpen}
              src={src}
              poster={
                mode === 'episode'
                  ? version?.coverUrl || undefined
                  : selected?.firstFrameUrl || undefined
              }
              label={
                mode === 'episode'
                  ? `第${activeEpisode}集 · ${version?.versionName || '整集成片'}`
                  : selected
                    ? `分镜 ${String(selected.shotNo).padStart(2, '0')} · ${selected.shotType || '镜头'}`
                    : '视频预览'
              }
              sourceLabel={
                mode === 'episode'
                  ? '整集成片'
                  : selectedState?.sourceLabel || ''
              }
              empty={emptyPreview}
              leadingControls={
                <>
                  <button
                    type="button"
                    aria-pressed={mode === 'shot'}
                    onClick={() => setMode('shot')}
                  >
                    单镜头
                  </button>
                  <button
                    type="button"
                    aria-pressed={mode === 'episode'}
                    disabled={!version}
                    title={!version ? '合成整集后可预览成片' : undefined}
                    onClick={() => setMode('episode')}
                  >
                    整集成片
                  </button>
                  {mode === 'episode' && (
                    <Select
                      aria-label="成片版本"
                      value={versionId}
                      onChange={setVersionId}
                      options={versions.map((item) => ({
                        value: item.id,
                        label: item.versionName || `版本 ${item.versionNo}`,
                      }))}
                    />
                  )}
                </>
              }
              previous={
                <button
                  type="button"
                  aria-label="上一个分镜"
                  disabled={mode === 'episode' || selectedIndex <= 0}
                  onClick={() => chooseShot(shots[selectedIndex - 1].id)}
                >
                  <LeftOutlined />
                </button>
              }
              next={
                <button
                  type="button"
                  aria-label="下一个分镜"
                  disabled={
                    mode === 'episode' ||
                    selectedIndex < 0 ||
                    selectedIndex >= shots.length - 1
                  }
                  onClick={() => chooseShot(shots[selectedIndex + 1].id)}
                >
                  <RightOutlined />
                </button>
              }
              trailingControls={
                <Button
                  aria-label="编辑分镜"
                  icon={<EditOutlined />}
                  disabled={!selected}
                  onClick={edit}
                >
                  编辑分镜
                </Button>
              }
            />
            <section className="video-shot-strip" aria-label="分镜序列">
              <div className="video-strip-heading">
                <div>
                  <strong>分镜序列</strong>
                  <span>预计总时长 {formatVideoTime(totalDuration)}</span>
                </div>
                <Select
                  aria-label="分镜状态筛选"
                  value={filter}
                  onChange={setFilter}
                  options={[
                    { value: 'all', label: '全部状态' },
                    { value: 'ready', label: '已就绪' },
                    { value: 'generating', label: '生成中' },
                    { value: 'pending', label: '待生成' },
                    { value: 'failed', label: '生成失败' },
                  ]}
                />
              </div>
              <div className="video-shot-cards">
                {visibleShots.map((shot) => {
                  const state = getShotState(shot, videoTasks);
                  return (
                    <button
                      type="button"
                      className="video-shot-card"
                      key={shot.id}
                      aria-label={`分镜${shot.shotNo}，${state.label}`}
                      aria-pressed={shot.id === selectedId}
                      ref={(node) => {
                        if (node && shot.id === selectedId)
                          node.scrollIntoView?.({
                            block: 'nearest',
                            inline: 'nearest',
                          });
                      }}
                      onClick={() => chooseShot(shot.id)}
                    >
                      <span className="video-shot-card-title">
                        <strong>{String(shot.shotNo).padStart(2, '0')}</strong>
                        <span>{shot.durationSeconds || 0}s</span>
                      </span>
                      <span className="video-shot-thumbnail">
                        <StableImage
                          src={
                            shot.firstFrameThumbnailUrl || shot.firstFrameUrl
                          }
                          alt={`分镜${shot.shotNo}缩略图`}
                          style={{ position: 'absolute', inset: 0 }}
                          imageStyle={{
                            objectFit: 'cover',
                            width: '100%',
                            height: '100%',
                          }}
                          fallback={<VideoCameraOutlined />}
                        />
                        {!state.src && (
                          <span className={`video-shot-overlay ${state.kind}`}>
                            {state.kind === 'generating' && <LoadingOutlined />}{' '}
                            {state.label}
                          </span>
                        )}
                      </span>
                      <span className={`video-shot-status ${state.kind}`}>
                        <i />
                        {state.label}
                        {shot.id === selectedId && <CheckCircleOutlined />}
                      </span>
                    </button>
                  );
                })}
                {!visibleShots.length && (
                  <p className="video-strip-empty">
                    {shots.length ? '暂无符合筛选条件的分镜' : '本集暂无分镜'}
                  </p>
                )}
              </div>
            </section>
          </>
        )}
      </main>
      <Drawer
        title="视频任务记录"
        open={recordsOpen}
        onClose={() => {
          setRecordsOpen(false);
          setRefresh((value) => value + 1);
        }}
        size="large"
        destroyOnHidden
      >
        <section className="video-compose-records">
          <h3>第{activeEpisode}集 · 整集合成记录</h3>
          <p>最近 20 条记录</p>
          {composeTasks.length ? (
            composeTasks.map((task) => (
              <div key={task.id}>
                <strong>{task.taskName || `任务 ${task.id}`}</strong>
                <span>{composeLabels[task.status] || task.status}</span>
                {task.errorMessage && <p role="alert">{task.errorMessage}</p>}
              </div>
            ))
          ) : (
            <p>暂无整集合成任务</p>
          )}
        </section>
        <ShotProductionWorkspace projectId={projectId} />
      </Drawer>
      <Modal
        title={`合成第${activeEpisode}集`}
        open={composeOpen}
        onCancel={() => setComposeOpen(false)}
        onOk={() => void startComposition()}
        okText="确认合成"
        confirmLoading={composing}
        okButtonProps={{ disabled: !canCompose }}
      >
        <p>
          将按分镜顺序合成当前集的 {shots.length} 个视频，预计总时长{' '}
          {formatVideoTime(totalDuration)}。
        </p>
        <p>
          优先使用已合成的单镜头结果，否则使用当前分镜视频。未合入视频的配音和字幕不会自动添加。
        </p>
        <p>生成新的成片版本，保留已有版本。最终以服务端素材校验结果为准。</p>
      </Modal>
    </div>
  );
}
