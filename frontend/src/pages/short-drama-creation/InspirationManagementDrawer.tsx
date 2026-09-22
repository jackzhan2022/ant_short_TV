import {
  DeleteOutlined,
  EditOutlined,
  HolderOutlined,
  PlusOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import {
  App,
  Button,
  Drawer,
  Empty,
  Flex,
  Form,
  Input,
  Select,
  Space,
  Spin,
  Switch,
  Tag,
  Upload,
} from 'antd';
import { useCallback, useEffect, useState } from 'react';
import {
  type CompressedImage,
  compressImage,
  formatFileSize,
} from './imageCompression';
import styles from './index.module.css';
import {
  createManagedInspiration,
  deleteManagedInspiration,
  type ManagedInspiration,
  queryManagedInspirations,
  reorderManagedInspirations,
  updateManagedInspiration,
  updateManagedInspirationStatus,
} from './service';

type Props = { open: boolean; onClose: () => void; onChanged: () => void };
type FormValues = {
  title: string;
  tags?: string[];
  promptText: string;
  published: boolean;
};

const InspirationManagementDrawer = ({ open, onClose, onChanged }: Props) => {
  const { message, modal } = App.useApp();
  const [form] = Form.useForm<FormValues>();
  const [items, setItems] = useState<ManagedInspiration[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState<ManagedInspiration>();
  const [file, setFile] = useState<File>();
  const [compression, setCompression] = useState<CompressedImage>();
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string>();
  const [mediaType, setMediaType] = useState<string>();
  const [dragId, setDragId] = useState<number>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const response = await queryManagedInspirations({
        page: 1,
        pageSize: 100,
        keyword: keyword || undefined,
        publishStatus: status,
        mediaType,
      });
      setItems(response.data?.records || []);
    } catch {
      message.error('管理列表加载失败');
    } finally {
      setLoading(false);
    }
  }, [keyword, mediaType, message, status]);

  useEffect(() => {
    if (open) void load();
  }, [load, open]);

  const resetEditor = () => {
    setEditing(undefined);
    setFile(undefined);
    setCompression(undefined);
    form.resetFields();
  };
  const chooseFile = async (next: File) => {
    setCompression(undefined);
    if (next.type.startsWith('image/')) {
      try {
        const result = await compressImage(next);
        setFile(result.file);
        setCompression(result);
      } catch (error) {
        setFile(undefined);
        message.error((error as Error).message);
      }
    } else if (next.type === 'video/mp4') setFile(next);
    else message.error('仅支持 JPG、PNG 图片或 MP4 视频');
    return false;
  };
  const submit = async (values: FormValues) => {
    if (!editing && !file) {
      message.error('请上传图片或视频');
      return;
    }
    setSaving(true);
    try {
      if (editing)
        await updateManagedInspiration(editing.id, {
          title: values.title,
          tags: values.tags || [],
          promptText: values.promptText,
        });
      else if (file)
        await createManagedInspiration({
          file,
          title: values.title,
          tags: values.tags || [],
          promptText: values.promptText,
          publishStatus: values.published ? 'PUBLISHED' : 'UNPUBLISHED',
        });
      message.success(editing ? '内容已更新' : '内容已添加');
      resetEditor();
      await load();
      onChanged();
    } catch {
      message.error('保存失败，请检查后重试');
    } finally {
      setSaving(false);
    }
  };
  const move = async (targetId: number) => {
    if (!dragId || dragId === targetId) return;
    const next = [...items];
    const from = next.findIndex((item) => item.id === dragId);
    const to = next.findIndex((item) => item.id === targetId);
    const [moved] = next.splice(from, 1);
    next.splice(to, 0, moved);
    setItems(next);
    try {
      await reorderManagedInspirations(next.map((item) => item.id));
      message.success('排序已保存');
      onChanged();
    } catch {
      message.error('排序保存失败');
      await load();
    }
  };

  return (
    <Drawer
      destroyOnHidden
      open={open}
      onClose={onClose}
      title="灵感广场管理"
      size="large"
    >
      <Flex gap={8} wrap className={styles.managementFilters}>
        <Input.Search
          allowClear
          placeholder="搜索标题或提示词"
          onSearch={setKeyword}
        />
        <Select
          allowClear
          placeholder="发布状态"
          value={status}
          onChange={setStatus}
          options={[
            { value: 'PUBLISHED', label: '已上架' },
            { value: 'UNPUBLISHED', label: '未上架' },
          ]}
        />
        <Select
          allowClear
          placeholder="素材类型"
          value={mediaType}
          onChange={setMediaType}
          options={[
            { value: 'IMAGE', label: '图片' },
            { value: 'VIDEO', label: '视频' },
          ]}
        />
        <Button icon={<PlusOutlined />} type="primary" onClick={resetEditor}>
          新增内容
        </Button>
      </Flex>
      <Form
        form={form}
        layout="vertical"
        onFinish={submit}
        initialValues={{ published: false }}
        className={styles.managementForm}
      >
        {!editing && (
          <Form.Item label="素材文件" required>
            <Upload
              accept="image/jpeg,image/png,video/mp4"
              beforeUpload={(value) => {
                void chooseFile(value as File);
                return false;
              }}
              maxCount={1}
              showUploadList={Boolean(file)}
            >
              <Button icon={<UploadOutlined />}>上传图片或视频</Button>
            </Upload>
            {compression && (
              <div className={styles.compressionInfo}>
                压缩前 {formatFileSize(compression.originalBytes)}，压缩后{' '}
                {formatFileSize(compression.compressedBytes)} ·{' '}
                {compression.width}×{compression.height}
              </div>
            )}
          </Form.Item>
        )}
        <Form.Item
          name="title"
          label="标题"
          rules={[{ required: true, whitespace: true, message: '请输入标题' }]}
        >
          <Input maxLength={200} />
        </Form.Item>
        <Form.Item name="tags" label="标签">
          <Select mode="tags" placeholder="输入后回车" />
        </Form.Item>
        <Form.Item
          name="promptText"
          label="完整提示词"
          rules={[
            { required: true, whitespace: true, message: '请输入完整提示词' },
          ]}
        >
          <Input.TextArea rows={4} maxLength={10000} />
        </Form.Item>
        {!editing && (
          <Form.Item name="published" label="立即上架" valuePropName="checked">
            <Switch />
          </Form.Item>
        )}
        <Space>
          <Button htmlType="submit" loading={saving} type="primary">
            {editing ? '保存修改' : '添加内容'}
          </Button>
          {editing && <Button onClick={resetEditor}>取消编辑</Button>}
        </Space>
      </Form>
      <Spin spinning={loading}>
        {items.length ? (
          <div className={styles.managementList}>
            {items.map((item) => (
              <div
                className={styles.managementItem}
                draggable
                key={item.id}
                onDragStart={() => setDragId(item.id)}
                onDragOver={(event) => event.preventDefault()}
                onDrop={() => void move(item.id)}
              >
                <HolderOutlined className={styles.dragHandle} />
                <img
                  alt={item.title || '灵感素材'}
                  src={item.thumbnailUrl || item.url}
                />
                <div className={styles.managementItemCopy}>
                  <strong>{item.title}</strong>
                  <span>{item.promptText}</span>
                  <Space size={4}>
                    {item.tags?.map((tag) => (
                      <Tag key={tag}>{tag}</Tag>
                    ))}
                  </Space>
                </div>
                <Switch
                  aria-label={`${item.title}上架状态`}
                  checked={item.publishStatus === 'PUBLISHED'}
                  onChange={async (checked) => {
                    await updateManagedInspirationStatus(
                      item.id,
                      checked ? 'PUBLISHED' : 'UNPUBLISHED',
                    );
                    await load();
                    onChanged();
                  }}
                />
                <Button
                  aria-label={`编辑${item.title}`}
                  icon={<EditOutlined />}
                  type="text"
                  onClick={() => {
                    setEditing(item);
                    form.setFieldsValue({
                      title: item.title || '',
                      tags: item.tags,
                      promptText: item.promptText,
                      published: item.publishStatus === 'PUBLISHED',
                    });
                  }}
                />
                <Button
                  danger
                  aria-label={`删除${item.title}`}
                  icon={<DeleteOutlined />}
                  type="text"
                  onClick={() =>
                    modal.confirm({
                      title: '确认删除这条灵感内容？',
                      content: '删除后将立即从灵感广场隐藏。',
                      okText: '删除',
                      okButtonProps: { danger: true },
                      onOk: async () => {
                        await deleteManagedInspiration(item.id);
                        await load();
                        onChanged();
                      },
                    })
                  }
                />
              </div>
            ))}
          </div>
        ) : (
          <Empty description="暂无管理内容" />
        )}
      </Spin>
    </Drawer>
  );
};

export default InspirationManagementDrawer;
