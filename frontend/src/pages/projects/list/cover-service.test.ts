import { beforeEach, describe, expect, it, vi } from 'vitest';
import { bindProjectCoverUpload, startProjectCoverUpload } from './service';

const mocks = vi.hoisted(() => ({ request: vi.fn(), start: vi.fn() }));
vi.mock('@umijs/max', () => ({ request: mocks.request }));
vi.mock('@/services/mediaUpload', () => ({ startMediaUpload: mocks.start }));
beforeEach(() => vi.clearAllMocks());
describe('project cover upload integration', () => {
  it('creates a project-scoped upload session rather than saving a data URI', async () => {
    const file = new File(['image'], 'cover.png', { type: 'image/png' });
    mocks.start.mockResolvedValue({ sessionToken: 'verified-session' });
    await startProjectCoverUpload(45, file);
    expect(mocks.start).toHaveBeenCalledWith(file, { projectId: 45 });
  });
  it('binds the verified session token with the existing project cover endpoint', async () => {
    mocks.request.mockResolvedValue({
      success: true,
      data: { status: 'PENDING' },
    });
    await bindProjectCoverUpload(45, 'verified-session');
    expect(mocks.request).toHaveBeenCalledWith(
      '/api/projects/45/cover/upload',
      { method: 'POST', data: { sessionToken: 'verified-session' } },
    );
  });
});
