import { useIntl } from '@umijs/max';
import {
  App,
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Progress,
  Space,
  Table,
  Tabs,
  Upload,
} from 'antd';
import { useEffect, useRef, useState } from 'react';
import { queryProjects } from '@/services/account-team/project';
import type { Project } from '@/services/account-team/types';
import {
  type MediaUploadHandle,
  startMediaUpload,
  type VerifiedMediaUpload,
} from '@/services/mediaUpload';
import {
  type EpisodeVideoVersion,
  queryEpisodeVideoVersions,
} from '../projects/production-workbench/service';
import { importEpisode } from './service';

export default function ImportEpisode({
  dramaId,
  nextNo,
  onClose,
  onImported,
}: {
  dramaId: number;
  nextNo: number;
  onClose: () => void;
  onImported: () => Promise<void>;
}) {
  const intl = useIntl();
  const t = (key: string, text: string) =>
    intl.formatMessage({ id: `drama.${key}`, defaultMessage: text });
  const { message } = App.useApp();
  const [form] = Form.useForm<{ episodeNo: number; title: string }>();
  const [source, setSource] = useState('local');
  const [file, setFile] = useState<File>();
  const [verified, setVerified] = useState<VerifiedMediaUpload>();
  const handle = useRef<MediaUploadHandle | undefined>(undefined);
  const alive = useRef(true);
  const [uploading, setUploading] = useState(false);
  const [uploadFailed, setUploadFailed] = useState(false);
  const [percent, setPercent] = useState(0);
  const [saving, setSaving] = useState(false);
  const [projects, setProjects] = useState<Project[]>([]);
  const [projectPage, setProjectPage] = useState(1);
  const [projectTotal, setProjectTotal] = useState(0);
  const [projectLoading, setProjectLoading] = useState(false);
  const [project, setProject] = useState<Project>();
  const [sourceNo, setSourceNo] = useState(1);
  const [versions, setVersions] = useState<EpisodeVideoVersion[]>([]);
  const [versionPage, setVersionPage] = useState(1);
  const [versionTotal, setVersionTotal] = useState(0);
  const [versionLoading, setVersionLoading] = useState(false);
  const [version, setVersion] = useState<EpisodeVideoVersion>();

  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
      handle.current?.cancel();
    };
  }, []);
  useEffect(() => {
    if (source !== 'project') return;
    let active = true;
    setProjectLoading(true);
    queryProjects({ current: projectPage, pageSize: 10 })
      .then((res) => {
        if (active) {
          setProjects(res.data.data);
          setProjectTotal(res.data.total);
        }
      })
      .catch(() => {
        if (active) setProjects([]);
      })
      .finally(() => {
        if (active) setProjectLoading(false);
      });
    return () => {
      active = false;
    };
  }, [source, projectPage]);
  useEffect(() => {
    setVersion(undefined);
    setVersions([]);
    setVersionTotal(0);
    if (!project || source !== 'project') return;
    let active = true;
    setVersionLoading(true);
    queryEpisodeVideoVersions(project.id, sourceNo, {
      current: versionPage,
      pageSize: 10,
    })
      .then((res) => {
        if (active) {
          setVersions(res.data.data);
          setVersionTotal(res.data.total);
        }
      })
      .catch(() => {
        if (active) setVersions([]);
      })
      .finally(() => {
        if (active) setVersionLoading(false);
      });
    return () => {
      active = false;
    };
  }, [project, sourceNo, versionPage, source]);

  const upload = async (retry = false) => {
    if (!file) return;
    setUploading(true);
    setUploadFailed(false);
    try {
      if (!retry || !handle.current) {
        handle.current = await startMediaUpload(file, {
          onProgress: (p) => {
            if (alive.current) setPercent(Math.round(p.percent * 100));
          },
        });
        if (!alive.current) {
          handle.current.cancel();
          return;
        }
      }
      const result = await (retry
        ? handle.current.retry()
        : handle.current.attempt);
      if (alive.current) {
        setVerified(result);
        setPercent(100);
      }
    } catch (error) {
      if (alive.current) {
        setUploadFailed(true);
        message.error(
          error instanceof Error
            ? error.message
            : t('uploadFailed', '上传失败，请重试'),
        );
      }
    } finally {
      if (alive.current) setUploading(false);
    }
  };
  const submit = async () => {
    const values = await form.validateFields();
    if (source === 'local' && !verified) {
      message.warning(t('uploadFirst', '请先上传视频并完成校验'));
      return;
    }
    if (source === 'project' && (!project || !version)) {
      message.warning(t('selectVersion', '请选择项目成片版本'));
      return;
    }
    setSaving(true);
    try {
      if (source === 'local' && verified) {
        await importEpisode(dramaId, {
          ...values,
          title: values.title.trim(),
          uploadSessionToken: verified.sessionToken,
        });
      } else if (source === 'project' && project && version) {
        await importEpisode(dramaId, {
          ...values,
          title: values.title.trim(),
          projectId: project.id,
          sourceVersionId: version.id,
        });
      } else {
        return;
      }
      await onImported();
      onClose();
    } catch {
      /* 请求层统一提示业务错误，保留已上传的会话以便重新导入。 */
    } finally {
      setSaving(false);
    }
  };
  return (
    <Modal
      open
      width={860}
      title={t('import', '导入单集')}
      onCancel={onClose}
      onOk={() => void submit().catch(() => undefined)}
      confirmLoading={saving}
      cancelButtonProps={{ disabled: uploading || saving }}
      closable={!uploading && !saving}
      mask={{ closable: !uploading && !saving }}
      keyboard={!uploading && !saving}
      okButtonProps={{ disabled: uploading }}
    >
      <Form form={form} layout="vertical" initialValues={{ episodeNo: nextNo }}>
        <Form.Item
          name="episodeNo"
          label={t('episodeNo', '剧库集号')}
          rules={[{ required: true }]}
        >
          <InputNumber min={1} max={2147483647} precision={0} />
        </Form.Item>
        <Form.Item
          name="title"
          label={t('episodeTitle', '单集标题')}
          rules={[{ required: true, whitespace: true, max: 200 }]}
        >
          <Input maxLength={200} />
        </Form.Item>
      </Form>
      <Tabs
        activeKey={source}
        onChange={setSource}
        items={[
          {
            key: 'local',
            label: t('local', '本地视频'),
            disabled: uploading || saving,
            children: (
              <Space orientation="vertical" style={{ width: '100%' }}>
                <Upload
                  accept="video/*"
                  onChange={({ fileList }) => {
                    if (fileList.length === 0) setFile(undefined);
                  }}
                  maxCount={1}
                  disabled={uploading || saving}
                  fileList={
                    file
                      ? [
                          {
                            uid: 'video',
                            name: file.name,
                            status: verified ? 'done' : undefined,
                          },
                        ]
                      : []
                  }
                  beforeUpload={(value) => {
                    if (!value.type.startsWith('video/') || value.size <= 0) {
                      message.error(t('videoOnly', '请选择非空视频文件'));
                      return Upload.LIST_IGNORE;
                    }
                    handle.current?.cancel();
                    handle.current = undefined;
                    setFile(value);
                    setVerified(undefined);
                    setPercent(0);
                    setUploadFailed(false);
                    return false;
                  }}
                  onRemove={() => {
                    handle.current?.cancel();
                    handle.current = undefined;
                    setFile(undefined);
                    setVerified(undefined);
                    setPercent(0);
                    setUploadFailed(false);
                  }}
                >
                  <Button>{t('chooseFile', '选择视频文件')}</Button>
                </Upload>
                <Progress
                  percent={percent}
                  status={
                    uploadFailed ? 'exception' : verified ? 'success' : 'normal'
                  }
                />
                <Button
                  disabled={!file || Boolean(verified) || saving}
                  loading={uploading}
                  onClick={() => void upload(uploadFailed)}
                >
                  {uploadFailed
                    ? t('retryUpload', '重试上传/校验')
                    : t('upload', '上传并校验')}
                </Button>
                <span>
                  {t(
                    'uploadHint',
                    '视频通过现有上传会话直传私有存储，完成服务端校验后才能导入。',
                  )}
                </span>
              </Space>
            ),
          },
          {
            key: 'project',
            label: t('projectSource', '项目成片'),
            disabled: uploading || saving,
            children: (
              <Space orientation="vertical" style={{ width: '100%' }}>
                <span>
                  {t(
                    'projectHint',
                    '项目仅作为单集来源，不与剧库绑定。请选择拥有成片查看和下载权限的项目。',
                  )}
                </span>
                <Table<Project>
                  size="small"
                  rowKey="id"
                  loading={projectLoading}
                  dataSource={projects}
                  columns={[
                    { title: t('project', '项目'), dataIndex: 'name' },
                    { title: t('code', '编号'), dataIndex: 'code' },
                  ]}
                  pagination={{
                    current: projectPage,
                    total: projectTotal,
                    pageSize: 10,
                    showSizeChanger: false,
                    onChange: setProjectPage,
                  }}
                  rowSelection={{
                    type: 'radio',
                    selectedRowKeys: project ? [project.id] : [],
                    onChange: (_, rows) => {
                      setProject(rows[0]);
                      setVersionPage(1);
                    },
                    getCheckboxProps: (row) => ({
                      disabled: !row.effectivePermissions.includes('MENU:DRAMA_LIBRARY'),
                    }),
                  }}
                />
                <Space>
                  <span>{t('sourceNo', '来源项目集号')}</span>
                  <InputNumber
                    min={1}
                    max={2147483647}
                    precision={0}
                    value={sourceNo}
                    onChange={(value) => {
                      if (value) {
                        setSourceNo(value);
                        setVersionPage(1);
                      }
                    }}
                  />
                </Space>
                <Table<EpisodeVideoVersion>
                  size="small"
                  rowKey="id"
                  loading={versionLoading}
                  dataSource={versions}
                  columns={[
                    {
                      title: t('version', '成片版本'),
                      dataIndex: 'versionName',
                    },
                    { title: t('versionNo', '版本号'), dataIndex: 'versionNo' },
                    { title: t('status', '状态'), dataIndex: 'status' },
                  ]}
                  pagination={{
                    current: versionPage,
                    total: versionTotal,
                    pageSize: 10,
                    showSizeChanger: false,
                    onChange: setVersionPage,
                  }}
                  rowSelection={{
                    type: 'radio',
                    selectedRowKeys: version ? [version.id] : [],
                    onChange: (_, rows) => setVersion(rows[0]),
                  }}
                />
              </Space>
            ),
          },
        ]}
      />
    </Modal>
  );
}
