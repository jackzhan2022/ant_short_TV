import { UploadOutlined } from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import { Alert, App, Button, Popconfirm, Spin, Upload } from 'antd';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  type CustomerServiceConfig,
  getCustomerServiceConfig,
  removeCustomerService,
  saveCustomerService,
} from '@/services/customerService';
import { useStyles } from './index.style';

export default function CustomerServicePage() {
  const access = useAccess();
  const { message } = App.useApp();
  const { styles } = useStyles();
  const [config, setConfig] = useState<CustomerServiceConfig>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [file, setFile] = useState<File>();
  const [preview, setPreview] = useState('');
  const [saving, setSaving] = useState(false);
  const pending = useRef(false);
  const sequence = useRef(0);

  const load = useCallback(async () => {
    const current = ++sequence.current;
    setLoading(true);
    setError('');
    try {
      const value = await getCustomerServiceConfig();
      if (sequence.current === current) setConfig(value);
    } catch {
      if (sequence.current === current) setError('客服配置加载失败，请重试');
    } finally {
      if (sequence.current === current) setLoading(false);
    }
  }, []);
  useEffect(() => {
    void load();
    return () => {
      sequence.current += 1;
    };
  }, [load]);
  useEffect(() => {
    if (!file) {
      setPreview('');
      return;
    }
    const url = URL.createObjectURL(file);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  const save = async () => {
    if (!file || !access.canEditCustomerService || pending.current) return;
    pending.current = true;
    setSaving(true);
    setError('');
    try {
      setConfig(await saveCustomerService(file));
      setFile(undefined);
      message.success('客服二维码已保存');
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '保存失败，请重试');
    } finally {
      pending.current = false;
      setSaving(false);
    }
  };
  const remove = async () => {
    if (!access.canEditCustomerService || pending.current) return;
    pending.current = true;
    setSaving(true);
    setError('');
    try {
      setConfig(await removeCustomerService());
      setFile(undefined);
      message.success('客服二维码已移除');
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '移除失败，请重试');
    } finally {
      pending.current = false;
      setSaving(false);
    }
  };

  if (loading) return <Spin aria-label="正在加载客服配置" />;
  if (!config)
    return (
      <Alert
        type="error"
        title={error}
        action={<Button onClick={() => void load()}>重试</Button>}
      />
    );
  const imageUrl = preview || config.qrCodeUrl;
  return (
    <section className={styles.container} aria-label="客服配置">
      <h3 className={styles.title}>企微客服二维码</h3>
      <p className={styles.description}>
        上传企业微信「客户联系 →
        联系我」生成的二维码。保存后，用户可点击页面右下角「获取帮助」扫码添加客服。
      </p>
      {error && <Alert type="error" showIcon title={error} />}
      <div className={styles.preview}>
        {imageUrl ? (
          <img src={imageUrl} alt="客服二维码预览" className={styles.image} />
        ) : (
          <span>暂未配置客服二维码</span>
        )}
      </div>
      <div className={styles.description}>
        {file
          ? `待保存：${file.name}`
          : config.qrCodeUrl
            ? '当前正在使用的客服二维码'
            : '未配置时，帮助入口将提示客服暂未开放。'}
      </div>
      {access.canEditCustomerService && (
        <div className={styles.actions}>
          <Upload
            accept="image/png,image/jpeg"
            showUploadList={false}
            disabled={saving}
            beforeUpload={(selected) => {
              if (
                !['image/png', 'image/jpeg'].includes(selected.type) ||
                selected.size <= 0 ||
                selected.size > 2 * 1024 * 1024
              ) {
                setError('请选择不超过 2 MB 的 PNG 或 JPG 图片');
                return Upload.LIST_IGNORE;
              }
              setError('');
              setFile(selected);
              return false;
            }}
          >
            <Button
              aria-label="选择二维码"
              icon={<UploadOutlined aria-hidden="true" />}
              disabled={saving}
            >
              选择二维码
            </Button>
          </Upload>
          <Button
            aria-label="保存二维码"
            type="primary"
            disabled={!file}
            loading={saving}
            onClick={() => void save()}
          >
            保存二维码
          </Button>
          {file && (
            <Button
              disabled={saving}
              onClick={() => {
                setFile(undefined);
                setError('');
              }}
            >
              取消选择
            </Button>
          )}
          {config.qrCodeUrl && (
            <Popconfirm
              title="移除客服二维码？"
              description="移除后，用户将无法通过帮助入口扫码添加客服。"
              okText="移除"
              cancelText="取消"
              onConfirm={remove}
            >
              <Button danger disabled={saving}>
                移除二维码
              </Button>
            </Popconfirm>
          )}
        </div>
      )}
      <p className={styles.description}>
        支持 PNG、JPG 格式，最大 2 MB，图片边长不超过 4096
        像素。请确保二维码清晰且可用。
      </p>
    </section>
  );
}
