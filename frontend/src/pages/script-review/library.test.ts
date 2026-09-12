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

  it('offers the Markdown report from the latest completed task', () => {
    expect(
      deriveLibraryState({
        project: projects[0],
        task: {
          status: 'COMPLETED',
          reportMarkdown: '# 审核报告',
        },
      }),
    ).toMatchObject({
      key: 'COMPLETED',
      actionLabel: '查看报告',
    });
  });

  it('filters projects by client-side query and derived work state', () => {
    const states = new Map([
      [1, { key: 'COMPLETED' as const }],
      [2, { key: 'RUNNING' as const }],
    ]);

    expect(
      filterLibraryProjects(projects, states, '剧本', 'COMPLETED'),
    ).toEqual([projects[0]]);
  });
});
