import AssetExtractionSkillSelect, { defaultAssetExtractionSkill } from '@/components/AssetExtractionSkillSelect';
import {
  CameraOutlined,
  CloseOutlined,
  CopyOutlined,
  DownOutlined,
  FileTextOutlined,
  GlobalOutlined,
  InfoCircleOutlined,
  PictureOutlined,
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
  Popover,
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
import PointEstimateButton from '@/components/PointEstimateButton';
import ProjectArtStylePicker from '@/components/ProjectArtStylePicker';
import { getCurrentTenantId } from '@/services/account-team/auth';
import type { ProjectFormValues } from '@/services/account-team/project';
import type { TenantMember } from '@/services/account-team/types';
import type { MediaUploadHandle } from '@/services/mediaUpload';
import { isPointEstimateCancelled } from '@/services/point-estimate';
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
  storyboardSkillOptions,
} from './options';
import ScriptContentImport from './ScriptContentImport';
import {
  bindProjectCover,
  createProject,
  type InspirationCreation,
  type InspirationCreationDetail,
  queryInspirationCreationDetail,
  queryInspirationCreations,
  queryTenantMembers,
  startProjectCoverUpload,
} from './service';
import usePlatformStyleGallery from './usePlatformStyleGallery';

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
  const [styleOpen, setStyleOpen] = useState(false);
  const [openPanel, setOpenPanel] = useState<'creation' | 'more'>();
  const creationTriggerRef = useRef<HTMLButtonElement>(null);
  const moreTriggerRef = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    if (!openPanel) return;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key !== 'Escape' || event.defaultPrevented) return;
      setOpenPanel(undefined);
      (openPanel === 'creation'
        ? creationTriggerRef
        : moreTriggerRef
      ).current?.focus();
    };
    document.addEventListener('keydown', closeOnEscape);
    return () => document.removeEventListener('keydown', closeOnEscape);
  }, [openPanel]);
  const [activeTab, setActiveTab] = useState<DramaRegion>('domestic');
  const [scriptDraft, setScriptDraft] = useState('');
  const [scriptSourceLabel, setScriptSourceLabel] = useState<string>();
  const [coverFileName, setCoverFileName] = useState<string>();
  const [coverPreviewUrl, setCoverPreviewUrl] = useState<string>();
  const [coverUploadSessionToken, setCoverUploadSessionToken] =
    useState<string>();
  const coverUploadHandleRef = useRef<MediaUploadHandle | undefined>(undefined);
  const [selectedStyle, setSelectedStyle] = useState<PublicStyle>();
  const {
    category: styleCategory,
    changeCategory: changeStyleCategory,
    gallery: styleGallery,
    loading: galleryLoading,
    error: styleError,
    hasMore: stylesHaveMore,
    bottomRef: styleBottomRef,
    loadMore: loadMoreStyles,
    retry: retryStyles,
  } = usePlatformStyleGallery(styleOpen);
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
    storyboardSkillVersion: 'V1',
    assetExtractionSkillCode: defaultAssetExtractionSkill,
    visualStyleVersion: 'V1',
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
    if (!first || projectForm.visualStyleVersion === 'V2') return;
    setSelectedStyle((current) => current || first);
    setCoverPreviewUrl((current) => current || first.imageUrl);
    setProjectForm((current) => ({
      ...current,
      visualStyle: current.visualStyle || first.name,
      coverUrl: current.coverUrl || first.imageUrl,
    }));
  }, [styleGallery, projectForm.visualStyleVersion]);

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
      visualStyleVersion: 'V1',
      visualStyleCode: '',
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
    if (coverPreviewUrl?.startsWith('blob:'))
      URL.revokeObjectURL(coverPreviewUrl);
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
      if (coverUploadHandleRef.current)
        coverUploadHandleRef.current = undefined;
      message.error('封面上传失败，请重试');
    }
    return false;
  };

  useEffect(
    () => () => {
      coverUploadHandleRef.current?.cancel();
      if (coverPreviewUrl?.startsWith('blob:'))
        URL.revokeObjectURL(coverPreviewUrl);
    },
    [coverPreviewUrl],
  );

  const submitProject = async () => {
    if (
      projectForm.visualStyleVersion === 'V2' &&
      !projectForm.visualStyleCode
    ) {
      message.warning('请选择画风 2.0 风格');
      return;
    }
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
        coverUrl:
          projectForm.coverSource === 'UPLOAD'
            ? undefined
            : projectForm.coverUrl ||
              (projectForm.visualStyleVersion !== 'V2'
                ? selectedStyle?.imageUrl
                : undefined),
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
        storyboardSkillVersion: projectForm.storyboardSkillVersion,
        assetExtractionSkillCode: projectForm.assetExtractionSkillCode,
        visualStyleVersion: projectForm.visualStyleVersion || 'V1',
        visualStyleCode: projectForm.visualStyleCode,
        visualStyle:
          projectForm.visualStyle ||
          (projectForm.visualStyleVersion !== 'V2'
            ? selectedStyle?.name
            : undefined),
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
    } catch (error) {
      if (!isPointEstimateCancelled(error))
        message.error(error instanceof Error ? error.message : '创建项目失败');
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
              <LazyMediaImage
                native
                height="100%"
                alt={style.name}
                src={style.imageUrl}
              />
              <span>{style.name}</span>
            </button>
          );
        })}
      </div>
      <section
        ref={styleBottomRef}
        className={styles.styleLoadMore}
        aria-label="风格加载状态"
        aria-live="polite"
      >
        {galleryLoading ? (
          <>
            <Spin size="small" /> 正在加载风格...
          </>
        ) : styleError ? (
          <div role="alert">
            风格加载失败 <Button onClick={retryStyles}>重试加载风格</Button>
          </div>
        ) : stylesHaveMore ? (
          <Button onClick={loadMoreStyles}>加载更多风格</Button>
        ) : styleGallery.length ? (
          '已展示全部风格'
        ) : (
          <Empty
            description="当前分类暂无平台风格"
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          />
        )}
      </section>
    </>
  );

  const creationSettings = (
    <section
      className={styles.settingsPopover}
      role="dialog"
      aria-label="创作设定"
    >
      <h3>创作设定</h3>
      <div className={styles.popoverField}>
        <span>创作地区</span>
        <Radio.Group
          aria-label="创作地区"
          optionType="button"
          buttonStyle="solid"
          value={activeTab}
          onChange={(event) => setActiveTab(event.target.value)}
          options={inspirationTabs.map((item) => ({
            label: item.label,
            value: item.key,
          }))}
        />
      </div>
      <div className={styles.popoverField}>
        <span>文件格式</span>
        <Radio.Group
          aria-label="文件格式"
          optionType="button"
          buttonStyle="solid"
          options={fileFormatOptions}
          value={projectForm.fileFormat}
          onChange={(event) => updateForm({ fileFormat: event.target.value })}
        />
      </div>
      <div className={styles.popoverField}>
        <span>剧本类型</span>
        <Radio.Group
          aria-label="剧本类型"
          optionType="button"
          buttonStyle="solid"
          options={scriptTypeOptions}
          value={projectForm.scriptType}
          onChange={(event) => updateForm({ scriptType: event.target.value })}
        />
      </div>
    </section>
  );
  const moreSettings = (
    <section
      className={styles.settingsPopover}
      role="dialog"
      aria-label="更多设置"
    >
      <h3>更多设置</h3>
      <div className={styles.popoverRow}>
        <span>视频分辨率</span>
        <Select
          aria-label="默认视频分辨率"
          value={projectForm.videoResolution}
          onChange={(value) => updateForm({ videoResolution: value })}
          options={['480p', '720p', '1080p', '4k'].map((value) => ({
            label: value,
            value,
          }))}
        />
      </div>
      <div className={styles.popoverRow}>
        <span>场景解析力度</span>
        <Select
          aria-label="场景解析力度"
          value={projectForm.breakdownStrength}
          options={breakdownStrengthOptions}
          onChange={(value) => updateForm({ breakdownStrength: value })}
        />
      </div>
      <AssetExtractionSkillSelect
        value={projectForm.assetExtractionSkillCode}
        onChange={(assetExtractionSkillCode) => updateForm({ assetExtractionSkillCode })}
      />
      <div className={styles.popoverRow}>
        <span>分镜技能</span>
        <Select
          aria-label="分镜技能"
          value={projectForm.storyboardSkillVersion}
          options={storyboardSkillOptions}
          onChange={(value) => updateForm({ storyboardSkillVersion: value })}
        />
      </div>
      <p className={styles.settingHelp}>
        {projectForm.storyboardSkillVersion === 'V2'
          ? '按完整叙事片段拆分，支持 4–15 秒片段，镜头数量随剧情调整。'
          : '沿用现有分镜方式，以中近景和特写推进剧情。'}
      </p>
      <div className={styles.popoverRow}>
        <span>生成音频</span>
        <Switch
          aria-label="默认生成音频"
          checked={projectForm.videoGenerateAudio !== false}
          onChange={(checked) => updateForm({ videoGenerateAudio: checked })}
        />
      </div>
      <div className={styles.popoverRow}>
        <span>视频水印</span>
        <Switch
          aria-label="默认视频水印"
          checked={projectForm.videoWatermark === true}
          onChange={(checked) => updateForm({ videoWatermark: checked })}
        />
      </div>
      <div className={styles.coverSetting}>
        <span>剧本封面</span>
        <div className={styles.coverSettingContent}>
          <div className={styles.coverPreview}>
            {coverPreviewUrl ? (
              <LazyMediaImage
                alt="封面预览"
                height={72}
                preview={false}
                src={coverPreviewUrl}
                width={112}
              />
            ) : (
              <CameraOutlined />
            )}
          </div>
          <div>
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
            <p className={styles.settingHelp}>默认使用分镜首图</p>
          </div>
        </div>
      </div>
    </section>
  );
  const composer = (
    <div className={styles.creationShell}>
      <header className={styles.intro}>
        <div>
          <div className={styles.eyebrow}>A NEW STORY STARTS HERE</div>
          <Typography.Title className={styles.heroTitle} level={1}>
            今天想创作<span>什么样的故事?</span>
          </Typography.Title>
          <p>写下故事，选好画风。你的下一部短剧，从这里开始。</p>
        </div>
        <span className={styles.creationBadge}>
          <VideoCameraOutlined /> AI 短剧创作
        </span>
      </header>
      <section className={styles.scriptPanel} aria-label="剧本输入与创作配置">
        <div className={styles.composerHeading}>
          <FileTextOutlined />
          <Input
            aria-label="剧本名称"
            variant="borderless"
            maxLength={200}
            placeholder="为故事起个名字"
            value={projectForm.scriptName || ''}
            onChange={(event) =>
              updateForm({
                name: event.target.value,
                scriptName: event.target.value,
              })
            }
          />
          <ScriptContentImport
            className={styles.uploadButton}
            currentContent={scriptDraft}
            buttonLabel="导入剧本"
            onImport={(content, sourceLabel) => {
              setScriptDraft(content);
              setScriptSourceLabel(sourceLabel);
              updateForm({ initialScriptContent: content });
            }}
          />
        </div>
        <Input.TextArea
          aria-label="剧本内容"
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
        <div className={styles.writingMeta}>
          <span>{scriptSourceLabel || '支持剧本 / 小说内容'}</span>
          <span>{scriptDraft.length.toLocaleString()} / 50,000 字</span>
        </div>
        <div className={styles.composerDock}>
          <Popover
            trigger="click"
            placement="bottomLeft"
            arrow={false}
            open={openPanel === 'creation'}
            onOpenChange={(open) => setOpenPanel(open ? 'creation' : undefined)}
            content={creationSettings}
          >
            <Button
              ref={creationTriggerRef}
              className={styles.dockControl}
              aria-label="创作设定"
              aria-haspopup="dialog"
              aria-expanded={openPanel === 'creation'}
            >
              <GlobalOutlined />
              <span>
                {activeTab === 'domestic' ? '国内剧' : '海外剧'} ·{' '}
                {projectForm.fileFormat === 'NOVEL' ? '小说' : '剧本'} ·{' '}
                {projectForm.scriptType === 'COMMENTARY_COMIC'
                  ? '解说漫'
                  : '精品剧'}
              </span>
              <DownOutlined />
            </Button>
          </Popover>
          <Button
            className={styles.styleTrigger}
            aria-label="选择画风"
            onClick={() => {
              setOpenPanel(undefined);
              setStyleOpen(true);
            }}
          >
            {projectForm.visualStyleVersion !== 'V2' &&
            selectedStyle?.imageUrl ? (
              <LazyMediaImage
                native
                src={selectedStyle.imageUrl}
                alt=""
                height={28}
                width={40}
              />
            ) : (
              <PictureOutlined />
            )}
            <span>{projectForm.visualStyle || '选择画风'}</span>
            <DownOutlined />
          </Button>
          <Select
            className={styles.ratioControl}
            aria-label="画面比例"
            variant="borderless"
            value={projectForm.aspectRatio}
            options={aspectRatioOptions}
            onChange={(value) => updateForm({ aspectRatio: value })}
          />
          <Popover
            trigger="click"
            placement="bottomRight"
            arrow={false}
            open={openPanel === 'more'}
            onOpenChange={(open) => setOpenPanel(open ? 'more' : undefined)}
            content={moreSettings}
          >
            <Button
              ref={moreTriggerRef}
              className={styles.dockControl}
              aria-label="更多设置"
              aria-haspopup="dialog"
              aria-expanded={openPanel === 'more'}
            >
              <SettingOutlined />
              <span>更多设置</span>
              <DownOutlined />
            </Button>
          </Popover>
          <div className={styles.createAction}>
            <PointEstimateButton
              tenantId={tenantId}
              displayMode="points"
              operations={
                scriptDraft.trim()
                  ? [
                      {
                        url: '/api/projects',
                        data: {
                          ...projectForm,
                          initialScriptContent: scriptDraft.trim(),
                        },
                      },
                    ]
                  : []
              }
              className={styles.startButton}
              loading={submitting}
              onClick={submitProject}
              type="primary"
              disabled={
                projectForm.visualStyleVersion === 'V2' &&
                !projectForm.visualStyleCode
              }
              icon={
                <svg
                  aria-hidden="true"
                  width="20"
                  height="20"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.7"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <path d="m12 3 2.7 6.3L21 12l-6.3 2.7L12 21l-2.7-6.3L3 12l6.3-2.7zM20 2v4m-2-2h4" />
                </svg>
              }
            >
              开始创作
            </PointEstimateButton>
          </div>
        </div>
      </section>
      <div className={styles.uploadHint}>
        <InfoCircleOutlined /> 请确认你拥有所导入剧本的合法版权
      </div>
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
  const styleModal = (
    <Modal
      title="选择画风"
      forceRender
      open={styleOpen}
      onCancel={() => setStyleOpen(false)}
      footer={
        <Button type="primary" onClick={() => setStyleOpen(false)}>
          完成选择
        </Button>
      }
      width={1040}
      styles={{ body: { maxHeight: '68vh', overflowY: 'auto' } }}
    >
      <ProjectArtStylePicker
        value={projectForm}
        onChange={(selection) => {
          setProjectForm((current) => ({
            ...current,
            ...selection,
            ...(current.coverSource !== 'UPLOAD'
              ? {
                  coverUrl:
                    selection.visualStyleVersion === 'V2'
                      ? undefined
                      : selectedStyle?.imageUrl,
                }
              : {}),
          }));
          if (projectForm.coverSource !== 'UPLOAD')
            setCoverPreviewUrl(
              selection.visualStyleVersion === 'V2'
                ? undefined
                : selectedStyle?.imageUrl,
            );
        }}
        legacyPicker={
          <>
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
                children:
                  category === styleCategory ? platformStyleGallery : undefined,
              }))}
            />
          </>
        }
      />
    </Modal>
  );

  return (
    <PageContainer className={styles.pageContainer} title={false}>
      {composer}
      {styleModal}
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
