import { contextBridge, ipcRenderer } from 'electron';
import type { AiMatchProgress } from './ai-match-types';
import type { Creation, StudioAPI } from './shared';
import type { WorkspaceAPI } from './workspace-shared';

const api: StudioAPI = {
  chooseDirectory: () => ipcRenderer.invoke('studio:directory'),
  state: () => ipcRenderer.invoke('studio:state'),
  settings: (input) => ipcRenderer.invoke('studio:settings', input),
  generate: (draft) => ipcRenderer.invoke('studio:generate', draft),
  stop: (id) => ipcRenderer.invoke('studio:stop', id),
  reuse: (id) => ipcRenderer.invoke('studio:reuse', id),
  reference: (input) => ipcRenderer.invoke('studio:reference', input),
  save: (input) => ipcRenderer.invoke('studio:save', input),
  openFolder: () => ipcRenderer.invoke('studio:folder'),
  onUpdate: (callback) => {
    const handler = (_event: Electron.IpcRendererEvent, record: Creation) =>
      callback(record);
    ipcRenderer.on('studio:update', handler);
    return () => ipcRenderer.removeListener('studio:update', handler);
  },
};
contextBridge.exposeInMainWorld('studio', api);
const workspace: WorkspaceAPI = {
  aiSettings: () => ipcRenderer.invoke('studio:workspace-ai-settings'),
  configureAi: (input) =>
    ipcRenderer.invoke('studio:workspace-ai-configure', input),
  aiMatch: (id, mode = 'vector') =>
    ipcRenderer.invoke('studio:workspace-ai-match', { projectId: id, mode }),
  onAiProgress: (callback) => {
    const listener = (
      _event: Electron.IpcRendererEvent,
      value: AiMatchProgress,
    ) => callback(value);
    ipcRenderer.on('workspace:ai-progress', listener);
    return () => ipcRenderer.removeListener('workspace:ai-progress', listener);
  },
  aiApply: (input) => ipcRenderer.invoke('studio:workspace-ai-apply', input),
  aiCancel: (reviewId) =>
    ipcRenderer.invoke('studio:workspace-ai-cancel', reviewId),
  state: (refresh) => ipcRenderer.invoke('studio:workspace-state', refresh),
  configure: (input) => ipcRenderer.invoke('studio:workspace-configure', input),
  loginCloud: () => ipcRenderer.invoke('studio:workspace-login-cloud'),
  openProject: (id) => ipcRenderer.invoke('studio:workspace-open-project', id),
  selectTeam: (id) => ipcRenderer.invoke('studio:workspace-team', id),
  loginSource: () => ipcRenderer.invoke('studio:workspace-login-source'),
  checkSource: () => ipcRenderer.invoke('studio:workspace-check-source'),
  sync: () => ipcRenderer.invoke('studio:workspace-sync'),
  assets: (id) => ipcRenderer.invoke('studio:workspace-assets', id),
  bind: (input) => ipcRenderer.invoke('studio:workspace-bind', input),
  match: (id) => ipcRenderer.invoke('studio:workspace-match', id),
  candidates: (input) =>
    ipcRenderer.invoke('studio:workspace-candidates', input),
  resolve: (input) => ipcRenderer.invoke('studio:workspace-resolve', input),
  prompts: (ids) => ipcRenderer.invoke('studio:workspace-prompts', ids),
  batch: (input) => ipcRenderer.invoke('studio:workspace-batch', input),
  queue: (input) => ipcRenderer.invoke('studio:workspace-queue', input),
  onChanged: (callback) => {
    const listener = () => callback();
    ipcRenderer.on('workspace:changed', listener);
    return () => ipcRenderer.removeListener('workspace:changed', listener);
  },
};
contextBridge.exposeInMainWorld('workspace', workspace);
