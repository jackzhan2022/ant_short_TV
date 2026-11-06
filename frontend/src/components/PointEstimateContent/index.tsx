import {
  AppstoreOutlined,
  CheckCircleOutlined,
  DownOutlined,
  FileTextOutlined,
  InfoCircleOutlined,
  VideoCameraOutlined,
} from '@ant-design/icons';
import { useId, useState } from 'react';
import type { PointEstimate } from '@/services/point-estimate';
import { formatPoints } from '@/utils/points';
import styles from './index.module.css';

type Detail = { label: string; points: number; count: number };
type Stage = {
  key: string;
  title: string;
  kind: 'script' | 'episodes' | 'storyboards' | 'other';
  points: number;
  items: Detail[];
};

function groupLines(lines: PointEstimate['lines']): Stage[] {
  const stages = new Map<string, Stage>();
  for (const line of lines) {
    const kind = /^(剧本全局理解|剧集智能拆分)$/.test(line.label)
      ? 'script'
      : /^(第\d+集(?:概要|资产识别)|分集(?:概要|资产识别|资产提取及提示词生成))/.test(
            line.label,
          )
        ? 'episodes'
        : /^生成分镜(?:（含自动分镜）)?$/.test(line.label)
          ? 'storyboards'
          : 'other';
    const key = kind === 'other' ? `label:${line.label}` : kind;
    const stage = stages.get(key) || {
      key,
      title:
        kind === 'script'
          ? '剧本分析'
          : kind === 'episodes'
            ? '剧集处理'
            : kind === 'storyboards'
              ? '分镜生成'
              : line.label,
      kind,
      points: 0,
      items: [],
    };
    stage.points += Number(line.points);
    const existing = stage.items.find((item) => item.label === line.label);
    if (existing) {
      existing.points += Number(line.points);
      existing.count += 1;
    } else {
      stage.items.push({
        label: line.label,
        points: Number(line.points),
        count: 1,
      });
    }
    stages.set(key, stage);
  }
  return [...stages.values()];
}

function stageDescription(stage: Stage) {
  if (stage.kind === 'script') {
    return stage.items
      .map((item) => (item.label === '剧本全局理解' ? '全局理解' : '剧集拆分'))
      .join(' · ');
  }
  if (stage.kind === 'episodes') {
    return [
      stage.items.some((item) => item.label.includes('概要')) ? '剧集概要' : '',
      stage.items.some((item) => item.label.includes('资产')) ? '资产识别' : '',
    ]
      .filter(Boolean)
      .join(' · ');
  }
  const count = stage.items.reduce((sum, item) => sum + item.count, 0);
  return [
    stage.items.some((item) => item.label.includes('自动分镜'))
      ? '含自动分镜'
      : '',
    count > 1 ? `${count} 项` : '',
  ]
    .filter(Boolean)
    .join(' · ');
}

export default function PointEstimateContent({
  estimate,
}: {
  estimate: PointEstimate;
}) {
  const [expanded, setExpanded] = useState(false);
  const detailsId = useId();
  const stages = groupLines(estimate.lines);
  const notice = estimate.notice ? (
    <p className={styles.notice}>
      <InfoCircleOutlined aria-hidden="true" />
      <span>{estimate.notice}</span>
    </p>
  ) : null;
  return (
    <section className={styles.content} aria-label="积分预估详情">
      <p className={styles.subtitle}>核对积分后，继续本次操作</p>
      <div className={styles.hero}>
        <div className={styles.secondary}>本次消耗</div>
        <div className={styles.total}>
          <strong>
            <output aria-label="本次消耗积分">
              {formatPoints(Math.ceil(estimate.estimatedPoints))}
            </output>
          </strong>
          <span>积分</span>
        </div>
        <div className={styles.balance}>
          <span>
            当前可用{' '}
            <strong>
              <output aria-label="当前可用积分">
                {formatPoints(estimate.availablePoints)}
              </output>
            </strong>
          </span>
          <span
            className={
              estimate.sufficient ? styles.status : styles.insufficient
            }
          >
            {estimate.sufficient ? (
              <CheckCircleOutlined aria-hidden="true" />
            ) : (
              <InfoCircleOutlined aria-hidden="true" />
            )}
            {estimate.sufficient ? '余额充足' : '积分不足'}
          </span>
        </div>
      </div>
      {stages.length ? (
        <>
          <div className={styles.sectionLabel}>费用构成</div>
          <ul className={styles.summary} aria-label="费用构成">
            {stages.map((stage) => (
              <li key={stage.key}>
                <span className={styles.stageIcon} aria-hidden="true">
                  {stage.kind === 'episodes' ? (
                    <AppstoreOutlined />
                  ) : stage.kind === 'storyboards' ? (
                    <VideoCameraOutlined />
                  ) : (
                    <FileTextOutlined />
                  )}
                </span>
                <div className={styles.stageCopy}>
                  <span>{stage.title}</span>
                  {stageDescription(stage) ? (
                    <small>{stageDescription(stage)}</small>
                  ) : null}
                </div>
                <span className={styles.number}>
                  {formatPoints(stage.points)}
                </span>
              </li>
            ))}
          </ul>
          <button
            className={styles.disclosure}
            type="button"
            aria-expanded={expanded}
            aria-controls={detailsId}
            onClick={() => setExpanded(!expanded)}
          >
            <span>{expanded ? '收起费用明细' : '查看费用明细'}</span>
            <DownOutlined aria-hidden="true" rotate={expanded ? 180 : 0} />
          </button>
          {expanded ? (
            <section
              className={styles.details}
              id={detailsId}
              aria-label="费用明细"
            >
              {stages.map((stage) => (
                <div className={styles.detailGroup} key={stage.key}>
                  <div className={styles.detailTitle}>{stage.title}</div>
                  <ul>
                    {stage.items.map((item) => (
                      <li key={item.label}>
                        <span>
                          {item.label}
                          {item.count > 1 ? ` × ${item.count}` : ''}
                        </span>
                        <span className={styles.number}>
                          {formatPoints(item.points)}
                        </span>
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
              <p className={styles.rounding}>
                明细按项取整，与总额可能存在尾差。
              </p>
              {notice}
            </section>
          ) : null}
        </>
      ) : null}
      {estimate.lines.some((line) => !line.trialEligible) ? (
        <div className={styles.videoBalance}>
          <div>
            视频生成可用{' '}
            <strong>
              <output aria-label="视频生成可用积分">
                {formatPoints(estimate.paidAvailablePoints)}
              </output>
            </strong>{' '}
            积分
          </div>
          <p>试用积分不可用于视频生成</p>
        </div>
      ) : null}
      {!estimate.sufficient ? (
        <p className={styles.alert} role="alert">
          可用积分不足，请充值后重试。
        </p>
      ) : null}
      {!stages.length ? notice : null}
    </section>
  );
}
