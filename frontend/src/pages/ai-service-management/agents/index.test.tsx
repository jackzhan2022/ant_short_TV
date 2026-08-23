import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import BuiltInAgentsPage from './index';

vi.mock('../platform-service', () => ({
  queryBuiltInAgents: vi.fn().mockResolvedValue({
    success: true,
    data: [
      {
        code: 'script-character-extract',
        name: '提取角色',
        description: '从剧本中提取角色',
        businessScene: 'character_extract',
        businessSceneName: 'AI提取角色',
        capability: 'TEXT',
        modelRouting: 'PLATFORM_DEFAULT',
        variables: [],
        outputSchema: '{}',
        skills: [{ code: 'strict-json-output', name: '严格 JSON 输出', category: 'OUTPUT' }],
      },
    ],
  }),
  queryBuiltInSkills: vi.fn().mockResolvedValue({
    success: true,
    data: [
      {
        code: 'strict-json-output',
        name: '严格 JSON 输出',
        description: '只返回合法 JSON',
        category: 'OUTPUT',
        content: '只返回合法 JSON。',
        agents: [{ code: 'script-character-extract', name: '提取角色', businessScene: 'character_extract' }],
      },
    ],
  }),
  previewBuiltInAgent: vi.fn(),
}));

vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({ children }: any) => <main>{children}</main>,
  ProTable: ({ headerTitle }: any) => <section>{headerTitle}</section>,
}));

vi.mock('antd', () => ({
  Alert: ({ title }: any) => <div>{title}</div>,
  Button: ({ children }: any) => <button type="button">{children}</button>,
  Descriptions: ({ children }: any) => <div>{children}</div>,
  Drawer: ({ children }: any) => <div>{children}</div>,
  Empty: () => <div />,
  Input: { TextArea: () => <textarea /> },
  Space: ({ children }: any) => <div>{children}</div>,
  Tabs: ({ items }: any) => (
    <div>
      {items.map((item: any) => (
        <section key={item.key}>
          <h2>{item.label}</h2>
          {item.children}
        </section>
      ))}
    </div>
  ),
  Tag: ({ children }: any) => <span>{children}</span>,
  Typography: {
    Paragraph: ({ children }: any) => <p>{children}</p>,
    Text: ({ children }: any) => <span>{children}</span>,
    Title: ({ children }: any) => <h3>{children}</h3>,
  },
}));

describe('BuiltInAgentsPage', () => {
  it('renders the read-only Agent and Skill tabs', () => {
    render(<BuiltInAgentsPage />);

    expect(screen.getByText('Agent 管理')).toBeInTheDocument();
    expect(screen.getByText('系统内置 Agent')).toBeInTheDocument();
    expect(screen.getByText('Skill 管理')).toBeInTheDocument();
    expect(screen.getByText('系统预置 Skill')).toBeInTheDocument();
  });
});
