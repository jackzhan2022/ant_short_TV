import { cleanup, render, screen } from '@testing-library/react';
import { memo, type ComponentProps } from 'react';
import { afterEach, expect, it, vi } from 'vitest';
import { StoryboardCardProvider, useStoryboardMedia, useStoryboardResources } from './storyboard-card-context';

type Input = ComponentProps<typeof StoryboardCardProvider>;
type Media = Input['media'];

const renders = vi.fn();
afterEach(() => { cleanup(); renders.mockClear(); });

const fixture = (): Omit<Input, 'children'> => ({
  projectId: 1,
  storyboards: [1, 2].map(id => ({ id, shotNo: id, episodeNo: 1, visualDescription: `分镜${id}` })) as Input['storyboards'],
  editor: {} as Input['editor'],
  media: { imageTasks: [], videoTasks: [], videoHistories: {} } as unknown as Media,
  resources: {
    projectId: 1, canCaptureVideo: false, characters: [], scenes: [], props: [], episodes: [],
    referenceOptions: { IMAGE: [], VIDEO: [], AUDIO: [] }, referenceThumbnails: new Map(), model: '模型',
  },
  video: {
    settingsFor: () => ({ durationSeconds: 5, resolution: '720p', watermark: false, generateAudio: false }),
    statuses: {}, saveSettings: vi.fn(), retrySettings: vi.fn(), promptFor: () => ({ version: 2, nodes: [] }),
    preview: () => undefined, generate: vi.fn(),
  },
  assets: { uploaded: vi.fn(), generate: vi.fn(), generating: new Set(), loadVariants: vi.fn() },
  actions: { add: vi.fn(), copy: vi.fn(), remove: vi.fn(), capture: vi.fn(), erase: vi.fn() },
  erasure: { tasks: [], previews: {}, retry: vi.fn() },
});

  const MediaCard = memo(({ id }: { id: number }) => {
    const media = useStoryboardMedia(id);
    const resources = useStoryboardResources();
    renders(id);
    return <div data-testid={`card-${id}`}>{media.videoTasks?.[0]?.status || 'idle'}:{resources.referenceOptions.VIDEO.length}</div>;
  });

it('updates only the affected media card when polling recreates unchanged material choices', () => {
  const input = fixture();
  const children = <><MediaCard id={1} /><MediaCard id={2} /></>;
  const view = render(<StoryboardCardProvider {...input}>{children}</StoryboardCardProvider>);
  renders.mockClear();
  const updated = {
    ...input,
    media: { ...input.media, videoTasks: [{ id: 80, storyboardId: 1, status: 'GENERATING', results: [] }] } as unknown as Media,
    resources: { ...input.resources, referenceOptions: { IMAGE: [], VIDEO: [], AUDIO: [] }, referenceThumbnails: new Map<string, string>() },
  };
  view.rerender(<StoryboardCardProvider {...updated}>{children}</StoryboardCardProvider>);
  expect(screen.getByTestId('card-1')).toHaveTextContent('GENERATING:0');
  expect(renders.mock.calls.every(([id]) => id === 1)).toBe(true);
  expect(renders).toHaveBeenCalled();

  // A genuinely new shared material must still become available to every card.
  renders.mockClear();
  view.rerender(<StoryboardCardProvider {...updated} resources={{ ...updated.resources,
    referenceOptions: { ...updated.resources.referenceOptions, VIDEO: [{
      type: 'mention', mediaType: 'VIDEO', sourceType: 'VIDEO_MATERIAL', sourceId: 800, displayName: '新视频',
    }] },
  }}>{children}</StoryboardCardProvider>);
  expect(screen.getByTestId('card-1')).toHaveTextContent('GENERATING:1');
  expect(screen.getByTestId('card-2')).toHaveTextContent('idle:1');
  expect(renders).toHaveBeenCalledWith(2);
});

  const BindingCard = memo(({ id }: { id: number }) => {
    const media = useStoryboardMedia(id);
    renders(id);
    return <button type="button" onClick={() => media.bindVideoResult(80)} data-testid={`bind-${id}`}>
      {media.bindingVideoId || 'ready'}
    </button>;
  });

it('scopes binding progress to its owning card and reads the latest action callback', () => {
  const input = fixture();
  const children = <><BindingCard id={1} /><BindingCard id={2} /></>;
  const view = render(<StoryboardCardProvider {...input}>{children}</StoryboardCardProvider>);
  const bind = vi.fn();
  renders.mockClear();
  view.rerender(<StoryboardCardProvider {...input} media={{ ...input.media,
    bindingVideoId: 80, bindingStoryboardId: 1, bindVideoResult: bind,
  }}>{children}</StoryboardCardProvider>);
  expect(screen.getByTestId('bind-1')).toHaveTextContent('80');
  expect(screen.getByTestId('bind-2')).toHaveTextContent('ready');
  expect(renders.mock.calls.every(([id]) => id === 1)).toBe(true);
  screen.getByTestId('bind-2').click();
  expect(bind).toHaveBeenCalledExactlyOnceWith(80, 2);
});
