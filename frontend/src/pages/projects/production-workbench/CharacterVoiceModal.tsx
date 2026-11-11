import {
  AudioOutlined,
  CheckOutlined,
  FolderOutlined,
  PlayCircleOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { useModel } from '@umijs/max';
import { App, Button, Empty, Input, Modal, Pagination } from 'antd';
import { useEffect, useRef, useState } from 'react';
import {
  type AudioReference,
  queryCharacterVoice,
  queryStoryboardCharacterVoice,
  registerProjectAudio,
  resetStoryboardCharacterVoice,
  type StoryboardVoiceScope,
  saveCharacterVoice,
  saveStoryboardCharacterVoice,
} from '@/pages/asset-library/audio-service';
import {
  type AssetLibraryItem,
  queryProjectAssetLibrary,
} from '@/pages/asset-library/service';
import SystemAudioList from '@/pages/system-audio/AudioList';
import { queryProjects } from '@/services/account-team/project';
import type { Project } from '@/services/account-team/types';
import {
  type MediaUploadHandle,
  startMediaUpload,
} from '@/services/mediaUpload';
import styles from './CharacterVoiceModal.module.css';

type Props = {
  projectId: number;
  characterId: number;
  characterName: string;
  storyboard?: StoryboardVoiceScope;
  disabled?: boolean;
  onClose: () => void;
  onSaved: (audio: AudioReference) => void;
  onChanged?: () => void;
};
const folders = [
  { key: 'REFERENCE', label: '参考音频' },
  { key: 'GENERATED', label: '生成语音' },
];
const durationText = (seconds?: number | null) =>
  seconds
    ? `${Math.floor(seconds / 60)}:${String(Math.floor(seconds % 60)).padStart(2, '0')}`
    : '';

export default function CharacterVoiceModal(props: Props) {
  const { initialState } = useModel('@@initialState');
  return (
    <VoicePicker
      key={`${initialState?.currentTenantId}-${props.projectId}-${props.characterId}-${props.storyboard?.id}-${props.storyboard?.variantId}`}
      {...props}
    />
  );
}

function VoicePicker({
  projectId,
  characterId,
  characterName,
  storyboard,
  disabled,
  onClose,
  onSaved,
  onChanged,
}: Props) {
  const { message } = App.useApp();
  const [source, setSource] = useState<'library' | 'upload' | 'system'>(
    'system',
  );
  const [project, setProject] = useState<Project>();
  const [projects, setProjects] = useState<Project[]>([]);
  const [items, setItems] = useState<AssetLibraryItem[]>([]);
  const [counts, setCounts] = useState<Record<string, number>>({});
  const [view, setView] = useState<'all' | 'folders'>('all');
  const [folder, setFolder] = useState<string>();
  const [keyword, setKeyword] = useState('');
  const [inputKeyword, setInputKeyword] = useState('');
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [retry, setRetry] = useState(0);
  const [current, setCurrent] = useState<AudioReference | null>();
  const [currentFailed, setCurrentFailed] = useState(false);
  const [overridden, setOverridden] = useState(false);
  const [selected, setSelected] = useState<AudioReference>();
  const [selectedLabel, setSelectedLabel] = useState<string>();
  const [preview, setPreview] = useState<{
    name: string;
    previewUrl: string;
    label?: string;
  }>();
  const [playbackAttempt, setPlaybackAttempt] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [playbackFailed, setPlaybackFailed] = useState(false);
  const [file, setFile] = useState<File>();
  const [localUrl, setLocalUrl] = useState<string>();
  const [dragging, setDragging] = useState(false);
  const [saving, setSaving] = useState(false);
  const [progress, setProgress] = useState<number>();
  const active = useRef(true);
  const busy = useRef(false);
  const uploadHandle = useRef<MediaUploadHandle | undefined>(undefined);

  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
      uploadHandle.current?.cancel();
    };
  }, []);
  useEffect(() => {
    if (!file) {
      setLocalUrl(undefined);
      return;
    }
    const url = URL.createObjectURL(file);
    setLocalUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);
  useEffect(() => {
    const controller = new AbortController();
    setCurrentFailed(false);
    const loadCurrent = async () => {
      if (storyboard) {
        const response = await queryStoryboardCharacterVoice(
          projectId,
          characterId,
          storyboard,
          controller.signal,
        );
        if (controller.signal.aborted) return;
        if (!response.success) throw new Error(response.errorMessage);
        setCurrent(response.data.audio);
        setOverridden(response.data.overridden);
      } else {
        const response = await queryCharacterVoice(
          projectId,
          characterId,
          controller.signal,
        );
        if (controller.signal.aborted) return;
        if (!response.success) throw new Error(response.errorMessage);
        setCurrent(response.data);
      }
    };
    void loadCurrent().catch(() => {
      if (!controller.signal.aborted) setCurrentFailed(true);
    });
    return () => controller.abort();
  }, [projectId, characterId, storyboard?.id, storyboard?.variantId]);
  useEffect(() => {
    const controller = new AbortController();
    if (source !== 'library') return () => controller.abort();
    setLoading(true);
    setFailed(false);
    setItems([]);
    setProjects([]);
    setCounts({});
    setTotal(0);
    const load = async () => {
      try {
        if (project) {
          const response = await queryProjectAssetLibrary(
            project.id,
            {
              mediaType: 'AUDIO',
              category: folder,
              keyword: keyword || undefined,
              current: page,
              pageSize: 12,
            },
            controller.signal,
          );
          if (controller.signal.aborted) return;
          if (!response.success) throw new Error(response.errorMessage);
          setItems(response.data.items.data);
          setTotal(response.data.items.total);
          setCounts(response.data.categoryCounts);
        } else {
          const response = await queryProjects(
            { current: page, pageSize: 12, keyword: keyword || undefined },
            controller.signal,
          );
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
  }, [source, project, folder, keyword, page, retry]);

  const chooseProject = (value?: Project) => {
    setProject(value);
    setSelected(undefined);
    setPreview(undefined);
    setFolder(undefined);
    setView('all');
    setKeyword('');
    setInputKeyword('');
    setPage(1);
  };
  const chooseFile = (value?: File) => {
    if (!value || busy.current || disabled) return;
    const extension = value.name.split('.').pop()?.toLowerCase();
    if (
      !['mp3', 'wav'].includes(extension || '') ||
      value.size <= 0 ||
      value.size > 10 * 1024 * 1024
    ) {
      message.error('请选择不超过10MB的 MP3 或 WAV 音频');
      return;
    }
    setFile(
      new File([value], value.name, {
        type: extension === 'mp3' ? 'audio/mpeg' : 'audio/wav',
      }),
    );
  };
  const submit = async () => {
    if (busy.current || disabled || (source === 'upload' ? !file : !selected))
      return;
    busy.current = true;
    setSaving(true);
    try {
      let audio = selected;
      if (source === 'upload' && file) {
        setProgress(0);
        const handle = await startMediaUpload(file, {
          projectId,
          onProgress: ({ percent }) => {
            if (active.current) setProgress(Math.round(percent * 100));
          },
        });
        uploadHandle.current = handle;
        if (!active.current) {
          handle.cancel();
          return;
        }
        const upload = await handle.attempt;
        if (!active.current) return;
        const registered = await registerProjectAudio(
          projectId,
          upload.sessionToken,
          file.name,
        );
        if (!registered.success)
          throw new Error(registered.errorMessage || '音频保存失败');
        audio = registered.data;
      }
      if (!active.current || !audio) return;
      const response = storyboard
        ? await saveStoryboardCharacterVoice(
            projectId,
            characterId,
            storyboard,
            audio,
          ).then((response) => ({ ...response, data: response.data?.audio }))
        : await saveCharacterVoice(projectId, characterId, audio);
      if (!response.success)
        throw new Error(response.errorMessage || '音色配置失败');
      if (active.current && response.data) {
        message.success('角色音色已配置');
        onChanged?.();
        onSaved(response.data);
      }
    } catch (error) {
      if (active.current)
        message.error(
          error instanceof Error ? error.message : '音色配置失败，请重试',
        );
    } finally {
      busy.current = false;
      uploadHandle.current = undefined;
      if (active.current) {
        setSaving(false);
        setProgress(undefined);
      }
    }
  };

  const resetToDefault = async () => {
    if (!storyboard || busy.current || disabled) return;
    busy.current = true;
    setSaving(true);
    try {
      const response = await resetStoryboardCharacterVoice(
        projectId,
        characterId,
        storyboard,
      );
      if (!response.success)
        throw new Error(response.errorMessage || '恢复默认音色失败');
      if (active.current) {
        setCurrent(response.data.audio);
        setOverridden(false);
        setSelected(undefined);
        setFile(undefined);
        message.success('已恢复角色默认音色');
        onChanged?.();
      }
    } catch (error) {
      if (active.current)
        message.error(
          error instanceof Error ? error.message : '恢复默认音色失败',
        );
    } finally {
      busy.current = false;
      if (active.current) setSaving(false);
    }
  };
  const showingFolders = Boolean(project && view === 'folders' && !folder);
  const audition =
    source === 'upload'
      ? file && localUrl
        ? { name: file.name, previewUrl: localUrl }
        : undefined
      : preview || selected;
  const hasSelection = source === 'upload' ? Boolean(file) : Boolean(selected);
  const auditionLabel =
    preview?.label || (!preview ? selectedLabel : undefined);
  const startPreview = (audio: {
    name: string;
    previewUrl: string;
    label?: string;
  }) => {
    setPreview(audio);
    setPlaybackAttempt((value) => value + 1);
    setPlaybackFailed(false);
    setPlaying(false);
  };
  const clearSelection = () => {
    setSelected(undefined);
    setSelectedLabel(undefined);
    setFile(undefined);
    setPreview(undefined);
    setPlaying(false);
    setPlaybackFailed(false);
  };

  return (
    <Modal
      open
      centered
      className={styles.modal}
      classNames={{
        container: styles.container,
        header: styles.header,
        body: styles.body,
        footer: styles.footer,
      }}
      title={
        <div className={styles.title}>
          <span className={styles.titleIcon}>
            <AudioOutlined />
          </span>
          <div>
            <h2>{storyboard ? '分镜角色音色' : '配置音色'}</h2>
            <p>
              {characterName}
              {storyboard?.variantName ? ` / ${storyboard.variantName}` : ''}
              <span>
                {' '}
                ·{' '}
                {storyboard
                  ? overridden
                    ? '当前分镜独立音色'
                    : '使用角色默认音色'
                  : '默认音色'}
              </span>
            </p>
          </div>
        </div>
      }
      width={1020}
      onCancel={() => {
        if (!busy.current) onClose();
      }}
      onOk={() => void submit()}
      okText="应用音色"
      cancelText="取消"
      confirmLoading={saving}
      okButtonProps={{
        'aria-label': '应用音色',
        disabled: disabled || (source === 'upload' ? !file : !selected),
      }}
      cancelButtonProps={{ disabled: saving }}
      closable={!saving}
      footer={(_, { OkBtn, CancelBtn }) => (
        <div className={styles.footerContent}>
          <div className={styles.selection} aria-live="polite">
            <span className={styles.selectionIcon} data-selected={hasSelection}>
              <CheckOutlined />
            </span>
            <span>{hasSelection ? '已选 1 项' : '未选择音色'}</span>
            {hasSelection ? (
              <Button
                type="text"
                size="small"
                disabled={saving || disabled}
                onClick={clearSelection}
              >
                清除
              </Button>
            ) : null}
          </div>
          <div className={styles.actions}>
            <CancelBtn />
            <OkBtn />
          </div>
        </div>
      )}
    >
      <div className={styles.sourceTabs} role="tablist" aria-label="音色来源">
        {(
          [
            { key: 'system', label: '系统音频', icon: <AudioOutlined /> },
            { key: 'library', label: '项目音频库', icon: <FolderOutlined /> },
            { key: 'upload', label: '本地上传', icon: <UploadOutlined /> },
          ] as const
        ).map((tab) => (
          <button
            type="button"
            key={tab.key}
            role="tab"
            aria-label={tab.label}
            id={`voice-${tab.key}`}
            aria-controls={`voice-panel-${tab.key}`}
            aria-selected={source === tab.key}
            disabled={saving}
            onClick={() => {
              if (source === tab.key) return;
              setSource(tab.key);
              clearSelection();
            }}
          >
            {tab.icon}
            {tab.label}
          </button>
        ))}
      </div>
      <div className={styles.workspace}>
        <div className={styles.browser}>
          {source === 'system' ? (
            <section
              id="voice-panel-system"
              role="tabpanel"
              aria-labelledby="voice-system"
              className={styles.panel}
            >
              <SystemAudioList
                onPreview={(audio) =>
                  startPreview({ ...audio, label: audio.category })
                }
                disabled={disabled || saving}
                selectedId={
                  selected?.resourceType === 'SYSTEM_AUDIO_ASSET'
                    ? selected.resourceId
                    : undefined
                }
                onSelect={(audio) => {
                  setPreview(undefined);
                  setPlaybackFailed(false);
                  setPlaying(false);
                  setSelectedLabel(audio.category);
                  setSelected({
                    sourceProjectId: projectId,
                    sourceProjectName: '系统音频',
                    resourceType: 'SYSTEM_AUDIO_ASSET',
                    resourceId: audio.id,
                    name: audio.name,
                    previewUrl: audio.previewUrl,
                    durationSeconds: audio.durationSeconds,
                  });
                }}
              />
            </section>
          ) : source === 'upload' ? (
            <section
              id="voice-panel-upload"
              role="tabpanel"
              aria-labelledby="voice-upload"
              className={styles.panel}
            >
              {file ? (
                <div className={styles.uploaded}>
                  <div className={styles.fileInfo}>
                    <AudioOutlined />
                    <span>
                      {file.name}
                      <small>{(file.size / 1024 / 1024).toFixed(2)} MB</small>
                    </span>
                    <Button
                      disabled={saving}
                      onClick={() => setFile(undefined)}
                    >
                      移除
                    </Button>
                  </div>
                  {progress !== undefined ? (
                    <p role="status">
                      {progress < 100 ? `上传中 ${progress}%` : '正在保存音频…'}
                    </p>
                  ) : null}
                </div>
              ) : (
                <label
                  className={`${styles.dropzone} ${dragging ? styles.dragging : ''}`}
                  onDragOver={(event) => {
                    event.preventDefault();
                    setDragging(true);
                  }}
                  onDragLeave={() => setDragging(false)}
                  onDrop={(event) => {
                    event.preventDefault();
                    setDragging(false);
                    chooseFile(event.dataTransfer.files[0]);
                  }}
                >
                  <AudioOutlined className={styles.uploadIcon} />
                  <span>点击或拖入音频</span>
                  <small>MP3 / WAV · 最大 10 MB</small>
                  <input
                    type="file"
                    accept=".mp3,.wav,audio/mpeg,audio/wav"
                    aria-label="上传参考音频"
                    disabled={saving || disabled}
                    onChange={(event) => {
                      chooseFile(event.target.files?.[0]);
                      event.target.value = '';
                    }}
                  />
                </label>
              )}
            </section>
          ) : (
            <section
              id="voice-panel-library"
              role="tabpanel"
              aria-labelledby="voice-library"
              className={styles.panel}
            >
              <div className={styles.toolbar}>
                <span>{project ? '项目音频' : '选择项目'}</span>
                {!project && !loading && !failed ? (
                  <small>{total} 个项目</small>
                ) : null}
                {project ? (
                  <div
                    role="tablist"
                    aria-label="音频库视图"
                    className={styles.viewTabs}
                  >
                    {(
                      [
                        { key: 'all', label: '所有音频' },
                        { key: 'folders', label: '文件夹' },
                      ] as const
                    ).map((tab) => (
                      <button
                        type="button"
                        role="tab"
                        id={`voice-view-${tab.key}`}
                        aria-controls="voice-library-content"
                        key={tab.key}
                        aria-selected={view === tab.key}
                        disabled={saving}
                        onClick={() => {
                          setView(tab.key);
                          setFolder(undefined);
                          setPage(1);
                          setPreview(undefined);
                        }}
                      >
                        {tab.label}
                      </button>
                    ))}
                  </div>
                ) : null}
              </div>
              <Input.Search
                aria-label={project ? '搜索项目音频' : '搜索音频项目'}
                placeholder={project ? '搜索当前项目音频' : '搜索项目名称'}
                value={inputKeyword}
                allowClear
                disabled={saving}
                onChange={(event) => {
                  setInputKeyword(event.target.value);
                  if (!event.target.value) {
                    setKeyword('');
                    setPage(1);
                  }
                }}
                onSearch={(value) => {
                  setKeyword(value.trim());
                  setPage(1);
                }}
              />
              {project ? (
                <nav className={styles.breadcrumb} aria-label="项目音频导航">
                  <button
                    type="button"
                    disabled={saving}
                    onClick={() => chooseProject()}
                  >
                    所有项目
                  </button>
                  <span>›</span>
                  <span>{project.name}</span>
                  {folder ? (
                    <>
                      <span>›</span>
                      <button
                        type="button"
                        disabled={saving}
                        onClick={() => {
                          setFolder(undefined);
                          setPage(1);
                        }}
                      >
                        {folders.find((item) => item.key === folder)?.label}
                      </button>
                    </>
                  ) : null}
                </nav>
              ) : null}
              <div
                id="voice-library-content"
                className={styles.libraryContent}
                role="tabpanel"
                aria-labelledby={
                  project ? `voice-view-${view}` : 'voice-library'
                }
              >
                {failed ? (
                  <div className={styles.empty}>
                    <Empty description="音频库加载失败" />
                    <Button onClick={() => setRetry((value) => value + 1)}>
                      重试
                    </Button>
                  </div>
                ) : loading ? (
                  <div role="status" className={styles.empty}>
                    正在加载音频库…
                  </div>
                ) : !project ? (
                  projects.length ? (
                    <div className={styles.projectGrid}>
                      {projects.map((item) => (
                        <button
                          data-current={item.id === projectId}
                          type="button"
                          key={item.id}
                          disabled={saving}
                          aria-label={`打开${item.name}音频库`}
                          onClick={() => chooseProject(item)}
                        >
                          <FolderOutlined />
                          <strong>{item.name}</strong>
                          {item.id === projectId ? (
                            <small>当前项目</small>
                          ) : null}
                        </button>
                      ))}
                    </div>
                  ) : (
                    <Empty
                      description={
                        keyword ? '未找到相关项目' : '暂无可访问的项目'
                      }
                    />
                  )
                ) : showingFolders ? (
                  <div className={styles.projectGrid}>
                    {folders.map((item) => (
                      <button
                        type="button"
                        key={item.key}
                        disabled={saving}
                        onClick={() => {
                          setFolder(item.key);
                          setPage(1);
                          setPreview(undefined);
                        }}
                      >
                        <FolderOutlined />
                        <strong>{item.label}</strong>
                        <small>{counts[item.key] || 0} 条音频</small>
                      </button>
                    ))}
                  </div>
                ) : items.length ? (
                  <div
                    className={styles.audioList}
                    role="radiogroup"
                    aria-label="选择参考音频"
                  >
                    {items.map((item) => (
                      <div
                        className={styles.audioRow}
                        data-selected={
                          selected?.resourceType === item.resourceType &&
                          selected?.resourceId === item.id
                        }
                        key={`${item.resourceType}-${item.id}`}
                      >
                        <label>
                          <input
                            type="radio"
                            name="character-voice"
                            checked={
                              selected?.resourceType === item.resourceType &&
                              selected?.resourceId === item.id
                            }
                            disabled={saving}
                            onChange={() => {
                              setPreview(undefined);
                              setPlaybackFailed(false);
                              setPlaying(false);
                              setSelectedLabel(undefined);
                              if (
                                item.resourceType === 'PROJECT_AUDIO_ASSET' ||
                                item.resourceType === 'AI_VOICE_RESULT'
                              )
                                setSelected({
                                  sourceProjectId: project.id,
                                  sourceProjectName: project.name,
                                  resourceType: item.resourceType,
                                  resourceId: item.id,
                                  name: item.name,
                                  previewUrl: item.previewUrl,
                                  durationSeconds:
                                    item.durationSeconds ?? undefined,
                                });
                            }}
                          />
                          <span>
                            <strong>{item.name}</strong>
                          </span>
                        </label>
                        <span className={styles.duration}>
                          {durationText(item.durationSeconds)}
                        </span>
                        <Button
                          type="text"
                          icon={<PlayCircleOutlined />}
                          aria-label={`试听${item.name}`}
                          disabled={disabled || saving}
                          onClick={() => startPreview(item)}
                        />
                      </div>
                    ))}
                  </div>
                ) : (
                  <Empty
                    description={
                      keyword ? '未找到相关音频' : '当前项目暂无音频'
                    }
                  />
                )}
              </div>
              {!failed && !loading && !showingFolders && total > 12 ? (
                <Pagination
                  className={styles.pagination}
                  current={page}
                  pageSize={12}
                  total={total}
                  showSizeChanger={false}
                  onChange={(value) => {
                    setPage(value);
                    setPreview(undefined);
                  }}
                />
              ) : null}
            </section>
          )}
        </div>
        <aside
          className={styles.audition}
          aria-label="音色试听"
          data-playing={playing && Boolean(audition)}
          data-overridden={Boolean(storyboard && overridden)}
        >
          <span className={styles.asideTitle}>试听</span>
          <div className={styles.soundOrb} aria-hidden="true">
            <i />
            <i />
            <i />
            <i />
            <i />
          </div>
          <strong className={styles.audioName}>
            {audition ? auditionLabel || audition.name : '选择一个音色'}
          </strong>
          {audition && auditionLabel && auditionLabel !== audition.name ? (
            <span className={styles.audioCode}>{audition.name}</span>
          ) : null}
          <div className={styles.player}>
            {audition ? (
              // biome-ignore lint/a11y/useMediaCaption: Reference voice samples have no separate transcript files.
              <audio
                key={`${audition.previewUrl}-${playbackAttempt}`}
                src={audition.previewUrl}
                controls
                autoPlay={Boolean(preview) && source !== 'upload'}
                preload="metadata"
                aria-label={
                  source === 'upload'
                    ? '本地参考音频试听'
                    : `试听${audition.name}`
                }
                onPlay={() => {
                  setPlaying(true);
                  setPlaybackFailed(false);
                }}
                onPause={() => setPlaying(false)}
                onEnded={() => setPlaying(false)}
                onError={() => {
                  setPlaying(false);
                  setPlaybackFailed(true);
                }}
              />
            ) : (
              <span>选择后可在这里试听</span>
            )}
            {audition && playbackFailed ? (
              <span role="alert">音频播放失败，请重试或选择其他音频</span>
            ) : null}
          </div>
          <div className={styles.currentVoice}>
            <span>
              {currentFailed
                ? '当前音色读取失败，可重新选择'
                : current
                  ? `当前音色：${current.name}`
                  : current === null
                    ? '当前音色：未配置'
                    : '正在读取当前音色…'}
            </span>
            {storyboard && overridden ? (
              <Button
                type="link"
                size="small"
                disabled={disabled || saving}
                onClick={() => void resetToDefault()}
              >
                恢复角色默认音色
              </Button>
            ) : null}
          </div>
        </aside>
      </div>
    </Modal>
  );
}
