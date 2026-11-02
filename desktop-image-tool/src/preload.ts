import { contextBridge, ipcRenderer } from 'electron';
import type { Creation, StudioAPI } from './shared';

const api: StudioAPI = {
  chooseDirectory: () => ipcRenderer.invoke('studio:directory'),
  state: () => ipcRenderer.invoke('studio:state'),
  settings: (input) => ipcRenderer.invoke('studio:settings', input),
  generate: (draft) => ipcRenderer.invoke('studio:generate', draft),
  stop: () => ipcRenderer.invoke('studio:stop'),
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
