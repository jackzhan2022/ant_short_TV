import { App, Button, Modal, Radio } from 'antd';
import { useEffect, useRef, useState } from 'react';
import styles from './CharacterPromptSimilarity.module.css';
import {
  type CharacterSimilarityStatus,
  type CharacterVariantResolution,
  type CharacterVariantSimilarityPair,
  queryCharacterVariantResolution,
  resolveCharacterVariants,
  type VariantResolutionDecision,
} from './character-similarity-service';

const pairKey = (pair: CharacterVariantSimilarityPair) =>
  `${pair.characterId}-${pair.leftVariantId}-${pair.rightVariantId}`;
const episodeLabel = (episodes: number[]) =>
  episodes.length
    ? episodes.map((value) => `第 ${value} 集`).join('、')
    : '暂无出镜集数';
const resultLabel = (decision: VariantResolutionDecision) =>
  decision === 'MERGED' ? '已合并' : '已保留为不同形态';

const resolutionErrorMessage = (cause: unknown, fallback: string) => {
  const failure = cause as
    | {
        response?: { data?: { errorMessage?: string } };
        info?: { errorMessage?: string };
        message?: string;
      }
    | null
    | undefined;
  return (
    failure?.response?.data?.errorMessage ||
    failure?.info?.errorMessage ||
    failure?.message ||
    fallback
  );
};

export default function CharacterVariantSimilarity({
  status,
  projectId,
  checkId,
  disabled = false,
  onInspect,
  onResolved,
}: {
  status: CharacterSimilarityStatus;
  projectId?: number;
  checkId?: number;
  disabled?: boolean;
  onInspect?: (characterId: number, variantId: number) => void;
  onResolved?: () => Promise<unknown>;
}) {
  const { message } = App.useApp();
  const [selected, setSelected] = useState('');
  const [decisions, setDecisions] = useState<
    Record<
      string,
      { decision: VariantResolutionDecision; keptVariantId?: number }
    >
  >({});
  const [mergeOpen, setMergeOpen] = useState(false);
  const [preflight, setPreflight] = useState<CharacterVariantResolution>();
  const [keptId, setKeptId] = useState<number>();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string>();
  const savingRef = useRef(false);
  const requestVersion = useRef(0);
  useEffect(
    () => () => {
      requestVersion.current += 1;
    },
    [],
  );
  const pairs = status.analysis?.variantPairs ?? [];
  const comparable = status.characters?.some(
    (character) => (character.variants?.length ?? 0) >= 2,
  );
  const pair = pairs.find((item) => pairKey(item) === selected) ?? pairs[0];
  const character = status.characters?.find(
    (item) => item.id === pair?.characterId,
  );
  const decision =
    pair && (pair.resolution ?? decisions[pairKey(pair)]?.decision);
  const keptVariantId =
    pair && (pair.keptVariantId ?? decisions[pairKey(pair)]?.keptVariantId);
  const done = pairs.filter(
    (item) => item.resolution || decisions[pairKey(item)],
  ).length;
  const pairCurrent = pair?.canResolve ?? !status.stale;
  const canInspect =
    pair?.canInspect ?? (decision === 'DISTINCT' ? !status.stale : pairCurrent);
  const canResolve = Boolean(
    pair && projectId && checkId && !disabled && pairCurrent && !decision,
  );
  const primaryId = preflight?.left.isPrimary
    ? preflight.left.id
    : preflight?.right.isPrimary
      ? preflight.right.id
      : undefined;
  const canConfirm =
    canResolve &&
    !loading &&
    !saving &&
    Boolean(
      preflight?.canMerge &&
        keptId &&
        [preflight.left.id, preflight.right.id].includes(keptId) &&
        (!primaryId || keptId === primaryId),
    );

  const loadPreflight = async () => {
    if (!pair || !projectId || !checkId || !canResolve || savingRef.current)
      return;
    const version = ++requestVersion.current;
    setLoading(true);
    setError(undefined);
    setPreflight(undefined);
    setKeptId(undefined);
    try {
      const response = await queryCharacterVariantResolution(
        projectId,
        checkId,
        pair.characterId,
        {
          leftVariantId: pair.leftVariantId,
          rightVariantId: pair.rightVariantId,
        },
      );
      if (version !== requestVersion.current) return;
      setPreflight(response.data);
      setKeptId(
        response.data.left.isPrimary
          ? response.data.left.id
          : response.data.right.isPrimary
            ? response.data.right.id
            : response.data.recommendedKeptVariantId,
      );
    } catch (cause) {
      if (version === requestVersion.current)
        setError(resolutionErrorMessage(cause, '合并预检读取失败，请重试'));
    } finally {
      if (version === requestVersion.current) setLoading(false);
    }
  };
  const closeMerge = () => {
    if (savingRef.current) return;
    requestVersion.current += 1;
    setMergeOpen(false);
    setLoading(false);
    setPreflight(undefined);
    setError(undefined);
  };
  const resolve = async (nextDecision: VariantResolutionDecision) => {
    if (
      !pair ||
      !projectId ||
      !checkId ||
      !canResolve ||
      savingRef.current ||
      (nextDecision === 'MERGED' && !canConfirm)
    )
      return;
    savingRef.current = true;
    setSaving(true);
    setError(undefined);
    const version = ++requestVersion.current;
    try {
      const fresh =
        nextDecision === 'MERGED'
          ? preflight
          : (
              await queryCharacterVariantResolution(
                projectId,
                checkId,
                pair.characterId,
                {
                  leftVariantId: pair.leftVariantId,
                  rightVariantId: pair.rightVariantId,
                },
              )
            ).data;
      if (!fresh || version !== requestVersion.current) return;
      const response = await resolveCharacterVariants(
        projectId,
        checkId,
        pair.characterId,
        {
          leftVariantId: pair.leftVariantId,
          rightVariantId: pair.rightVariantId,
          decision: nextDecision,
          fingerprint: fresh.fingerprint,
          ...(nextDecision === 'MERGED' ? { keptVariantId: keptId } : {}),
        },
      );
      if (version !== requestVersion.current) return;
      setDecisions((values) => ({
        ...values,
        [pairKey(pair)]: {
          decision: response.data.decision,
          keptVariantId: response.data.keptVariantId,
        },
      }));
      setMergeOpen(false);
      setPreflight(undefined);
      message.success(resultLabel(response.data.decision));
      try {
        await onResolved?.();
      } catch {
        message.warning('处理已保存，但页面刷新失败，请重新读取检查结果。');
      }
    } catch (cause) {
      if (version === requestVersion.current) {
        setError(resolutionErrorMessage(cause, '形态处理失败，请重试'));
        setPreflight(undefined);
      }
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  };

  return (
    <div className={styles.workspace}>
      <aside className={styles.sidebar} aria-label="相似形态列表">
        <div className={styles.sectionLabel}>
          同角色疑似重复形态 · {pairs.length} 组
        </div>
        <div className={styles.resolutionCount}>
          待处理 {pairs.length - done} · 已处理 {done}
        </div>
        <div className={styles.pairList}>
          {pairs.map((item) => {
            const owner = status.characters?.find(
              (value) => value.id === item.characterId,
            );
            const left = owner?.variants?.find(
              (value) => value.id === item.leftVariantId,
            );
            const right = owner?.variants?.find(
              (value) => value.id === item.rightVariantId,
            );
            const resolved =
              item.resolution ?? decisions[pairKey(item)]?.decision;
            return (
              <button
                key={pairKey(item)}
                type="button"
                className={`${styles.pairButton} ${styles.variantPairButton} ${item === pair ? styles.active : ''}`}
                aria-pressed={item === pair}
                disabled={saving || mergeOpen}
                onClick={() => {
                  setSelected(pairKey(item));
                  setError(undefined);
                }}
              >
                <span className={styles.variantPairHeading}>
                  <strong>{owner?.name}</strong>
                  <span
                    className={resolved ? styles.resolvedLabel : styles.score}
                  >
                    {resolved ? resultLabel(resolved) : `${item.score} 分`}
                  </span>
                </span>
                <span className={styles.variantPairNames}>
                  {left?.name} / {right?.name}
                </span>
              </button>
            );
          })}
        </div>
      </aside>
      <section
        className={`${styles.detail} ${styles.variantDetail}`}
        aria-label="角色形态对照"
      >
        {pair && character ? (
          <>
            <div className={styles.variantDetailHeading}>
              <h3>{character.name} · 疑似重复形态</h3>
              <span className={styles.similarityBadge}>
                {pair.score} 分 · 造型相似
              </span>
            </div>
            <p className={styles.sectionLabel}>
              根据形态名称、外观描述和提示词判断；相似分数表示造型语义接近程度。
            </p>
            {decision ? (
              <div className={styles.resolutionResult} role="status">
                {resultLabel(decision)}
                {decision === 'MERGED'
                  ? ' · 以下保留检查时的原始对照'
                  : ' · 两个形态及其素材均保留'}
              </div>
            ) : null}
            <div className={styles.variantActions}>
              {decision ? (
                decision === 'MERGED' && onInspect && keptVariantId ? (
                  <Button
                    onClick={() => onInspect(character.id, keptVariantId)}
                  >
                    查看保留形态
                  </Button>
                ) : null
              ) : (
                <>
                  <Button
                    type="primary"
                    disabled={!canResolve || saving || loading}
                    onClick={() => {
                      setMergeOpen(true);
                      void loadPreflight();
                    }}
                  >
                    合并形态
                  </Button>
                  <Button
                    disabled={!canResolve || saving || loading}
                    loading={saving && !mergeOpen}
                    onClick={() => void resolve('DISTINCT')}
                  >
                    保留为不同形态
                  </Button>
                  <span className={styles.sectionLabel}>
                    {pairCurrent
                      ? '合并前可预览影响范围'
                      : '此组设定已变化，请重新检查'}
                  </span>
                </>
              )}
            </div>
            {error && !mergeOpen ? (
              <p role="alert" className={styles.resolutionError}>
                {error}
              </p>
            ) : null}
            <div className={styles.variantComparison}>
              {[pair.leftVariantId, pair.rightVariantId].map((id) => {
                const variant = character.variants?.find(
                  (item) => item.id === id,
                );
                return (
                  <article key={id} className={styles.variantCard}>
                    <h4>{variant?.name || `形态 #${id}`}</h4>
                    {variant?.isPrimary ? (
                      <span className={styles.sectionLabel}>默认形态</span>
                    ) : null}
                    <h5>外观描述</h5>
                    <p>{variant?.appearance || '未填写外观描述'}</p>
                    <h5>提示词</h5>
                    <pre>
                      {variant?.prompt ||
                        (variant?.isPrimary ? character.prompt : '') ||
                        '未填写提示词'}
                    </pre>
                    {onInspect && decision !== 'MERGED' ? (
                      <Button
                        disabled={!canInspect}
                        onClick={() => onInspect(character.id, id)}
                      >
                        查看此形态
                      </Button>
                    ) : null}
                  </article>
                );
              })}
            </div>
            {(
              [
                ['相似依据', pair.sharedFeatures],
                ['已有差异', pair.differences],
                ['处理建议', pair.suggestions],
              ] as const
            ).map(([title, values]) => (
              <div key={title} className={styles.reviewSection}>
                <h4>{title}</h4>
                {values.length ? (
                  <ul>
                    {values.map((value) => (
                      <li key={value}>{value}</li>
                    ))}
                  </ul>
                ) : (
                  <p>未报告明确差异</p>
                )}
              </div>
            ))}
          </>
        ) : (
          <div className={styles.empty}>
            <h3>
              {status.analysis?.variantPairs === undefined
                ? '旧报告未包含形态检查'
                : comparable
                  ? '未发现疑似重复形态'
                  : '暂无可比较的同角色形态'}
            </h3>
            <p>
              {status.analysis?.variantPairs === undefined
                ? '请重新检查，将纳入同一角色下的服装与造型描述。'
                : comparable
                  ? '已检查同一角色下的形态名称、外观描述和提示词。'
                  : '同一角色至少有两个形态时，可检查服装与造型是否重复。'}
            </p>
          </div>
        )}
      </section>
      <Modal
        open={mergeOpen}
        title="合并角色形态"
        width={880}
        centered
        onCancel={closeMerge}
        closable={!saving}
        keyboard={!saving}
        mask={{ closable: !saving }}
        destroyOnHidden
        styles={{
          body: { maxHeight: 'calc(100dvh - 220px)', overflowY: 'auto' },
        }}
        footer={
          <>
            <Button disabled={saving} onClick={closeMerge}>
              取消
            </Button>
            <Button
              type="primary"
              disabled={!canConfirm}
              loading={saving}
              onClick={() => void resolve('MERGED')}
            >
              确认合并
            </Button>
          </>
        }
      >
        <p className={styles.sectionLabel}>
          选择要保留的形态，其现有图片和完整提示词将一并保留。
        </p>
        {loading ? <p role="status">正在读取最新形态及影响范围…</p> : null}
        {error ? (
          <div role="alert" className={styles.resolutionError}>
            {error}{' '}
            <Button
              disabled={loading || saving}
              onClick={() => void loadPreflight()}
            >
              重新读取
            </Button>
          </div>
        ) : null}
        {preflight ? (
          <>
            <h4 className={styles.mergeCharacter}>
              {preflight.characterName} · 选择保留形态
            </h4>
            {primaryId ? (
              <p className={styles.primaryNotice}>
                此组包含默认形态，必须保留默认形态。
              </p>
            ) : null}
            <Radio.Group
              value={keptId}
              onChange={(event) => setKeptId(event.target.value)}
              className={styles.retentionGrid}
              disabled={saving || disabled}
            >
              {[preflight.left, preflight.right].map((variant) => (
                <div
                  key={variant.id}
                  className={`${styles.retentionCard} ${keptId === variant.id ? styles.retentionSelected : ''}`}
                >
                  <Radio
                    value={variant.id}
                    disabled={Boolean(primaryId && primaryId !== variant.id)}
                    aria-label={`保留 ${variant.name}`}
                  >
                    <strong>{variant.name}</strong>
                  </Radio>
                  <span className={styles.retentionHint}>
                    {keptId === variant.id
                      ? variant.isPrimary
                        ? '默认形态 · 将保留'
                        : '将保留此形态'
                      : '合并后移除'}
                  </span>
                  {variant.currentImageUrl ? (
                    <img
                      className={styles.retentionImage}
                      src={variant.currentImageUrl}
                      alt={`${variant.name}现有图片`}
                    />
                  ) : (
                    <div className={styles.noVariantImage}>
                      <span>暂无形态图片</span>
                      <small>
                        {variant.generationStatus === 'GENERATING'
                          ? '图片生成中'
                          : '保留现有提示词，后续可生成图片'}
                      </small>
                    </div>
                  )}
                  <div className={styles.retentionPrompt}>
                    <span>提示词</span>
                    <p>{variant.prompt || '未填写提示词'}</p>
                  </div>
                  <p className={styles.retentionEpisodes}>
                    出镜集数：{episodeLabel(variant.episodeNumbers)}
                  </p>
                </div>
              ))}
            </Radio.Group>
            <div className={styles.mergeImpact}>
              <h4>合并后将发生什么</h4>
              <p>
                出镜集数合并为：{episodeLabel(preflight.mergedEpisodeNumbers)}
              </p>
              <p>涉及分镜：{preflight.affectedStoryboardCount} 个</p>
              <ul>
                <li>出镜绑定迁移至保留形态，来源形态在迁移后移除。</li>
                <li>
                  保留所选形态的现有图片和提示词，已有生成媒体历史仍保留。
                </li>
                <li>
                  {preflight.voiceConflictCount
                    ? `${preflight.voiceConflictCount} 处音色设置冲突，保留目标形态已有设置。`
                    : '音色设置无冲突；发生冲突时保留目标形态已有设置。'}
                </li>
              </ul>
            </div>
            {!preflight.canMerge ? (
              <p role="alert" className={styles.resolutionError}>
                {preflight.disabledReason || '此组当前无法合并，请重新检查。'}
              </p>
            ) : null}
          </>
        ) : null}
      </Modal>
    </div>
  );
}
