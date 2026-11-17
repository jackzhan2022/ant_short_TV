import { CloseOutlined, CustomerServiceOutlined } from '@ant-design/icons';
import { Button, Popover, Spin } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { getCustomerService } from '@/services/customerService';
import { useStyles } from './index.style';

export default function CustomerServiceFloat() {
  const { styles } = useStyles();
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [qrCodeUrl, setQrCodeUrl] = useState('');
  const [error, setError] = useState('');
  const triggerRef = useRef<HTMLButtonElement>(null);
  const sequence = useRef(0);
  useEffect(
    () => () => {
      sequence.current += 1;
    },
    [],
  );

  const load = async () => {
    const current = ++sequence.current;
    setLoading(true);
    setError('');
    setQrCodeUrl('');
    try {
      const config = await getCustomerService();
      if (current === sequence.current) setQrCodeUrl(config.qrCodeUrl);
    } catch {
      if (current === sequence.current) setError('客服信息加载失败，请重试');
    } finally {
      if (current === sequence.current) setLoading(false);
    }
  };
  const changeOpen = (nextOpen: boolean) => {
    setOpen(nextOpen);
    if (nextOpen) void load();
    else sequence.current += 1;
  };
  const close = () => {
    changeOpen(false);
    triggerRef.current?.focus();
  };
  useEffect(() => {
    if (!open) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false);
        sequence.current += 1;
        triggerRef.current?.focus();
      }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [open]);

  return (
    <Popover
      trigger="click"
      placement="topRight"
      open={open}
      onOpenChange={changeOpen}
      content={
        <section className={styles.card} aria-label="企微客服联系方式">
          <div className={styles.header}>
            <span className={styles.headerIcon}>
              <CustomerServiceOutlined />
            </span>
            <div>
              <div className={styles.title}>添加企微客服</div>
              <div className={styles.hint}>有问题，随时联系我们</div>
            </div>
            <button
              type="button"
              className={styles.close}
              onClick={close}
              aria-label="关闭客服卡片"
            >
              <CloseOutlined />
            </button>
          </div>
          {loading ? (
            <div
              className={styles.status}
              role="status"
              aria-label="正在加载客服信息"
            >
              <Spin />
            </div>
          ) : error ? (
            <div className={styles.status}>
              <div role="alert">{error}</div>
              <Button type="link" onClick={() => void load()}>
                重试
              </Button>
            </div>
          ) : qrCodeUrl ? (
            <>
              <img
                className={styles.qrCode}
                src={qrCodeUrl}
                alt="企业微信客服「联系我」二维码"
                onError={() => setError('二维码加载失败，请重试')}
              />
              <div className={styles.instruction}>微信扫码，添加专属客服</div>
              <div className={styles.hint}>使用咨询 · 问题反馈 · 商务合作</div>
            </>
          ) : (
            <div className={styles.status} role="status">
              客服暂未开放，请稍后再试
            </div>
          )}
        </section>
      }
    >
      <button
        ref={triggerRef}
        className={styles.trigger}
        type="button"
        aria-label="获取帮助"
        aria-expanded={open}
      >
        <CustomerServiceOutlined aria-hidden="true" />
        <span>获取帮助</span>
      </button>
    </Popover>
  );
}
