import {
  ArrowLeftOutlined,
  CameraOutlined,
  CloseOutlined,
  GlobalOutlined,
  InfoCircleOutlined,
  MobileOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  UploadOutlined,
  VideoCameraOutlined,
} from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { history } from '@umijs/max';
import {
  App,
  Button,
  Empty,
  Flex,
  Image,
  Input,
  Modal,
  Radio,
  Segmented,
  Spin,
  Tooltip,
  Typography,
  Upload,
} from 'antd';
import { useEffect, useState } from 'react';
import { getCurrentTenantId } from '@/services/account-team/auth';
import type { ProjectFormValues } from '@/services/account-team/project';
import type { TenantMember } from '@/services/account-team/types';
import type { PublicStyle } from '../style-library/service';
import {
  aspectRatioOptions,
  breakdownStrengthOptions,
  fileFormatOptions,
  scriptTypeOptions,
} from './options';
import {
  createProject,
  type InspirationCreation,
  type InspirationCreationDetail,
  queryInspirationCreationDetail,
  queryInspirationCreations,
  queryOrganizations,
  queryStyleLibrary,
  queryTenantMembers,
} from './service';
import styles from './index.module.css';

type CreationStep = 1 | 2;
type DramaRegion = 'domestic' | 'overseas';

const inspirationTabs = [
  { key: 'domestic', label: '国内剧', icon: <VideoCameraOutlined /> },
  { key: 'overseas', label: '海外剧', icon: <GlobalOutlined /> },
] as const;

const readFileAsText = async (file: File) => {
  if ('text' in file) {
    return file.text();
  }
  return '';
};

const isTextLikeFile = (file: File) =>
  file.type.startsWith('text/') ||
  file.name.endsWith('.txt') ||
  file.name.endsWith('.md') ||
  file.name.endsWith('.json');

const resolvePromptText = (detail?: InspirationCreationDetail) => {
  if (!detail?.detailJson) {
    return detail?.title || '暂无提示词';
  }
  try {
    const parsed = JSON.parse(detail.detailJson) as Record<string, unknown>;
    const candidates = [
      parsed.prompt,
      parsed.materialPrompt,
      parsed.description,
      typeof parsed.input === 'object' && parsed.input
        ? (parsed.input as Record<string, unknown>).prompt
        : undefined,
    ];
    const prompt = candidates.find(
      (value): value is string => typeof value === 'string' && value.trim().length > 0,
    );
    return prompt?.trim() || detail.title || '暂无提示词';
  } catch {
    return detail.detailJson;
  }
};

const ShortDramaCreationPage = () => {
  const { message } = App.useApp();
  const tenantId = getCurrentTenantId();
  const [step, setStep] = useState<CreationStep>(1);
  const [activeTab, setActiveTab] = useState<DramaRegion>('domestic');
  const [scriptDraft, setScriptDraft] = useState('');
  const [scriptFileName, setScriptFileName] = useState<string>();
  const [coverFileName, setCoverFileName] = useState<string>();
  const [coverPreviewUrl, setCoverPreviewUrl] = useState<string>();
  const [selectedStyle, setSelectedStyle] = useState<PublicStyle>();
  const [styleGallery, setStyleGallery] = useState<PublicStyle[]>([]);
  const [inspirationGallery, setInspirationGallery] = useState<
    InspirationCreation[]
  >([]);
  const [galleryLoading, setGalleryLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [selectedInspiration, setSelectedInspiration] =
    useState<InspirationCreationDetail>();
  const [members, setMembers] = useState<TenantMember[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [projectForm, setProjectForm] = useState<Partial<ProjectFormValues>>({
    coverSource: 'FIRST_FRAME',
    aspectRatio: '16:9',
    fileFormat: 'SCRIPT',
    scriptType: 'PREMIUM_DRAMA',
    breakdownStrength: 'MEDIUM',
  });

  useEffect(() => {
    if (!tenantId) {
      return;
    }
    let active = true;
    setGalleryLoading(true);
    Promise.all([
      queryOrganizations(),
      queryTenantMembers(tenantId),
      queryStyleLibrary({}),
      queryInspirationCreations(),
    ])
      .then(([orgResponse, memberResponse, styleResponse, inspirationResponse]) => {
        if (!active) {
          return;
        }
        const stylesData = styleResponse.data || [];
        void orgResponse;
        setMembers(memberResponse.data || []);
        setStyleGallery(stylesData);
        setInspirationGallery(inspirationResponse.data || []);
        setSelectedStyle(stylesData[0]);
        if (stylesData[0]) {
          setCoverPreviewUrl(stylesData[0].imageUrl);
          setProjectForm((current) => ({
            ...current,
            visualStyle: current.visualStyle || stylesData[0].name,
            coverUrl: current.coverUrl || stylesData[0].imageUrl,
          }));
        }
        setProjectForm((current) =>
          current.ownerId
            ? current
            : {
                ...current,
                ownerId: memberResponse.data?.[0]?.userId,
              },
        );
      })
      .catch(() => {
        message.error('短剧创作数据加载失败');
      })
      .finally(() => {
        if (active) {
          setGalleryLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [tenantId, message]);

  const updateForm = (next: Partial<ProjectFormValues>) => {
    setProjectForm((current) => ({ ...current, ...next }));
  };

  const openInspirationDetail = async (item: InspirationCreation) => {
    const externalId = item.externalId || String(item.id);
    setSelectedInspiration(item);
    setDetailLoading(true);
    try {
      const response = await queryInspirationCreationDetail(externalId);
      setSelectedInspiration(response.data || item);
    } catch {
      message.error('灵感详情加载失败');
    } finally {
      setDetailLoading(false);
    }
  };

  const applyStyleSelection = (style: PublicStyle) => {
    setSelectedStyle(style);
    updateForm({
      visualStyle: style.name,
      coverUrl: style.imageUrl,
      coverSource: projectForm.coverSource === 'UPLOAD' ? 'UPLOAD' : 'FIRST_FRAME',
    });
    if (projectForm.coverSource !== 'UPLOAD') {
      setCoverPreviewUrl(style.imageUrl);
    }
  };

  const handleScriptUpload = async (file: File) => {
    setScriptFileName(file.name);
    if (isTextLikeFile(file)) {
      const text = await readFileAsText(file);
      setScriptDraft(text);
      updateForm({ initialScriptContent: text });
    } else {
      message.info(`已选择 ${file.name}，可继续进入项目配置。`);
    }
    return false;
  };

  const handleCoverUpload = async (file: File) => {
    setCoverFileName(file.name);
    const reader = new FileReader();
    reader.onload = () => {
      const url = String(reader.result || '');
      setCoverPreviewUrl(url);
      updateForm({
        coverUrl: url,
        coverSource: 'UPLOAD',
      });
    };
    reader.readAsDataURL(file);
    return false;
  };

  const goNext = () => {
    updateForm({ initialScriptContent: scriptDraft.trim() || undefined });
    setStep(2);
  };

  const submitProject = async () => {
    const ownerId = projectForm.ownerId || members[0]?.userId;
    if (!ownerId) {
      message.warning('请先选择当前创作团队的负责人');
      return;
    }
    setSubmitting(true);
    try {
      const projectName = projectForm.name?.trim() || '未命名短剧';
      const projectCode =
        projectForm.code?.trim().toUpperCase() || `SHORT_DRAMA_${Date.now()}`;
      const payload: ProjectFormValues = {
        organizationId: projectForm.organizationId ?? null,
        name: projectName,
        code: projectCode,
        description: projectForm.description?.trim(),
        coverUrl: projectForm.coverUrl || selectedStyle?.imageUrl,
        coverSource: projectForm.coverSource || 'FIRST_FRAME',
        ownerId,
        startDate: projectForm.startDate,
        endDate: projectForm.endDate,
        aspectRatio: projectForm.aspectRatio,
        fileFormat: projectForm.fileFormat,
        scriptType: projectForm.scriptType,
        breakdownStrength: projectForm.breakdownStrength,
        visualStyle: projectForm.visualStyle || selectedStyle?.name,
        initialScriptContent: scriptDraft.trim() || undefined,
      };
      const response = await createProject(payload);
      message.success('项目已创建');
      history.push(`/projects/${response.data.id}/production-workbench/script`);
    } finally {
      setSubmitting(false);
    }
  };

  if (!tenantId) {
    return (
      <PageContainer>
        <Empty description="请先在我的团队中选择当前创作团队" />
      </PageContainer>
    );
  }

  const firstStep = (
    <div className={styles.creationShell}>
      <Typography.Title className={styles.heroTitle} level={1}>
        今天想创作<span>什么样的故事?</span>
      </Typography.Title>

      <section className={styles.scriptPanel}>
        <div className={styles.regionTabs}>
          {inspirationTabs.map((item) => (
            <button
              className={activeTab === item.key ? styles.regionTabActive : styles.regionTab}
              key={item.key}
              onClick={() => setActiveTab(item.key)}
              type="button"
            >
              {item.icon}
              {item.label}
            </button>
          ))}
        </div>
        <div className={styles.scriptInputWrap}>
          <Input.TextArea
            autoSize={false}
            className={styles.scriptTextarea}
            maxLength={50000}
            onChange={(event) => {
              setScriptDraft(event.target.value);
              updateForm({ initialScriptContent: event.target.value });
            }}
            placeholder="复制粘贴剧本，或上传文件（支持txt、docx、pdf）"
            value={scriptDraft}
          />
          <Upload
            accept=".txt,.md,.docx,.pdf,text/plain,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            beforeUpload={(file) => {
              void handleScriptUpload(file as File);
              return false;
            }}
            showUploadList={false}
          >
            <Tooltip title="上传剧本文件">
              <Button
                aria-label="上传剧本文件"
                className={styles.uploadButton}
                icon={<PlusOutlined />}
                shape="circle"
                type="text"
              />
            </Tooltip>
          </Upload>
          <Tooltip title="进入创作设置">
            <Button
              aria-label="开始创作"
              className={styles.magicButton}
              icon={<PlayCircleOutlined />}
              onClick={goNext}
              shape="round"
              type="primary"
            >
              开始创作
            </Button>
          </Tooltip>
        </div>
      </section>

      <Flex className={styles.uploadHint} gap={14} justify="center" wrap>
        <Typography.Text type="secondary">
          <InfoCircleOutlined /> 请确认上传的剧本有合法版权
        </Typography.Text>
        <button className={styles.skipButton} onClick={goNext} type="button">
          跳过上传，创建空白剧本
        </button>
      </Flex>

      {(scriptFileName || scriptDraft.trim()) && (
        <Typography.Text className={styles.readyText} type="secondary">
          {scriptFileName ? `已选择：${scriptFileName}` : '剧本内容已就绪'}
        </Typography.Text>
      )}

      <section className={styles.inspirationSection}>
        <Typography.Text className={styles.sectionTitle}>灵感广场</Typography.Text>
        <Spin spinning={galleryLoading}>
          {inspirationGallery.length ? (
            <div className={styles.inspirationGrid}>
              {inspirationGallery.map((item) => {
                const title = item.title?.trim() || `灵感 ${item.id}`;
                const isVideo = item.mimeType?.startsWith('video/');
                return (
                  <button
                    className={styles.inspirationCard}
                    aria-label={title}
                    key={item.id}
                    onClick={() => {
                      void openInspirationDetail(item);
                    }}
                    type="button"
                  >
                    {isVideo ? (
                      <video muted playsInline preload="metadata" src={item.localUrl} />
                    ) : (
                      <img alt={title} src={item.localUrl} />
                    )}
                    <span className={styles.inspirationOverlay}>
                      <strong>{title}</strong>
                    </span>
                  </button>
                );
              })}
            </div>
          ) : (
            <Empty description="暂无灵感内容" />
          )}
        </Spin>
      </section>
    </div>
  );

  const secondStep = (
    <div className={styles.settingsPage}>
      <header className={styles.settingsHeader}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => setStep(1)} type="text">
          初始设定
        </Button>
        <div className={styles.costNote}>
          剧本每100字消耗5积分，实际消耗与最终上传的剧本字数相关
          <InfoCircleOutlined />
          <Button
            className={styles.startButton}
            icon={<PlayCircleOutlined />}
            loading={submitting}
            onClick={submitProject}
            type="primary"
          >
            开始创作 12
          </Button>
        </div>
      </header>

      <main className={styles.settingsBody}>
        <section className={styles.settingBlock}>
          <Typography.Text className={styles.settingTitle}>
            画面比例 <InfoCircleOutlined />
          </Typography.Text>
          <Segmented
            className={styles.pillSegment}
            options={aspectRatioOptions.map((item) => ({
              ...item,
              icon: item.value === '16:9' ? <VideoCameraOutlined /> : <MobileOutlined />,
            }))}
            onChange={(value) => updateForm({ aspectRatio: String(value) })}
            shape="round"
            value={projectForm.aspectRatio}
          />
        </section>

        <section className={styles.settingBlock}>
          <Typography.Text className={styles.settingTitle}>
            文件格式 <InfoCircleOutlined />
          </Typography.Text>
          <Segmented
            className={styles.pillSegment}
            onChange={(value) => updateForm({ fileFormat: String(value) })}
            options={fileFormatOptions}
            shape="round"
            value={projectForm.fileFormat}
          />
        </section>

        <section className={styles.settingBlock}>
          <Typography.Text className={styles.settingTitle}>
            剧本类型 <InfoCircleOutlined />
          </Typography.Text>
          <Segmented
            className={styles.pillSegment}
            onChange={(value) => updateForm({ scriptType: String(value) })}
            options={scriptTypeOptions}
            shape="round"
            value={projectForm.scriptType}
          />
        </section>

        <section className={styles.settingBlock}>
          <Typography.Text className={styles.settingTitle}>
            剧本解析力度 <InfoCircleOutlined />
          </Typography.Text>
          <Flex align="center" gap={14} wrap>
            <Typography.Text type="secondary">场景解析力度</Typography.Text>
            <Radio.Group
              buttonStyle="solid"
              className={styles.strengthGroup}
              onChange={(event) => updateForm({ breakdownStrength: event.target.value })}
              optionType="button"
              options={breakdownStrengthOptions}
              value={projectForm.breakdownStrength}
            />
          </Flex>
        </section>

        <section className={styles.settingBlock}>
          <Typography.Text className={styles.settingTitle}>剧本封面</Typography.Text>
          <Flex align="center" gap={10} wrap>
            <div className={styles.coverPreview}>
              {coverPreviewUrl ? (
                <Image
                  alt="封面预览"
                  height={110}
                  preview={false}
                  src={coverPreviewUrl}
                  width={180}
                />
              ) : (
                <CameraOutlined />
              )}
            </div>
            <Typography.Text type="secondary">
              *默认分镜的第一张图，也可上传图片
            </Typography.Text>
            <Upload
              accept="image/*"
              beforeUpload={(file) => {
                void handleCoverUpload(file as File);
                return false;
              }}
              showUploadList={false}
            >
              <Button icon={<UploadOutlined />}>{coverFileName || '上传图片'}</Button>
            </Upload>
          </Flex>
        </section>

        <section className={styles.settingBlock}>
          <Typography.Text className={styles.settingTitle}>画面风格</Typography.Text>
          <Typography.Text className={styles.selectedStyleText} type="secondary">
            已选风格
          </Typography.Text>
          {selectedStyle && (
            <button
              className={styles.selectedStyleCard}
              onClick={() => applyStyleSelection(selectedStyle)}
              type="button"
            >
              <img alt={selectedStyle.name} src={selectedStyle.imageUrl} />
              <span>{selectedStyle.name}</span>
            </button>
          )}
          <Typography.Text className={styles.selectedStyleText} type="secondary">
            平台风格
          </Typography.Text>
          <Spin spinning={galleryLoading}>
            <div className={styles.styleStrip}>
              {styleGallery.slice(0, 12).map((style) => {
                const active = selectedStyle?.id === style.id;
                return (
                  <button
                    className={active ? styles.styleCardActive : styles.styleCard}
                    key={style.externalId}
                    onClick={() => applyStyleSelection(style)}
                    type="button"
                  >
                    <img alt={style.name} src={style.imageUrl} />
                    <span>{style.name}</span>
                  </button>
                );
              })}
            </div>
          </Spin>
        </section>
      </main>
    </div>
  );

  return (
    <PageContainer className={styles.pageContainer} title={false}>
      {step === 1 ? firstStep : secondStep}
      <Modal
        centered
        closable
        closeIcon={<CloseOutlined />}
        destroyOnHidden
        footer={null}
        mask={{ enabled: true, blur: true }}
        onCancel={() => setSelectedInspiration(undefined)}
        open={Boolean(selectedInspiration)}
        title={null}
        width={820}
        styles={{
          root: {
            background: 'transparent',
          },
          body: {
            padding: 0,
          },
          close: {
            top: 14,
            insetInlineEnd: 14,
            color: '#fff',
            width: 34,
            height: 34,
            borderRadius: '50%',
            background: 'rgba(0, 0, 0, 0.46)',
          },
        }}
      >
        {selectedInspiration && (
          <Spin spinning={detailLoading}>
            <div className={styles.detailMedia}>
              {selectedInspiration.mimeType?.startsWith('video/') ? (
                <video controls src={selectedInspiration.localUrl}>
                  <track kind="captions" />
                </video>
              ) : (
                <img
                  alt={selectedInspiration.title || '灵感素材'}
                  src={selectedInspiration.localUrl}
                />
              )}
            </div>
            <div className={styles.detailInfoPanel}>
              <div className={styles.detailThumb}>
                {selectedInspiration.mimeType?.startsWith('video/') ? (
                  <video muted playsInline src={selectedInspiration.localUrl} />
                ) : (
                  <img
                    alt={selectedInspiration.title || '灵感缩略图'}
                    src={selectedInspiration.localUrl}
                  />
                )}
              </div>
              <div className={styles.detailCopy}>
                <Typography.Text className={styles.detailTitle}>
                  {selectedInspiration.title || `灵感 ${selectedInspiration.id}`}
                </Typography.Text>
                <Typography.Text className={styles.detailLabel}>素材提示词</Typography.Text>
                <Typography.Paragraph className={styles.detailPrompt}>
                  {resolvePromptText(selectedInspiration)}
                </Typography.Paragraph>
                <Typography.Text className={styles.detailMeta} type="secondary">
                  {[
                    selectedInspiration.taskType,
                    selectedInspiration.authorName,
                    selectedInspiration.sourceCreatedAt?.slice(0, 10),
                  ]
                    .filter(Boolean)
                    .join(' · ')}
                </Typography.Text>
              </div>
            </div>
          </Spin>
        )}
      </Modal>
    </PageContainer>
  );
};

export default ShortDramaCreationPage;
