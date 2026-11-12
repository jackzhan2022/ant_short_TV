import { useIntl } from '@umijs/max';
import {
  Alert,
  App,
  Button,
  Descriptions,
  Form,
  Input,
  Modal,
  Space,
} from 'antd';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  type Episode,
  type EpisodePreview,
  getEpisodePreview,
  inspectEpisode,
  updateEpisode,
} from './service';

export default function EpisodeEditor({
  dramaId,
  episode,
  canEdit,
  onClose,
  onSaved,
}: {
  dramaId: number;
  episode: Episode;
  canEdit: boolean;
  onClose: () => void;
  onSaved: () => Promise<void>;
}) {
  const intl = useIntl();
  const t = (key: string, text: string) =>
    intl.formatMessage({ id: `drama.${key}`, defaultMessage: text });
  const { message } = App.useApp();
  const [form] = Form.useForm<{ title: string; posterUrl?: string | null }>();
  const [metadata, setMetadata] = useState(episode);
  const [preview, setPreview] = useState<EpisodePreview>();
  const [previewFailed, setPreviewFailed] = useState(false);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const working = useRef(false);
  const alive = useRef(true);
  const previewVersion = useRef(0);
  const loadPreview = useCallback(async () => {
    const version = ++previewVersion.current;
    setPreviewLoading(true);
    setPreviewFailed(false);
    setPreview(undefined);
    try {
      const response = await getEpisodePreview(dramaId, episode.id);
      if (alive.current && version === previewVersion.current)
        setPreview(response);
    } catch {
      if (alive.current && version === previewVersion.current)
        setPreviewFailed(true);
    } finally {
      if (alive.current && version === previewVersion.current)
        setPreviewLoading(false);
    }
  }, [dramaId, episode.id]);
  useEffect(() => {
    alive.current = true;
    void loadPreview();
    return () => {
      alive.current = false;
      previewVersion.current++;
    };
  }, [loadPreview]);
  const inspect = async () => {
    if (working.current) return;
    working.current = true;
    setBusy(true);
    try {
      const response = await inspectEpisode(dramaId, episode.id);
      if (alive.current) {
        setMetadata(response);
        message.success(t('mediaInspected', '媒体信息已更新'));
        await onSaved();
      }
    } catch {
      /* 请求层显示失败，已编辑的标题和封面保留。 */
    } finally {
      working.current = false;
      if (alive.current) setBusy(false);
    }
  };
  const save = async () => {
    if (working.current || !canEdit) return;
    working.current = true;
    setBusy(true);
    try {
      const values = await form.validateFields();
      await updateEpisode(dramaId, episode.id, {
        title: values.title.trim(),
        posterUrl: values.posterUrl?.trim() || null,
      });
      if (!alive.current) return;
      await onSaved();
      if (alive.current) onClose();
    } catch {
      /* 请求失败时保留输入，允许重试。 */
    } finally {
      working.current = false;
      if (alive.current) setBusy(false);
    }
  };
  return (
    <Modal
      open
      width={760}
      title={`${t('episodeNo', '剧库集号')} ${episode.episodeNo} · ${t('previewEdit', '预览 / 编辑')}`}
      onCancel={onClose}
      closable={!busy}
      keyboard={!busy}
      mask={{ closable: !busy }}
      footer={
        <Space>
          <Button disabled={busy} onClick={onClose}>
            {t('close', '关闭')}
          </Button>
          {canEdit && (
            <Button
              type="primary"
              aria-label={t('save', '保存')}
              loading={busy}
              onClick={() => void save().catch(() => undefined)}
            >
              {t('save', '保存')}
            </Button>
          )}
        </Space>
      }
    >
      <Space orientation="vertical" style={{ width: '100%' }}>
        {previewFailed && (
          <Alert
            type="warning"
            title={t(
              'previewFailed',
              '预览不可用或地址已过期，请刷新预览地址重试',
            )}
          />
        )}
        {preview && (
          <>
            {/* biome-ignore lint/a11y/useMediaCaption: 私有成片预览不提供独立字幕轨道。 */}
            <video
              key={preview.url}
              src={preview.url}
              poster={metadata.posterUrl || undefined}
              controls
              playsInline
              preload="metadata"
              style={{ width: '100%', maxHeight: 360, background: '#000' }}
              onError={() => setPreviewFailed(true)}
            />
            <span>
              {t('previewExpires', '预览地址到期时间')}：{preview.expiresAt}
            </span>
          </>
        )}
        <Button loading={previewLoading} onClick={() => void loadPreview()}>
          {t('refreshPreview', '刷新预览地址')}
        </Button>
        <Descriptions
          size="small"
          column={2}
          items={[
            {
              key: 'resolution',
              label: t('resolution', '分辨率'),
              children:
                metadata.width && metadata.height
                  ? `${metadata.width} × ${metadata.height}`
                  : '—',
            },
            {
              key: 'duration',
              label: t('duration', '时长'),
              children: metadata.durationSeconds
                ? `${metadata.durationSeconds} ${t('seconds', '秒')}`
                : '—',
            },
          ]}
        />
        {canEdit && (
          <Button
            aria-label={t('inspectMedia', '分析媒体信息')}
            loading={busy}
            onClick={() => void inspect()}
          >
            {t('inspectMedia', '分析媒体信息')}
          </Button>
        )}
        <Form
          form={form}
          layout="vertical"
          disabled={!canEdit || busy}
          initialValues={{
            title: episode.title,
            posterUrl: episode.posterUrl ?? '',
          }}
        >
          <Form.Item
            name="title"
            label={t('episodeTitle', '单集标题')}
            rules={[{ required: true, whitespace: true, max: 200 }]}
          >
            <Input maxLength={200} />
          </Form.Item>
          <Form.Item
            name="posterUrl"
            label={t('posterUrl', '单集封面 HTTPS 地址')}
            rules={[
              { max: 1024 },
              {
                type: 'url',
                message: t('httpsRequired', '请输入有效的 HTTPS 地址'),
              },
              {
                pattern: /^https:\/\/\S+$/,
                message: t('httpsRequired', '请输入有效的 HTTPS 地址'),
              },
            ]}
          >
            <Input maxLength={1024} placeholder="https://" />
          </Form.Item>
        </Form>
      </Space>
    </Modal>
  );
}
