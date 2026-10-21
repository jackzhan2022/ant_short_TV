import { Alert, Form, Input, Modal, Select } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { categories, categoryOptions, errorText } from './constants';
import { findRecipients, type Recipient, sendMessage } from './service';

type Values = {
  title: string;
  content: string;
  category: string;
  scope: 'SELECTED' | 'ALL';
  recipientIds?: number[];
};

export default function SendMessageModal({
  onClose,
  onSent,
}: {
  onClose: () => void;
  onSent: (count: number) => void;
}) {
  const [form] = Form.useForm<Values>();
  const scope = Form.useWatch('scope', form);
  const [recipients, setRecipients] = useState<Recipient[]>([]);
  const [search, setSearch] = useState('');
  const [busy, setBusy] = useState(false);
  const sending = useRef(false);
  const [error, setError] = useState('');
  const [recipientError, setRecipientError] = useState('');
  const [preview, setPreview] = useState<Values>();

  useEffect(() => {
    let disposed = false;
    const timer = window.setTimeout(() => {
      void findRecipients(search)
        .then((items) => {
          if (!disposed) {
            setRecipients(items);
            setRecipientError('');
          }
        })
        .catch((e) => {
          if (!disposed) setRecipientError(errorText(e));
        });
    }, 250);
    return () => {
      disposed = true;
      window.clearTimeout(timer);
    };
  }, [search]);

  const submit = async () => {
    if (!preview || sending.current) return;
    sending.current = true;
    setBusy(true);
    setError('');
    try {
      const result = await sendMessage({
        category: preview.category,
        title: preview.title.trim(),
        content: preview.content.trim(),
        allUsers: preview.scope === 'ALL',
        recipientIds:
          preview.scope === 'ALL' ? undefined : preview.recipientIds,
      });
      onSent(result.recipientCount);
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
      sending.current = false;
    }
  };

  return (
    <Modal
      open
      title={preview ? '确认发送消息' : '发送消息'}
      okText={preview ? '确认发送' : '下一步'}
      cancelText={preview ? '返回编辑' : '取消'}
      confirmLoading={busy}
      closable={!busy}
      mask={{ closable: !busy }}
      cancelButtonProps={{ disabled: busy }}
      onCancel={() => {
        if (!busy) {
          if (preview) setPreview(undefined);
          else onClose();
        }
      }}
      onOk={() => {
        if (preview) void submit();
        else form.submit();
      }}
    >
      {error && <Alert type="error" title={error} showIcon />}
      {preview ? (
        <div className="message-send-preview">
          <p>
            发送范围：
            <strong>
              {preview.scope === 'ALL'
                ? '全部当前有效用户'
                : `已选择的 ${preview.recipientIds?.length ?? 0} 位用户`}
            </strong>
          </p>
          <p>消息分类：{categories[preview.category]}</p>
          <h3>{preview.title}</h3>
          <div className="message-content">{preview.content}</div>
        </div>
      ) : null}
      <Form
        form={form}
        layout="vertical"
        initialValues={{ scope: 'SELECTED', category: 'SYSTEM' }}
        style={{ display: preview ? 'none' : undefined }}
        onFinish={(values) => {
          setError('');
          setPreview(values);
        }}
      >
        <Form.Item label="发送范围" name="scope" rules={[{ required: true }]}>
          <Select
            options={[
              { value: 'SELECTED', label: '指定用户' },
              { value: 'ALL', label: '全部当前有效用户' },
            ]}
          />
        </Form.Item>
        {scope !== 'ALL' && (
          <Form.Item
            label="收件人"
            name="recipientIds"
            rules={[
              {
                required: true,
                type: 'array',
                min: 1,
                max: 500,
                message: '请选择1至500位用户',
              },
            ]}
          >
            <Select
              mode="multiple"
              showSearch={{ filterOption: false, onSearch: setSearch }}
              placeholder="搜索用户昵称或用户编号"
              options={recipients.map((user) => ({
                value: user.id,
                label: `${user.nickname}（${user.id}）`,
              }))}
            />
          </Form.Item>
        )}
        {recipientError && scope !== 'ALL' && (
          <Alert title={recipientError} type="error" showIcon />
        )}
        <Form.Item
          label="消息分类"
          name="category"
          rules={[{ required: true }]}
        >
          <Select options={categoryOptions} />
        </Form.Item>
        <Form.Item
          label="消息标题"
          name="title"
          rules={[
            {
              required: true,
              whitespace: true,
              max: 200,
              message: '请输入消息标题，最多200字',
            },
          ]}
        >
          <Input maxLength={200} showCount placeholder="请输入消息标题" />
        </Form.Item>
        <Form.Item
          label="消息正文"
          name="content"
          rules={[
            {
              required: true,
              whitespace: true,
              max: 10000,
              message: '请输入消息正文，最多10000字',
            },
          ]}
        >
          <Input.TextArea
            rows={6}
            maxLength={10000}
            showCount
            placeholder="请输入消息正文"
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}
