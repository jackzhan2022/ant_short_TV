import {
  BarsOutlined,
  CloseOutlined,
  CopyOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import { useParams } from '@umijs/max';
import {
  App,
  Button,
  Empty,
  Flex,
  Image,
  Input,
  Select,
  Typography,
} from 'antd';
import { useEffect, useMemo, useState } from 'react';
import type {
  AiImageTask,
  AiVideoTask,
  CharacterAsset,
  PropAsset,
  SaveStoryboardValues,
  SceneAsset,
  ScriptWorkspace,
  StoryboardShot,
} from './service';
import {
  createAiVideoTask,
  createStoryboard,
  deleteStoryboard,
  queryAiImageTasks,
  queryAiVideoTasks,
  queryScriptWorkspace,
  updateStoryboard,
} from './service';

type StoryboardDraft = Record<
  number,
  { scriptText: string; videoPrompt: string }
>;
type StoryboardWithProps = StoryboardShot & { props?: string };

const successStatuses = ['SUCCESS', 'SUCCEEDED'];
const supportedVideoDurations = [5, 8, 10];

const getTaskImage = (
  imageTasks: AiImageTask[],
  targetType: string,
  targetId: number,
) => {
  const tasks = imageTasks.filter(
    (item) =>
      item.targetType === targetType &&
      item.targetId === targetId &&
      successStatuses.includes(item.status) &&
      item.results?.length,
  );
  const selectedResult =
    tasks
      .flatMap((task) => task.results)
      .find(
        (result) => result.selected && successStatuses.includes(result.status),
      ) ||
    tasks[0]?.results.find((result) =>
      successStatuses.includes(result.status),
    ) ||
    tasks[0]?.results[0];
  return selectedResult?.thumbnailUrl || selectedResult?.imageUrl || undefined;
};

const getPlaceholderBackground = (key: string) => {
  if (key.startsWith('scene')) {
    return [
      'linear-gradient(135deg, #d8e9f8 0%, #f9fbff 52%, #b8c6d6 100%)',
      'linear-gradient(135deg, #20242d 0%, #3d4148 50%, #11141a 100%)',
      'linear-gradient(135deg, #d4ead8 0%, #eef6ff 48%, #a6b6ca 100%)',
      'linear-gradient(135deg, #101720 0%, #2d3542 52%, #111827 100%)',
    ][Number(key.at(-1)) || 0];
  }
  if (key.startsWith('prop')) {
    return [
      'radial-gradient(circle at 50% 38%, #d98a1f 0 17%, transparent 18%), linear-gradient(#fff, #fbfcff)',
      'linear-gradient(145deg, #2d3034 0%, #777b82 46%, #f5f7fb 47%, #ffffff 100%)',
      'radial-gradient(circle at 52% 44%, #35a2ff 0 18%, transparent 19%), linear-gradient(#fff, #fbfcff)',
      'radial-gradient(ellipse at 50% 52%, #edf0f5 0 32%, transparent 33%), linear-gradient(#fff, #fbfcff)',
    ][Number(key.at(-1)) || 0];
  }
  return [
    'linear-gradient(90deg, #fff 0%, #fff5d8 32%, #f8fbff 33%, #fff 100%)',
    'linear-gradient(90deg, #fff 0%, #2f343b 34%, #f7f9ff 35%, #fff 100%)',
    'linear-gradient(90deg, #fff 0%, #e8f2ff 34%, #fbfdff 35%, #fff 100%)',
    'linear-gradient(90deg, #fff 0%, #8b1f2c 34%, #fff 35%, #fff 100%)',
  ][Number(key.at(-1)) || 0];
};

const episodeTitles: Record<number, string> = {
  1: '致命捉迷藏',
  2: '夜色警报',
};

const episodeSummaries: Record<number, string> = {
  1: '斌斌独自下楼玩耍，为躲猫猫爬进一辆未关后备箱的灰色轿车，后备箱意外锁死。奶奶刘凤英却只顾跳广场舞，对孙子的危险一无所知。',
  2: '夜幕压低小区楼影，家人意识到斌斌失踪后开始寻找，停车场与楼道里的线索逐渐指向同一辆灰色轿车。',
};

const splitNames = (value?: string | null) =>
  String(value || '')
    .split(/[、,，]/)
    .map((item) => item.trim())
    .filter(Boolean);

const getStoryboardPropNames = (storyboard: StoryboardShot) =>
  splitNames((storyboard as StoryboardWithProps).props);

const getStoryboardProps = (storyboard: StoryboardShot) =>
  (storyboard as StoryboardWithProps).props || '';

const firstByName = <T extends { name: string }>(items: T[], names: string[]) =>
  items.find((item) => names.includes(item.name));

const getStoryboardScriptText = (storyboard: StoryboardShot) =>
  [
    storyboard.scene ? `▲${storyboard.scene}` : '',
    storyboard.visualDescription,
    storyboard.dialogue,
  ]
    .filter(Boolean)
    .join('\n');

const parseStoryboardScriptText = (
  storyboard: StoryboardShot,
  scriptText: string,
) => {
  const lines = scriptText
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
  let scene = storyboard.scene;
  if (lines[0]?.startsWith('▲')) {
    scene =
      lines
        .shift()
        ?.replace(/^▲+\s*/, '')
        .trim() || storyboard.scene;
  }

  let dialogue = storyboard.dialogue;
  const dialogueIndex = lines.findLastIndex((line) =>
    /(?:VO|旁白)?[:：]|[“"].+[”"]/.test(line),
  );
  if (dialogueIndex >= 0) {
    dialogue = lines.splice(dialogueIndex, 1)[0];
  }

  return {
    scene,
    dialogue,
    visualDescription: lines.join('\n') || storyboard.visualDescription,
  };
};

const getStoryboardPrompt = (storyboard: StoryboardShot) =>
  [
    '画风：写实都市',
    '视频中不得出现任何字幕、文字叠加、纯画面，不要bgm，不要配乐。',
    '### 素材引用',
    storyboard.characters ? `【人物】${storyboard.characters}` : '',
    storyboard.scene ? `【场景】${storyboard.scene}` : '',
    getStoryboardPropNames(storyboard).length
      ? `【道具】${getStoryboardPropNames(storyboard).join('、')}`
      : '',
    '### 画面描写',
    `镜头${storyboard.shotNo} ${storyboard.durationSeconds || 5}s`,
    storyboard.visualDescription,
    storyboard.dialogue,
    storyboard.videoPrompt,
  ]
    .filter(Boolean)
    .join('\n');

const getStoryboardSavePayload = (
  storyboard: StoryboardShot,
  overrides: Partial<SaveStoryboardValues> = {},
): SaveStoryboardValues => ({
  visualDescription: storyboard.visualDescription,
  characters: storyboard.characters,
  dialogue: storyboard.dialogue,
  scene: storyboard.scene,
  props: getStoryboardProps(storyboard),
  durationSeconds: storyboard.durationSeconds,
  imagePrompt: storyboard.imagePrompt,
  videoPrompt: storyboard.videoPrompt,
  ...overrides,
});

const normalizeVideoDuration = (durationSeconds?: number) => {
  if (!durationSeconds) {
    return supportedVideoDurations[0];
  }
  return (
    supportedVideoDurations.find((duration) => duration >= durationSeconds) ||
    supportedVideoDurations.at(-1) ||
    supportedVideoDurations[0]
  );
};

const toLocalStoryboard = (
  values: SaveStoryboardValues,
  fallbackId: number,
): StoryboardWithProps => ({
  id: fallbackId,
  shotNo: values.shotNo || 1,
  episodeNo: values.episodeNo || 1,
  shotType: values.shotType || '中景',
  visualDescription: values.visualDescription,
  characters: values.characters || '',
  scene: values.scene || '',
  props: values.props || '',
  dialogue: values.dialogue || '',
  durationSeconds: values.durationSeconds || 5,
  imagePrompt: values.imagePrompt || '',
  videoPrompt: values.videoPrompt || '',
  firstFrameUrl: null,
  currentVideoUrl: null,
});

const getStoryboardVideo = (
  storyboard: StoryboardShot,
  videoTasks: AiVideoTask[],
) => {
  const selectedTask = videoTasks.find(
    (task) =>
      task.storyboardId === storyboard.id &&
      task.results?.some((result) => result.isSelected),
  );
  const task =
    selectedTask ||
    videoTasks.find(
      (item) => item.storyboardId === storyboard.id && item.results?.length,
    );
  const result =
    task?.results?.find((item) => item.isSelected) || task?.results?.[0];
  return {
    coverUrl: result?.coverUrl || storyboard.firstFrameUrl || undefined,
    videoUrl:
      result?.videoUrl ||
      storyboard.currentVideoUrl ||
      storyboard.currentShotVideoUrl ||
      undefined,
  };
};

const thumbnailFor = (
  imageTasks: AiImageTask[],
  targetType: string,
  targetId?: number,
  fallback?: string | null,
) => {
  if (targetId) {
    return (
      getTaskImage(imageTasks, targetType, targetId) || fallback || undefined
    );
  }
  return fallback || undefined;
};

const avatarRail = (count: number, tone: string) => (
  <div style={{ display: 'flex', width: 58, overflow: 'hidden' }}>
    {['avatar-1', 'avatar-2', 'avatar-3', 'avatar-4']
      .slice(0, Math.max(1, Math.min(count, 4)))
      .map((avatarKey, index) => (
        <span
          key={avatarKey}
          style={{
            width: 18,
            height: 36,
            marginLeft: index ? -5 : 0,
            borderRadius: 9,
            border: '1px solid #fff',
            background:
              tone === 'warm'
                ? `linear-gradient(180deg, #ffe2a5 0%, #${index % 2 ? 'f5a623' : 'fff7e6'} 48%, #5973a8 49%, #273855 100%)`
                : `linear-gradient(180deg, #f7f9ff 0%, #${index % 2 ? '304152' : 'a9c2e8'} 48%, #fff 49%, #dfe6f5 100%)`,
          }}
        />
      ))}
  </div>
);

const PreviewPoster = ({ src, title }: { src?: string; title: string }) =>
  src ? (
    <Image
      src={src}
      alt={title}
      width="100%"
      height="100%"
      preview={false}
      style={{ objectFit: 'cover' }}
    />
  ) : (
    <div
      role="img"
      aria-label={title}
      style={{
        width: '100%',
        height: '100%',
        background:
          'linear-gradient(180deg, #9eb8d2 0%, #dce7f2 38%, #b9c3cc 39%, #6f7d8c 100%)',
      }}
    />
  );

const StoryboardCard = ({
  item,
  index,
  characters,
  scenes,
  props,
  imageTasks,
  videoTasks,
  draft,
  model,
  onDraftChange,
  onGenerate,
  onSaveScript,
  onUpdateStoryboard,
  onAddStoryboard,
  onCopyStoryboard,
  onDelete,
}: {
  item: StoryboardShot;
  index: number;
  characters: CharacterAsset[];
  scenes: SceneAsset[];
  props: PropAsset[];
  imageTasks: AiImageTask[];
  videoTasks: AiVideoTask[];
  draft: { scriptText: string; videoPrompt: string };
  model: string;
  onDraftChange: (
    storyboardId: number,
    values: Partial<StoryboardDraft[number]>,
  ) => void;
  onGenerate: (storyboard: StoryboardShot) => void;
  onSaveScript: (storyboard: StoryboardShot) => void;
  onUpdateStoryboard: (
    storyboard: StoryboardShot,
    values: Partial<SaveStoryboardValues>,
  ) => void;
  onAddStoryboard: (storyboard: StoryboardShot) => void;
  onCopyStoryboard: (storyboard: StoryboardShot) => void;
  onDelete: (storyboard: StoryboardShot) => void;
}) => {
  const characterNames = splitNames(item.characters);
  const sceneNames = splitNames(item.scene);
  const propNames = getStoryboardPropNames(item);
  const scene = firstByName(scenes, sceneNames);
  const prop = firstByName(props, propNames);
  const video = getStoryboardVideo(item, videoTasks);
  const sceneImage =
    thumbnailFor(imageTasks, 'SCENE', scene?.id) ||
    item.firstFrameUrl ||
    undefined;
  const propImage = thumbnailFor(imageTasks, 'PROP', prop?.id);

  return (
    <article
      style={{
        border: '1px solid #e7ebf5',
        borderRadius: 18,
        background: '#fff',
        overflow: 'hidden',
      }}
    >
      <div
        style={{
          height: 49,
          borderBottom: '1px solid #eef2f8',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '0 20px 0 14px',
        }}
      >
        <Flex align="center" gap={10}>
          <span style={{ color: '#111827', letterSpacing: 1 }}>⁝⁝</span>
          <Typography.Text strong>分镜{index + 1}</Typography.Text>
          <span
            style={{
              height: 16,
              borderRadius: 4,
              background: '#eef1ff',
              color: '#4957ff',
              fontSize: 10,
              padding: '0 3px',
              lineHeight: '16px',
            }}
          >
            ID
          </span>
          <Flex
            align="center"
            gap={9}
            style={{
              height: 32,
              borderRadius: 8,
              background: '#f7f8ff',
              border: '1px solid #e7ebff',
              padding: '0 14px',
            }}
          >
            <span
              style={{
                width: 16,
                height: 16,
                borderRadius: 8,
                background: '#5454ff',
                display: 'inline-grid',
                placeItems: 'center',
              }}
            >
              <span
                style={{
                  width: 5,
                  height: 5,
                  borderRadius: 3,
                  background: '#fff',
                }}
              />
            </span>
            <span style={{ fontWeight: 600 }}>全能参考生视频</span>
            <span
              style={{
                width: 16,
                height: 16,
                borderRadius: 8,
                border: '1px solid #cfd6e8',
                background: '#fff',
              }}
            />
            <span>首尾帧生视频</span>
          </Flex>
        </Flex>
        <Flex gap={18} style={{ color: '#7f88a6', fontSize: 17 }}>
          <Button
            type="text"
            size="small"
            icon={<PlusOutlined />}
            aria-label="新增分镜"
            onClick={() => onAddStoryboard(item)}
          />
          <Button
            type="text"
            size="small"
            icon={<CopyOutlined />}
            aria-label="复制分镜"
            onClick={() => onCopyStoryboard(item)}
          />
          <Button
            type="text"
            size="small"
            icon={<CloseOutlined />}
            aria-label={`删除分镜${index + 1}`}
            onClick={() => onDelete(item)}
          />
        </Flex>
      </div>

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: '400px minmax(520px, 1fr) 520px',
          gap: 22,
          padding: '16px 24px 24px',
        }}
      >
        <section
          style={{
            borderRight: '1px solid #eef2f7',
            paddingRight: 22,
            minHeight: 520,
          }}
        >
          <Typography.Text strong style={{ fontSize: 15 }}>
            分镜信息
          </Typography.Text>
          <div style={{ marginTop: 22, color: '#1f2937', fontSize: 14 }}>
            剧本原文
          </div>
          <Input.TextArea
            aria-label={`分镜${index + 1}剧本原文`}
            value={draft.scriptText}
            onChange={(event) =>
              onDraftChange(item.id, { scriptText: event.target.value })
            }
            onBlur={() => onSaveScript(item)}
            autoSize={{ minRows: 4, maxRows: 5 }}
            style={{
              marginTop: 9,
              borderColor: '#d8deee',
              borderRadius: 8,
              fontWeight: 600,
              lineHeight: 1.7,
            }}
          />

          <div style={{ marginTop: 22 }}>
            <Flex justify="space-between" align="center">
              <span>出镜角色</span>
              <PlusOutlined style={{ color: '#59627a' }} />
            </Flex>
            <Flex align="center" gap={9} style={{ marginTop: 13 }}>
              {avatarRail(characterNames.length, index % 2 ? 'warm' : 'cool')}
              <Select
                aria-label={`分镜${index + 1}出镜角色`}
                value={characterNames[0] || undefined}
                options={characters.map((asset) => ({
                  label: `${asset.name} - ${asset.name}`,
                  value: asset.name,
                }))}
                style={{ flex: 1 }}
                onChange={(value) =>
                  onUpdateStoryboard(item, { characters: value || '' })
                }
              />
              <Button style={{ width: 118 }}>▶ 自定义音色</Button>
              <Button style={{ width: 38 }}>...</Button>
            </Flex>
          </div>

          <div style={{ marginTop: 28 }}>
            <Flex justify="space-between" align="center">
              <span>分镜场景</span>
              <PlusOutlined style={{ color: '#59627a' }} />
            </Flex>
            <div
              style={{
                width: 190,
                height: 102,
                marginTop: 12,
                borderRadius: 8,
                overflow: 'hidden',
                display: 'grid',
                gridTemplateColumns: '1fr 1fr',
                background: '#eef2f7',
              }}
            >
              {[
                'scene-thumb-a',
                'scene-thumb-b',
                'scene-thumb-c',
                'scene-thumb-d',
              ].map((thumbnailKey) => (
                <div
                  key={thumbnailKey}
                  style={{
                    border: '1px solid rgba(255,255,255,0.8)',
                    overflow: 'hidden',
                  }}
                >
                  <PreviewPoster
                    src={sceneImage}
                    title={`${item.scene || '场景'}参考图`}
                  />
                </div>
              ))}
            </div>
            <Flex gap={8} style={{ marginTop: 10 }}>
              <Select
                aria-label={`分镜${index + 1}场景`}
                value={sceneNames[0] || undefined}
                options={scenes.map((asset) => ({
                  label: `${asset.name} - ${asset.name}`,
                  value: asset.name,
                }))}
                style={{ flex: 1 }}
                onChange={(value) =>
                  onUpdateStoryboard(item, { scene: value || '' })
                }
              />
              <Button style={{ width: 38 }}>...</Button>
            </Flex>
          </div>

          <div style={{ marginTop: 20 }}>
            <Flex justify="space-between" align="center">
              <span>场景道具</span>
              <PlusOutlined style={{ color: '#59627a' }} />
            </Flex>
            <Flex align="center" gap={9} style={{ marginTop: 13 }}>
              <div
                style={{
                  width: 48,
                  height: 38,
                  borderRadius: 7,
                  overflow: 'hidden',
                  background: getPlaceholderBackground('prop-1'),
                }}
              >
                {propImage && (
                  <Image
                    src={propImage}
                    alt={prop?.name}
                    width="100%"
                    height="100%"
                    preview={false}
                    style={{ objectFit: 'cover' }}
                  />
                )}
              </div>
              <Select
                aria-label={`分镜${index + 1}场景道具`}
                value={propNames[0] || undefined}
                options={props.map((asset) => ({
                  label: `${asset.name} - ${asset.name}`,
                  value: asset.name,
                }))}
                style={{ flex: 1 }}
                onChange={(value) =>
                  onUpdateStoryboard(item, { props: value || '' })
                }
              />
              <Button style={{ width: 38 }}>...</Button>
            </Flex>
          </div>
        </section>

        <section>
          <Flex align="center" gap={12}>
            <Typography.Text strong style={{ fontSize: 15 }}>
              分镜视频生成
            </Typography.Text>
            <span
              style={{
                height: 32,
                borderRadius: 8,
                background: '#f0f2fb',
                color: '#20283a',
                display: 'inline-flex',
                alignItems: 'center',
                gap: 8,
                padding: '0 14px',
                fontWeight: 600,
              }}
            >
              <span>◎</span>
              <span>3D导演台</span>
              <span style={{ color: '#6956ff', fontSize: 12 }}>Beta</span>
            </span>
          </Flex>
          <div
            style={{
              marginTop: 22,
              border: '1px solid #dfe5f2',
              borderRadius: 14,
              minHeight: 414,
              padding: '16px 18px 12px',
            }}
          >
            <Flex
              align="center"
              gap={10}
              style={{ color: '#b5bdcc', marginBottom: 12 }}
            >
              <Button style={{ width: 38 }}>+</Button>
              <Button style={{ width: 38 }}>▧</Button>
              <span>
                使用 @
                引用角色、场景、道具、音色及参考素材，编辑更灵活，分镜更精准
              </span>
            </Flex>
            <Input.TextArea
              aria-label={`分镜${index + 1}视频提示词`}
              value={draft.videoPrompt}
              onChange={(event) =>
                onDraftChange(item.id, { videoPrompt: event.target.value })
              }
              autoSize={{ minRows: 14, maxRows: 22 }}
              variant="borderless"
              style={{ fontWeight: 600, lineHeight: 1.7, resize: 'none' }}
            />
            <Flex
              justify="space-between"
              align="center"
              style={{ marginTop: 9 }}
            >
              <Flex gap={6}>
                <Button size="small">✦ {model}</Button>
                <Button size="small">
                  ◷ {item.durationSeconds || 5}s | 1个 | 720p | mp4
                </Button>
                <Button size="small">@</Button>
              </Flex>
              <Button
                type="primary"
                onClick={() => onGenerate(item)}
                aria-label={`生成分镜${index + 1}视频`}
                style={{
                  width: 92,
                  background: '#5b50ff',
                  borderColor: '#5b50ff',
                  fontWeight: 700,
                }}
              >
                ✦ 1,135
              </Button>
            </Flex>
          </div>
        </section>

        <section
          style={{
            position: 'relative',
            minHeight: 500,
            background: '#f8faff',
            borderRadius: 10,
            overflow: 'hidden',
          }}
        >
          <div
            style={{
              position: 'absolute',
              top: 8,
              right: 12,
              zIndex: 2,
              height: 30,
              borderRadius: 8,
              background: '#111827',
              color: '#fff',
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              padding: '0 12px',
              fontSize: 12,
            }}
          >
            <span>▣</span>
            <span>↔</span>
            <span>▤</span>
            <span>T</span>
            <span>ⓘ</span>
          </div>
          <div
            style={{
              height: 490,
              background: '#2c333f',
              display: 'flex',
              justifyContent: 'center',
            }}
          >
            <div style={{ width: 276, height: '100%', position: 'relative' }}>
              {video.videoUrl ? (
                <video
                  aria-label={`分镜${index + 1}成片预览`}
                  src={video.videoUrl}
                  poster={video.coverUrl || undefined}
                  controls
                  style={{
                    width: '100%',
                    height: '100%',
                    objectFit: 'cover',
                    background: '#000',
                  }}
                >
                  <track kind="captions" label="暂无字幕" />
                </video>
              ) : (
                <>
                  <PreviewPoster
                    src={video.coverUrl}
                    title={`分镜${index + 1}预览`}
                  />
                  <span
                    style={{
                      position: 'absolute',
                      left: '50%',
                      top: '50%',
                      transform: 'translate(-50%, -50%)',
                      width: 54,
                      height: 54,
                      borderRadius: 27,
                      background: 'rgba(17,24,39,0.62)',
                      color: '#fff',
                      display: 'grid',
                      placeItems: 'center',
                      fontSize: 24,
                    }}
                  >
                    ▶
                  </span>
                </>
              )}
            </div>
          </div>
          <Flex
            justify="center"
            gap={10}
            style={{ height: 54, paddingTop: 12 }}
          >
            {[
              { key: 'first-frame', thumb: item.firstFrameUrl },
              { key: 'cover', thumb: video.coverUrl },
              { key: 'scene', thumb: sceneImage },
            ].map((thumbnail, thumbIndex) => (
              <div
                key={thumbnail.key}
                style={{
                  position: 'relative',
                  width: 42,
                  height: 40,
                  borderRadius: 6,
                  border:
                    thumbIndex === 0
                      ? '2px solid #5454ff'
                      : '1px solid #dfe5f2',
                  overflow: 'hidden',
                }}
              >
                <PreviewPoster
                  src={thumbnail.thumb || undefined}
                  title="分镜缩略图"
                />
                {thumbIndex === 0 && (
                  <span
                    style={{
                      position: 'absolute',
                      left: 0,
                      right: 0,
                      bottom: 0,
                      height: 16,
                      background: '#5454ff',
                      color: '#fff',
                      fontSize: 10,
                      textAlign: 'center',
                      lineHeight: '16px',
                    }}
                  >
                    当前分镜
                  </span>
                )}
              </div>
            ))}
          </Flex>
        </section>
      </div>
    </article>
  );
};

const ProductionWorkbenchStoryboard = () => {
  const params = useParams<{ id: string }>();
  const projectId = Number(params.id);
  const { message } = App.useApp();
  const [imageTasks, setImageTasks] = useState<AiImageTask[]>([]);
  const [videoTasks, setVideoTasks] = useState<AiVideoTask[]>([]);
  const [activeEpisode, setActiveEpisode] = useState(1);
  const [selectedModel, setSelectedModel] = useState('Doubao-Seedance-2.5');
  const [drafts, setDrafts] = useState<StoryboardDraft>({});
  const [workspace, setWorkspace] = useState<ScriptWorkspace>({
    projectId: projectId || 0,
    script: null,
    versions: [],
    characters: [],
    scenes: [],
    props: [],
    storyboards: [],
  });

  useEffect(() => {
    if (!projectId) {
      return;
    }
    let active = true;
    Promise.all([
      queryScriptWorkspace(projectId),
      queryAiImageTasks(projectId, undefined).catch(() => ({ data: [] })),
      queryAiVideoTasks(projectId, undefined).catch(() => ({ data: [] })),
    ])
      .then(
        ([
          workspaceResponse,
          imageTaskResponse,
          videoTaskResponse,
        ]) => {
          if (!active) {
            return;
          }
          const nextWorkspace = {
            projectId,
            script: workspaceResponse.data?.script || null,
            versions: workspaceResponse.data?.versions || [],
            characters: workspaceResponse.data?.characters || [],
            scenes: workspaceResponse.data?.scenes || [],
            props: workspaceResponse.data?.props || [],
            storyboards: workspaceResponse.data?.storyboards || [],
          };
          setWorkspace(nextWorkspace);
          setImageTasks(imageTaskResponse.data || []);
          setVideoTasks(videoTaskResponse.data || []);
          const firstEpisode = nextWorkspace.storyboards[0]?.episodeNo || 1;
          setActiveEpisode(firstEpisode);
          setDrafts(
            Object.fromEntries(
              nextWorkspace.storyboards.map((item) => [
                item.id,
                {
                  scriptText: getStoryboardScriptText(item),
                  videoPrompt: getStoryboardPrompt(item),
                },
              ]),
            ),
          );
        },
      )
      .catch(() => {
        if (active) {
          message.error('分镜页面加载失败');
        }
      });
    return () => {
      active = false;
    };
  }, [message, projectId]);

  const characters = workspace.characters;
  const scenes = workspace.scenes;
  const props = workspace.props;
  const episodeNumbers = useMemo(() => {
    const fromData = Array.from(
      new Set(workspace.storyboards.map((item) => item.episodeNo)),
    ).sort((a, b) => a - b);
    return Array.from(
      { length: Math.max(15, fromData.at(-1) || 0) },
      (_, index) => index + 1,
    );
  }, [workspace.storyboards]);
  const visibleStoryboards = workspace.storyboards.filter(
    (item) => item.episodeNo === activeEpisode,
  );

  const updateDraft = (
    storyboardId: number,
    values: Partial<StoryboardDraft[number]>,
  ) => {
    setDrafts((previous) => ({
      ...previous,
      [storyboardId]: {
        scriptText: previous[storyboardId]?.scriptText || '',
        videoPrompt: previous[storyboardId]?.videoPrompt || '',
        ...values,
      },
    }));
  };

  const reloadVideoTasks = async () => {
    const response = await queryAiVideoTasks(projectId, undefined);
    setVideoTasks(response.data || []);
  };

  const syncStoryboardsFromResponse = (
    nextWorkspace: ScriptWorkspace | undefined,
  ) => {
    if (nextWorkspace?.storyboards?.length) {
      setWorkspace(nextWorkspace);
    }
  };

  const createVideoTaskForStoryboard = async (storyboard: StoryboardShot) => {
    const prompt =
      drafts[storyboard.id]?.videoPrompt || getStoryboardPrompt(storyboard);
    await createAiVideoTask(projectId, {
      storyboardId: storyboard.id,
      prompt,
      firstFrameUrl: storyboard.firstFrameUrl || undefined,
      durationSeconds: normalizeVideoDuration(storyboard.durationSeconds),
      aspectRatio: '9:16',
      resolution: '720p',
    });
  };

  const generateVideo = async (storyboard: StoryboardShot) => {
    try {
      await createVideoTaskForStoryboard(storyboard);
      message.success('视频任务已创建');
      await reloadVideoTasks();
    } catch {
      message.error('视频任务创建失败');
    }
  };

  const batchGenerateVideo = async () => {
    try {
      await Promise.all(
        visibleStoryboards.map((item) => createVideoTaskForStoryboard(item)),
      );
      message.success('批量视频任务已创建');
      await reloadVideoTasks();
    } catch {
      message.error('批量生成视频失败');
    }
  };

  const saveStoryboardScript = async (storyboard: StoryboardShot) => {
    const scriptText = drafts[storyboard.id]?.scriptText;
    if (!scriptText || scriptText === getStoryboardScriptText(storyboard)) {
      return;
    }
    const parsedScript = parseStoryboardScriptText(storyboard, scriptText);
    const nextStoryboard = {
      ...storyboard,
      ...parsedScript,
    };
    const videoPrompt =
      drafts[storyboard.id]?.videoPrompt || getStoryboardPrompt(nextStoryboard);
    try {
      const response = await updateStoryboard(projectId, storyboard.id, {
        visualDescription: parsedScript.visualDescription,
        scene: parsedScript.scene,
        dialogue: parsedScript.dialogue,
        characters: storyboard.characters,
        props: getStoryboardProps(storyboard),
        durationSeconds: storyboard.durationSeconds,
        imagePrompt: storyboard.imagePrompt,
        videoPrompt,
      });
      setWorkspace((previous) => ({
        ...previous,
        storyboards: previous.storyboards.map((item) =>
          item.id === storyboard.id ? nextStoryboard : item,
        ),
      }));
      syncStoryboardsFromResponse(response.data);
      message.success('分镜已保存');
    } catch {
      message.error('分镜保存失败');
    }
  };

  const saveStoryboardFields = async (
    storyboard: StoryboardShot,
    values: Partial<SaveStoryboardValues>,
  ) => {
    const nextStoryboard = {
      ...storyboard,
      ...values,
    };
    const nextVideoPrompt = getStoryboardPrompt(nextStoryboard);
    setWorkspace((previous) => ({
      ...previous,
      storyboards: previous.storyboards.map((item) =>
        item.id === storyboard.id ? nextStoryboard : item,
      ),
    }));
    setDrafts((previous) => ({
      ...previous,
      [storyboard.id]: {
        scriptText: getStoryboardScriptText(nextStoryboard),
        videoPrompt: nextVideoPrompt,
      },
    }));

    try {
      const response = await updateStoryboard(
        projectId,
        storyboard.id,
        getStoryboardSavePayload(nextStoryboard, {
          videoPrompt: nextVideoPrompt,
        }),
      );
      syncStoryboardsFromResponse(response.data);
      message.success('分镜已保存');
    } catch {
      message.error('分镜保存失败');
    }
  };

  const appendStoryboard = async (
    values: SaveStoryboardValues,
    successText: string,
  ) => {
    try {
      const response = await createStoryboard(projectId, values);
      setWorkspace((previous) => {
        const responseStoryboards = response.data?.storyboards;
        if (
          responseStoryboards?.length &&
          responseStoryboards.length >= previous.storyboards.length
        ) {
          return response.data;
        }
        return {
          ...previous,
          storyboards: [
            ...previous.storyboards,
            toLocalStoryboard(values, Date.now()),
          ],
        };
      });
      message.success(successText);
    } catch {
      message.error('分镜创建失败');
    }
  };

  const addStoryboardAfter = async (storyboard: StoryboardShot) => {
    const shotNo =
      Math.max(0, ...visibleStoryboards.map((item) => item.shotNo)) + 1;
    await appendStoryboard(
      {
        episodeNo: storyboard.episodeNo,
        shotNo,
        shotType: '中景',
        visualDescription: '新增镜头画面描述',
        durationSeconds: 5,
        status: 'DRAFT',
      },
      '分镜已新增',
    );
  };

  const copyStoryboard = async (storyboard: StoryboardShot) => {
    const shotNo =
      Math.max(0, ...visibleStoryboards.map((item) => item.shotNo)) + 1;
    await appendStoryboard(
      getStoryboardSavePayload(storyboard, {
        episodeNo: storyboard.episodeNo,
        shotNo,
        shotType: storyboard.shotType,
        status: 'DRAFT',
      }),
      '分镜已复制',
    );
  };

  const removeStoryboard = async (storyboard: StoryboardShot) => {
    try {
      await deleteStoryboard(projectId, storyboard.id);
      setWorkspace((previous) => ({
        ...previous,
        storyboards: previous.storyboards.filter(
          (item) => item.id !== storyboard.id,
        ),
      }));
      message.success('分镜已删除');
    } catch {
      message.error('删除分镜失败');
    }
  };

  if (!projectId) {
    return <Empty description="项目不存在" />;
  }

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '60px 1fr' }}>
          <aside
            style={{
              minHeight: 'calc(100vh - 68px)',
              borderRight: '1px solid #e5eaf3',
              background: '#fff',
              paddingTop: 24,
            }}
          >
            <div
              style={{ textAlign: 'center', fontWeight: 700, marginBottom: 17 }}
            >
              集数
            </div>
            <div style={{ position: 'relative', display: 'grid', gap: 10 }}>
              <span
                style={{
                  position: 'absolute',
                  left: 61,
                  top: 38,
                  bottom: 8,
                  width: 5,
                  borderRadius: 4,
                  background: '#75e0aa',
                }}
              />
              {episodeNumbers.map((episode) => (
                <button
                  key={episode}
                  type="button"
                  onClick={() => setActiveEpisode(episode)}
                  style={{
                    width: 36,
                    height: 36,
                    margin: '0 auto',
                    borderRadius: 6,
                    border:
                      episode === activeEpisode
                        ? '1px solid #5454ff'
                        : '1px solid #dfe5f1',
                    background: episode === activeEpisode ? '#5454ff' : '#fff',
                    color: episode === activeEpisode ? '#fff' : '#1f2937',
                    fontWeight: 600,
                    cursor: 'pointer',
                  }}
                >
                  {episode}
                </button>
              ))}
            </div>
          </aside>

          <div style={{ padding: '16px 28px 34px' }}>
            <Flex justify="space-between" align="center">
              <Button style={{ height: 32, fontWeight: 700 }}>分镜表</Button>
              <Flex gap={10}>
                <Select
                  aria-label="视频生成模型"
                  value={selectedModel}
                  onChange={setSelectedModel}
                  options={[
                    {
                      label: 'Doubao-Seedance-2.5',
                      value: 'Doubao-Seedance-2.5',
                    },
                  ]}
                  style={{ width: 202 }}
                />
                <Button icon={<BarsOutlined />} onClick={batchGenerateVideo}>
                  批量生成视频
                </Button>
              </Flex>
            </Flex>

            <section style={{ marginTop: 18, paddingLeft: 2 }}>
              <Typography.Title level={4} style={{ margin: 0, fontSize: 16 }}>
                第{activeEpisode}集{' '}
                {episodeTitles[activeEpisode] || `第${activeEpisode}集`}
              </Typography.Title>
              <Typography.Paragraph
                style={{
                  margin: '14px 0 18px',
                  color: '#536079',
                  fontSize: 14,
                }}
              >
                {episodeSummaries[activeEpisode] ||
                  '本集分镜内容已按镜头拆解，可继续编辑提示词并生成视频。'}
                <Button type="link" size="small" style={{ paddingInline: 8 }}>
                  详情
                </Button>
              </Typography.Paragraph>
            </section>

            {visibleStoryboards.length ? (
              <div style={{ display: 'grid', gap: 16 }}>
                {visibleStoryboards.map((item, index) => (
                  <StoryboardCard
                    key={item.id}
                    item={item}
                    index={index}
                    characters={characters}
                    scenes={scenes}
                    props={props}
                    imageTasks={imageTasks}
                    videoTasks={videoTasks}
                    draft={
                      drafts[item.id] || {
                        scriptText: getStoryboardScriptText(item),
                        videoPrompt: getStoryboardPrompt(item),
                      }
                    }
                    model={selectedModel}
                    onDraftChange={updateDraft}
                    onGenerate={generateVideo}
                    onSaveScript={saveStoryboardScript}
                    onUpdateStoryboard={saveStoryboardFields}
                    onAddStoryboard={addStoryboardAfter}
                    onCopyStoryboard={copyStoryboard}
                    onDelete={removeStoryboard}
                  />
                ))}
              </div>
            ) : (
              <div style={{ paddingTop: 100 }}>
                <Empty description="暂无分镜，请先完成剧本分镜拆解" />
              </div>
            )}
          </div>
        </div>
  );
};

export default ProductionWorkbenchStoryboard;
