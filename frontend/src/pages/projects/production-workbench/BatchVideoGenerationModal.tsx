import { ThunderboltOutlined } from '@ant-design/icons';
import { Button, Checkbox, Empty, Modal, Spin } from 'antd';
import type { StoryboardShot } from './service';

export type BatchVideoCandidate = { storyboard: StoryboardShot; disabledReason?: string };

export default function BatchVideoGenerationModal({
  open, loading, submitting, error, candidates, selectedIds,
  onSelectionChange, onCancel, onRetry, onSubmit,
}: {
  open: boolean;
  loading: boolean;
  submitting: boolean;
  error: boolean;
  candidates: BatchVideoCandidate[];
  selectedIds: number[];
  onSelectionChange: (ids: number[]) => void;
  onCancel: () => void;
  onRetry: () => void;
  onSubmit: () => void;
}) {
  const selectableIds = candidates.filter((item) => !item.disabledReason).map((item) => item.storyboard.id);
  const selected = new Set(selectedIds.filter((id) => selectableIds.includes(id)));
  const allSelected = selectableIds.length > 0 && selected.size === selectableIds.length;
  return (
    <Modal
      title="批量生成视频"
      className="storyboard-batch-video-modal"
      open={open}
      onCancel={onCancel}
      closable={!submitting}
      keyboard={!submitting}
      mask={{ closable: !submitting }}
      width={760}
      centered
      destroyOnHidden
      footer={(
        <div className="storyboard-batch-video-footer">
          <Button
            disabled={loading || submitting || error || !selectableIds.length}
            onClick={() => onSelectionChange(allSelected ? [] : selectableIds)}
          >
            {allSelected ? '取消全选' : '全选'}
          </Button>
          <span className="storyboard-batch-video-count">已选 {selected.size} 个</span>
          <span className="storyboard-batch-video-spacer" />
          <Button disabled={submitting} onClick={onCancel}>取消</Button>
          <Button
            type="primary"
            aria-label="生成所选视频"
            icon={<ThunderboltOutlined />}
            loading={submitting}
            disabled={loading || error || !selected.size}
            onClick={onSubmit}
          >
            生成视频（{selected.size}）
          </Button>
        </div>
      )}
    >
      {loading ? (
        <div className="storyboard-batch-video-empty" role="status"><Spin /><span>正在加载本集分镜…</span></div>
      ) : error ? (
        <div className="storyboard-batch-video-empty" role="alert">
          <span>分镜列表加载失败，请重试。</span><Button onClick={onRetry}>重新加载分镜列表</Button>
        </div>
      ) : candidates.length ? (
        <div className="storyboard-batch-video-grid">
          {candidates.map(({ storyboard, disabledReason }) => {
            const checked = selected.has(storyboard.id);
            const number = storyboard.storyboardNo || storyboard.shotNo;
            return (
              <Checkbox
                key={storyboard.id}
                className={`storyboard-batch-video-option${checked ? ' is-selected' : ''}${disabledReason ? ' is-disabled' : ''}`}
                aria-label={`选择分镜${number}`}
                checked={checked}
                disabled={Boolean(disabledReason) || submitting}
                onChange={(event) => onSelectionChange(event.target.checked
                  ? [...selected, storyboard.id] : [...selected].filter((id) => id !== storyboard.id))}
              >
                <span className="storyboard-batch-video-option-copy">
                  <span>分镜{number}</span>
                  {disabledReason ? <span className="storyboard-batch-video-reason">{disabledReason}</span> : null}
                </span>
              </Checkbox>
            );
          })}
        </div>
      ) : <Empty description="本集暂无分镜" />}
    </Modal>
  );
}
