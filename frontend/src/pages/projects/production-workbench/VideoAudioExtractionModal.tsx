import {
  AudioOutlined,
  CaretRightOutlined,
  CheckCircleOutlined,
  FolderOpenOutlined,
  InfoCircleOutlined,
  PauseOutlined,
  ScissorOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import { Button, Input, InputNumber, Modal, Slider } from 'antd';
import { useCallback, useEffect, useId, useRef, useState } from 'react';
import {
  type AudioReference,
  registerProjectAudio,
} from '@/pages/asset-library/audio-service';
import { getCurrentTenantId } from '@/services/account-team/auth';
import {
  type MediaUploadHandle,
  startMediaUpload,
} from '@/services/mediaUpload';
import {
  audioWaveform,
  type AudioWaveformBar,
  decodeVideoAudio,
  encodeAudioSelection,
  formatAudioTime,
  validateAudioRange,
} from './video-audio-extraction';
import { videoFrameSource } from './video-frame-service';
import './VideoAudioExtractionModal.css';

type Props = {
  projectId: number;
  resultId: number;
  sourceName: string;
  projectName?: string;
  onClose: () => void;
};
type Clip = {
  file: File;
  url: string;
  duration: number;
  sessionToken?: string;
  upload?: MediaUploadHandle;
  uploadFailed?: boolean;
};

export default function VideoAudioExtractionModal(props: Props) {
  return (
    <ExtractionDialog key={`${props.projectId}:${props.resultId}`} {...props} />
  );
}

function ExtractionDialog({
  projectId,
  resultId,
  sourceName,
  projectName = '当前项目',
  onClose,
}: Props) {
  const source = videoFrameSource(projectId, resultId);
  const id = useId();
  const video = useRef<HTMLVideoElement>(null);
  const player = useRef<HTMLAudioElement>(null);
  const mediaNodes = useRef<{
    video: HTMLVideoElement | null;
    audio: HTMLAudioElement | null;
  }>({ video: null, audio: null });
  const attachVideo = useCallback((node: HTMLVideoElement | null) => {
    video.current = node;
    if (node) mediaNodes.current.video = node;
  }, []);
  const attachAudio = useCallback((node: HTMLAudioElement | null) => {
    player.current = node;
    if (node) mediaNodes.current.audio = node;
  }, []);
  const audio = useRef<AudioBuffer | undefined>(undefined);
  const lifetime = useRef<AbortController | undefined>(undefined);
  const sourceRequest = useRef<AbortController | undefined>(undefined);
  const pending = useRef<Clip | undefined>(undefined);
  const busy = useRef(false);
  const close = useRef(onClose);
  close.current = onClose;
  const [duration, setDuration] = useState(0);
  const [start, setStart] = useState<number | null>(0);
  const [end, setEnd] = useState<number | null>(0);
  const [name, setName] = useState(
    `${sourceName} · 角色参考音色`.slice(0, 100),
  );
  const [bars, setBars] = useState<AudioWaveformBar[]>([]);
  const [loading, setLoading] = useState(true);
  const [sourceError, setSourceError] = useState('');
  const [retry, setRetry] = useState(0);
  const [clip, setClip] = useState<Clip>();
  const [saving, setSaving] = useState(false);
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState('');
  const [mediaError, setMediaError] = useState(false);
  const [videoReady, setVideoReady] = useState(false);
  const [videoPlaying, setVideoPlaying] = useState(false);
  const [videoTime, setVideoTime] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [playbackTime, setPlaybackTime] = useState(0);
  const [saved, setSaved] = useState<AudioReference>();

  useEffect(() => {
    const controller = new AbortController();
    lifetime.current = controller;
    const tenant = getCurrentTenantId();
    const stop = () => {
      controller.abort();
      sourceRequest.current?.abort();
      pending.current?.upload?.cancel();
      mediaNodes.current.video?.pause();
      mediaNodes.current.audio?.pause();
    };
    const changed = () => {
      if (!controller.signal.aborted && getCurrentTenantId() !== tenant) {
        stop();
        close.current();
      }
    };
    window.addEventListener('current-tenant-changed', changed);
    window.addEventListener('storage', changed);
    return () => {
      stop();
      audio.current = undefined;
      for (const media of [
        mediaNodes.current.video,
        mediaNodes.current.audio,
      ]) {
        media?.removeAttribute('src');
        media?.load();
      }
      window.removeEventListener('current-tenant-changed', changed);
      window.removeEventListener('storage', changed);
    };
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    sourceRequest.current = controller;
    let timedOut = false;
    const timeout = window.setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, 45000);
    setLoading(true);
    setSourceError('');
    audio.current = undefined;
    void decodeVideoAudio(source, controller.signal)
      .then((decoded) => {
        if (
          controller.signal.aborted ||
          lifetime.current?.signal.aborted ||
          sourceRequest.current !== controller
        )
          return;
        const waveform = audioWaveform(decoded);
        audio.current = decoded;
        setBars(waveform);
        setDuration(decoded.duration);
        setStart(0);
        setEnd(Math.min(5, decoded.duration));
      })
      .catch((reason) => {
        if (
          lifetime.current?.signal.aborted ||
          sourceRequest.current !== controller ||
          (controller.signal.aborted && !timedOut)
        )
          return;
        setSourceError(
          timedOut
            ? '音轨读取超时，请重新读取音轨。'
            : reason instanceof Error
              ? reason.message
              : '音轨读取失败，请重试。',
        );
      })
      .finally(() => {
        window.clearTimeout(timeout);
        if (
          !lifetime.current?.signal.aborted &&
          sourceRequest.current === controller &&
          (!controller.signal.aborted || timedOut)
        )
          setLoading(false);
      });
    return () => {
      controller.abort();
      window.clearTimeout(timeout);
    };
  }, [source, retry]);
  useEffect(
    () => () => {
      if (clip) URL.revokeObjectURL(clip.url);
    },
    [clip],
  );

  const invalidate = () => {
    video.current?.pause();
    player.current?.pause();
    pending.current?.upload?.cancel();
    pending.current = undefined;
    setClip(undefined);
    setSaved(undefined);
    setError('');
    setPlaybackTime(0);
    setPlaying(false);
  };
  const rangeError = (() => {
    if (!duration) return '';
    if (start === null || end === null) return '请输入开始和结束时间。';
    try {
      validateAudioRange(duration, start, end);
      return '';
    } catch (reason) {
      return reason instanceof Error ? reason.message : '选段无效。';
    }
  })();
  const changeRange = (nextStart: number | null, nextEnd: number | null) => {
    if (busy.current || lifetime.current?.signal.aborted) return;
    invalidate();
    setStart(nextStart);
    setEnd(nextEnd);
    if (
      nextStart !== null &&
      Number.isFinite(nextStart) &&
      nextStart >= 0 &&
      nextStart <= duration &&
      video.current &&
      video.current.readyState >= 1
    ) {
      video.current.currentTime = nextStart;
    }
  };
  const extract = () => {
    const decoded = audio.current;
    if (
      busy.current ||
      !decoded ||
      lifetime.current?.signal.aborted ||
      start === null ||
      end === null ||
      rangeError
    )
      return;
    invalidate();
    try {
      const file = new File(
        [encodeAudioSelection(decoded, start, end)],
        `video-${resultId}-${Math.round(start * 1000)}-${Math.round(end * 1000)}.wav`,
        { type: 'audio/wav' },
      );
      const next = {
        file,
        url: URL.createObjectURL(file),
        duration: end - start,
      };
      pending.current = next;
      setClip(next);
    } catch (reason) {
      setError(
        reason instanceof Error ? reason.message : '音频提取失败，请重试。',
      );
    }
  };
  const save = async () => {
    const signal = lifetime.current?.signal,
      selected = pending.current;
    if (
      !signal ||
      signal.aborted ||
      busy.current ||
      !selected ||
      !name.trim() ||
      saved
    )
      return;
    busy.current = true;
    setSaving(true);
    setError('');
    setProgress(0);
    video.current?.pause();
    player.current?.pause();
    try {
      if (!selected.sessionToken) {
        try {
          if (!selected.upload)
            selected.upload = await startMediaUpload(selected.file, {
              projectId,
              onProgress: ({ percent }) => {
                if (!signal.aborted) setProgress(Math.round(percent * 100));
              },
            });
          if (signal.aborted) {
            selected.upload.cancel();
            return;
          }
          const verified = await (selected.uploadFailed
            ? selected.upload.retry()
            : selected.upload.attempt);
          selected.sessionToken = verified.sessionToken;
          selected.uploadFailed = false;
        } catch (reason) {
          selected.uploadFailed = true;
          throw reason;
        }
      }
      if (signal.aborted) return;
      const response = await registerProjectAudio(
        projectId,
        selected.sessionToken,
        name.trim(),
        signal,
      );
      if (signal.aborted) return;
      if (!response.success || !response.data)
        throw new Error(response.errorMessage || '音频保存失败，请重试。');
      setSaved(response.data);
    } catch (reason) {
      if (!signal.aborted)
        setError(
          reason instanceof Error ? reason.message : '音频保存失败，请重试。',
        );
    } finally {
      if (!signal.aborted) {
        busy.current = false;
        setSaving(false);
      }
    }
  };
  const toggleVideo = async () => {
    const media = video.current;
    if (
      !media ||
      !videoReady ||
      busy.current ||
      start === null ||
      end === null ||
      rangeError
    )
      return;
    player.current?.pause();
    if (!media.paused) {
      media.pause();
      return;
    }
    if (media.currentTime < start || media.currentTime >= end)
      media.currentTime = start;
    try {
      await media.play();
    } catch (reason) {
      if (
        !lifetime.current?.signal.aborted &&
        !(reason instanceof DOMException && reason.name === 'AbortError')
      )
        setMediaError(true);
    }
  };
  const toggleAudio = async () => {
    const media = player.current;
    if (!media || !clip || busy.current) return;
    video.current?.pause();
    if (!media.paused) {
      media.pause();
      return;
    }
    const current = clip;
    try {
      await media.play();
    } catch (reason) {
      if (
        !lifetime.current?.signal.aborted &&
        pending.current === current &&
        !(reason instanceof DOMException && reason.name === 'AbortError')
      )
        setError('试听暂时无法播放，请重新提取。');
    }
  };
  const selectionDuration =
    start !== null && end !== null && !rangeError ? end - start : 0;
  const disabled = loading || !!sourceError || saving || !duration;
  const feedback = error || rangeError || sourceError;
  const sliderStart = Math.max(0, Math.min(duration, start ?? 0)),
    sliderEnd = Math.max(0, Math.min(duration, end ?? 0));

  return (
    <Modal
      open
      centered
      width={720}
      className="video-audio-modal"
      mask={{ closable: false }}
      closable={!saving}
      keyboard={!saving}
      onCancel={() => {
        if (!busy.current) onClose();
      }}
      title={
        <div className="video-audio-heading">
          <span className="video-audio-heading-icon">
            <AudioOutlined aria-hidden="true" />
          </span>
          <div>
            <h2>提取音色</h2>
            <p>从视频对白中，保留一段角色参考音频</p>
          </div>
        </div>
      }
      footer={
        <div className="video-audio-footer">
          <div className={`video-audio-feedback ${saved ? 'is-saved' : ''}`}>
            {feedback ? (
              <span role="alert" id={`${id}-error`}>
                {feedback}
              </span>
            ) : (
              <span role="status">
                {saved ? (
                  <>
                    <CheckCircleOutlined aria-hidden="true" />「{saved.name}
                    」已保存到项目音频库
                  </>
                ) : saving ? (
                  `正在保存音频… ${progress}%`
                ) : loading ? (
                  '正在读取音轨…'
                ) : clip ? (
                  '试听满意后，保存到项目音频库'
                ) : (
                  '先选择片段并试听'
                )}
              </span>
            )}
          </div>
          <div className="video-audio-footer-buttons">
            <Button
              aria-label={saved ? '完成' : '取消'}
              disabled={saving}
              onClick={() => {
                if (!busy.current) onClose();
              }}
            >
              {saved ? '完成' : '取消'}
            </Button>
            <Button
              aria-label={saved ? '查看项目音频' : '保存到项目音频库'}
              type="primary"
              loading={saving}
              disabled={!saved && (!clip || !name.trim() || !!rangeError)}
              onClick={() => {
                if (saved) {
                  onClose();
                  history.push(
                    `/asset-library/projects/${projectId}?type=AUDIO`,
                  );
                } else void save();
              }}
            >
              {saved ? '查看项目音频' : '保存到项目音频库'}
            </Button>
          </div>
        </div>
      }
    >
      <div className="video-audio-source">
        <div>
          <FolderOpenOutlined aria-hidden="true" />
          <span title={projectName}>{projectName}</span>
          <span aria-hidden="true">/</span>
          <span title={sourceName}>{sourceName}</span>
        </div>
        <span>原音轨 {duration ? formatAudioTime(duration) : '—'}</span>
      </div>
      <div className="video-audio-content">
        <div className="video-audio-stage">
          {/* biome-ignore lint/a11y/useMediaCaption: Existing generated videos do not have separate caption files. */}
          <video
            ref={attachVideo}
            src={source}
            preload="metadata"
            playsInline
            aria-label="音色提取视频预览"
            onLoadedData={() => {
              setVideoReady(true);
              setMediaError(false);
            }}
            onPlay={() => {
              player.current?.pause();
              setVideoPlaying(true);
            }}
            onPause={() => setVideoPlaying(false)}
            onEnded={() => setVideoPlaying(false)}
            onTimeUpdate={(event) => {
              setVideoTime(event.currentTarget.currentTime);
              if (end !== null && event.currentTarget.currentTime >= end)
                event.currentTarget.pause();
            }}
            onError={() => {
              setVideoReady(false);
              setMediaError(true);
            }}
          />
          <span className="video-audio-original">原视频</span>
          {mediaError ? (
            <div className="video-audio-media-error">
              画面暂时无法播放，仍可根据音轨选段。
              <Button
                type="link"
                disabled={saving}
                onClick={() => {
                  setMediaError(false);
                  video.current?.load();
                }}
              >
                重新加载画面
              </Button>
            </div>
          ) : (
            <button
              type="button"
              className="video-audio-play"
              aria-label={videoPlaying ? '暂停原视频' : '播放选中视频片段'}
              disabled={!videoReady || disabled || !!rangeError}
              onClick={() => void toggleVideo()}
            >
              {videoPlaying ? <PauseOutlined /> : <CaretRightOutlined />}
            </button>
          )}
          <span className="video-audio-video-time">
            {formatAudioTime(videoTime)}{' '}
            <span>/ {formatAudioTime(duration)}</span>
          </span>
        </div>
        <section className="video-audio-selection" aria-label="选择对白片段">
          <div className="video-audio-section-title">
            <span>选择对白片段</span>
            <span className="video-audio-selected-time">
              已选 {selectionDuration.toFixed(1)} 秒
            </span>
          </div>
          <div className="video-audio-waveform">
            <div className="video-audio-bars" aria-hidden="true">
              {bars.map((bar) => (
                <i
                  key={bar.sample}
                  data-selected={
                    bar.sample / (audio.current?.sampleRate || 1) >=
                      sliderStart &&
                    bar.sample / (audio.current?.sampleRate || 1) < sliderEnd
                  }
                  style={{ height: `${Math.max(4, bar.level * 82)}%` }}
                />
              ))}
            </div>
            {loading || sourceError ? (
              <div className="video-audio-wave-status">
                {loading ? (
                  '正在读取音轨…'
                ) : (
                  <>
                    <span>音轨暂不可用</span>
                    <Button
                      type="link"
                      onClick={() => {
                        invalidate();
                        setRetry((value) => value + 1);
                      }}
                    >
                      重新读取音轨
                    </Button>
                  </>
                )}
              </div>
            ) : null}
            <Slider
              range={{ draggableTrack: true }}
              className="video-audio-range-slider"
              min={0}
              max={duration || 1}
              step={0.1}
              disabled={disabled}
              value={[
                Math.min(sliderStart, sliderEnd),
                Math.max(sliderStart, sliderEnd),
              ]}
              ariaLabelForHandle={['选段开始时间', '选段结束时间']}
              ariaValueTextFormatterForHandle={(value) =>
                formatAudioTime(value)
              }
              tooltip={{ formatter: (value) => formatAudioTime(value ?? 0) }}
              onChange={(values: number[]) => changeRange(values[0], values[1])}
            />
          </div>
          <div className="video-audio-ticks" aria-hidden="true">
            <span>00:00.0</span>
            <span>{formatAudioTime(duration / 2)}</span>
            <span>{formatAudioTime(duration)}</span>
          </div>
          <div className="video-audio-range-fields">
            <div>
              <label htmlFor={`${id}-start`}>开始</label>
              <InputNumber
                id={`${id}-start`}
                aria-label="开始时间（秒）"
                aria-describedby={rangeError ? `${id}-error` : undefined}
                min={0}
                max={duration}
                step={0.1}
                precision={1}
                controls={false}
                value={start}
                status={rangeError ? 'error' : undefined}
                disabled={disabled}
                onChange={(value) => changeRange(value, end)}
              />
              <span>秒</span>
            </div>
            <div>
              <label htmlFor={`${id}-end`}>结束</label>
              <InputNumber
                id={`${id}-end`}
                aria-label="结束时间（秒）"
                aria-describedby={rangeError ? `${id}-error` : undefined}
                min={0}
                max={duration}
                step={0.1}
                precision={1}
                controls={false}
                value={end}
                status={rangeError ? 'error' : undefined}
                disabled={disabled}
                onChange={(value) => changeRange(start, value)}
              />
              <span>秒</span>
            </div>
            <Button
              aria-label="提取并试听"
              type={clip ? 'default' : 'primary'}
              icon={<ScissorOutlined />}
              disabled={disabled || !!rangeError}
              onClick={extract}
            >
              {clip ? '重新提取' : '提取并试听'}
            </Button>
          </div>
        </section>
        <section
          className={`video-audio-result ${clip ? 'is-ready' : ''}`}
          aria-label="提取结果试听"
        >
          <div className="video-audio-result-player">
            <button
              type="button"
              aria-label={playing ? '暂停提取结果' : '试听提取结果'}
              disabled={!clip || saving}
              onClick={() => void toggleAudio()}
            >
              {playing ? <PauseOutlined /> : <CaretRightOutlined />}
            </button>
            <div>
              <span>{clip ? '角色参考音频' : '尚未生成参考音频'}</span>
              <small>
                {clip
                  ? `${clip.duration.toFixed(1)} 秒 · WAV · 试听满意后即可保存`
                  : '选好对白片段，点击“提取并试听”'}
              </small>
            </div>
            <span className="video-audio-result-time">
              {formatAudioTime(playbackTime)}
            </span>
          </div>
          <Slider
            ariaLabelForHandle="试听播放位置"
            className="video-audio-result-progress"
            min={0}
            max={clip?.duration || 1}
            step={0.1}
            value={playbackTime}
            disabled={!clip || saving}
            tooltip={{ formatter: (value) => formatAudioTime(value ?? 0) }}
            onChange={(value) => {
              if (player.current && player.current.readyState >= 1 && clip) {
                player.current.currentTime = Math.min(value, clip.duration);
                setPlaybackTime(value);
              }
            }}
          />
          {/* biome-ignore lint/a11y/useMediaCaption: Extracted reference audio has no separate transcript file. */}
          <audio
            ref={attachAudio}
            src={clip?.url}
            preload="metadata"
            aria-label="提取音色试听"
            onPlay={() => {
              video.current?.pause();
              setPlaying(true);
            }}
            onPause={() => setPlaying(false)}
            onEnded={() => setPlaying(false)}
            onTimeUpdate={(event) =>
              setPlaybackTime(
                Math.min(clip?.duration || 0, event.currentTarget.currentTime),
              )
            }
            onError={() => {
              if (clip) {
                setPlaying(false);
                setError('试听暂时无法播放，请重新提取。');
              }
            }}
          />
        </section>
        <div className="video-audio-name">
          <label htmlFor={`${id}-name`}>音频名称</label>
          <Input
            id={`${id}-name`}
            value={name}
            maxLength={100}
            disabled={saving || !!saved}
            onChange={(event) => setName(event.target.value)}
          />
          <div className="video-audio-destination">
            <FolderOpenOutlined aria-hidden="true" />
            <span>保存到 {projectName} / 项目音频库</span>
          </div>
        </div>
        <p className="video-audio-hint">
          <InfoCircleOutlined aria-hidden="true" />
          <span>
            建议选择清晰的单人对白，背景配乐及其他声音会一并保留。单次最多30秒。
          </span>
        </p>
      </div>
    </Modal>
  );
}
