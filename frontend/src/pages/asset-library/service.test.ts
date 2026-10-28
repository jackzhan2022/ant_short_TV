import { beforeEach, expect, it, vi } from 'vitest';
import { request } from '@umijs/max';
import { downloadAssetImage, downloadAssetImages } from './service';
vi.mock('@umijs/max', () => ({ request: vi.fn() }));
beforeEach(() => { vi.clearAllMocks();vi.mocked(request).mockResolvedValue({success:true,data:{id:'download-1',status:'QUEUED'}}); });
it('queues a single image instead of downloading bytes immediately',async()=>{
  const signal=new AbortController().signal;
  await expect(downloadAssetImage(22,51,signal)).resolves.toMatchObject({status:'QUEUED'});
  expect(request).toHaveBeenCalledWith('/api/downloads',expect.objectContaining({method:'POST',signal,data:{name:'图片_51',sources:[{kind:'IMAGE',projectId:22,id:51}]}}));
});
it('submits all selected images in one ZIP task',async()=>{
  await downloadAssetImages(22,[51,52]);
  expect(request).toHaveBeenCalledTimes(1);
  expect(request).toHaveBeenCalledWith('/api/downloads',expect.objectContaining({data:{name:'项目22_资产图片',sources:[{kind:'IMAGE',projectId:22,id:51},{kind:'IMAGE',projectId:22,id:52}]}}));
});
it('preserves permission errors',async()=>{
  vi.mocked(request).mockResolvedValue({success:false,errorMessage:'无权访问该项目'});
  await expect(downloadAssetImage(22,51)).rejects.toThrow('无权访问该项目');
});
