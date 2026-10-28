import { beforeEach, describe, expect, it, vi } from 'vitest';
import { request } from '@umijs/max';
import { downloadEpisodeStoryboardVideos, downloadStoryboardVideo, downloadEpisodeVideoVersion } from './service';
vi.mock('@umijs/max', () => ({request:vi.fn()}));
beforeEach(()=>{vi.clearAllMocks();vi.mocked(request).mockResolvedValue({success:true,data:{id:'download-1',status:'QUEUED'}});});
describe('video archive submission',()=>{
 it.each([
   [downloadStoryboardVideo,'STORYBOARD_VIDEO'],[downloadEpisodeStoryboardVideos,'EPISODE_STORYBOARDS'],[downloadEpisodeVideoVersion,'EPISODE_VERSION'],
 ] as const)('queues %s with server-side source resolution',async(action,kind)=>{
   await action(44,31);
   expect(request).toHaveBeenCalledWith('/api/downloads',expect.objectContaining({method:'POST',data:expect.objectContaining({sources:[{kind,projectId:44,id:31}]})}));
 });
 it('does not hide backend permission failures',async()=>{
   vi.mocked(request).mockResolvedValue({success:false,errorMessage:'无权下载'});
   await expect(downloadStoryboardVideo(44,31)).rejects.toThrow('无权下载');
 });
});
