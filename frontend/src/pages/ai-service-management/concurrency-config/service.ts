import { request } from '@umijs/max';
import type { ApiResponse } from '../platform-service';
import {
  type ConcurrencyConfig,
  type ConcurrencySettings,
  settingMaximums,
} from './types';

const endpoint = '/api/platform/ai/concurrency-config';

const readConfig = (
  response: ApiResponse<ConcurrencyConfig>,
): ConcurrencyConfig => {
  if (!response?.success) {
    throw new Error(response?.errorMessage || '配置请求失败，请重试');
  }
  const config = response.data;
  if (
    !config?.settings ||
    !Number.isInteger(config.databaseMaxConnections) ||
    config.databaseMaxConnections <= 0 ||
    Object.entries(settingMaximums).some(([key, max]) => {
      const value = config.settings[key as keyof ConcurrencySettings];
      return !Number.isInteger(value) || value < 1 || value > max;
    })
  ) {
    throw new Error('配置响应不完整或数值无效，请重试');
  }
  return config;
};

export const getConcurrencyConfig = async () =>
  readConfig(
    await request<ApiResponse<ConcurrencyConfig>>(endpoint, {
      method: 'GET',
      skipErrorHandler: true,
    }),
  );

export const updateConcurrencyConfig = async (
  settings: ConcurrencySettings,
) => {
  const data = Object.fromEntries(
    Object.keys(settingMaximums).map((key) => [
      key,
      settings[key as keyof ConcurrencySettings],
    ]),
  );
  return readConfig(
    await request<ApiResponse<ConcurrencyConfig>>(endpoint, {
      method: 'PUT',
      data,
      skipErrorHandler: true,
    }),
  );
};
