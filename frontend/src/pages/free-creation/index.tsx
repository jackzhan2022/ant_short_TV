import PointEstimateButton from '@/components/PointEstimateButton';
import {
  BulbOutlined,
  PictureOutlined,
  PlusOutlined,
  ReloadOutlined,
  VideoCameraOutlined,
} from '@ant-design/icons';
import { useAccess, useModel } from '@umijs/max';
import { Alert, Button, Spin } from 'antd';
import { type KeyboardEvent, useEffect, useRef, useState } from 'react';
import { PointEstimateCancelledError } from '@/services/point-estimate';
import {
  type CreationRecord,
  type CreationRequest,
  createCreation,
} from './api';
import Composer from './Composer';
import {
  type CreationDraft,
  draftError,
  newDraft,
  requestFromDraft,
  reuseDraft,
} from './draft';
import styles from './index.module.css';
import Records from './Records';
import {
  type CreationMode,
  type CreationModel,
  loadCreationModels,
} from './service';

const labels = { IMAGE: '图片', VIDEO: '视频' };
const examples = {
  IMAGE:
    '雨后的城市街角，一家温暖的小咖啡馆，窗边透出柔和灯光，街道倒影，电影感构图。',
  VIDEO:
    '镜头缓缓推近雨夜的咖啡馆，窗边的人抬头望向街道，暖色灯光映在湿润的路面上。',
};
function navigateTabs(event: KeyboardEvent<HTMLButtonElement>) {
  const tabs = Array.from(
    event.currentTarget.parentElement?.querySelectorAll<HTMLButtonElement>(
      '[role="tab"]',
    ) ?? [],
  );
  const index = tabs.indexOf(event.currentTarget);
  const target =
    event.key === 'Home'
      ? tabs[0]
      : event.key === 'End'
        ? tabs.at(-1)
        : event.key === 'ArrowRight'
          ? tabs[(index + 1) % tabs.length]
          : event.key === 'ArrowLeft'
            ? tabs[(index + tabs.length - 1) % tabs.length]
            : undefined;
  if (target) {
    event.preventDefault();
    target.focus();
    target.click();
  }
}
function Workspace({ teamName, tenantId }: { teamName: string; tenantId: number }) {
  const [mode, setMode] = useState<CreationMode>('IMAGE');
  const [models, setModels] = useState<CreationModel[]>([]);
  const [loading, setLoading] = useState(true);
  const [modelError, setModelError] = useState('');
  const [revision, setRevision] = useState(0);
  const [recordsRevision, setRecordsRevision] = useState(0);
  const [drafts, setDrafts] = useState<Record<CreationMode, CreationDraft>>({
    IMAGE: newDraft(),
    VIDEO: newDraft(),
  });
  const [resets, setResets] = useState({ IMAGE: 0, VIDEO: 0 });
  const [notice, setNotice] = useState('');
  const [submitError, setSubmitError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const busy = useRef(false);
  const lifetime = useRef(new AbortController());
  const attempts = useRef<
    Partial<
      Record<CreationMode, { fingerprint: string; payload: CreationRequest }>
    >
  >({});
  useEffect(() => {
    if (lifetime.current.signal.aborted)
      lifetime.current = new AbortController();
    const controller = lifetime.current;
    return () => controller.abort();
  }, []);
  useEffect(() => {
    const abort = new AbortController();
    setLoading(true);
    setModelError('');
    void loadCreationModels(abort.signal)
      .then((next) => {
        if (abort.signal.aborted) return;
        setModels(next);
        setDrafts(
          (current) =>
            Object.fromEntries(
              (['IMAGE', 'VIDEO'] as const).map((type) => [
                type,
                current[type].modelId
                  ? current[type]
                  : {
                      ...newDraft(
                        next.find((model) => model.serviceType === type),
                      ),
                      prompt: current[type].prompt,
                    },
              ]),
            ) as Record<CreationMode, CreationDraft>,
        );
      })
      .catch((failure) => {
        if (!abort.signal.aborted)
          setModelError(
            failure instanceof Error ? failure.message : '模型加载失败，请重试',
          );
      })
      .finally(() => {
        if (!abort.signal.aborted) setLoading(false);
      });
    return () => abort.abort();
  }, [revision]);
  const update = (
    type: CreationMode,
    value: CreationDraft | ((current: CreationDraft) => CreationDraft),
  ) => {
    setDrafts((current) => ({
      ...current,
      [type]: typeof value === 'function' ? value(current[type]) : value,
    }));
    setNotice('');
    setSubmitError('');
  };
  const submit = async () => {
    const signal = lifetime.current.signal;
    if (busy.current) return;
    const draft = drafts[mode];
    const model = models.find(
      (item) => item.id === draft.modelId && item.serviceType === mode,
    );
    const invalid = draftError(draft, model);
    if (invalid) {
      setSubmitError(invalid);
      return;
    }
    const candidate = requestFromDraft(draft, mode, '');
    const fingerprint = JSON.stringify(candidate);
    const attempt =
      attempts.current[mode]?.fingerprint === fingerprint
        ? attempts.current[mode]
        : {
            fingerprint,
            payload: { ...candidate, requestId: crypto.randomUUID() },
          };
    if (!attempt) return;
    attempts.current[mode] = attempt;
    busy.current = true;
    setSubmitting(true);
    setSubmitError('');
    setNotice('');
    try {
      await createCreation(attempt.payload, signal);
      if (signal.aborted) return;
      delete attempts.current[mode];
      setNotice(`${labels[mode]}创作已提交，可在创作记录中查看进度`);
      setRecordsRevision((value) => value + 1);
    } catch (failure) {
      if (!signal.aborted && !(failure instanceof PointEstimateCancelledError))
        setSubmitError(
          failure instanceof Error ? failure.message : '提交失败，请重试',
        );
    } finally {
      if (!signal.aborted) {
        busy.current = false;
        setSubmitting(false);
      }
    }
  };
  const reuse = async (record: CreationRecord) => {
    const signal = lifetime.current.signal;
    if (busy.current) throw new Error('请先完成当前生成确认');
    const latestModels = await loadCreationModels(signal);
    if (signal.aborted) return;
    const draft = reuseDraft(record, latestModels);
    setModels(latestModels);
    setMode(record.mediaType);
    update(record.mediaType, draft);
    setResets((current) => ({
      ...current,
      [record.mediaType]: current[record.mediaType] + 1,
    }));
    setNotice('已回填参数和参考素材，请检查后点击生成');
  };
  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <div>
          <div className={styles.title}>
            <h1>自由创作</h1>
            <span>内测</span>
          </div>
          <p>从一个灵感开始，创作图片与视频</p>
        </div>
        <div className={styles.headerActions}>
          <span className={styles.draftState}>本地草稿 · 刷新后清空</span>
          <Button
            disabled={submitting}
            icon={<PlusOutlined aria-hidden="true" />}
            onClick={() => {
              update(
                mode,
                newDraft(models.find((item) => item.serviceType === mode)),
              );
              setResets((current) => ({
                ...current,
                [mode]: current[mode] + 1,
              }));
              delete attempts.current[mode];
              setNotice(`已清空${labels[mode]}创作草稿`);
            }}
          >
            新建创作
          </Button>
        </div>
      </header>
      <div className={styles.workspace}>
        <section className={styles.editor} aria-label="创作设置">
          <div className={styles.editorBody}>
            <div
              className={styles.modeTabs}
              role="tablist"
              aria-label="创作类型"
            >
              {(['IMAGE', 'VIDEO'] as const).map((type) => (
                <button
                  key={type}
                  id={`creation-tab-${type}`}
                  type="button"
                  role="tab"
                  aria-selected={mode === type}
                  aria-controls={`creation-panel-${type}`}
                  tabIndex={mode === type ? 0 : -1}
                  disabled={submitting}
                  onKeyDown={navigateTabs}
                  onClick={() => {
                    setMode(type);
                    setSubmitError('');
                  }}
                >
                  {type === 'IMAGE' ? (
                    <PictureOutlined aria-hidden="true" />
                  ) : (
                    <VideoCameraOutlined aria-hidden="true" />
                  )}
                  {labels[type]}创作
                </button>
              ))}
            </div>
            {loading ? (
              <div className={styles.loading}>
                <Spin size="small" />
                正在加载可用模型…
              </div>
            ) : null}
            {modelError ? (
              <div className={styles.modelError}>
                <Alert type="error" title={modelError} />
                <Button
                  size="small"
                  icon={<ReloadOutlined aria-hidden="true" />}
                  onClick={() => setRevision((value) => value + 1)}
                >
                  重新加载模型
                </Button>
              </div>
            ) : null}
            {(['IMAGE', 'VIDEO'] as const).map((type) => (
              <div
                key={`${type}-${resets[type]}`}
                id={`creation-panel-${type}`}
                role="tabpanel"
                aria-labelledby={`creation-tab-${type}`}
                hidden={mode !== type}
              >
                <Composer
                  mode={type}
                  models={models.filter((item) => item.serviceType === type)}
                  loading={loading}
                  draft={drafts[type]}
                  onChange={(value) => update(type, value)}
                  example={examples[type]}
                  disabled={submitting}
                />
              </div>
            ))}
          </div>
          <div className={styles.editorFooter}>
            {submitError ? (
              <p role="alert" className={styles.error}>
                {submitError}
              </p>
            ) : null}
            <PointEstimateButton
              tenantId={tenantId}
              operations={draftError(drafts[mode], models.find((item) => item.id === drafts[mode].modelId && item.serviceType === mode))
                ? undefined : [{ url: '/api/free-creations', data: requestFromDraft(drafts[mode], mode, 'button-point-estimate') }]}
              type="primary"
              block
              size="large"
              aria-label={`生成${labels[mode]}`}
              loading={submitting}
              disabled={loading || Boolean(modelError) || !drafts[mode].modelId}
              onClick={() => void submit()}
            >
              生成{labels[mode]}
            </PointEstimateButton>
            <p>生成前确认预计积分，提交后在右侧查看结果</p>
          </div>
        </section>
        <div className={styles.resultColumn}>
          <Records
            tenantId={tenantId}
            revision={recordsRevision}
            onReuse={reuse}
            teamName={teamName}
          />
          <div className={styles.tip}>
            <BulbOutlined aria-hidden="true" />
            <div>
              <strong>让灵感更清晰一点</strong>
              <p>
                写清主体、场景、风格与镜头变化，参考素材要求随所选模型变化。
              </p>
            </div>
          </div>
        </div>
      </div>
      {notice ? (
        <p role="status" className={styles.successNotice}>
          {notice}
        </p>
      ) : null}
    </div>
  );
}
export default function FreeCreationPage() {
  const { initialState } = useModel('@@initialState');
  const access = useAccess();
  if (!access.canUseFreeCreation)
    return <Alert type="info" title="请先登录并选择有效的创作团队" />;
  if (!initialState?.currentTenantId)
    return <Alert type="info" title="请先选择创作团队" />;
  return (
    <Workspace
      tenantId={initialState.currentTenantId}
      key={`${initialState.currentTenantId}-${initialState.currentUser?.userid}`}
      teamName={
        initialState.tenants?.find(
          (tenant) => tenant.id === initialState.currentTenantId,
        )?.name || '当前团队'
      }
    />
  );
}
