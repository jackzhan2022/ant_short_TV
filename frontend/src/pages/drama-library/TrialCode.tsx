import { useAccess, useIntl } from '@umijs/max';
import { Alert, Button, Checkbox, Input, Modal, Select, Space } from 'antd';
import { useEffect, useRef, useState } from 'react';
import {
  type CodeTemplate,
  canManageCode,
  commitCode,
  getCodeTemplates,
  getTrialQrCode,
  type Miniapp,
} from './service';

export default function TrialCode(props: {
  app: Miniapp;
  tenantId: number;
  onClose: () => void;
}) {
  const access = useAccess();
  if (!access.canManageMiniapps || !canManageCode(props.app)) return null;
  return (
    <TrialCodeContent
      key={`${props.tenantId}:${props.app.id}:${props.app.appid}`}
      {...props}
    />
  );
}

function TrialCodeContent({
  app,
  tenantId,
  onClose,
}: {
  app: Miniapp;
  tenantId: number;
  onClose: () => void;
}) {
  const intl = useIntl();
  const t = (key: string, defaultMessage: string) =>
    intl.formatMessage({ id: `drama.code.${key}`, defaultMessage });
  const [templates, setTemplates] = useState<CodeTemplate[]>([]);
  const [templateId, setTemplateId] = useState<number>();
  const [version, setVersion] = useState('');
  const [description, setDescription] = useState('');
  const [confirmed, setConfirmed] = useState(false);
  const [status, setStatus] = useState('');
  const [error, setError] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [path, setPath] = useState('');
  const [qrBusy, setQrBusy] = useState(false);
  const [qrError, setQrError] = useState(false);
  const [url, setUrl] = useState<string>();
  const controller = useRef<AbortController | null>(null);
  const submitted = useRef(false);
  const qrLock = useRef(false);
  const objectUrl = useRef<string | undefined>(undefined);
  useEffect(() => {
    const current = new AbortController();
    controller.current = current;
    void getCodeTemplates(tenantId, current.signal)
      .then((rows) => {
        if (!current.signal.aborted) setTemplates(rows);
      })
      .catch(() => {
        if (!current.signal.aborted) setError(true);
      })
      .finally(() => {
        if (!current.signal.aborted) setLoading(false);
      });
    return () => {
      current.abort();
      if (objectUrl.current) URL.revokeObjectURL(objectUrl.current);
      objectUrl.current = undefined;
    };
  }, [tenantId]);
  const template = templates.find((item) => item.templateId === templateId);
  const valid = Boolean(
    template &&
      version.trim() &&
      version.length <= 64 &&
      description.trim() &&
      description.length <= 1024 &&
      confirmed,
  );
  const upload = async () => {
    const current = controller.current;
    if (
      !current ||
      current.signal.aborted ||
      submitted.current ||
      !valid ||
      templateId === undefined
    )
      return;
    submitted.current = true;
    setBusy(true);
    setError(false);
    try {
      const result = await commitCode(
        tenantId,
        app.id,
        {
          templateId,
          version: version.trim(),
          description: description.trim(),
        },
        current.signal,
      );
      if (!current.signal.aborted) setStatus(result);
    } catch {
      if (!current.signal.aborted) {
        setError(true);
        setStatus('REJECTED');
      }
    } finally {
      if (!current.signal.aborted) setBusy(false);
    }
  };
  const loadQr = async () => {
    const current = controller.current;
    if (!current || current.signal.aborted || qrLock.current || busy) return;
    qrLock.current = true;
    setQrBusy(true);
    setQrError(false);
    if (objectUrl.current) URL.revokeObjectURL(objectUrl.current);
    objectUrl.current = undefined;
    setUrl(undefined);
    try {
      const blob = await getTrialQrCode(tenantId, app.id, path, current.signal);
      if (current.signal.aborted) return;
      objectUrl.current = URL.createObjectURL(blob);
      setUrl(objectUrl.current);
    } catch {
      if (!current.signal.aborted) setQrError(true);
    } finally {
      if (!current.signal.aborted) {
        qrLock.current = false;
        setQrBusy(false);
      }
    }
  };
  return (
    <Modal
      open
      destroyOnHidden
      title={t('title', '体验版管理')}
      footer={null}
      closable={!busy}
      mask={{ closable: !busy }}
      keyboard={!busy}
      onCancel={onClose}
    >
      <Space orientation="vertical" style={{ width: '100%' }}>
        <div>
          {app.name} · AppID: {app.appid}
        </div>
        <Alert
          type="warning"
          showIcon
          title={t(
            'overwrite',
            '上传将覆盖目标小程序的开发版本，不影响正式版；不提交审核、不发布上线。',
          )}
        />
        {error && (
          <Alert
            type="error"
            title={t(
              'failed',
              '请求失败，请核对权限、授权状态和模板。上传不会自动重试。',
            )}
          />
        )}
        {status === 'UNKNOWN' && (
          <Alert
            type="warning"
            showIcon
            title={t(
              'unknown',
              'UNKNOWN：上传结果未知，可能已执行。网络中断或超时不能证明失败，请人工核实微信开发版本，勿自动重试或重复上传。',
            )}
          />
        )}
        {status === 'SUCCEEDED' && (
          <Alert
            type="success"
            title={t(
              'success',
              '代码上传成功，可获取体验版二维码；正式版未改变。',
            )}
          />
        )}
        <Select
          style={{ width: '100%' }}
          aria-label={t('template', '普通模板')}
          placeholder={t('template', '普通模板')}
          loading={loading}
          disabled={loading || busy || Boolean(status)}
          value={templateId}
          onChange={setTemplateId}
          options={templates.map((item) => ({
            value: item.templateId,
            label: `#${item.templateId} · ${item.version} · ${item.sourceName}`,
          }))}
        />
        {!loading && !error && templates.length === 0 && (
          <Alert
            type="info"
            title={t('empty', '暂无普通模板，请先在服务端对应的模板库配置。')}
          />
        )}
        {template && (
          <div>
            {t('source', '模板来源 / 来源版本')}：{template.sourceName} (
            {template.sourceAppid}) / {template.version}
            <p>{template.description}</p>
          </div>
        )}
        <Input
          aria-label={t('version', '上传版本号')}
          placeholder={t('version', '上传版本号')}
          maxLength={64}
          value={version}
          disabled={busy || Boolean(status)}
          onChange={(event) => setVersion(event.target.value)}
        />
        <Input.TextArea
          aria-label={t('description', '上传说明')}
          placeholder={t('description', '上传说明')}
          maxLength={1024}
          value={description}
          disabled={busy || Boolean(status)}
          onChange={(event) => setDescription(event.target.value)}
        />
        <Checkbox
          checked={confirmed}
          disabled={busy || Boolean(status)}
          onChange={(event) => setConfirmed(event.target.checked)}
        >
          {t(
            'confirm',
            '我确认目标 AppID，并同意覆盖其开发版本（不影响正式版）',
          )}
          ：{app.appid}
        </Checkbox>
        <Button
          type="primary"
          loading={busy}
          disabled={!valid || Boolean(status) || qrBusy}
          onClick={() => void upload()}
        >
          {t('upload', '确认上传一次')}
        </Button>
        <Input
          aria-label={t('path', '体验版页面路径（可选）')}
          placeholder={t('path', '体验版页面路径（可选）')}
          maxLength={1024}
          value={path}
          disabled={qrBusy}
          onChange={(event) => setPath(event.target.value)}
        />
        <Button
          loading={qrBusy}
          disabled={
            busy ||
            path.length > 1024 ||
            Array.from(path).some((char) => {
              const code = char.charCodeAt(0);
              return code < 32 || (code >= 127 && code <= 159);
            })
          }
          onClick={() => void loadQr()}
        >
          {t('qr', '获取体验版二维码')}
        </Button>
        {qrError && (
          <Alert
            type="error"
            title={t(
              'qrError',
              '二维码获取失败或响应不是有效图片，请核实授权及体验版状态。',
            )}
          />
        )}
        {url && (
          <img
            src={url}
            alt={t('qrAlt', '体验版二维码')}
            width={240}
            height={240}
            onError={() => {
              if (objectUrl.current) URL.revokeObjectURL(objectUrl.current);
              objectUrl.current = undefined;
              setUrl(undefined);
              setQrError(true);
            }}
          />
        )}
      </Space>
    </Modal>
  );
}
