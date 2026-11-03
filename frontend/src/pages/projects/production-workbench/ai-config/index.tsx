import {
  PictureOutlined,
  RobotOutlined,
  SoundOutlined,
  VideoCameraOutlined,
} from '@ant-design/icons';
import { useParams } from '@umijs/max';
import { App, Button, Select, Spin } from 'antd';
import type { ReactNode } from 'react';
import { useEffect, useState } from 'react';
import {
  canUseProjectFeatures,
  queryProject,
} from '@/services/account-team/project';
import styles from './index.module.css';
import type {
  ProjectAiConfig,
  ProjectAiModels,
  ProjectModelOption,
} from './service';
import {
  queryProjectAiConfig,
  queryProjectAiModels,
  saveProjectAiConfig,
} from './service';

type ModelField =
  | 'textModelId'
  | 'imageModelId'
  | 'videoModelId'
  | 'audioModelId';
type ModelSection = {
  field: ModelField;
  title: string;
  description: string;
  icon: ReactNode;
  options: (models: ProjectAiModels) => ProjectModelOption[];
};

const modelSections: ModelSection[] = [
  {
    field: 'textModelId',
    title: '文本模型',
    description: '剧本解析、分镜与提示词',
    icon: <RobotOutlined />,
    options: (models) => models.textModels,
  },
  {
    field: 'imageModelId',
    title: '图片模型',
    description: '角色、场景与分镜图片',
    icon: <PictureOutlined />,
    options: (models) => models.imageModels,
  },
  {
    field: 'videoModelId',
    title: '视频模型',
    description: '分镜视频生成',
    icon: <VideoCameraOutlined />,
    options: (models) => models.videoModels,
  },
  {
    field: 'audioModelId',
    title: '音频模型',
    description: '角色配音与音频生成',
    icon: <SoundOutlined />,
    options: (models) => models.audioModels,
  },
];

const emptyModels: ProjectAiModels = {
  textModels: [],
  imageModels: [],
  videoModels: [],
  audioModels: [],
};

type ProjectAiConfigPageProps = { embedded?: boolean; onCancel?: () => void };

const ProjectAiConfigPage = ({
  embedded = false,
  onCancel,
}: ProjectAiConfigPageProps) => {
  const params = useParams<{ id: string }>();
  const projectId = Number(params.id);
  const { message } = App.useApp();
  const [models, setModels] = useState<ProjectAiModels>(emptyModels);
  const [config, setConfig] = useState<ProjectAiConfig>({ projectId });
  const [savedConfig, setSavedConfig] = useState<ProjectAiConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [canEdit, setCanEdit] = useState(false);

  useEffect(() => {
    if (!projectId) return;
    let active = true;
    setLoading(true);
    setCanEdit(false);
    setSavedConfig(null);
    setModels(emptyModels);
    setConfig({ projectId });
    Promise.all([
      queryProject(projectId),
      queryProjectAiModels(projectId),
      queryProjectAiConfig(projectId),
    ])
      .then(([projectResponse, modelsResponse, configResponse]) => {
        if (!active) return;
        setModels(modelsResponse.data || emptyModels);
        setConfig(configResponse.data);
        setSavedConfig(configResponse.data);
        setCanEdit(canUseProjectFeatures(projectResponse.data));
      })
      .catch(() => {
        if (active) message.error('项目 AI 模型配置加载失败');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [message, projectId]);

  const hasUnavailableSelection = modelSections.some(
    (section) =>
      config[section.field] != null &&
      !section
        .options(models)
        .some((option) => option.id === config[section.field]),
  );
  const disabled = loading || saving || !canEdit || !savedConfig;

  const updateField = (field: ModelField, value: number) => {
    if (disabled) return;
    setConfig((current) => ({ ...current, [field]: value }));
  };

  const cancel = () => {
    if (saving) return;
    if (savedConfig) setConfig(savedConfig);
    onCancel?.();
  };

  const save = async () => {
    if (disabled || hasUnavailableSelection) return;
    setSaving(true);
    try {
      const response = await saveProjectAiConfig(projectId, {
        textModelId: config.textModelId,
        imageModelId: config.imageModelId,
        videoModelId: config.videoModelId,
        audioModelId: config.audioModelId,
      });
      setConfig(response.data);
      setSavedConfig(response.data);
      message.success('项目 AI 模型配置已保存');
    } catch {
      message.error('项目 AI 模型配置保存失败，请重试');
    } finally {
      setSaving(false);
    }
  };

  if (!projectId) return null;

  return (
    <div className={embedded ? styles.embedded : styles.page}>
      {!embedded && <h2 className={styles.heading}>项目 AI 模型</h2>}
      <p className={styles.subtitle}>为当前项目选择各环节使用的模型</p>
      <Spin spinning={loading}>
        <div className={styles.fields}>
          {modelSections.map((section) => {
            const available = section.options(models);
            const selected = config[section.field];
            const unavailable =
              selected != null &&
              !available.some((option) => option.id === selected);
            const options = available.map((option) => ({
              value: option.id,
              label: option.name,
              title: option.description,
            }));
            if (unavailable)
              options.unshift({
                value: selected,
                label: '原选模型已不可用，请重新选择',
                title: undefined,
              });
            return (
              <div className={styles.row} key={section.field}>
                <label
                  className={styles.label}
                  htmlFor={`project-ai-${section.field}`}
                >
                  <span className={styles.icon} aria-hidden="true">
                    {section.icon}
                  </span>
                  <span className={styles.labelText}>
                    <span className={styles.title}>{section.title}</span>
                    <span className={styles.description}>
                      {section.description}
                    </span>
                  </span>
                </label>
                <Select<number>
                  id={`project-ai-${section.field}`}
                  aria-label={section.title}
                  className={styles.select}
                  value={selected ?? undefined}
                  options={options}
                  showSearch={{ optionFilterProp: 'label' }}
                  loading={loading}
                  disabled={
                    !!disabled || (!available.length && selected == null)
                  }
                  placeholder={
                    !loading && !available.length
                      ? '暂无可用模型'
                      : `请选择${section.title}`
                  }
                  notFoundContent="暂无匹配模型"
                  status={unavailable ? 'warning' : undefined}
                  onChange={(value) => updateField(section.field, value)}
                />
              </div>
            );
          })}
        </div>
      </Spin>
      <div className={styles.footer}>
        <span className={styles.scope}>配置仅对当前项目生效</span>
        <div className={styles.actions}>
          <Button disabled={saving} onClick={cancel}>
            取消
          </Button>
          <Button
            type="primary"
            loading={saving}
            disabled={!!disabled || hasUnavailableSelection}
            onClick={save}
          >
            保存配置
          </Button>
        </div>
      </div>
    </div>
  );
};

export default ProjectAiConfigPage;
