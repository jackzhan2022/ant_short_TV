import { CheckOutlined } from '@ant-design/icons';
import { Progress } from 'antd';
import styles from './index.module.css';

export function FlowSteps({ current }: { current: 0 | 1 | 2 }) {
  return (
    <ol className={styles.flowSteps} aria-label="拆剧流程">
      {['整理视频', '生成剧本', '阅读与复制'].map((label, index) => (
        <li
          key={label}
          aria-current={index === current ? 'step' : undefined}
          data-done={index < current}
        >
          <span>{index < current ? <CheckOutlined /> : index + 1}</span>
          {label}
        </li>
      ))}
    </ol>
  );
}

export function EpisodeProgress({
  percent,
  compact = false,
}: {
  percent: number;
  compact?: boolean;
}) {
  const value = Math.round(
    Math.max(0, Math.min(100, Number.isFinite(percent) ? percent : 0)),
  );
  return (
    <div className={compact ? styles.miniProgress : styles.progressOrbit}>
      {!compact && (
        <span className={styles.orbitAnimation} aria-hidden="true" />
      )}
      <Progress
        type="circle"
        percent={value}
        size={compact ? 34 : 136}
        strokeWidth={compact ? 6 : 3}
        status="normal"
        strokeColor="var(--app-color-primary, #5252ff)"
        railColor="var(--app-color-primary-bg, #edeffc)"
        format={() => (
          <span className={styles.progressNumber}>
            {value}
            <small>%</small>
          </span>
        )}
      />
    </div>
  );
}
