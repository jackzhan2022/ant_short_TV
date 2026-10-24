import { CheckOutlined, CopyOutlined, ScanOutlined } from '@ant-design/icons';
import { Alert, App, Button, Input, Modal } from 'antd';
import { useEffect, useRef, useState } from 'react';
import PointEstimateButton from '@/components/PointEstimateButton';
import { isPointEstimateCancelled } from '@/services/point-estimate';
import styles from './CharacterPromptSimilarity.module.css';
import {
  applyCharacterSimilarityPrompt,
  type CharacterSimilarityStatus,
  queryCharacterSimilarity,
  submitCharacterSimilarity,
} from './character-similarity-service';

export default function CharacterPromptSimilarity({
  projectId,
  characterCount,
  disabled = false,
  onApplied,
}: {
  projectId: number;
  characterCount: number;
  disabled?: boolean;
  onApplied?: () => Promise<unknown>;
}) {
  const { message } = App.useApp();
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [readError, setReadError] = useState<string>();
  const [status, setStatus] = useState<CharacterSimilarityStatus>();
  const [revision, setRevision] = useState(0);
  const [selectedPair, setSelectedPair] = useState('');
  const [selectedCharacter, setSelectedCharacter] = useState<number>();
  const [drafts, setDrafts] = useState<Record<number, string>>({});
  const [applied, setApplied] = useState<number[]>([]);
  const [applying, setApplying] = useState(false);
  const [showOriginal, setShowOriginal] = useState(false);
  const [showReview, setShowReview] = useState(false);
  const submittingRef = useRef(false);
  const applyingRef = useRef(false);
  const reportId = useRef<number | undefined>(undefined);
  const busy = status?.status === 'PENDING' || status?.status === 'RUNNING';

  useEffect(() => {
    if (!open) return;
    let active = true;
    let timer: ReturnType<typeof setTimeout> | undefined;
    setLoading(true);
    const load = async () => {
      try {
        const response = await queryCharacterSimilarity(projectId);
        if (!active) return;
        if (response.data.id !== reportId.current) {
          reportId.current = response.data.id;
          setSelectedPair('');
          setSelectedCharacter(undefined);
          setDrafts({});
          setApplied([]);
          setShowOriginal(false);
          setShowReview(false);
        }
        setStatus(response.data);
        setReadError(undefined);
        if (['PENDING', 'RUNNING'].includes(response.data.status))
          timer = setTimeout(load, 2500);
      } catch (error) {
        if (active)
          setReadError(
            error instanceof Error ? error.message : '检查结果读取失败',
          );
      } finally {
        if (active) setLoading(false);
      }
    };
    void load();
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [projectId, open, revision]);

  const submit = async () => {
    if (submittingRef.current || busy || disabled) return;
    submittingRef.current = true;
    setSubmitting(true);
    try {
      const response = await submitCharacterSimilarity(projectId);
      if (!response.data.id) throw new Error('检查任务未返回编号');
      setStatus({
        status: response.data.status || 'PENDING',
        executionId: response.data.id,
      });
      setRevision((value) => value + 1);
    } catch (error) {
      if (!isPointEstimateCancelled(error))
        message.error(
          error instanceof Error ? error.message : '相似度检查提交失败',
        );
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  };

  const characters = new Map(
    (status?.characters ?? []).map((character) => [character.id, character]),
  );
  const name = (id: number) => characters.get(id)?.name || `角色 #${id}`;
  const analysis = status?.analysis;
  const proposals = analysis?.revisions ?? [];
  const pair =
    analysis?.pairs.find(
      (item) => `${item.leftId}-${item.rightId}` === selectedPair,
    ) ?? analysis?.pairs[0];
  const pairIds = pair ? [pair.leftId, pair.rightId] : [];
  const proposedId = proposals.find((item) =>
    pairIds.includes(item.characterId),
  )?.characterId;
  const characterId =
    selectedCharacter && pairIds.includes(selectedCharacter)
      ? selectedCharacter
      : (proposedId ?? pair?.leftId);
  const proposal = proposals.find((item) => item.characterId === characterId);
  const original = characters.get(characterId ?? 0)?.prompt || '';
  const draft = characterId
    ? (drafts[characterId] ?? proposal?.prompt ?? original)
    : '';
  const isApplied = applied.includes(characterId ?? 0);
  const comparedCount =
    status?.characters?.filter((character) => character.prompt.trim()).length ??
    0;

  const copyOriginal = showOriginal || !proposal;

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(copyOriginal ? original : draft);
      message.success(copyOriginal ? '原提示词已复制' : '修改稿已复制');
    } catch {
      message.error('复制失败，可以选中文本后手动复制。');
    }
  };
  const apply = async () => {
    if (
      !status?.id ||
      !characterId ||
      !proposal ||
      applyingRef.current ||
      disabled ||
      !draft.trim()
    )
      return;
    applyingRef.current = true;
    setApplying(true);
    try {
      await applyCharacterSimilarityPrompt(
        projectId,
        status.id,
        characterId,
        draft,
      );
      setApplied((ids) => [...new Set([...ids, characterId])]);
      message.success(`已更新${name(characterId)}的主体提示词`);
      try {
        await onApplied?.();
      } catch {
        message.warning('提示词已应用，但页面刷新失败，请重新加载。');
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '修改稿应用失败');
    } finally {
      applyingRef.current = false;
      setApplying(false);
    }
  };

  const applyButton = proposal ? (
    <Button
      aria-label={isApplied ? '已应用' : `应用到${name(characterId ?? 0)}`}
      type="primary"
      loading={applying}
      disabled={
        disabled ||
        submitting ||
        busy ||
        isApplied ||
        !draft.trim() ||
        draft === original
      }
      onClick={() => void apply()}
    >
      {isApplied ? '已应用' : `应用到${name(characterId ?? 0)}`}
    </Button>
  ) : null;

  return (
    <>
      <Button
        aria-label="提示词相似度检查"
        size="small"
        type="text"
        icon={<ScanOutlined />}
        disabled={disabled || characterCount < 2}
        onClick={() => setOpen(true)}
      >
        提示词相似度检查
      </Button>
      <Modal
        open={open}
        onCancel={() => setOpen(false)}
        width="min(1280px, calc(100vw - 32px))"
        className={styles.modal}
        style={{ top: 16, paddingBottom: 0 }}
        styles={{
          container: {
            height: 'min(900px, calc(100dvh - 32px))',
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
            padding: 'clamp(12px, 2.4vw, 24px)',
          },
          header: { flex: '0 0 auto', marginBottom: 0 },
          body: {
            flex: '1 1 0',
            minHeight: 0,
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
          },
          footer: { flex: '0 0 auto', marginTop: 12 },
        }}
        title={
          <div className={styles.heading}>
            <span className={styles.headingIcon}>
              <ScanOutlined />
            </span>
            <div>
              <div className={styles.title}>角色相似度检查</div>
            </div>
          </div>
        }
        footer={
          <div className={styles.footer}>
            <span>应用后需重新生成图片</span>
            <div>
              <Button onClick={() => setOpen(false)}>关闭</Button>
              <PointEstimateButton
                className={styles.recheckButton}
                operations={[
                  {
                    url: `/api/projects/${projectId}/character-prompt-similarity`,
                  },
                ]}
                loading={submitting}
                disabled={
                  disabled ||
                  characterCount < 2 ||
                  busy ||
                  applying ||
                  loading ||
                  Boolean(readError)
                }
                onClick={() => void submit()}
              >
                {busy ? '正在检查' : analysis ? '重新检查' : '开始检查'}
              </PointEstimateButton>
              {applyButton}
            </div>
          </div>
        }
      >
        {readError ? (
          <Alert
            type="error"
            title={readError}
            className={styles.notice}
            action={
              <Button onClick={() => setRevision((v) => v + 1)}>
                重新读取
              </Button>
            }
          />
        ) : null}
        {status?.stale ? (
          <Alert
            type="warning"
            showIcon
            className={styles.notice}
            title="设定已变化，请重新检查。"
          />
        ) : null}
        {busy ? (
          <Alert
            type="info"
            showIcon
            className={styles.notice}
            title="正在检查，关闭后仍会继续。"
          />
        ) : null}
        {['FAILED', 'CANCELED', 'TIMED_OUT'].includes(status?.status || '') ? (
          <Alert
            type="error"
            className={styles.notice}
            title={
              status?.status === 'CANCELED' ? '检查已取消' : '本次检查未完成'
            }
            description={status?.errorMessage}
          />
        ) : null}
        {analysis ? (
          <>
            <div className={styles.overview}>
              <span>
                角色 <b>{comparedCount}</b>
              </span>
              <span>
                相似 <b>{analysis.pairs.length}</b>
              </span>
              <span>
                修改稿 <b>{proposals.length}</b>
              </span>
              <span>
                待补全 <b>{analysis.insufficient.length}</b>
              </span>
              {analysis.hasMore ? (
                <span className={styles.moreHint}>展示前30组</span>
              ) : null}
            </div>
            <div className={styles.workspace}>
              <aside className={styles.sidebar} aria-label="相似角色列表">
                <div className={styles.sectionLabel}>相似角色对</div>
                <div className={styles.pairList}>
                  {analysis.pairs.map((item) => {
                    const key = `${item.leftId}-${item.rightId}`;
                    const active =
                      pair?.leftId === item.leftId &&
                      pair?.rightId === item.rightId;
                    return (
                      <button
                        type="button"
                        key={key}
                        className={`${styles.pairButton} ${active ? styles.active : ''}`}
                        aria-pressed={active}
                        onClick={() => {
                          setSelectedPair(key);
                          setSelectedCharacter(undefined);
                          setShowOriginal(false);
                          setShowReview(false);
                        }}
                      >
                        <span className={styles.pairNames}>
                          {name(item.leftId)} / {name(item.rightId)}
                        </span>
                        <span className={styles.score}>{item.score} 分</span>
                      </button>
                    );
                  })}
                </div>
              </aside>
              <section className={styles.detail} aria-label="角色提示词编辑">
                {pair ? (
                  <>
                    <fieldset
                      className={styles.roleBar}
                      aria-label="选择要调整的角色"
                    >
                      <span className={styles.sectionLabel}>角色</span>
                      <div className={styles.characterTabs}>
                        {pairIds.map((id) => (
                          <button
                            type="button"
                            key={id}
                            aria-pressed={characterId === id}
                            className={
                              characterId === id ? styles.characterActive : ''
                            }
                            onClick={() => {
                              setSelectedCharacter(id);
                              setShowOriginal(false);
                              setShowReview(false);
                            }}
                          >
                            {name(id)}
                          </button>
                        ))}
                      </div>
                    </fieldset>
                    {!proposal && !showReview ? (
                      <div className={styles.noDraft}>
                        {proposals.length
                          ? '暂无修改稿，可保留原文。'
                          : analysis.revisions === undefined
                            ? '报告未含修改稿，请重新检查。'
                            : '缺少脸型字段，请补全后重新检查。'}
                      </div>
                    ) : null}
                    <div className={styles.viewToolbar}>
                      <fieldset
                        className={styles.viewTabs}
                        aria-label="查看内容"
                      >
                        {proposal ? (
                          <button
                            type="button"
                            className={
                              !showReview && !showOriginal
                                ? styles.viewActive
                                : ''
                            }
                            aria-pressed={!showReview && !showOriginal}
                            onClick={() => {
                              setShowOriginal(false);
                              setShowReview(false);
                            }}
                          >
                            修改稿
                          </button>
                        ) : null}
                        <button
                          type="button"
                          className={
                            !showReview && copyOriginal ? styles.viewActive : ''
                          }
                          aria-pressed={!showReview && copyOriginal}
                          onClick={() => {
                            setShowOriginal(true);
                            setShowReview(false);
                          }}
                        >
                          原文
                        </button>
                        <button
                          type="button"
                          className={showReview ? styles.viewActive : ''}
                          aria-pressed={showReview}
                          onClick={() => setShowReview(true)}
                        >
                          检查说明
                        </button>
                      </fieldset>
                      {!showReview ? (
                        <Button
                          size="small"
                          type="text"
                          icon={<CopyOutlined />}
                          aria-label={copyOriginal ? '复制原文' : '复制修改稿'}
                          onClick={() => void copy()}
                        >
                          复制
                        </Button>
                      ) : null}
                    </div>
                    <div className={styles.promptEditor} hidden={showReview}>
                      <Input.TextArea
                        key={`${characterId}-${copyOriginal}`}
                        aria-label={
                          copyOriginal ? '原始角色提示词' : '修改后的角色提示词'
                        }
                        className={styles.prompt}
                        value={copyOriginal ? original : draft}
                        readOnly={copyOriginal || isApplied}
                        rows={8}
                        style={{ height: '100%', minHeight: 0, resize: 'none' }}
                        maxLength={8000}
                        onChange={(event) =>
                          characterId &&
                          setDrafts((current) => ({
                            ...current,
                            [characterId]: event.target.value,
                          }))
                        }
                      />
                    </div>
                    <section
                      className={styles.reviewPanel}
                      hidden={!showReview}
                      aria-label="角色差异说明"
                    >
                      <div className={styles.reviewHeader}>
                        <h3>
                          {name(pair.leftId)} 与 {name(pair.rightId)}
                        </h3>
                        <span className={styles.risk}>
                          {pair.score} 分 ·{' '}
                          {pair.score >= 80 ? '高度相似' : '部分相似'}
                        </span>
                      </div>
                      <div className={styles.reviewNote}>
                        仅比较提示词设定，不代表图片相似度。
                      </div>
                      <div className={styles.findings}>
                        <div>
                          <h4>重合特征</h4>
                          <ul>
                            {pair.sharedFeatures.map((value) => (
                              <li key={value}>{value}</li>
                            ))}
                          </ul>
                        </div>
                        <div>
                          <h4>已有差异</h4>
                          {pair.differences.length ? (
                            <ul>
                              {pair.differences.map((value) => (
                                <li key={value}>{value}</li>
                              ))}
                            </ul>
                          ) : (
                            <p>未报告明确差异</p>
                          )}
                        </div>
                      </div>
                      <div className={styles.reviewSection}>
                        <h4>调整建议</h4>
                        <ul>
                          {pair.suggestions.map((value) => (
                            <li key={value}>{value}</li>
                          ))}
                        </ul>
                      </div>
                      {proposal ? (
                        <div className={styles.reviewSection}>
                          <h4>{name(characterId ?? 0)} · 脸部对照</h4>
                          <div className={styles.faceComparison}>
                            <div>
                              <span>调整前</span>
                              <p>{proposal.originalFace}</p>
                            </div>
                            <div>
                              <span>调整后</span>
                              <p>{proposal.face}</p>
                            </div>
                          </div>
                          {proposal.changes.length ? (
                            <div className={styles.changes}>
                              {proposal.changes.map((value) => (
                                <span key={value}>
                                  <CheckOutlined /> {value}
                                </span>
                              ))}
                            </div>
                          ) : null}
                        </div>
                      ) : null}
                      {analysis.insufficient.length ? (
                        <div className={styles.reviewSection}>
                          <h4>待补全 · {analysis.insufficient.length}</h4>
                          {analysis.insufficient.map((issue) => (
                            <div
                              className={styles.issue}
                              key={issue.characterId}
                            >
                              <span>{name(issue.characterId)}</span>
                              <p>{issue.reason}</p>
                            </div>
                          ))}
                        </div>
                      ) : null}
                    </section>
                  </>
                ) : (
                  <div className={styles.empty}>
                    <CheckOutlined />
                    <h3>未发现相似角色</h3>
                    <p>{analysis.summary}</p>
                  </div>
                )}
              </section>
            </div>
          </>
        ) : !busy && !readError ? (
          <div className={styles.empty}>
            <ScanOutlined />
            <h3>{loading ? '加载中…' : '暂无检查结果'}</h3>
            <p>检查后生成相似结果与修改稿。</p>
          </div>
        ) : null}
      </Modal>
    </>
  );
}
