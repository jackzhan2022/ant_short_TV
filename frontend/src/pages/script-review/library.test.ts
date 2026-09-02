import { describe, expect, it } from 'vitest';
import { deriveLibraryState, filterLibraryProjects } from './library';

describe('script review library helpers', () => {
  const projects = [
    {
      id: 1,
      name: '待处理剧本',
      sourceType: 'TEXT',
      status: 'ACTIVE',
      versionCount: 2,
      latestRoundNo: 1,
    },
    {
      id: 2,
      name: '审核中剧本',
      sourceType: 'TEXT',
      status: 'ACTIVE',
      versionCount: 1,
      latestRoundNo: 1,
    },
  ];

  it('derives outstanding issue handling from the latest completed task', () => {
    expect(
      deriveLibraryState({
        project: projects[0],
        task: {
          status: 'COMPLETED',
          issues: [{ manuallyResolved: false }, { manuallyResolved: true }],
        },
      }),
    ).toMatchObject({
      key: 'ACTION_REQUIRED',
      outstandingIssueCount: 1,
      actionLabel: '处理问题',
    });
  });

  it('filters projects by client-side query and derived work state', () => {
    const states = new Map([
      [1, { key: 'ACTION_REQUIRED' as const }],
      [2, { key: 'RUNNING' as const }],
    ]);

    expect(
      filterLibraryProjects(projects, states, '剧本', 'ACTION_REQUIRED'),
    ).toEqual([projects[0]]);
  });
});
