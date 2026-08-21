import { request } from '@umijs/max';

export type ApiResponse<T> = {
  success: boolean;
  data: T;
  errorCode?: string;
  errorMessage?: string;
};

export type PublicStyle = {
  id: number;
  externalId: string;
  name: string;
  category: string;
  description: string;
  imageUrl: string;
  storagePath?: string;
  imageWidth?: number;
  imageHeight?: number;
};

export type QueryStyleLibraryParams = {
  category?: string;
  keyword?: string;
};

export const queryStyleLibrary = async (params: QueryStyleLibraryParams = {}) =>
  request<ApiResponse<PublicStyle[]>>('/api/style-library', {
    params: {
      category:
        params.category && params.category !== '全部'
          ? params.category
          : undefined,
      keyword: params.keyword || undefined,
    },
  });
