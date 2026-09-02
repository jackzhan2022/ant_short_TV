import '@testing-library/jest-dom/vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { vi } from 'vitest';
import ScriptReviewLibraryPage from '.';

const mocks = vi.hoisted(() => ({
  queryReviewProjects: vi.fn(),
  queryReviewProject: vi.fn(),
  importReviewProject: vi.fn(),
  push: vi.fn(),
  message: { error: vi.fn(), success: vi.fn(), warning: vi.fn() },
}));

vi.mock('@umijs/max', () => ({ history: { push: mocks.push } }));
vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({ children, extra, title }: any) => <main><h1>{title}</h1>{extra}{children}</main>,
}));
vi.mock('antd', () => ({
  App: { useApp: () => ({ message: mocks.message }) },
  Button: ({ children, onClick }: any) => <button type="button" onClick={onClick}>{children}</button>,
  Card: ({ children, title }: any) => <section><h2>{title}</h2>{children}</section>,
  Empty: ({ description }: any) => <div>{description}</div>,
  Input: Object.assign(({ value, onChange, placeholder }: any) => <input value={value} onChange={onChange} placeholder={placeholder} />, { Search: ({ value, onChange, placeholder }: any) => <input value={value} onChange={onChange} placeholder={placeholder} />, TextArea: ({ value, onChange, placeholder }: any) => <textarea value={value} onChange={onChange} placeholder={placeholder} /> }),
  List: Object.assign(
    ({ dataSource = [], renderItem }: any) => <div>{dataSource.map(renderItem)}</div>,
    {
      Item: Object.assign(
        ({ children, actions }: any) => <div>{children}{actions}</div>,
        { Meta: ({ title, description }: any) => <div>{title}{description}</div> },
      ),
    },
  ),
  Modal: ({ open, title, children, onCancel, onOk }: any) => open ? <section><h2>{title}</h2>{children}<button type="button" onClick={onCancel}>取消</button><button type="button" onClick={onOk}>导入剧本</button></section> : null,
  Select: ({ value, options, onChange }: any) => <select value={value} onChange={(event) => onChange(event.target.value)}>{options.map((option: any) => <option key={option.value} value={option.value}>{option.label}</option>)}</select>,
  Space: ({ children }: any) => <div>{children}</div>,
  Tag: ({ children }: any) => <span>{children}</span>,
  Typography: { Text: ({ children }: any) => <span>{children}</span> },
  Upload: { Dragger: ({ children }: any) => <div>{children}</div> },
}));
vi.mock('@ant-design/icons', () => ({ CloudUploadOutlined: () => null, FileTextOutlined: () => null, PlusOutlined: () => null }));
vi.mock('../script-review/service', () => ({
  queryReviewProjects: mocks.queryReviewProjects,
  queryReviewProject: mocks.queryReviewProject,
  importReviewProject: mocks.importReviewProject,
}));

describe('ScriptReviewLibraryPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.queryReviewProjects.mockResolvedValue({ data: [{ id: 1, name: '待处理剧本', sourceType: 'TEXT', status: 'ACTIVE', versionCount: 2, latestRoundNo: 1 }] });
    mocks.queryReviewProject.mockResolvedValue({ data: { project: { id: 1 }, versions: [], tasks: [{ status: 'COMPLETED', issues: [{ manuallyResolved: false }] }] } });
  });

  it('opens an import modal and navigates to the selected project workbench', async () => {
    render(<ScriptReviewLibraryPage />);
    expect(await screen.findByText('待处理剧本')).toBeInTheDocument();
    expect(screen.getAllByText('待处理')).not.toHaveLength(0);
    fireEvent.click(screen.getByRole('button', { name: '新建剧本' }));
    expect(screen.getByText('新建独立剧本')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '处理问题' }));
    expect(mocks.push).toHaveBeenCalledWith('/script-review?projectId=1');
  });
});
