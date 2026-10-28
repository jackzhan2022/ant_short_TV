import { beforeEach, expect, it, vi } from 'vitest';
import { request } from '@umijs/max';
import { getCreationDownload } from './api';
vi.mock('@umijs/max', () => ({ request: vi.fn() }));
vi.mock('@/services/point-estimate', () => ({ pointEstimatedRequest: vi.fn() }));
beforeEach(()=>{vi.clearAllMocks();vi.mocked(request).mockResolvedValue({success:true,data:{id:'download-1',status:'QUEUED'}});});
it('queues the selected creation output with its parent identity',async()=>{
 const signal=new AbortController().signal;
 await expect(getCreationDownload(14,20,signal)).resolves.toMatchObject({status:'QUEUED'});
 expect(request).toHaveBeenCalledWith('/api/downloads',expect.objectContaining({method:'POST',signal,data:{name:'自由创作_14',sources:[{kind:'FREE_CREATION',id:20,parentId:14}]}}));
});
it('surfaces permission errors',async()=>{
 vi.mocked(request).mockResolvedValue({success:false,errorMessage:'无权下载该作品'});
 await expect(getCreationDownload(14,20)).rejects.toThrow('无权下载该作品');
});
