export const BASE_URL = 'http://89.58.47.162:8080';
export const SIZES = [
  'auto',
  '1024x1024',
  '1536x1024',
  '1024x1536',
  '1536x864',
  '864x1536',
] as const;
export type Format = 'png' | 'jpeg' | 'webp';
export type Quality = 'auto' | 'low' | 'medium' | 'high';
export interface Reference {
  name: string;
  dataUrl: string;
}
export interface Draft {
  outputName?: string;
  prompt: string;
  model: string;
  size: string;
  quality: Quality;
  format: Format;
  references: Reference[];
}
export interface Creation {
  outputName?: string;
  outputDirectory?: string;
  savedFiles?: string[];
  saveError?: string;
  id: string;
  createdAt: string;
  finishedAt?: string;
  status: 'running' | 'succeeded' | 'failed' | 'stopped' | 'interrupted';
  prompt: string;
  model: string;
  size: string;
  quality: Quality;
  format: Format;
  referenceCount: number;
  imageCount: number;
  error?: string;
  responseId?: string;
}
export interface Settings {
  outputDirectory: string;
  hasKey: boolean;
  responseModel: string;
  baseUrl: string;
}
export type Result<T> = { ok: true; value: T } | { ok: false; error: string };
export interface StudioAPI {
  chooseDirectory(): Promise<Result<Settings>>;
  state(): Promise<Result<{ settings: Settings; records: Creation[] }>>;
  settings(input: {
    key?: string;
    responseModel: string;
    clearKey?: boolean;
  }): Promise<Result<Settings>>;
  generate(draft: Draft): Promise<Result<Creation>>;
  stop(): Promise<Result<void>>;
  reuse(id: string): Promise<Result<Draft>>;
  reference(input: { id: string; index: number }): Promise<Result<Reference>>;
  save(input: { id: string; index: number }): Promise<Result<boolean>>;
  openFolder(): Promise<Result<void>>;
  onUpdate(callback: (record: Creation) => void): () => void;
}
