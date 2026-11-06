import { contextBridge, ipcRenderer } from 'electron';

contextBridge.exposeInMainWorld('vectorRuntime', {
  ready: () => ipcRenderer.send('vector:ready'),
  onRequest: (callback: (request: { id: string; texts: string[] }) => void) =>
    ipcRenderer.on('vector:request', (_event, request) => callback(request)),
  result: (value: unknown) => ipcRenderer.send('vector:result', value),
});
