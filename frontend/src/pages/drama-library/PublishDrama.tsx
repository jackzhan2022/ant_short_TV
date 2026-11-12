import { useIntl } from '@umijs/max';
import {
  Alert,
  App,
  Button,
  Form,
  InputNumber,
  Modal,
  Space,
  Switch,
  Typography,
} from 'antd';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  listReleases,
  type Miniapp,
  type PublishOptions,
  type PublishPreflight,
  preflightDrama,
  publishDrama,
} from './service';

export default function PublishDrama({
  dramaId,
  app,
  episodeIds,
  onClose,
  onPublished,
}: {
  dramaId: number;
  app: Miniapp;
  episodeIds: number[];
  onClose: () => void;
  onPublished: () => Promise<void>;
}) {
  const intl = useIntl();
  const t = (key: string, text: string) =>
    intl.formatMessage({ id: `drama.${key}`, defaultMessage: text });
  const { message } = App.useApp();
  const [form] = Form.useForm<PublishOptions>();
  const [result, setResult] = useState<PublishPreflight>();
  const [busy, setBusy] = useState(false);
  const [settingsStatus, setSettingsStatus] = useState<
    'loading' | 'ready' | 'failed'
  >('loading');
  const [currentReleaseId, setCurrentReleaseId] = useState<number>();
  const settingsGeneration = useRef(0);
  const alive = useRef(true);
  const revision = useRef(0);
  const working = useRef(false);
  const selectionKey = episodeIds.join(',');
  const previousSelection = useRef(selectionKey);
  useEffect(() => {
    if (previousSelection.current !== selectionKey) {
      previousSelection.current = selectionKey;
      revision.current++;
      setResult(undefined);
    }
  }, [selectionKey]);
  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
      revision.current++;
    };
  }, []);
  const loadSettings = useCallback(async () => {
    const generation = ++settingsGeneration.current;
    revision.current++;
    setResult(undefined);
    setSettingsStatus('loading');
    try {
      const releases = await listReleases(app.id);
      if (!alive.current || generation !== settingsGeneration.current) return;
      const current = releases.find(
        (release) => release.dramaId === dramaId && release.published,
      );
      form.setFieldsValue({
        sortOrder: current?.sortOrder ?? 0,
        recommended: current?.recommended ?? false,
      });
      setCurrentReleaseId(current?.id);
      setSettingsStatus('ready');
    } catch {
      if (alive.current && generation === settingsGeneration.current)
        setSettingsStatus('failed');
    }
  }, [app.id, dramaId, form]);
  useEffect(() => {
    void loadSettings();
    return () => {
      settingsGeneration.current++;
    };
  }, [loadSettings]);
  const check = async () => {
    if (working.current || settingsStatus !== 'ready') return;
    const values = await form.validateFields();
    working.current = true;
    setBusy(true);
    setResult(undefined);
    const version = ++revision.current;
    try {
      const response = await preflightDrama(
        app.id,
        dramaId,
        episodeIds,
        values,
      );
      if (alive.current && revision.current === version) setResult(response);
    } catch {
      /* 请求层显示业务错误，失败时保持发布禁用。 */
    } finally {
      working.current = false;
      if (alive.current) setBusy(false);
    }
  };
  const publish = async () => {
    if (!result?.valid || working.current || settingsStatus !== 'ready') return;
    working.current = true;
    setBusy(true);
    try {
      const values = await form.validateFields();
      const release = await publishDrama(app.id, dramaId, episodeIds, values);
      if (!alive.current) return;
      setResult(undefined);
      message.success(
        `${t('contentVersion', '内容版本')} #${release.id} ${t('published', '已上架')}`,
      );
      await onPublished();
      if (alive.current) onClose();
    } catch {
      if (alive.current) setResult(undefined);
    } finally {
      working.current = false;
      if (alive.current) setBusy(false);
    }
  };
  return (
    <Modal
      open
      width={640}
      title={`${app.name} · ${t('publishPreflight', '发布预检')}`}
      onCancel={onClose}
      closable={!busy}
      mask={{ closable: !busy }}
      keyboard={!busy}
      footer={
        <Space>
          <Button disabled={busy} onClick={onClose}>
            {t('cancel', '取消')}
          </Button>
          <Button
            loading={busy}
            disabled={settingsStatus !== 'ready'}
            onClick={() => void check().catch(() => undefined)}
          >
            {t('runPreflight', '执行预检')}
          </Button>
          <Button
            type="primary"
            disabled={!result?.valid || busy || settingsStatus !== 'ready'}
            onClick={() => void publish().catch(() => undefined)}
          >
            {t('confirmPublish', '确认发布')}
          </Button>
        </Space>
      }
    >
      <Space orientation="vertical" style={{ width: '100%' }}>
        <Typography.Paragraph>
          {t(
            'snapshotNotice',
            '所选单集与剧集元数据将生成新的内容版本。只更新当前目标小程序，旧版本保留在发布记录中。',
          )}{' '}
          ({episodeIds.length})
        </Typography.Paragraph>
        {settingsStatus === 'loading' && (
          <Typography.Text>
            {t('loadingPublishSettings', '正在加载当前发布设置…')}
          </Typography.Text>
        )}
        {settingsStatus === 'failed' && (
          <Alert
            type="error"
            title={t('publishSettingsFailed', '当前发布设置加载失败，请重试')}
            action={
              <Button onClick={() => void loadSettings()}>
                {t('retryPublishSettings', '重试加载设置')}
              </Button>
            }
          />
        )}
        {settingsStatus === 'ready' && currentReleaseId != null && (
          <Typography.Text>
            {t(
              'currentReleaseSettings',
              '已沿用当前在架内容版本的排序及推荐设置',
            )}
            ：#{currentReleaseId}
          </Typography.Text>
        )}
        <Form
          form={form}
          layout="vertical"
          initialValues={{ sortOrder: 0, recommended: false }}
          disabled={busy || settingsStatus !== 'ready'}
          onValuesChange={() => {
            revision.current++;
            setResult(undefined);
          }}
        >
          <Form.Item
            name="sortOrder"
            label={t('sortOrder', '目录排序')}
            rules={[
              { required: true, type: 'number', min: -100000, max: 100000 },
            ]}
            extra={t('sortHint', '数字越小越靠前，仅对当前小程序生效')}
          >
            <InputNumber min={-100000} max={100000} precision={0} />
          </Form.Item>
          <Form.Item
            name="recommended"
            label={t('recommended', '首页推荐')}
            valuePropName="checked"
          >
            <Switch />
          </Form.Item>
        </Form>
        {result && (
          <>
            <Alert
              type={result.valid ? 'success' : 'error'}
              showIcon
              title={
                result.valid
                  ? t('preflightPassed', '预检通过，请确认以下差异')
                  : t('preflightFailed', '预检未通过，修复后重新预检')
              }
            />
            {result.errors.map((error) => (
              <Alert key={error} type="error" title={error} />
            ))}
            {result.warnings.map((warning) => (
              <Alert key={warning} type="warning" title={warning} />
            ))}
            <Typography.Text>
              {t('previousVersion', '上一内容版本')}：
              {result.previousReleaseId == null
                ? t('firstRelease', '首次发布')
                : `#${result.previousReleaseId}`}
            </Typography.Text>
            {[
              ['added', t('addedEpisodes', '新增集号'), result.added],
              ['removed', t('removedEpisodes', '移除集号'), result.removed],
              ['changed', t('changedEpisodes', '变更集号'), result.changed],
            ].map(([key, label, numbers]) => (
              <Typography.Text key={String(key)}>
                {String(label)}：{(numbers as number[]).join('、') || '—'}
              </Typography.Text>
            ))}
            <Typography.Text>
              {t('metadataChanged', '剧集元数据变更')}：
              {result.metadataChanged ? t('yes', '是') : t('no', '否')}
            </Typography.Text>
          </>
        )}
        <Alert
          type="info"
          title={t('wechatFormalRelease', '正式版需在微信后台提交审核并发布')}
          description={t(
            'wechatFormalReleaseHint',
            '本操作只发布本系统内容目录。体验版验证后，请在微信公众平台完成小程序代码审核，审核通过后手动发布正式版。',
          )}
        />
      </Space>
    </Modal>
  );
}
