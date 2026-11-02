import { Alert, Button } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { grantErrorMessage } from '@/pages/commercial-management/tenants/grantService';
import {
  getImpersonationDisplay,
  stopImpersonation,
} from '@/services/account-team/impersonation';
import {
  getSessionChanged,
  SESSION_CONTEXT_EVENT,
} from '@/services/account-team/sessionIdentity';

export default function ImpersonationBanner() {
  const [display, setDisplay] = useState(getImpersonationDisplay);
  const [stale, setStale] = useState(getSessionChanged);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const submitting = useRef(false);
  useEffect(() => {
    const sync = () => {
      setDisplay(getImpersonationDisplay());
      setStale(getSessionChanged());
    };
    window.addEventListener(SESSION_CONTEXT_EVENT, sync);
    sync();
    return () => window.removeEventListener(SESSION_CONTEXT_EVENT, sync);
  }, []);

  const restore = async () => {
    if (submitting.current) return;
    submitting.current = true;
    setBusy(true);
    setError(undefined);
    try {
      await stopImpersonation();
    } catch (failure) {
      setError(grantErrorMessage(failure));
      setBusy(false);
      submitting.current = false;
    }
  };

  if (!display && !stale) return null;
  return (
    <div style={{ position: 'sticky', top: 0, zIndex: 2000 }}>
      {stale ? (
        <Alert
          banner
          type="error"
          showIcon
          title="登录身份已在其他页面切换，请刷新页面后继续操作。"
          action={
            <Button onClick={() => window.location.reload()}>刷新页面</Button>
          }
        />
      ) : (
        <Alert
          banner
          type="warning"
          showIcon
          title={`当前以成员身份登录：${display?.tenantName} · ${display?.userName}`}
          description={
            error ?? '数据修改和任务会真实生效，并消耗目标租户积分。'
          }
          action={
            <Button loading={busy} onClick={() => void restore()}>
              返回管理员
            </Button>
          }
        />
      )}
    </div>
  );
}
