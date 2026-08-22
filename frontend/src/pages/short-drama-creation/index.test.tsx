import { App } from 'antd';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ShortDramaCreationPage from './index';

const mocks = vi.hoisted(() => ({
  createProject: vi.fn(),
  getCurrentTenantId: vi.fn(),
  historyPush: vi.fn(),
  queryInspirationCreationDetail: vi.fn(),
  queryInspirationCreations: vi.fn(),
  queryOrganizations: vi.fn(),
  queryStyleLibrary: vi.fn(),
  queryTenantMembers: vi.fn(),
}));

vi.mock('@umijs/max', () => ({
  history: {
    push: mocks.historyPush,
  },
}));

vi.mock('@/services/account-team/auth', () => ({
  getCurrentTenantId: mocks.getCurrentTenantId,
}));

vi.mock('./service', () => ({
  createProject: mocks.createProject,
  queryInspirationCreationDetail: mocks.queryInspirationCreationDetail,
  queryInspirationCreations: mocks.queryInspirationCreations,
  queryOrganizations: mocks.queryOrganizations,
  queryStyleLibrary: mocks.queryStyleLibrary,
  queryTenantMembers: mocks.queryTenantMembers,
}));

vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({ children, title }: any) => (
    <main>
      <h1>{title}</h1>
      {children}
    </main>
  ),
}));

describe('ShortDramaCreationPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.getCurrentTenantId.mockReturnValue(9);
    mocks.queryOrganizations.mockResolvedValue({ data: [] });
    mocks.queryTenantMembers.mockResolvedValue({
      data: [{ userId: 1, nickname: '负责人A' }],
    });
    mocks.queryInspirationCreations.mockResolvedValue({
      data: [
        {
          id: 101,
          externalId: '864900000000000001',
          creationType: 'IMAGE',
          taskType: 'STORY',
          title: '线上灵感 A',
          authorName: '管理员',
          localUrl: '/api/inspiration-creations/101/file',
          mimeType: 'image/png',
          sortOrder: 1,
          sourceCreatedAt: '2026-08-22T10:00:00',
        },
        {
          id: 102,
          externalId: '864900000000000002',
          creationType: 'IMAGE',
          taskType: 'STORY',
          title: '线上灵感 B',
          authorName: '管理员',
          localUrl: '/api/inspiration-creations/102/file',
          mimeType: 'image/png',
          sortOrder: 2,
          sourceCreatedAt: '2026-08-22T10:10:00',
        },
      ],
    });
    mocks.queryInspirationCreationDetail.mockResolvedValue({
      data: {
        id: 101,
        externalId: '864900000000000001',
        creationType: 'IMAGE',
        taskType: 'STORY',
        title: '线上灵感 A',
        authorName: '管理员',
        localUrl: '/api/inspiration-creations/101/file',
        mimeType: 'image/png',
        sortOrder: 1,
        sourceCreatedAt: '2026-08-22T10:00:00',
        detailJson: '{"prompt":"被误解的女主多年后带着证据回归。"}',
      },
    });
    mocks.queryStyleLibrary.mockResolvedValue({
      data: [
        {
          id: 1,
          externalId: '864621266010645040',
          name: '3D风格-高清真实渲染',
          category: '3D风格',
          description: '高清 3D 真实渲染风格',
          imageUrl: '/api/style-library/images/864621266010645040',
        },
      ],
    });
  });

  it('shows the full online inspiration gallery on the first page', async () => {
    render(
      <App>
        <ShortDramaCreationPage />
      </App>,
    );

    expect(
      screen.getByRole('heading', { name: '今天想创作 什么样的故事?' }),
    ).toBeInTheDocument();
    expect(screen.getByText('灵感广场')).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: '跳过上传，创建空白剧本' }),
    ).toBeInTheDocument();
    expect(await screen.findByText('线上灵感 A')).toBeInTheDocument();
    expect(screen.getByText('线上灵感 B')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /海外剧/ }));
    expect(screen.getByText('线上灵感 A')).toBeInTheDocument();
    expect(screen.getByText('线上灵感 B')).toBeInTheDocument();
    expect(screen.queryByText('豪门继承人归来')).not.toBeInTheDocument();
    expect(screen.queryByText('3D风格-高清真实渲染')).not.toBeInTheDocument();
    expect(mocks.queryInspirationCreations).toHaveBeenCalledWith();
    expect(mocks.queryStyleLibrary).toHaveBeenCalledWith({});
  });

  it('opens an inspiration detail panel with media and prompt', async () => {
    render(
      <App>
        <ShortDramaCreationPage />
      </App>,
    );

    fireEvent.click(await screen.findByRole('button', { name: '线上灵感 A' }));

    expect(mocks.queryInspirationCreationDetail).toHaveBeenCalledWith(
      '864900000000000001',
    );
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toBeInTheDocument();
    expect(screen.getByText('素材提示词')).toBeInTheDocument();
    expect(screen.getByText('被误解的女主多年后带着证据回归。')).toBeInTheDocument();
    expect(within(dialog).getAllByAltText('线上灵感 A')[0]).toHaveAttribute(
      'src',
      '/api/inspiration-creations/101/file',
    );
  });

  it('opens the settings page from the first page start button', async () => {
    render(
      <App>
        <ShortDramaCreationPage />
      </App>,
    );

    await waitFor(() => expect(mocks.queryStyleLibrary).toHaveBeenCalledWith({}));

    fireEvent.click(screen.getByRole('button', { name: '开始创作' }));

    expect(screen.getByRole('button', { name: /初始设定/ })).toBeInTheDocument();
  });

  it('creates a project from the settings page and opens the script workbench', async () => {
    mocks.createProject.mockResolvedValue({ data: { id: 9 } });

    render(
      <App>
        <ShortDramaCreationPage />
      </App>,
    );

    await waitFor(() => expect(mocks.queryStyleLibrary).toHaveBeenCalledWith({}));

    fireEvent.click(screen.getByRole('button', { name: '跳过上传，创建空白剧本' }));
    fireEvent.click(screen.getByRole('button', { name: /开始创作/ }));

    await waitFor(() => {
      expect(mocks.createProject).toHaveBeenCalledWith(
        expect.objectContaining({
          name: '未命名短剧',
          code: expect.stringMatching(/^SHORT_DRAMA_/),
          ownerId: 1,
        }),
      );
      expect(mocks.historyPush).toHaveBeenCalledWith(
        '/projects/9/production-workbench/script',
      );
    });
  });
});
