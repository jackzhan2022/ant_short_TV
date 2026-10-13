import { useIntl } from '@umijs/max';
import { Alert, Button, Modal, Space } from 'antd';
import { useEffect, useRef, useState } from 'react';
import {
  type AuthorizationAvailability,
  type AuthorizationCreated,
  type AuthorizationRequestStatus,
  createAuthorizationRequest,
  getAuthorizationAvailability,
  getAuthorizationRequest,
} from './service';

export function isOfficialAuthorizationUrl(value: string) {
  try {
    const url = new URL(value);
    return (
      url.protocol === 'https:' &&
      url.hostname === 'mp.weixin.qq.com' &&
      !url.port &&
      !url.username &&
      !url.password &&
      url.pathname === '/cgi-bin/componentloginpage' &&
      !url.hash
    );
  } catch {
    return false;
  }
}

const MAX_WAIT = 10 * 60 * 1000;
const POLL_INTERVAL = 2000;

export default function OfficialAuthorization({
  onAuthorized,
}: {
  onAuthorized: () => Promise<void>;
}) {
  const intl = useIntl();
  const t = (key: string, text: string) =>
    intl.formatMessage({
      id: `drama.authorization.${key}`,
      defaultMessage: text,
    });
  const [availability, setAvailability] = useState<AuthorizationAvailability>();
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [created, setCreated] = useState<AuthorizationCreated>();
  const [status, setStatus] = useState<AuthorizationRequestStatus['status']>();
  const [error, setError] = useState(false);
  const generation = useRef(0);
  const availabilityGeneration = useRef(0);
  const timers = useRef<ReturnType<typeof setTimeout>[]>([]);
  const starting = useRef(false);
  const stop = () => {
    generation.current++;
    timers.current.forEach(clearTimeout);
    timers.current = [];
    starting.current = false;
  };
  const refreshAvailability = async () => {
    const current = ++availabilityGeneration.current;
    setLoading(true);
    setError(false);
    try {
      const result = await getAuthorizationAvailability();
      if (current === availabilityGeneration.current) setAvailability(result);
    } catch {
      if (current === availabilityGeneration.current) setError(true);
    } finally {
      if (current === availabilityGeneration.current) setLoading(false);
    }
  };
  // 团队切换由父页面 key 重挂载；失效所有在途结果并清除定时器。
  useEffect(() => {
    void refreshAvailability();
    return () => {
      stop();
      availabilityGeneration.current++;
    };
  }, []);

  const start = async () => {
    if (
      starting.current ||
      availability?.status !== 'READY' ||
      !availability.available
    )
      return;
    stop();
    starting.current = true;
    const current = generation.current;
    setOpen(true);
    setCreated(undefined);
    setStatus(undefined);
    setError(false);
    setLoading(true);
    try {
      const result = await createAuthorizationRequest();
      if (current !== generation.current) return;
      setLoading(false);
      const expires = Date.parse(result.expiresAt);
      if (
        !isOfficialAuthorizationUrl(result.authorizationUrl) ||
        !Number.isFinite(expires)
      )
        throw new Error('无效授权响应');
      const remaining = Math.min(MAX_WAIT, expires - Date.now());
      if (remaining <= 0) {
        setStatus('EXPIRED');
        stop();
        return;
      }
      setCreated(result);
      setStatus('PENDING');
      // 微信校验授权发起域名，保留 Referer；noopener 隔离窗口，备用链接处理弹窗拦截。
      try {
        window.open(result.authorizationUrl, '_blank', 'noopener');
      } catch {
        /* 备用链接仍可用 */
      }
      timers.current.push(
        setTimeout(() => {
          if (current !== generation.current) return;
          stop();
          setStatus('EXPIRED');
        }, remaining),
      );
      const poll = async () => {
        if (current !== generation.current) return;
        try {
          const next = await getAuthorizationRequest(result.requestId);
          if (current !== generation.current) return;
          if (
            next.requestId !== result.requestId ||
            ![
              'PENDING',
              'PROCESSING',
              'SUCCEEDED',
              'FAILED',
              'EXPIRED',
            ].includes(next.status)
          )
            throw new Error('无效授权状态');
          setStatus(next.status);
          if (
            next.status === 'SUCCEEDED' ||
            next.status === 'FAILED' ||
            next.status === 'EXPIRED'
          ) {
            stop();
            if (next.status === 'SUCCEEDED') await onAuthorized();
            return;
          }
          timers.current.push(setTimeout(() => void poll(), POLL_INTERVAL));
        } catch {
          if (current !== generation.current) return;
          stop();
          setError(true);
        }
      };
      timers.current.push(setTimeout(() => void poll(), POLL_INTERVAL));
    } catch {
      if (current === generation.current) {
        stop();
        setLoading(false);
        setError(true);
      }
    } finally {
      if (current === generation.current) setLoading(false);
    }
  };
  const availabilityText = {
    DISABLED: t(
      'disabled',
      '官方授权尚未启用，请联系管理员开启服务端第三方平台授权。',
    ),
    NOT_CONFIGURED: t(
      'notConfigured',
      '服务端第三方平台配置未完成，请联系管理员检查配置和回调地址。本页面不收集 AppSecret。',
    ),
    WAITING_TICKET: t(
      'waitingTicket',
      '正在等待微信推送 component_verify_ticket，请管理员检查事件接收配置，收到后刷新重试。',
    ),
    READY: t(
      'ready',
      '通过微信官方页面由小程序管理员确认授权，无需在此填写密钥。',
    ),
  };
  const statusText = {
    PENDING: t('pending', '等待小程序管理员在微信官方页面确认授权。'),
    PROCESSING: t('processing', '微信已回调，服务端正在处理授权，请稍候。'),
    SUCCEEDED: t(
      'succeeded',
      '授权成功，已刷新团队小程序列表。授权不代表审核通过或小程序上线。',
    ),
    FAILED: t('failed', '授权失败，请检查小程序权限或联系管理员后重新发起。'),
    EXPIRED: t(
      'expired',
      '授权链接或本页等待时间已到期，请刷新列表确认结果，或重新发起授权。',
    ),
  };
  return (
    <Space orientation="vertical" style={{ width: '100%' }}>
      <Alert
        showIcon
        type={availability?.status === 'READY' ? 'info' : 'warning'}
        title={t('title', '微信官方授权')}
        description={
          availability
            ? availabilityText[availability.status]
            : t('checking', '正在检查官方授权服务状态…')
        }
      />
      <Alert
        type="info"
        title={t(
          'notice',
          '授权仅建立本系统与小程序的授权关系；剧集上架仅更新本系统分发目录，不代表微信审核通过或小程序上线。历史手工绑定记录仍保留。',
        )}
      />
      {error && !open && (
        <Alert type="error" title={t('error', '授权服务请求失败，请重试。')} />
      )}
      <Space>
        <Button
          type="primary"
          loading={loading && open}
          disabled={
            loading ||
            availability?.status !== 'READY' ||
            !availability.available
          }
          onClick={() => void start()}
        >
          {t('start', '微信官方授权小程序')}
        </Button>
        <Button
          loading={loading && !open}
          disabled={open}
          onClick={() => void refreshAvailability()}
        >
          {t('refresh', '刷新授权状态')}
        </Button>
      </Space>
      <Modal
        open={open}
        title={t('title', '微信官方授权')}
        footer={null}
        onCancel={() => {
          stop();
          setLoading(false);
          setOpen(false);
        }}
      >
        <Space orientation="vertical">
          {error ? (
            <Alert
              type="error"
              title={t('error', '授权服务请求失败，请重试。')}
            />
          ) : (
            <Alert
              type={
                status === 'SUCCEEDED'
                  ? 'success'
                  : status === 'FAILED' || status === 'EXPIRED'
                    ? 'warning'
                    : 'info'
              }
              title={
                status ? statusText[status] : t('creating', '正在创建授权链接…')
              }
            />
          )}
          {created &&
            !error &&
            (status === 'PENDING' || status === 'PROCESSING') && (
              <>
                <span>
                  {t(
                    'fallback',
                    '若新窗口未打开，请点击备用链接。关闭本弹窗只停止等待，不撤销微信授权。',
                  )}
                </span>
                <a
                  href={created.authorizationUrl}
                  target="_blank"
                  rel="noopener"
                  referrerPolicy="origin"
                >
                  {t('link', '打开微信官方授权页面')}
                </a>
                <span>
                  {t('expiresAt', '链接到期时间')}：{created.expiresAt}
                </span>
              </>
            )}
        </Space>
      </Modal>
    </Space>
  );
}
