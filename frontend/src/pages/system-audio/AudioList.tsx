import { PlayCircleOutlined } from '@ant-design/icons';
import { Button, Empty, Input, Pagination } from 'antd';
import { useEffect, useState } from 'react';
import styles from './index.module.css';
import { querySystemAudio, type SystemAudio } from './service';

type Props = {
  selectedId?: number;
  disabled?: boolean;
  onSelect?: (audio: SystemAudio) => void;
};

export default function SystemAudioList({
  selectedId,
  disabled,
  onSelect,
}: Props) {
  const [input, setInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [gender, setGender] = useState('');
  const [ageGroup, setAgeGroup] = useState('');
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [items, setItems] = useState<SystemAudio[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [retry, setRetry] = useState(0);
  const [preview, setPreview] = useState<SystemAudio>();
  const [playbackFailed, setPlaybackFailed] = useState(false);
  const [playbackAttempt, setPlaybackAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setFailed(false);
    setItems([]);
    setTotal(0);
    setPreview(undefined);
    void querySystemAudio(
      {
        keyword: keyword || undefined,
        gender: gender || undefined,
        ageGroup: ageGroup || undefined,
        current: page,
        pageSize: 12,
      },
      controller.signal,
    )
      .then((response) => {
        if (controller.signal.aborted) return;
        if (!response.success) throw new Error(response.errorMessage);
        setItems(response.data.data);
        setTotal(response.data.total);
      })
      .catch(() => {
        if (!controller.signal.aborted) setFailed(true);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [keyword, gender, ageGroup, page, retry]);

  return (
    <div className={onSelect ? styles.picker : undefined}>
      <Input.Search
        aria-label="搜索系统音频"
        placeholder="搜索音频名称或分类"
        value={input}
        disabled={disabled}
        allowClear
        onChange={(event) => {
          setInput(event.target.value);
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
      <div className={styles.filters}>
        <select
          aria-label="筛选性别"
          value={gender}
          disabled={disabled}
          onChange={(event) => {
            setGender(event.target.value);
            setPage(1);
          }}
        >
          <option value="">全部性别</option>
          <option value="男声">男声</option>
          <option value="女声">女声</option>
        </select>
        <select
          aria-label="筛选年龄"
          value={ageGroup}
          disabled={disabled}
          onChange={(event) => {
            setAgeGroup(event.target.value);
            setPage(1);
          }}
        >
          <option value="">全部年龄</option>
          {['少年', '青年', '中年', '老年'].map((age) => (
            <option key={age} value={age}>
              {age}
            </option>
          ))}
        </select>
        {!loading && !failed ? <span>{total} 个音频</span> : null}
      </div>
      {failed ? (
        <div className={styles.empty}>
          <Empty description="音频加载失败" />
          <Button aria-label="重试" onClick={() => setRetry((value) => value + 1)}>重试</Button>
        </div>
      ) : loading ? (
        <div className={styles.empty} role="status">
          正在加载…
        </div>
      ) : !items.length ? (
        <Empty
          description={
            keyword || gender || ageGroup ? '未找到相关音频' : '暂无系统音频'
          }
        />
      ) : (
        <section
          className={styles.list}
          role={onSelect ? 'radiogroup' : 'group'}
          aria-label="系统音频列表"
        >
          {items.map((item) => (
            <div
              key={item.id}
              className={styles.row}
              data-selected={selectedId === item.id}
            >
              {onSelect ? (
                <label>
                  <input
                    type="radio"
                    name="system-audio"
                    checked={selectedId === item.id}
                    disabled={disabled}
                    onChange={() => onSelect(item)}
                  />
                  <span>
                    <strong>{item.name}</strong>
                    <small>
                      {[item.gender, item.ageGroup, item.category]
                        .filter(Boolean)
                        .join(' · ')}
                    </small>
                  </span>
                </label>
              ) : (
                <div className={styles.info}>
                  <strong>{item.name}</strong>
                  <small>
                    {[item.gender, item.ageGroup, item.category]
                      .filter(Boolean)
                      .join(' · ')}
                  </small>
                </div>
              )}
              <span className={styles.duration}>
                {Math.floor(item.durationSeconds / 60)}:
                {String(Math.floor(item.durationSeconds % 60)).padStart(2, '0')}
              </span>
              <Button
                type="text"
                disabled={disabled}
                icon={<PlayCircleOutlined />}
                aria-label={`试听${item.name}`}
                onClick={() => {
                  setPreview(item);
                  setPlaybackFailed(false);
                  setPlaybackAttempt((value) => value + 1);
                }}
              />
            </div>
          ))}
        </section>
      )}
      {!failed && !loading && total > 12 ? (
        <Pagination
          className={styles.pagination}
          current={page}
          pageSize={12}
          total={total}
          disabled={disabled}
          showSizeChanger={false}
          onChange={setPage}
        />
      ) : null}
      {preview ? (
        <div className={styles.player}>
          <span>{preview.name}</span>
          {/* biome-ignore lint/a11y/useMediaCaption: Reference voice samples have no separate transcript files. */}
          <audio
            key={`${preview.id}-${playbackAttempt}`}
            src={preview.previewUrl}
            controls
            autoPlay
            preload="none"
            aria-label={`试听${preview.name}`}
            onError={() => setPlaybackFailed(true)}
          />
          {playbackFailed ? (
            <span role="alert">音频播放失败，请重试或选择其他音频</span>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
