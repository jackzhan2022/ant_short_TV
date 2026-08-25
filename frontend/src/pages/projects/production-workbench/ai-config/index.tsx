import {
  AudioOutlined,
  CheckCircleFilled,
  PictureOutlined,
  RobotOutlined,
  SoundOutlined,
  VideoCameraOutlined,
} from '@ant-design/icons';
import { useParams } from '@umijs/max';
import { App, Button, Empty, Flex, Spin, Tag, Typography } from 'antd';
import type { ReactNode } from 'react';
import { useEffect, useMemo, useState } from 'react';
import {
  hasProjectPermission,
  queryProject,
} from '@/services/account-team/project';
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

type ModelField = 'textModelId' | 'imageModelId' | 'videoModelId' | 'audioModelId';

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
    description: '用于剧本生成、改写、元素提取和提示词生成',
    icon: <RobotOutlined />,
    options: (models) => models.textModels,
  },
  {
    field: 'imageModelId',
    title: '图片模型',
    description: '用于角色、场景、道具和分镜图片生成',
    icon: <PictureOutlined />,
    options: (models) => models.imageModels,
  },
  {
    field: 'videoModelId',
    title: '视频模型',
    description: '用于分镜视频与镜头合成任务',
    icon: <VideoCameraOutlined />,
    options: (models) => models.videoModels,
  },
  {
    field: 'audioModelId',
    title: '音频模型',
    description: '用于配音、音频和字幕相关任务',
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

const ModelOptionCard = ({
  option,
  selected,
  onSelect,
}: {
  option: ProjectModelOption;
  selected: boolean;
  onSelect: () => void;
}) => (
  <button
    type="button"
    onClick={onSelect}
    style={{
      position: 'relative',
      minHeight: 108,
      padding: '16px 18px',
      textAlign: 'left',
      border: selected ? '1px solid #6678ff' : '1px solid #e3e8f2',
      borderRadius: 8,
      background: selected ? '#f3f5ff' : '#fff',
      cursor: 'pointer',
      transition: 'all 160ms ease',
    }}
  >
    {selected && (
      <CheckCircleFilled
        style={{
          position: 'absolute',
          top: 12,
          right: 12,
          color: '#5368f5',
          fontSize: 17,
        }}
      />
    )}
    <Typography.Text strong>{option.name}</Typography.Text>
    <Typography.Paragraph
      type="secondary"
      ellipsis={{ rows: 2 }}
      style={{ margin: '8px 24px 0 0', fontSize: 13, lineHeight: '20px' }}
    >
      {option.description || '平台暂未填写模型描述'}
    </Typography.Paragraph>
  </button>
);

const ProjectAiConfigPage = () => {
  const params = useParams<{ id: string }>();
  const projectId = Number(params.id);
  const { message } = App.useApp();
  const [models, setModels] = useState<ProjectAiModels>(emptyModels);
  const [config, setConfig] = useState<ProjectAiConfig>({
    projectId,
    textModelId: null,
    imageModelId: null,
    videoModelId: null,
    audioModelId: null,
  });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [canEdit, setCanEdit] = useState(false);

  useEffect(() => {
    if (!projectId) {
      return;
    }
    let active = true;
    setLoading(true);
    Promise.all([
      queryProject(projectId),
      queryProjectAiModels(projectId),
      queryProjectAiConfig(projectId),
    ])
      .then(([projectResponse, modelsResponse, configResponse]) => {
        if (!active) {
          return;
        }
        setModels(modelsResponse.data || emptyModels);
        setConfig(configResponse.data);
        setCanEdit(
          hasProjectPermission(
            projectResponse.data,
            'PROJECT_AI_CONFIG_EDIT',
          ),
        );
      })
      .catch(() => {
        if (active) {
          message.error('项目 AI 模型配置加载失败');
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [message, projectId]);

  const selectedCount = useMemo(
    () =>
      modelSections.filter((section) => config[section.field] != null).length,
    [config],
  );

  const updateField = (field: ModelField, value: number) => {
    if (!canEdit) {
      return;
    }
    setConfig((current) => ({ ...current, [field]: value }));
  };

  const save = async () => {
    if (!canEdit) {
      return;
    }
    setSaving(true);
    try {
      const response = await saveProjectAiConfig(projectId, {
        textModelId: config.textModelId,
        imageModelId: config.imageModelId,
        videoModelId: config.videoModelId,
        audioModelId: config.audioModelId,
      });
      setConfig(response.data);
      message.success('项目 AI 模型配置已保存');
    } finally {
      setSaving(false);
    }
  };

  if (!projectId) {
    return null;
  }

  return (
    <div
      style={{
        minHeight: 'calc(100vh - 100px)',
        padding: '18px 28px 70px',
        background: '#f6f7fb',
        boxSizing: 'border-box',
      }}
    >
      <div style={{ maxWidth: 1500, margin: '0 auto' }}>
        <Flex justify="space-between" align="center" style={{ marginBottom: 18 }}>
          <div>
            <Typography.Title level={4} style={{ margin: 0, fontSize: 18 }}>
              项目 AI 模型
            </Typography.Title>
            <Typography.Text type="secondary">
              仅可选择平台开放的模型，API Key、Base URL 和真实 Model Code 由平台统一管理
            </Typography.Text>
          </div>
          <Flex align="center" gap={12}>
            <Tag icon={<AudioOutlined />}>已选择 {selectedCount} 类</Tag>
            <Button
              type="primary"
              loading={saving}
              disabled={!canEdit}
              onClick={save}
            >
              保存配置
            </Button>
          </Flex>
        </Flex>

        <Spin spinning={loading}>
          <div style={{ display: 'grid', gap: 16 }}>
            {modelSections.map((section) => {
              const options = section.options(models);
              const selected = config[section.field];
              return (
                <section
                  key={section.field}
                  style={{
                    padding: 18,
                    border: '1px solid #e5eaf3',
                    borderRadius: 8,
                    background: '#fff',
                  }}
                >
                  <Flex justify="space-between" align="center">
                    <Flex align="center" gap={10}>
                      <span
                        style={{
                          display: 'grid',
                          placeItems: 'center',
                          width: 32,
                          height: 32,
                          borderRadius: 8,
                          color: '#5368f5',
                          background: '#eef1ff',
                          fontSize: 17,
                        }}
                      >
                        {section.icon}
                      </span>
                      <div>
                        <Typography.Text strong>{section.title}</Typography.Text>
                        <div style={{ marginTop: 3 }}>
                          <Typography.Text type="secondary">
                            {section.description}
                          </Typography.Text>
                        </div>
                      </div>
                    </Flex>
                    {selected != null && <Tag color="blue">已选择</Tag>}
                  </Flex>

                  {options.length ? (
                    <div
                      style={{
                        display: 'grid',
                        gridTemplateColumns:
                          'repeat(auto-fill, minmax(260px, 1fr))',
                        gap: 12,
                        marginTop: 16,
                      }}
                    >
                      {options.map((option) => (
                        <ModelOptionCard
                          key={option.id}
                          option={option}
                          selected={selected === option.id}
                          onSelect={() => updateField(section.field, option.id)}
                        />
                      ))}
                    </div>
                  ) : (
                    <Empty
                      image={Empty.PRESENTED_IMAGE_SIMPLE}
                      description="暂无平台开放模型"
                      style={{ margin: '26px 0 10px' }}
                    />
                  )}
                </section>
              );
            })}
          </div>
        </Spin>
      </div>
    </div>
  );
};

export default ProjectAiConfigPage;
