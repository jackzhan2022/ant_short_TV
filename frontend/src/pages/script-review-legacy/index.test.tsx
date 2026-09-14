import { render, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ScriptReviewLegacyPage from '.';

const mocks = vi.hoisted(() => ({ replace: vi.fn() }));

vi.mock('@umijs/max', () => ({ history: { replace: mocks.replace } }));

describe('ScriptReviewLegacyPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it.each([
    ['/script-review?taskId=7&projectId=1', '/script-review/tasks/7'],
    ['/script-review?projectId=1', '/script-review/projects/1/reviews'],
    ['/script-review', '/script-review-library'],
  ])('redirects %s to %s', async (source, destination) => {
    window.history.replaceState({}, '', source);
    render(<ScriptReviewLegacyPage />);
    await waitFor(() => expect(mocks.replace).toHaveBeenCalledWith(destination));
  });
});
