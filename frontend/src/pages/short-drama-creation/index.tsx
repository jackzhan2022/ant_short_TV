import {
  ArrowLeftOutlined,
  CameraOutlined,
  CloseOutlined,
  CopyOutlined,
  GlobalOutlined,
  InfoCircleOutlined,
  MobileOutlined,
  PlayCircleOutlined,
  SettingOutlined,
  UploadOutlined,
  VideoCameraOutlined,
} from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { history, useModel } from '@umijs/max';
import {
  App,
  Button,
  Empty,
  Flex,
  Input,
  Modal,
  Radio,
  Select,
  Spin,
  Switch,
  Tabs,
  Tooltip,
  Typography,
  Upload,
} from 'antd';
import { useCallback, useEffect, useRef, useState } from 'react';
import ClickToPlayVideo, {
  stopActiveVideo,
} from '@/components/ClickToPlayVideo';
import LazyMediaImage from '@/components/LazyMediaImage';
import { getCurrentTenantId } from '@/services/account-team/auth';
import type { ProjectFormValues } from '@/services/account-team/project';
import type { TenantMember } from '@/services/account-team/types';
import type { MediaUploadHandle } from '@/services/mediaUpload';
import {
  type PublicStyle,
  queryStyleCategories,
} from '../style-library/service';
import InspirationGallery from './InspirationGallery';
import InspirationManagementDrawer from './InspirationManagementDrawer';
import styles from './index.module.css';
import {
  aspectRatioOptions,
  breakdownStrengthOptions,
  fileFormatOptions,
  scriptTypeOptions,
} from './options';
import ScriptContentImport from './ScriptContentImport';
import usePlatformStyleGallery from './usePlatformStyleGallery';
import {
  createProject,
  bindProjectCover,
  type InspirationCreation,
  type InspirationCreationDetail,
  queryInspirationCreationDetail,
  queryInspirationCreations,
  queryTenantMembers,
  startProjectCoverUpload,
} from './service';

type CreationStep = 1 | 2;
type DramaRegion = 'domestic' | 'overseas';

const inspirationTabs = [
  { key: 'domestic', label: '国内剧', icon: <VideoCameraOutlined /> },
  { key: 'overseas', label: '海外剧', icon: <GlobalOutlined /> },
] as const;
const inspirationPageSize = 8;

const resolvePromptText = (detail?: InspirationCreationDetail) => {
  if (detail?.promptText?.trim()) return detail.promptText.trim();
  if (!detail?.detailJson) {
    return detail?.title || '暂无提示词';
  }
  try {
    const parsed =
      typeof detail.detailJson === 'string'
        ? (JSON.parse(detail.detailJson) as Record<string, unknown>)
        : detail.detailJson;
    const candidates = [
      parsed.prompt,
      parsed.materialPrompt,
      parsed.description,
      typeof parsed.input === 'object' && parsed.input
        ? (parsed.input as Record<string, unknown>).prompt
        : undefined,
    ];
    const prompt = candidates.find(
      (value): value is string =>
        typeof value === 'string' && value.trim().length > 0,
    );
    return prompt?.trim() || detail.title || '暂无提示词';
  } catch {
    return detail.title || '暂无提示词';
  }
};

const ShortDramaCreationPage = () => {
  const { message } = App.useApp();
  const { initialState } = useModel('@@initialState');
  const isAdmin = initialState?.currentUser?.access === 'admin';
  const tenantId = getCurrentTenantId();
  const [step, setStep] = useState<CreationStep>(1);
  const [activeTab, setActiveTab] = useState<DramaRegion>('domestic');
  const [scriptDraft, setScriptDraft] = useState('');
  const [scriptSourceLabel, setScriptSourceLabel] = useState<string>();
  const [coverFileName, setCoverFileName] = useState<string>();
  const [coverPreviewUrl, setCoverPreviewUrl] = useState<string>();
  const [coverUploadSessionToken, setCoverUploadSessionToken] = useState<string>();
  const coverUploadHandleRef = useRef<MediaUploadHandle | undefined>(undefined);
  const [selectedStyle, setSelectedStyle] = useState<PublicStyle>();
  const {
    category: styleCategory, changeCategory: changeStyleCategory,
    gallery: styleGallery, loading: galleryLoading, error: styleError,
    hasMore: stylesHaveMore, bottomRef: styleBottomRef,
    loadMore: loadMoreStyles, retry: retryStyles,
  } = usePlatformStyleGallery(step === 2);
  const [styleCategories, setStyleCategories] = useState<string[]>([]);
  const [inspirationGallery, setInspirationGallery] = useState<
    InspirationCreation[]
  >([]);
  const [inspirationLoading, setInspirationLoading] = useState(false);
  const [inspirationHasMore, setInspirationHasMore] = useState(true);
  const [inspirationPage, setInspirationPage] = useState(0);
  const [hasScrolledGallery, setHasScrolledGallery] = useState(false);
  const [galleryScrollVersion, setGalleryScrollVersion] = useState(0);
  const [detailLoading, setDetailLoading] = useState(false);
  const [selectedInspiration, setSelectedInspiration] =
    useState<InspirationCreationDetail>();
  const [managementOpen, setManagementOpen] = useState(false);
  const [members, setMembers] = useState<TenantMember[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const inspirationBottomRef = useRef<HTMLDivElement>(null);
  const inspirationRequestInFlightRef = useRef(false);
  const lastPaginationScrollVersionRef = useRef(-1);
  const detailRequestRef = useRef(0);
  const [projectForm, setProjectForm] = useState<Partial<ProjectFormValues>>({
    coverSource: 'FIRST_FRAME',
    aspectRatio: '16:9',
    videoResolution: '720p',
    videoGenerateAudio: true,
    videoWatermark: false,
    fileFormat: 'SCRIPT',
    scriptType: 'PREMIUM_DRAMA',
    breakdownStrength: 'MEDIUM',
  });

  const loadInspirationPage = useCallback(
    async (page: number, append = false) => {
      if (inspirationRequestInFlightRef.current) {
        return;
      }
      inspirationRequestInFlightRef.current = true;
      setInspirationLoading(true);
      try {
        const response = await queryInspirationCreations({
          page,
          pageSize: inspirationPageSize,
        });
        const data = response.data;
        const records = data?.records || [];
        setInspirationGallery((current) =>
          append ? [...current, ...records] : records,
        );
        setInspirationPage(data?.current || page);
        setInspirationHasMore(
          (data?.current || page) * (data?.pageSize || inspirationPageSize) <
            (data?.total || 0),
        );
      } catch {
        message.error('灵感广场加载失败');
      } finally {
        inspirationRequestInFlightRef.current = false;
        setInspirationLoading(false);
      }
    },
    [message],
  );

  useEffect(() => {
    if (!tenantId) {
      return;
    }
    let active = true;
    queryTenantMembers(tenantId)
      .then((memberResponse) => {
        if (!active) {
          return;
        }
        setMembers(memberResponse.data || []);
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
      });
    return () => {
      active = false;
    };
  }, [tenantId, message]);

  useEffect(() => {
    let active = true;
    void queryStyleCategories()
      .then((response) => {
        if (active) setStyleCategories(response.data || []);
      })
      .catch(() => {
        if (active) setStyleCategories([]);
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    const first = styleGallery[0];
    if (!first) return;
    setSelectedStyle((current) => current || first);
    setCoverPreviewUrl((current) => current || first.imageUrl);
    setProjectForm((current) => ({
      ...current,
      visualStyle: current.visualStyle || first.name,
      coverUrl: current.coverUrl || first.imageUrl,
    }));
  }, [styleGallery]);

  useEffect(
    () => () => {
      detailRequestRef.current += 1;
    },
    [],
  );

  useEffect(() => {
    void loadInspirationPage(1);
  }, [loadInspirationPage]);

  useEffect(() => {
    const markScrolled = () => {
      setHasScrolledGallery(true);
      setGalleryScrollVersion((current) => current + 1);
    };
    window.addEventListener('scroll', markScrolled, { passive: true });
    return () => window.removeEventListener('scroll', markScrolled);
  }, []);

  useEffect(() => {
    const target = inspirationBottomRef.current;
    if (
      !target ||
      !hasScrolledGallery ||
      !inspirationHasMore ||
      inspirationLoading
    ) {
      return;
    }
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (
          entry.isIntersecting &&
          lastPaginationScrollVersionRef.current !== galleryScrollVersion
        ) {
          lastPaginationScrollVersionRef.current = galleryScrollVersion;
          void loadInspirationPage(inspirationPage + 1, true);
        }
      },
      { rootMargin: '240px 0px' },
    );
    observer.observe(target);
    return () => observer.disconnect();
  }, [
    galleryScrollVersion,
    hasScrolledGallery,
    inspirationHasMore,
    inspirationLoading,
    inspirationPage,
    loadInspirationPage,
  ]);

  const updateForm = (next: Partial<ProjectFormValues>) => {
    setProjectForm((current) => ({ ...current, ...next }));
  };

  const openInspirationDetail = async (item: InspirationCreation) => {
    const request = ++detailRequestRef.current;
    setSelectedInspiration(item);
    setDetailLoading(true);
    try {
      const response = await queryInspirationCreationDetail(item.id);
      if (request === detailRequestRef.current)
        setSelectedInspiration(response.data || item);
    } catch {
      if (request === detailRequestRef.current)
        message.error('灵感详情加载失败');
    } finally {
      if (request === detailRequestRef.current) setDetailLoading(false);
    }
  };

  const applyStyleSelection = (style: PublicStyle) => {
    setSelectedStyle(style);
    updateForm({
      visualStyle: style.name,
      coverUrl: style.imageUrl,
      coverSource:
        projectForm.coverSource === 'UPLOAD' ? 'UPLOAD' : 'FIRST_FRAME',
    });
    if (projectForm.coverSource !== 'UPLOAD') {
      setCoverPreviewUrl(style.imageUrl);
    }
  };

  const handleCoverUpload = async (file: File) => {
    coverUploadHandleRef.current?.cancel();
    coverUploadHandleRef.current = undefined;
    setCoverUploadSessionToken(undefined);
    setCoverFileName(file.name);
    if (coverPreviewUrl?.startsWith('blob:')) URL.revokeObjectURL(coverPreviewUrl);
    const preview = URL.createObjectURL(file);
    setCoverPreviewUrl(preview);
    updateForm({ coverUrl: undefined, coverSource: 'UPLOAD' });
    try {
      const handle = await startProjectCoverUpload(file);
      coverUploadHandleRef.current = handle;
      const uploaded = await handle.attempt;
      if (coverUploadHandleRef.current === handle) {
        setCoverUploadSessionToken(uploaded.sessionToken);
      }
    } catch {
      if (coverUploadHandleRef.current) coverUploadHandleRef.current = undefined;
      message.error('封面上传失败，请重试');
    }
    return false;
  };

  useEffect(() => () => {
    coverUploadHandleRef.current?.cancel();
    if (coverPreviewUrl?.startsWith('blob:')) URL.revokeObjectURL(coverPreviewUrl);
  }, [coverPreviewUrl]);

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
      if (projectForm.coverSource === 'UPLOAD' && !coverUploadSessionToken) {
        message.warning('封面仍在上传，请稍后再试');
        return;
      }
      const projectName = projectForm.name?.trim() || '未命名短剧';
      const projectCode =
        projectForm.code?.trim().toUpperCase() || `SHORT_DRAMA_${Date.now()}`;
      const payload: ProjectFormValues = {
        name: projectName,
        code: projectCode,
        description: projectForm.description?.trim(),
        coverUrl: projectForm.coverSource === 'UPLOAD'
          ? undefined
          : projectForm.coverUrl || selectedStyle?.imageUrl,
        coverSource: projectForm.coverSource || 'FIRST_FRAME',
        ownerId,
        startDate: projectForm.startDate,
        endDate: projectForm.endDate,
        aspectRatio: projectForm.aspectRatio,
        videoResolution: projectForm.videoResolution,
        videoGenerateAudio: projectForm.videoGenerateAudio,
        videoWatermark: projectForm.videoWatermark,
        fileFormat: projectForm.fileFormat,
        scriptType: projectForm.scriptType,
        breakdownStrength: projectForm.breakdownStrength,
        visualStyle: projectForm.visualStyle || selectedStyle?.name,
        scriptName: projectForm.scriptName?.trim() || undefined,
        initialScriptContent: scriptDraft.trim() || undefined,
      };
      const response = await createProject(payload);
      let coverBound = true;
      if (projectForm.coverSource === 'UPLOAD' && coverUploadSessionToken) {
        try {
          await bindProjectCover(response.data.id, coverUploadSessionToken);
        } catch {
          coverBound = false;
          message.warning('项目已创建，封面处理失败，可在项目中重新上传');
        }
      }
      if (coverBound) message.success('项目已创建');
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
              className={
                activeTab === item.key
                  ? styles.regionTabActive
                  : styles.regionTab
              }
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
            placeholder="复制粘贴剧本，或导入文件（支持 txt、md、docx）"
            value={scriptDraft}
          />
          <ScriptContentImport
            className={styles.uploadButton}
            currentContent={scriptDraft}
            onImport={(content, sourceLabel) => {
              setScriptDraft(content);
              setScriptSourceLabel(sourceLabel);
              updateForm({ initialScriptContent: content });
            }}
          />
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

      {(scriptSourceLabel || scriptDraft.trim()) && (
        <Typography.Text className={styles.readyText} type="secondary">
          {scriptSourceLabel || '剧本内容已就绪'}
        </Typography.Text>
      )}

      <section className={styles.inspirationSection}>
        <Flex align="center" justify="space-between">
          <Typography.Text className={styles.sectionTitle}>
            灵感广场
          </Typography.Text>
          {isAdmin && (
            <Tooltip title="管理灵感广场">
              <Button
                aria-label="管理灵感广场"
                icon={<SettingOutlined />}
                type="text"
                onClick={() => setManagementOpen(true)}
              />
            </Tooltip>
          )}
        </Flex>
        <Spin spinning={inspirationLoading && !inspirationGallery.length}>
          {inspirationGallery.length ? (
            <InspirationGallery
              items={inspirationGallery}
              onSelect={(item) => {
                void openInspirationDetail(item);
              }}
            />
          ) : (
            <Empty description="暂无灵感内容" />
          )}
          {inspirationHasMore && (
            <div
              className={styles.inspirationLoadMore}
              ref={inspirationBottomRef}
            >
              {inspirationLoading ? '加载中...' : ''}
            </div>
          )}
        </Spin>
      </section>
    </div>
  );

  const platformStyleGallery = (
    <>
      <div className={styles.styleStrip} aria-busy={galleryLoading}>
        {styleGallery.map((style) => {
          const active = selectedStyle?.id === style.id;
          return (
            <button
              className={active ? styles.styleCardActive : styles.styleCard}
              aria-label={style.name}
              aria-pressed={active}
              key={style.id}
              onClick={() => applyStyleSelection(style)}
              type="button"
            >
              <LazyMediaImage native height="100%" alt={style.name} src={style.imageUrl} />
              <span>{style.name}</span>
            </button>
          );
        })}
      </div>
      <section ref={styleBottomRef} className={styles.styleLoadMore} aria-label="风格加载状态" aria-live="polite">
        {galleryLoading ? <><Spin size="small" /> 正在加载风格...</>
          : styleError ? <div role="alert">风格加载失败 <Button onClick={retryStyles}>重试加载风格</Button></div>
          : stylesHaveMore ? <Button onClick={loadMoreStyles}>加载更多风格</Button>
          : styleGallery.length ? '已展示全部风格'
          : <Empty description="当前分类暂无平台风格" image={Empty.PRESENTED_IMAGE_SIMPLE} />}
      </section>
    </>
  );

  const secondStep = (
    <div className={styles.settingsPage}>
      <header className={styles.settingsHeader}>
        <Button
          icon={<ArrowLeftOutlined />}
          onClick={() => setStep(1)}
          type="text"
        >
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
            剧本名称
          </Typography.Text>
          <Input
            aria-label="剧本名称"
            maxLength={200}
            onChange={(event) => updateForm({ scriptName: event.target.value })}
            placeholder="请输入剧本名称"
            value={projectForm.scriptName || ''}
          />
        </section>

        <section className={styles.settingBlock}>
          <Typography.Text className={styles.settingTitle}>
            视频生成 <InfoCircleOutlined />
          </Typography.Text>
          <Flex gap={16} align="center" wrap>
            <Select
              aria-label="默认视频分辨率"
              value={projectForm.videoResolution}
              onChange={(value) => updateForm({ videoResolution: value })}
              options={['480p', '720p', '1080p', '4k'].map((value) => ({
                label: value,
                value,
              }))}
              style={{ width: 132 }}
            />
            <Flex gap={8} align="center">
              <Typography.Text>生成音频</Typography.Text>
              <Switch
                aria-label="默认生成音频"
                checked={projectForm.videoGenerateAudio !== false}
                onChange={(checked) =>
                  updateForm({ videoGenerateAudio: checked })
                }
              />
            </Flex>
            <Flex gap={8} align="center">
              <Typography.Text>视频水印</Typography.Text>
              <Switch
                aria-label="默认视频水印"
                checked={projectForm.videoWatermark === true}
                onChange={(checked) => updateForm({ videoWatermark: checked })}
              />
            </Flex>
          </Flex>
        </section>

        <section className={styles.settingBlock}>
          <Typography.Text className={styles.settingTitle}>
            画面比例 <InfoCircleOutlined />
          </Typography.Text>
          <Radio.Group
            buttonStyle="solid"
            className={styles.strengthGroup}
            options={aspectRatioOptions.map((item) => ({
              ...item,
              label: (
                <>
                  {item.value === '16:9' ? (
                    <VideoCameraOutlined />
                  ) : (
                    <MobileOutlined />
                  )}{' '}
                  {item.label}
                </>
              ),
            }))}
            onChange={(event) =>
              updateForm({ aspectRatio: String(event.target.value) })
            }
            optionType="button"
            value={projectForm.aspectRatio}
          />
        </section>

        <section className={styles.settingBlock}>
          <Typography.Text className={styles.settingTitle}>
            文件格式 <InfoCircleOutlined />
          </Typography.Text>
          <Radio.Group
            buttonStyle="solid"
            className={styles.strengthGroup}
            onChange={(event) =>
              updateForm({ fileFormat: String(event.target.value) })
            }
            optionType="button"
            options={fileFormatOptions}
            value={projectForm.fileFormat}
          />
        </section>

        <section className={styles.settingBlock}>
          <Typography.Text className={styles.settingTitle}>
            剧本类型 <InfoCircleOutlined />
          </Typography.Text>
          <Radio.Group
            buttonStyle="solid"
            className={styles.strengthGroup}
            onChange={(event) =>
              updateForm({ scriptType: String(event.target.value) })
            }
            optionType="button"
            options={scriptTypeOptions}
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
              onChange={(event) =>
                updateForm({ breakdownStrength: event.target.value })
              }
              optionType="button"
              options={breakdownStrengthOptions}
              value={projectForm.breakdownStrength}
            />
          </Flex>
        </section>

        <section className={styles.settingBlock}>
          <Typography.Text className={styles.settingTitle}>
            剧本封面
          </Typography.Text>
          <Flex align="center" gap={10} wrap>
            <div className={styles.coverPreview}>
              {coverPreviewUrl ? (
                <LazyMediaImage
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
              <Button icon={<UploadOutlined />}>
                {coverFileName || '上传图片'}
              </Button>
            </Upload>
          </Flex>
        </section>

        <section className={styles.settingBlock}>
          <Typography.Text className={styles.settingTitle}>
            画面风格
          </Typography.Text>
          <Typography.Text
            className={styles.selectedStyleText}
            type="secondary"
          >
            已选风格
          </Typography.Text>
          {selectedStyle && (
            <button
              className={styles.selectedStyleCard}
              aria-label={selectedStyle.name}
              onClick={() => applyStyleSelection(selectedStyle)}
              type="button"
            >
              <LazyMediaImage
                native
                height="100%"
                alt={selectedStyle.name}
                src={selectedStyle.imageUrl}
              />
              <span>{selectedStyle.name}</span>
            </button>
          )}
          <Typography.Text
            className={styles.selectedStyleText}
            type="secondary"
          >
            平台风格
          </Typography.Text>
          <Tabs
            className={styles.styleTabs}
            activeKey={styleCategory}
            onChange={changeStyleCategory}
            destroyOnHidden
            animated={false}
            items={['全部', ...styleCategories].map((category) => ({
              key: category,
              label: category,
              children: category === styleCategory ? platformStyleGallery : undefined,
            }))}
          />
        </section>
      </main>
    </div>
  );

  return (
    <PageContainer className={styles.pageContainer} title={false}>
      {step === 1 ? firstStep : secondStep}
      {isAdmin && (
        <InspirationManagementDrawer
          open={managementOpen}
          onClose={() => setManagementOpen(false)}
          onChanged={() => void loadInspirationPage(1)}
        />
      )}
      <Modal
        centered
        closable
        closeIcon={<CloseOutlined />}
        destroyOnHidden
        footer={null}
        mask={{ enabled: true, blur: true }}
        onCancel={() => {
          detailRequestRef.current += 1;
          setDetailLoading(false);
          stopActiveVideo();
          setSelectedInspiration(undefined);
        }}
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
                <ClickToPlayVideo
                  src={selectedInspiration.url}
                  poster={selectedInspiration.thumbnailUrl}
                  alt={selectedInspiration.title || '灵感素材'}
                  active={Boolean(selectedInspiration)}
                  style={{ height: '100%' }}
                />
              ) : (
                <LazyMediaImage
                  native
                  height="100%"
                  imageStyle={{ objectFit: 'contain' }}
                  alt={selectedInspiration.title || '灵感素材'}
                  src={selectedInspiration.thumbnailUrl}
                />
              )}
            </div>
            <div className={styles.detailInfoPanel}>
              <div className={styles.detailThumb}>
                <LazyMediaImage
                  native
                  height="100%"
                  alt={selectedInspiration.title || '灵感缩略图'}
                  src={selectedInspiration.thumbnailUrl}
                />
              </div>
              <div className={styles.detailCopy}>
                <Typography.Text className={styles.detailTitle}>
                  {selectedInspiration.title ||
                    `灵感 ${selectedInspiration.id}`}
                </Typography.Text>
                <Typography.Text className={styles.detailLabel}>
                  素材提示词
                </Typography.Text>
                <Typography.Paragraph className={styles.detailPrompt}>
                  {resolvePromptText(selectedInspiration)}
                </Typography.Paragraph>
                <Flex gap={8} wrap>
                  {selectedInspiration.tags?.map((tag) => (
                    <Typography.Text key={tag} type="secondary">
                      #{tag}
                    </Typography.Text>
                  ))}
                  <Button
                    icon={<CopyOutlined />}
                    size="small"
                    type="text"
                    onClick={() => {
                      void navigator.clipboard.writeText(
                        resolvePromptText(selectedInspiration),
                      );
                      message.success('提示词已复制');
                    }}
                  >
                    复制提示词
                  </Button>
                </Flex>
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
