import { App, Button, Input, Modal, Pagination } from 'antd';
import { useEffect, useRef, useState } from 'react';
import LazyMediaImage from '@/components/LazyMediaImage';
import PointEstimateButton from '@/components/PointEstimateButton';
import type { ProjectModelOption } from './ai-config/service';
import {
  type AiImageResult,
  queryAiImageResults,
  selectAiImageResult,
  type VisualVariant,
} from './service';
import styles from './AssetVariantGenerationModal.module.css';

export type AssetVariantGenerationValues = {
  prompt: string;
  modelId?: number;
  aspectRatio: string;
  imageCount: number;
};

type Props = {
  open: boolean;
  projectId: number;
  variant?: VisualVariant;
  assetType?: VisualVariant['assetType'];
  primaryImageUrl?: string | null;
  referenceImageUrl?: string | null;
  imageModels: ProjectModelOption[];
  values: AssetVariantGenerationValues;
  submitting: boolean;
  disabled?: boolean;
  onChange: (values: Partial<AssetVariantGenerationValues>) => void;
  onCancel: () => void;
  onSubmit: () => void;
  onImageSelected: () => Promise<void>;
};

type PreviewImage = { id?: number | null; thumbnailUrl?: string | null };

export default function AssetVariantGenerationModal(props: Props) {
  return props.open && props.variant ? (
    <GenerationDialog key={`${props.projectId}-${props.variant.id}`} {...props} variant={props.variant} />
  ) : null;
}

function GenerationDialog({
  projectId, variant, assetType, primaryImageUrl, referenceImageUrl, imageModels, values, submitting,
  disabled, onChange, onCancel, onSubmit, onImageSelected,
}: Props & { variant: VisualVariant }) {
  const { message } = App.useApp();
  const label = { CHARACTER: '角色', SCENE: '场景', PROP: '道具' }[assetType || variant.assetType];
  const [initialTaskId] = useState(variant.generationStatus === 'GENERATING' ? undefined : variant.generationTaskId);
  const [preview, setPreview] = useState<PreviewImage>();
  const [results, setResults] = useState<AiImageResult[]>([]);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadFailed, setLoadFailed] = useState(false);
  const [reload, setReload] = useState(0);
  const [selecting, setSelecting] = useState(false);
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);
  const generating = variant.generationStatus === 'GENERATING';
  const busy = submitting || selecting || generating || disabled;
  const current = { id: variant.currentImageResultId, thumbnailUrl: variant.currentImageThumbnailUrl };
  const selected = preview || current;
  const latestTaskId = variant.generationTaskId !== initialTaskId ? variant.generationTaskId : undefined;
  const latest = latestTaskId ? results.filter((image) => image.taskId === latestTaskId) : [];
  const history = results.filter((image) => !latestTaskId || image.taskId !== latestTaskId);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setLoadFailed(false);
    setResults([]);
    void queryAiImageResults(projectId, {
      targetType: 'VISUAL_VARIANT', targetId: variant.id, current: page, pageSize: 20,
    }, controller.signal).then((response) => {
      if (controller.signal.aborted) return;
      if (!response.success) throw new Error(response.errorMessage);
      setResults(response.data.data);
      setTotal(response.data.total);
    }).catch(() => {
      if (!controller.signal.aborted) setLoadFailed(true);
    }).finally(() => {
      if (!controller.signal.aborted) setLoading(false);
    });
    return () => controller.abort();
  }, [projectId, variant.id, variant.currentImageResultId, variant.generationTaskId, variant.generationStatus, page, reload]);

  // New results always enter the first page; keep a deliberately selected historical preview intact.
  useEffect(() => { setPage(1); }, [variant.currentImageResultId, variant.generationTaskId]);

  const confirm = async () => {
    if (busy) return;
    if (!selected.id || selected.id === current.id) { onCancel(); return; }
    setSelecting(true);
    try {
      const response = await selectAiImageResult(projectId, selected.id);
      if (!response.success) throw new Error(response.errorMessage || '配图选用失败');
      if (!mounted.current) return;
      await onImageSelected();
      if (mounted.current) onCancel();
    } catch (error) {
      if (mounted.current) message.error(error instanceof Error ? error.message : '配图选用失败，请重试');
    } finally {
      if (mounted.current) setSelecting(false);
    }
  };

  const thumbnail = (image: PreviewImage, name: string) => (
    <button
      type="button"
      key={image.id ?? name}
      className={styles.thumbnail}
      aria-label={name}
      aria-pressed={Boolean(image.thumbnailUrl && selected.id === image.id)}
      disabled={!image.thumbnailUrl || selecting}
      onClick={() => setPreview(image)}
    >
      {image.thumbnailUrl ? (
        <LazyMediaImage native src={image.thumbnailUrl} alt={name} height="100%" imageStyle={{ objectFit: 'contain' }} />
      ) : <span className={styles.empty}>暂无</span>}
    </button>
  );

  return (
    <Modal
      title={(
        <div className={styles.header}>
          <span>{label}生成</span>
          <Button type="primary" aria-label={`确认${label}`} disabled={Boolean(busy) || !selected.thumbnailUrl} loading={selecting} onClick={() => void confirm()}>
            确认{label}
          </Button>
        </div>
      )}
      open
      width="min(1680px, calc(100vw - 32px))"
      style={{ top: 16, paddingBottom: 0 }}
      styles={{ body: { height: 'calc(100dvh - 132px)', minHeight: 0 }, header: { marginBottom: 16 } }}
      footer={null}
      mask={{ closable: false }}
      onCancel={selecting ? undefined : onCancel}
    >
      <div className={styles.workspace}>
        <div className={styles.previewLayout}>
          <section className={styles.preview} aria-label="资产配图预览">
            <div className={styles.previewLabel}>{!preview || preview.id === current.id ? '当前主图' : '配图预览'}</div>
            <div className={styles.stage}>
              {selected.thumbnailUrl ? (
                <LazyMediaImage
                  native src={selected.thumbnailUrl}
                  alt={preview && preview.id !== current.id ? `${variant.name}历史配图预览` : `${variant.name}当前图`}
                  height="100%" imageStyle={{ objectFit: 'contain' }}
                />
              ) : <span className={styles.empty}>暂无当前图</span>}
            </div>
          </section>
          <aside className={styles.historyRail} aria-label="资产配图记录">
            <section className={styles.imageGroup} aria-label="当前配图">
              <div className={styles.groupLabel}>当前</div>
              {thumbnail(current, '预览当前配图')}
            </section>
            <section className={styles.imageGroup} aria-label="最新配图">
              <div className={styles.groupLabel}>最新</div>
              {generating || submitting ? <div role="status" className={styles.generating}>正在生成<span>···</span></div> : null}
              {latest.map((image) => thumbnail(image, `预览最新配图${image.id}`))}
              {!latest.length && !generating && !submitting ? <div className={styles.empty}>暂无</div> : null}
            </section>
            <section className={styles.imageGroup} aria-label="历史配图">
              <div className={styles.groupLabel}>历史</div>
              {loading ? <div role="status" className={styles.empty}>加载中</div> : null}
              {loadFailed ? <button type="button" className={styles.retry} onClick={() => setReload((value) => value + 1)}>加载失败，重试</button> : null}
              {history.map((image) => thumbnail(image, `预览历史配图${image.id}`))}
              {!loading && !loadFailed && !history.length ? <div className={styles.empty}>暂无历史配图</div> : null}
              {total > 20 ? <Pagination simple size="small" current={page} pageSize={20} total={total} showSizeChanger={false} onChange={setPage} /> : null}
            </section>
          </aside>
        </div>
        <section className={styles.composer} aria-label={`${variant.name}视觉生成任务`}>
          <div className={styles.composerHeading}>
            <span>{variant.name} · 视觉生成任务</span>
            {(assetType || variant.assetType) === 'CHARACTER' && !variant.primary && primaryImageUrl ? (
              <div className={styles.reference}>
                <span>主形象引用图</span>
                <LazyMediaImage native src={primaryImageUrl} aria-label={`${variant.name}引用图`} alt="主形象引用图" height={44} width={78} imageStyle={{ objectFit: 'contain' }} />
              </div>
            ) : null}
          </div>
          <Input.TextArea
            aria-label={`${variant.name}生成提示词`}
            className={styles.prompt}
            value={values.prompt}
            disabled={Boolean(busy)}
            onChange={(event) => onChange({ prompt: event.target.value })}
          />
          {variant.errorMessage ? <div role="alert" className={styles.error}>{variant.errorMessage}</div> : null}
          <div className={styles.controls}>
            <div className={styles.selects}>
              <select aria-label="图片模型" disabled={Boolean(busy)} value={values.modelId ?? ''} onChange={(event) => onChange({ modelId: event.target.value ? Number(event.target.value) : undefined })}>
                <option value="">使用项目默认模型</option>
                {imageModels.map((model) => <option key={model.id} value={model.id}>{model.name}</option>)}
              </select>
              <select aria-label="生成数量" disabled={Boolean(busy)} value={values.imageCount} onChange={(event) => onChange({ imageCount: Number(event.target.value) })}>
                {[1, 2, 4].map((count) => <option key={count} value={count}>{count} 张</option>)}
              </select>
              <select aria-label="画面比例" disabled={Boolean(busy)} value={values.aspectRatio} onChange={(event) => onChange({ aspectRatio: event.target.value })}>
                {['3:4', '1:1', '16:9'].map((ratio) => <option key={ratio} value={ratio}>{ratio}</option>)}
              </select>
              <span className={styles.priceNote}>积分按所选模型实时结算</span>
            </div>
            <PointEstimateButton operations={values.prompt.trim() ? [{ url: `/api/projects/${projectId}/ai-image-tasks`, data: {
              taskType: assetType || variant.assetType, targetType: 'VISUAL_VARIANT', targetId: variant.id,
              ...values, referenceImages: (assetType || variant.assetType) === 'CHARACTER' && !variant.primary && (referenceImageUrl || primaryImageUrl) ? [referenceImageUrl || primaryImageUrl] : undefined,
            } }] : undefined} type="primary" aria-label={`提交${variant.name}生成`} loading={submitting || generating} disabled={Boolean(busy)} onClick={onSubmit}>
              {generating ? '正在生成' : '生成图片'}
            </PointEstimateButton>
          </div>
        </section>
      </div>
    </Modal>
  );
}
