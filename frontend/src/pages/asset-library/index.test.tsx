import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import AssetLibrary from './index';

const mocks = vi.hoisted(() => ({
  push: vi.fn(), projects: vi.fn(), metadata: vi.fn(), library: vi.fn(), tenant: 10,
  location: { pathname: '/asset-library', search: '' },
}));
vi.mock('@umijs/max', () => ({
  history: { push: mocks.push },
  useLocation: () => mocks.location,
  useParams: () => ({ projectId: mocks.location.pathname.match(/projects\/(\d+)/)?.[1] }),
  useModel: () => ({ initialState: { currentTenantId: mocks.tenant } }),
}));
vi.mock('@/services/account-team/auth', () => ({ getCurrentTenantId: () => mocks.tenant }));
vi.mock('@/services/account-team/project', () => ({ queryProjects: mocks.projects, queryProjectMetadata: mocks.metadata }));
vi.mock('./service', () => ({ queryProjectAssetLibrary: mocks.library }));

const project = { id: 1, tenantId: 10, name: '长夜未央', updatedAt: '2026-10-22T01:00:00',
  effectivePermissions: ['MENU:ASSET_LIBRARY'] };
const image = { id: 51, resourceType: 'AI_IMAGE_RESULT', name: '林夏 · 主形象', category: 'CHARACTER',
  sourceType: '本地上传', thumbnailUrl: '/image-thumb', previewUrl: '/image-display', downloadUrl: '/download', current: true, updatedAt: '2026-10-22T01:00:00' };
const video = { ...image, id: 52, resourceType: 'AI_VIDEO_RESULT', name: 'EP1 · 镜头 1', episodeNo: 1,
  sourceType: '项目生成', previewUrl: '/video-playback', thumbnailUrl: '/video-cover', durationSeconds: 5 };
const response = (data = [image], total = data.length) => ({ success: true, data: {
  items: { data, current: 1, pageSize: 24, total }, episodeNumbers: [1, 2], categoryCounts: { CHARACTER: 1, SCENE: 0 },
} });
const route = (url: string) => {
  const parsed = new URL(url, 'http://localhost');
  mocks.location = { pathname: parsed.pathname, search: parsed.search };
};
beforeEach(() => {
  vi.clearAllMocks();
  vi.stubGlobal('IntersectionObserver', undefined);
  mocks.tenant = 10;
  route('/asset-library');
  mocks.push.mockImplementation(route);
  mocks.projects.mockResolvedValue({ success: true, data: { data: [project], total: 1 } });
  mocks.metadata.mockResolvedValue({ success: true, data: project });
  mocks.library.mockReset();
  mocks.library.mockResolvedValue(response());
});
afterEach(() => vi.unstubAllGlobals());

it('opens the selected project folder and previews its image without loading every project assets', async () => {
  const view = render(<AssetLibrary />);
  fireEvent.click(await screen.findByRole('button', { name: '打开长夜未央资产库' }));
  expect(mocks.library).not.toHaveBeenCalled();
  expect(mocks.push).toHaveBeenCalledWith('/asset-library/projects/1');
  view.rerender(<AssetLibrary />);
  fireEvent.click(await screen.findByRole('button', { name: '预览林夏 · 主形象' }));
  expect(mocks.library).toHaveBeenCalledWith(1,
    { mediaType: 'IMAGE', category: 'CHARACTER', episodeNo: undefined, keyword: undefined, current: 1, pageSize: 24 }, expect.any(AbortSignal));
  expect(screen.getByAltText('林夏 · 主形象大图')).toHaveAttribute('src', '/image-display');
  expect(screen.getByRole('link', { name: '下载原文件' })).toHaveAttribute('href', '/download');
});

it('filters project videos by episode and stops playback when the preview closes', async () => {
  route('/asset-library/projects/1?type=VIDEO');
  mocks.library.mockResolvedValue(response([video]));
  const view = render(<AssetLibrary />);
  fireEvent.click(await screen.findByRole('button', { name: '预览EP1 · 镜头 1' }));
  expect(screen.getByLabelText('EP1 · 镜头 1视频预览')).toHaveAttribute('src', '/video-playback');
  fireEvent.click(screen.getByRole('button', { name: 'Close' }));
  await waitFor(() => expect(screen.queryByLabelText('EP1 · 镜头 1视频预览')).not.toBeInTheDocument());
  fireEvent.change(screen.getByLabelText('筛选剧集'), { target: { value: '2' } });
  view.rerender(<AssetLibrary />);
  await waitFor(() => expect(mocks.library).toHaveBeenLastCalledWith(1,
    expect.objectContaining({ mediaType: 'VIDEO', episodeNo: 2 }), expect.any(AbortSignal)));
});

it('searches project folders on the server and paginates without fetching project details', async () => {
  route('/asset-library?keyword=长夜&page=2');
  mocks.projects.mockResolvedValue({ success: true, data: { data: [project], total: 25 } });
  render(<AssetLibrary />);
  await screen.findByRole('button', { name: '打开长夜未央资产库' });
  expect(mocks.projects).toHaveBeenCalledWith({ current: 2, pageSize: 24, keyword: '长夜' }, expect.any(AbortSignal));
  expect(mocks.metadata).not.toHaveBeenCalled();
  expect(mocks.library).not.toHaveBeenCalled();
});

it('ignores a late response after changing image categories', async () => {
  route('/asset-library/projects/1');
  let finish!: (value: unknown) => void;
  mocks.library.mockImplementationOnce(() => new Promise((resolve) => { finish = resolve; }));
  const view = render(<AssetLibrary />);
  await waitFor(() => expect(mocks.library).toHaveBeenCalledOnce());
  mocks.library.mockResolvedValue(response([{ ...image, id: 53, name: '地下停车场', category: 'SCENE' }]));
  fireEvent.click(screen.getByRole('button', { name: '场景' }));
  view.rerender(<AssetLibrary />);
  await screen.findByRole('button', { name: '预览地下停车场' });
  await act(async () => finish(response()));
  expect(screen.queryByRole('button', { name: '预览林夏 · 主形象' })).not.toBeInTheDocument();
  expect(mocks.library).toHaveBeenLastCalledWith(1, expect.objectContaining({ category: 'SCENE' }), expect.any(AbortSignal));
});

it('clears the previous team folders immediately when the team changes', async () => {
  const view = render(<AssetLibrary />);
  await screen.findByRole('button', { name: '打开长夜未央资产库' });
  mocks.tenant = 20;
  mocks.projects.mockImplementationOnce(() => new Promise(() => undefined));
  view.rerender(<AssetLibrary />);
  expect(screen.queryByRole('button', { name: '打开长夜未央资产库' })).not.toBeInTheDocument();
  expect(screen.getByRole('status')).toHaveTextContent('正在加载资产库');
});

it('does not request media without the matching project permission', async () => {
  route('/asset-library/projects/1');
  mocks.metadata.mockResolvedValue({ success: true, data: { ...project, effectivePermissions: [] } });
  render(<AssetLibrary />);
  await screen.findByText('暂无查看此类资产的权限');
  expect(mocks.library).not.toHaveBeenCalled();
});

it('retries a failed project asset request while keeping the project breadcrumb', async () => {
  route('/asset-library/projects/1');
  mocks.library.mockRejectedValueOnce(new Error('offline'));
  render(<AssetLibrary />);
  fireEvent.click(await screen.findByRole('button', { name: '重试' }));
  expect(await screen.findByRole('button', { name: '预览林夏 · 主形象' })).toBeTruthy();
  expect(screen.getByRole('navigation', { name: '资产库导航' })).toHaveTextContent('长夜未央');
});
