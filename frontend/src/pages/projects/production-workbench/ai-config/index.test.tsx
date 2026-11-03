import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  canEdit: true,
  queryProject: vi.fn(),
  queryModels: vi.fn(),
  queryConfig: vi.fn(),
  saveConfig: vi.fn(),
  success: vi.fn(),
  error: vi.fn(),
}));

vi.mock('@umijs/max', () => ({ useParams: () => ({ id: '1' }) }));
vi.mock('@/services/account-team/project', () => ({
  queryProject: mocks.queryProject,
  canUseProjectFeatures: () => mocks.canEdit,
}));
vi.mock('./service', () => ({
  queryProjectAiModels: mocks.queryModels,
  queryProjectAiConfig: mocks.queryConfig,
  saveProjectAiConfig: mocks.saveConfig,
}));
vi.mock('antd', async (original) => {
  const app = { message: { success: mocks.success, error: mocks.error } };
  return {
    ...(await original<typeof import('antd')>()),
    App: { useApp: () => app },
  };
});

import ProjectAiConfigPage from './index';

const savedConfig = {
  projectId: 1,
  textModelId: 12,
  imageModelId: 8,
  videoModelId: 11,
  audioModelId: null,
};

beforeEach(() => {
  vi.clearAllMocks();
  mocks.canEdit = true;
  mocks.queryProject.mockResolvedValue({ data: {} });
  mocks.queryConfig.mockResolvedValue({ data: { ...savedConfig } });
  mocks.queryModels.mockResolvedValue({
    data: {
      textModels: [
        { id: 12, name: 'GPT-6 Astra' },
        { id: 18, name: 'GPT-6.1 Sol' },
      ],
      imageModels: [{ id: 8, name: 'GPT Image 2' }],
      videoModels: [{ id: 11, name: 'Seedance 2.0 mini' }],
      audioModels: [],
    },
  });
  mocks.saveConfig.mockImplementation(async (_projectId, values) => ({
    data: { projectId: 1, ...values },
  }));
});

async function chooseSol() {
  const text = await screen.findByRole('combobox', { name: '文本模型' });
  await waitFor(() => expect(text).not.toBeDisabled());
  fireEvent.mouseDown(text);
  fireEvent.change(text, { target: { value: '6.1' } });
  fireEvent.click(
    await screen.findByText('GPT-6.1 Sol', {
      selector: '.ant-select-item-option-content',
    }),
  );
}

describe('compact project model configuration', () => {
  it('saves a searched text selection without changing the other model settings', async () => {
    render(<ProjectAiConfigPage />);
    await chooseSol();
    fireEvent.click(screen.getByRole('button', { name: '保存配置' }));
    await waitFor(() =>
      expect(mocks.saveConfig).toHaveBeenCalledWith(1, {
        textModelId: 18,
        imageModelId: 8,
        videoModelId: 11,
        audioModelId: null,
      }),
    );
    expect(
      await screen.findByRole('combobox', { name: '音频模型' }),
    ).toBeDisabled();
    expect(mocks.success).toHaveBeenCalled();
  });

  it('cancels an unsaved selection and restores the saved model', async () => {
    render(<ProjectAiConfigPage />);
    await chooseSol();
    fireEvent.click(screen.getByRole('button', { name: /取\s*消/ }));
    expect(
      screen.getByRole('combobox', { name: '文本模型' }).parentElement,
    ).toHaveTextContent('GPT-6 Astra');
    expect(mocks.saveConfig).not.toHaveBeenCalled();
  });

  it('retains the draft when saving fails so it can be retried', async () => {
    mocks.saveConfig.mockRejectedValueOnce(new Error('save failed'));
    render(<ProjectAiConfigPage />);
    await chooseSol();
    fireEvent.click(screen.getByRole('button', { name: '保存配置' }));
    await waitFor(() =>
      expect(mocks.error).toHaveBeenCalledWith(
        '项目 AI 模型配置保存失败，请重试',
      ),
    );
    expect(
      screen.getByRole('combobox', { name: '文本模型' }).parentElement,
    ).toHaveTextContent('GPT-6.1 Sol');
    await waitFor(() =>
      expect(
        screen.getByRole('combobox', { name: '文本模型' }),
      ).not.toBeDisabled(),
    );
    const retry = screen.getByRole('button', { name: /保存配置/ });
    await waitFor(() => {
      expect(retry).not.toBeDisabled();
      expect(retry).not.toHaveClass('ant-btn-loading');
    });
    fireEvent.click(retry);
    await waitFor(() => expect(mocks.saveConfig).toHaveBeenCalledTimes(2));
  });

  it('prevents saving or editing when configuration loading fails', async () => {
    mocks.queryConfig.mockRejectedValue(new Error('load failed'));
    render(<ProjectAiConfigPage />);
    await waitFor(() => expect(mocks.error).toHaveBeenCalled());
    expect(screen.getByRole('button', { name: '保存配置' })).toBeDisabled();
    expect(screen.getAllByRole('combobox')).toHaveLength(4);
    screen.getAllByRole('combobox').forEach((select) => {
      expect(select).toBeDisabled();
    });
  });

  it('keeps model settings readonly without edit permission', async () => {
    mocks.canEdit = false;
    render(<ProjectAiConfigPage />);
    await screen.findByText('GPT-6 Astra');
    screen.getAllByRole('combobox').forEach((select) => {
      expect(select).toBeDisabled();
    });
    expect(screen.getByRole('button', { name: '保存配置' })).toBeDisabled();
  });

  it('requires replacing an unavailable saved model before saving', async () => {
    mocks.queryConfig.mockResolvedValue({
      data: { ...savedConfig, textModelId: 99 },
    });
    render(<ProjectAiConfigPage />);
    await screen.findByText('原选模型已不可用，请重新选择');
    expect(screen.getByRole('button', { name: '保存配置' })).toBeDisabled();
    await chooseSol();
    fireEvent.click(screen.getByRole('button', { name: '保存配置' }));
    await waitFor(() =>
      expect(mocks.saveConfig).toHaveBeenCalledWith(1, {
        textModelId: 18,
        imageModelId: 8,
        videoModelId: 11,
        audioModelId: null,
      }),
    );
  });
});
