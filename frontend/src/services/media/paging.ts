export type MediaPage<T> = {
  data: T[];
  current: number;
  pageSize: number;
  total: number;
};

export type MediaPageParams = { current?: number; pageSize?: number };
