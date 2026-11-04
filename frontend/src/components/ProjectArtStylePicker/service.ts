import { request } from '@umijs/max';
import type { ApiResponse } from '@/services/account-team/types';

export type ArtStyleOption = {
  code: string;
  name: string;
  category: string;
  description: string;
  imageUrl: string;
};

export const queryArtStyles = () =>
  request<ApiResponse<ArtStyleOption[]>>('/api/style-library/v2');
