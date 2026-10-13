import { Provider, useModel } from '@@/plugin-model';
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { useCallback, useState } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { AppInitialState } from '@/app';
import { useTeamEntry } from './useTeamEntry';

const mocks = vi.hoisted(() => ({
  queryAuthBootstrap: vi.fn(),
  setCurrentTenantId: vi.fn(),
  push: vi.fn(),
  committedContexts: [] as string[],
}));
vi.mock('@umijs/max', async () => ({
  ...(await import('@@/plugin-model')),
  history: { push: mocks.push },
}));
vi.mock('@/services/account-team/auth', () => ({
  queryAuthBootstrap: mocks.queryAuthBootstrap,
  setCurrentTenantId: mocks.setCurrentTenantId,
}));

const tenant = {
  id: 12,
  code: 'T12',
  name: '新团队',
  type: 'PERSONAL',
  status: 'ACTIVE',
  memberType: 'OWNER',
  memberId: 1,
};
const bootstrap = {
  user: { id: 1, mobile: '13800000000', nickname: '用户', status: 'ACTIVE' },
  session: { sessionId: 'local-test', expiresAt: '2027-01-01T00:00:00' },
  platform: { roles: [], permissions: [] },
  tenants: [tenant],
  selectedTenant: {
    tenant,
    membership: { id: 1, memberType: 'OWNER', status: 'ACTIVE' },
    roles: ['OWNER'],
    permissions: ['PROJECT:CREATE'],
  },
  nextAction: 'ENTER_WORKSPACE',
};

const useInitialStateModel = () => {
  const [initialState, setState] = useState<AppInitialState>({
    currentUser: { name: '用户' },
    tenants: [],
    tenantPermissions: [],
  });
  const setInitialState = useCallback(
    async (
      next: AppInitialState | ((previous: AppInitialState) => AppInitialState),
    ) => {
      setState((previous) =>
        typeof next === 'function' ? next(previous) : next,
      );
    },
    [],
  );
  return { initialState, setInitialState };
};

const LayoutSubscriber = () => {
  const { initialState } = useModel('@@initialState');
  return (
    <output data-testid="layout-context">
      {initialState?.tenants?.length
        ? `team:${initialState.currentTenantId}`
        : 'no-team'}
    </output>
  );
};
const Entry = () => {
  const enterTeam = useTeamEntry();
  return (
    <button type="button" onClick={() => void enterTeam(12).catch(() => {})}>
      进入
    </button>
  );
};
const renderConsumers = () =>
  render(
    <Provider models={{ '@@initialState': useInitialStateModel }}>
      <LayoutSubscriber />
      <Entry />
    </Provider>,
  );

describe('useTeamEntry with the actual Umi model subscription chain', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.committedContexts.length = 0;
    mocks.queryAuthBootstrap.mockResolvedValue({ data: bootstrap });
    mocks.push.mockImplementation(() => {
      mocks.committedContexts.push(
        screen.getByTestId('layout-context').textContent || '',
      );
    });
  });

  it('navigates only after the layout subscriber commits the validated team', async () => {
    renderConsumers();
    expect(screen.getByTestId('layout-context')).toHaveTextContent('no-team');
    fireEvent.click(screen.getByRole('button', { name: '进入' }));
    await waitFor(() =>
      expect(mocks.push).toHaveBeenCalledWith('/projects/list'),
    );
    expect(mocks.push).toHaveBeenCalledTimes(1);
    expect(mocks.committedContexts).toEqual(['team:12']);
    expect(mocks.setCurrentTenantId).toHaveBeenCalledWith(12);
  });

  it('does not navigate while the bootstrap response is still pending', async () => {
    let resolveResponse: (value: unknown) => void = () => {};
    mocks.queryAuthBootstrap.mockReturnValue(
      new Promise((resolve) => {
        resolveResponse = resolve;
      }),
    );
    renderConsumers();
    fireEvent.click(screen.getByRole('button', { name: '进入' }));
    expect(mocks.push).not.toHaveBeenCalled();
    expect(screen.getByTestId('layout-context')).toHaveTextContent('no-team');
    await act(async () => {
      resolveResponse({ data: bootstrap });
    });
    await waitFor(() => expect(mocks.push).toHaveBeenCalledTimes(1));
  });

  it('keeps the current page if the requested team cannot be validated', async () => {
    mocks.queryAuthBootstrap.mockResolvedValue({
      data: {
        ...bootstrap,
        tenants: [],
        selectedTenant: null,
        unavailableSelectionReason: 'TENANT_MEMBERSHIP_INACTIVE',
      },
    });
    renderConsumers();
    fireEvent.click(screen.getByRole('button', { name: '进入' }));
    await waitFor(() => expect(mocks.queryAuthBootstrap).toHaveBeenCalled());
    expect(mocks.push).not.toHaveBeenCalled();
    expect(mocks.setCurrentTenantId).not.toHaveBeenCalled();
    expect(screen.getByTestId('layout-context')).toHaveTextContent('no-team');
  });
});
