import { Alert, Button, Modal, Select } from 'antd';
import { useEffect, useRef, useState, type PointerEvent } from 'react';
import {
  createSubtitleEraseTask,
  subtitleEraseSource,
  type EraseRegion,
  type SubtitleEraseSettings,
  type SubtitleEraseTask,
} from './subtitle-erase-service';
import { moveEraseRegion, normalizeEraseRegion } from './subtitle-erase-region';
import './SubtitleEraseModal.css';

export default function SubtitleEraseModal({
  projectId,
  resultId,
  storyboardNo,
  onClose,
  onSubmitted,
}: {
  projectId: number;
  resultId: number;
  storyboardNo: number;
  onClose: () => void;
  onSubmitted: (task: SubtitleEraseTask) => void;
}) {
  const video = useRef<HTMLVideoElement>(null),
    frame = useRef<HTMLDivElement>(null);
  const mounted = useRef(true),
    busy = useRef(false),
    key = useRef(crypto.randomUUID());
  const [method, setMethod] = useState<SubtitleEraseSettings['method']>('AUTO');
  const [quality, setQuality] =
    useState<SubtitleEraseSettings['quality']>('PRO');
  const [region, setRegion] = useState<EraseRegion>({
    left: 0.1,
    top: 0.7,
    right: 0.9,
    bottom: 0.92,
  });
  const [ready, setReady] = useState(false),
    [ratio, setRatio] = useState(16 / 9);
  const [duration, setDuration] = useState(0),
    [time, setTime] = useState(0),
    [playing, setPlaying] = useState(false);
  const [submitting, setSubmitting] = useState(false),
    [error, setError] = useState(''),
    [mediaError, setMediaError] = useState(false);
  const [loadAttempt, setLoadAttempt] = useState(0);
  const drag = useRef<
    { start: { x: number; y: number }; region?: EraseRegion } | undefined
  >(undefined);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);
  useEffect(() => {
    const player = video.current;
    return () => {
      player?.pause();
      player?.removeAttribute('src');
      player?.load();
    };
  }, [loadAttempt]);
  const changed = () => {
    key.current = crypto.randomUUID();
    setError('');
  };
  const choose = (next: typeof method) => {
    if (busy.current) return;
    changed();
    setMethod(next);
    if (next === 'REGION') setQuality('PRO');
  };
  const submit = async () => {
    if (busy.current || !ready || mediaError) return;
    busy.current = true;
    setSubmitting(true);
    setError('');
    video.current?.pause();
    try {
      const settings: SubtitleEraseSettings = {
        method,
        quality,
        ...(method === 'REGION' ? { region } : {}),
      };
      const task = await createSubtitleEraseTask(
        projectId,
        resultId,
        settings,
        key.current,
      );
      if (mounted.current) {
        onSubmitted(task);
        onClose();
      }
    } catch (failure) {
      if (mounted.current)
        setError(
          failure instanceof Error
            ? failure.message
            : '字幕擦除提交失败，请重试',
        );
    } finally {
      busy.current = false;
      if (mounted.current) setSubmitting(false);
    }
  };
  const pointerDown = (event: PointerEvent<HTMLDivElement>) => {
    if (method !== 'REGION' || !ready || busy.current || !frame.current) return;
    video.current?.pause();
    changed();
    drag.current = {
      start: { x: event.clientX, y: event.clientY },
      ...((event.target as HTMLElement).closest('[data-erase-region]')
        ? { region }
        : {}),
    };
    event.currentTarget.setPointerCapture?.(event.pointerId);
    event.preventDefault();
  };
  const pointerMove = (event: PointerEvent<HTMLDivElement>) => {
    if (!drag.current || !frame.current) return;
    const bounds = frame.current.getBoundingClientRect();
    if (drag.current.region) {
      if (!bounds.width || !bounds.height) return;
      setRegion(
        moveEraseRegion(
          drag.current.region,
          (event.clientX - drag.current.start.x) / bounds.width,
          (event.clientY - drag.current.start.y) / bounds.height,
        ),
      );
    } else {
      const next = normalizeEraseRegion(
        drag.current.start,
        { x: event.clientX, y: event.clientY },
        bounds,
      );
      if (next) setRegion(next);
    }
  };
  return (
    <Modal
      open
      centered
      width={850}
      title="擦除字幕"
      footer={null}
      className="subtitle-erase-modal"
      closable={!submitting}
      keyboard={!submitting}
      mask={{ closable: false }}
      onCancel={() => {
        if (!busy.current) onClose();
      }}
    >
      <p className="subtitle-erase-source-name">
        分镜 {storyboardNo} · 视频版本 {resultId}
      </p>
      <div className="subtitle-erase-columns">
        <section>
          <div className="subtitle-erase-stage">
            <div
              ref={frame}
              className="subtitle-erase-frame"
              style={{ width: Math.min(360, 264 * ratio), aspectRatio: ratio }}
              onPointerDown={pointerDown}
              onPointerMove={pointerMove}
              onPointerUp={() => {
                drag.current = undefined;
              }}
              onPointerCancel={() => {
                drag.current = undefined;
              }}
            >
              <video
                key={loadAttempt}
                ref={video}
                src={subtitleEraseSource(projectId, resultId)}
                preload="auto"
                playsInline
                aria-label="待擦除字幕的视频"
                onLoadedMetadata={(event) => {
                  const player = event.currentTarget;
                  if (player.videoWidth > 0 && player.videoHeight > 0)
                    setRatio(player.videoWidth / player.videoHeight);
                  setDuration(
                    Number.isFinite(player.duration) ? player.duration : 0,
                  );
                }}
                onLoadedData={() => {
                  setReady(true);
                  setMediaError(false);
                }}
                onTimeUpdate={(event) =>
                  setTime(event.currentTarget.currentTime)
                }
                onPlay={() => setPlaying(true)}
                onPause={() => setPlaying(false)}
                onEnded={() => setPlaying(false)}
                onError={() => {
                  setReady(false);
                  setMediaError(true);
                }}
              >
                <track kind="captions" />
              </video>
              {method === 'REGION' && ready ? (
                <div
                  data-erase-region
                  className="subtitle-erase-region"
                  role="img"
                  aria-label="字幕擦除区域"
                  style={{
                    left: `${region.left * 100}%`,
                    top: `${region.top * 100}%`,
                    width: `${(region.right - region.left) * 100}%`,
                    height: `${(region.bottom - region.top) * 100}%`,
                  }}
                >
                  <span>拖动或重新框选字幕区域</span>
                  <i />
                  <i />
                  <i />
                  <i />
                </div>
              ) : null}
            </div>
          </div>
          {!ready && !mediaError ? (
            <p className="subtitle-erase-helper" role="status">
              正在加载视频画面…
            </p>
          ) : null}
          {mediaError ? (
            <div className="subtitle-erase-load-error" role="alert">
              视频暂时无法加载{' '}
              <Button
                size="small"
                onClick={() => {
                  setMediaError(false);
                  setReady(false);
                  setLoadAttempt((value) => value + 1);
                }}
              >
                重新加载
              </Button>
            </div>
          ) : null}
          <div className="subtitle-erase-timeline">
            <Button
              size="small"
              disabled={!ready || submitting}
              aria-label={playing ? '暂停视频' : '播放视频'}
              onClick={() => {
                const player = video.current;
                if (!player) return;
                if (player.paused)
                  void player.play().catch(() => setMediaError(true));
                else player.pause();
              }}
            >
              {playing ? 'Ⅱ' : '▶'}
            </Button>
            <input
              type="range"
              aria-label="定位字幕画面"
              min={0}
              max={duration || 0}
              step={0.01}
              value={time}
              disabled={!ready || submitting}
              onChange={(event) => {
                if (video.current) {
                  video.current.pause();
                  video.current.currentTime = Number(event.target.value);
                  setTime(Number(event.target.value));
                }
              }}
            />
            <small>
              {time.toFixed(1)} / {duration.toFixed(1)} 秒
            </small>
          </div>
          <div className="subtitle-erase-original-note">
            <strong>处理完成后生成新的视频版本</strong>
            <p>原视频保留在分镜的视频版本列表中，可随时预览和选用。</p>
          </div>
        </section>
        <section>
          <div className="subtitle-erase-field-title">擦除方式</div>
          <label
            className={`subtitle-erase-method${method === 'AUTO' ? ' is-selected' : ''}`}
          >
            <input
              type="radio"
              name="subtitle-erase-method"
              aria-label="智能擦除"
              checked={method === 'AUTO'}
              disabled={submitting}
              onChange={() => choose('AUTO')}
            />
            <span>
              <strong>
                智能擦除 <em>推荐</em>
              </strong>
              <p>自动识别视频字幕，完成字幕擦除和画面修复。</p>
            </span>
          </label>
          <label
            className={`subtitle-erase-method${method === 'REGION' ? ' is-selected' : ''}`}
          >
            <input
              type="radio"
              name="subtitle-erase-method"
              aria-label="指定区域擦除"
              checked={method === 'REGION'}
              disabled={submitting}
              onChange={() => choose('REGION')}
            />
            <span>
              <strong>指定区域擦除</strong>
              <p>在画面中框选字幕所在区域，仅处理选定范围。</p>
            </span>
          </label>
          <div className="subtitle-erase-quality">
            <span>处理版本</span>
            <Select
              value={quality}
              aria-label="处理版本"
              size="small"
              disabled={submitting || method === 'REGION'}
              onChange={(value: SubtitleEraseSettings['quality']) => {
                changed();
                setQuality(value);
              }}
              options={[
                { value: 'PRO', label: '精细化版' },
                { value: 'STANDARD', label: '标准版' },
              ]}
            />
          </div>
          <p className="subtitle-erase-helper">
            {method === 'REGION'
              ? '在左侧实际视频画面上框选或拖动区域，指定区域使用精细化版。'
              : quality === 'PRO'
                ? '默认识别画面下半部分的字幕；其他位置可使用指定区域擦除。'
                : '自动检测并擦除整段视频中的硬字幕。'}
          </p>
          <div className="subtitle-erase-result-note">
            将基于当前预览的视频生成擦除版本；完成后可预览，再决定是否选用。
          </div>
        </section>
      </div>
      {error ? (
        <Alert
          type="error"
          showIcon
          title={error}
          className="subtitle-erase-error"
        />
      ) : null}
      <div className="subtitle-erase-footer">
        <span>可在任务中心查看处理进度</span>
        <div>
          <Button disabled={submitting} onClick={onClose}>
            取消
          </Button>
          <Button
            type="primary"
            loading={submitting}
            disabled={!ready || mediaError}
            onClick={() => void submit()}
          >
            开始擦除
          </Button>
        </div>
      </div>
    </Modal>
  );
}
