import { FileTextOutlined, FolderOpenOutlined, PlusOutlined } from '@ant-design/icons';
import {
  App,
  Button,
  Collapse,
  Dropdown,
  Empty,
  Modal,
  Radio,
  Space,
  Spin,
  Typography,
} from 'antd';
import type { MenuProps } from 'antd';
import { useRef, useState } from 'react';
import {
  parseScriptFile,
  queryReviewProject,
  queryReviewProjects,
  type ReviewProject,
  type ReviewVersion,
} from './service';

type ScriptContentImportProps = {
  className?: string;
  currentContent: string;
  onImport: (content: string, sourceLabel: string) => void;
};

type SelectedVersion = {
  project: ReviewProject;
  version: ReviewVersion;
};

const supportedExtensions = new Set(['txt', 'md', 'docx']);

const extensionOf = (fileName: string) => fileName.split('.').pop()?.toLowerCase() || '';

const ScriptContentImport = ({ className, currentContent, onImport }: ScriptContentImportProps) => {
  const { message, modal } = App.useApp();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [reviewOpen, setReviewOpen] = useState(false);
  const [projects, setProjects] = useState<ReviewProject[]>([]);
  const [projectsLoading, setProjectsLoading] = useState(false);
  const [projectsFailed, setProjectsFailed] = useState(false);
  const [versionsByProject, setVersionsByProject] = useState<Record<number, ReviewVersion[]>>({});
  const [versionLoading, setVersionLoading] = useState<Record<number, boolean>>({});
  const [selected, setSelected] = useState<SelectedVersion>();

  const applyCandidate = (content: string, label: string, onApplied?: () => void) => {
    const normalized = content.trim();
    if (!normalized) {
      message.error('剧本文件中没有可用文字');
      return;
    }
    if (!currentContent.trim()) {
      onImport(normalized, label);
      onApplied?.();
      return;
    }
    modal.confirm({
      title: '覆盖当前剧本内容',
      content: '当前剧本内容将被覆盖，是否继续？',
      okText: '继续覆盖',
      cancelText: '取消',
      onOk: () => {
        onImport(normalized, label);
        onApplied?.();
      },
    });
  };

  const handleFile = async (file?: File) => {
    if (!file) return;
    const extension = extensionOf(file.name);
    if (!supportedExtensions.has(extension)) {
      message.error('仅支持 txt、md、docx 文件');
      return;
    }
    try {
      const content = extension === 'docx'
        ? (await parseScriptFile(file)).data?.content || ''
        : await file.text();
      applyCandidate(content, `已导入：${file.name}`);
    } catch {
      message.error('剧本文件读取失败');
    } finally {
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const loadReviewProjects = async () => {
    setProjectsLoading(true);
    setProjectsFailed(false);
    try {
      const response = await queryReviewProjects();
      setProjects(response.data || []);
    } catch {
      setProjectsFailed(true);
      message.error('审核剧本列表加载失败');
    } finally {
      setProjectsLoading(false);
    }
  };

  const openReviewPicker = () => {
    setReviewOpen(true);
    if (!projects.length) void loadReviewProjects();
  };

  const loadProjectVersions = async (projectId: number) => {
    if (versionsByProject[projectId] || versionLoading[projectId]) return;
    setVersionLoading((current) => ({ ...current, [projectId]: true }));
    try {
      const response = await queryReviewProject(projectId);
      setVersionsByProject((current) => ({
        ...current,
        [projectId]: response.data?.versions || [],
      }));
    } catch {
      message.error('审核剧本版本加载失败');
    } finally {
      setVersionLoading((current) => ({ ...current, [projectId]: false }));
    }
  };

  const menuItems: MenuProps['items'] = [
    { key: 'local', icon: <FolderOpenOutlined />, label: '本地上传' },
    { key: 'review', icon: <FileTextOutlined />, label: '从审核剧本引用' },
  ];

  return (
    <>
      <Dropdown
        menu={{
          items: menuItems,
          onClick: ({ key }) => {
            if (key === 'local') fileInputRef.current?.click();
            if (key === 'review') openReviewPicker();
          },
        }}
        trigger={['click']}
      >
        <Button
          aria-label="导入剧本内容"
          className={className}
          icon={<PlusOutlined />}
          shape="circle"
          type="text"
        />
      </Dropdown>
      <input
        accept=".txt,.md,.docx"
        aria-label="选择剧本文件"
        hidden
        onChange={(event) => void handleFile(event.target.files?.[0])}
        ref={fileInputRef}
        type="file"
      />
      <Modal
        cancelText="取消"
        okButtonProps={{ disabled: !selected }}
        okText="引用所选版本"
        onCancel={() => setReviewOpen(false)}
        onOk={() => {
          if (!selected) return;
          applyCandidate(
            selected.version.content,
            `已引用：${selected.project.name} · 版本 ${selected.version.versionNo}`,
            () => setReviewOpen(false),
          );
        }}
        open={reviewOpen}
        title="从审核剧本引用"
        width={680}
      >
        <Spin spinning={projectsLoading}>
          {projects.length ? (
            <Collapse
              items={projects.map((project) => ({
                key: String(project.id),
                label: (
                  <Space>
                    <Typography.Text strong>{project.name}</Typography.Text>
                    <Typography.Text type="secondary">{project.versionCount} 个版本</Typography.Text>
                  </Space>
                ),
                children: versionLoading[project.id] ? (
                  <Spin size="small" />
                ) : (
                  <Radio.Group
                    onChange={(event) => {
                      const version = versionsByProject[project.id]?.find(
                        (item) => item.id === event.target.value,
                      );
                      if (version) setSelected({ project, version });
                    }}
                    value={selected?.version.id}
                  >
                    <Space orientation="vertical">
                      {(versionsByProject[project.id] || []).map((version) => (
                        <Radio key={version.id} value={version.id}>
                          版本 {version.versionNo}
                          {version.fileName ? ` · ${version.fileName}` : ''}
                          {version.createdAt ? ` · ${new Date(version.createdAt).toLocaleString()}` : ''}
                        </Radio>
                      ))}
                    </Space>
                  </Radio.Group>
                ),
              }))}
              onChange={(keys) => {
                const activeKeys = Array.isArray(keys) ? keys : [keys];
                for (const key of activeKeys) void loadProjectVersions(Number(key));
              }}
            />
          ) : (
            !projectsLoading && (
              <Empty description={projectsFailed ? '审核剧本列表加载失败' : '暂无可引用的审核剧本'}>
                {projectsFailed && <Button onClick={() => void loadReviewProjects()}>重新加载</Button>}
              </Empty>
            )
          )}
        </Spin>
      </Modal>
    </>
  );
};

export default ScriptContentImport;
