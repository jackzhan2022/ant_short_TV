import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ProductionWorkbenchSettings from './settings';

const mocks = vi.hoisted(() => ({
  confirmScriptElement: vi.fn(),
  deleteScriptElement: vi.fn(),
  extractScriptElements: vi.fn(),
  queryScriptWorkspace: vi.fn(),
  updateScriptElement: vi.fn(),
}));

vi.mock('@umijs/max', () => ({
  useParams: () => ({ id: '1' }),
}));

vi.mock('./service', () => ({
  confirmScriptElement: mocks.confirmScriptElement,
  deleteScriptElement: mocks.deleteScriptElement,
  extractScriptElements: mocks.extractScriptElements,
  queryScriptWorkspace: mocks.queryScriptWorkspace,
  updateScriptElement: mocks.updateScriptElement,
}));

vi.mock('@ant-design/icons', () => ({
  CheckOutlined: () => <span>check</span>,
  DeleteOutlined: () => <span>delete</span>,
  EditOutlined: () => <span>edit</span>,
  PlusOutlined: () => <span>plus</span>,
  RobotOutlined: () => <span>robot</span>,
  SearchOutlined: () => <span>search</span>,
}));

vi.mock('antd', () => ({
  App: {
    useApp: () => ({ message: { error: vi.fn(), success: vi.fn() } }),
  },
  Button: ({ children, icon, onClick, ...props }: any) => (
    <button type="button" onClick={onClick} {...props}>
      {icon}
      {children}
    </button>
  ),
  Empty: ({ description }: any) => <div>{description || '暂无数据'}</div>,
  Flex: ({ children }: any) => <div>{children}</div>,
  Input: Object.assign(
    ({ value, onChange, ...props }: any) => (
      <input
        value={value}
        onChange={(event) => onChange?.(event)}
        {...props}
      />
    ),
    {
      TextArea: ({ value, onChange, ...props }: any) => (
        <textarea
          value={value}
          onChange={(event) => onChange?.(event)}
          {...props}
        />
      ),
    },
  ),
  Tag: ({ children }: any) => <span>{children}</span>,
  Typography: {
    Paragraph: ({ children }: any) => <p>{children}</p>,
    Text: ({ children }: any) => <span>{children}</span>,
    Title: ({ children }: any) => <h2>{children}</h2>,
  },
}));

const workspace = {
  projectId: 1,
  script: null,
  versions: [],
  characters: [
    {
      id: 1,
      name: '斌斌',
      roleType: '主角',
      gender: '男',
      ageRange: '6岁',
      identity: '走失儿童',
      personality: ['好奇', '胆小'],
      appearance: '圆脸，黄色上衣',
      prompt: '6岁男孩，写实都市风格',
    },
  ],
  scenes: [
    {
      id: 2,
      name: '地下停车场',
      sceneType: '室内',
      atmosphere: '压抑',
      description: '灰色轿车停在昏暗车位',
      visualStyle: '写实冷色调',
      prompt: '地下停车场，低照度',
    },
  ],
  props: [
    {
      id: 3,
      name: '灰色轿车后备箱',
      propType: '关键道具',
      appearance: '半开后备箱',
      plotFunction: '困住斌斌',
      prompt: '灰色轿车后备箱特写',
    },
  ],
  storyboards: [],
};

describe('ProductionWorkbenchSettings', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.queryScriptWorkspace.mockResolvedValue({ data: workspace });
    mocks.extractScriptElements.mockResolvedValue({ data: workspace });
    mocks.confirmScriptElement.mockResolvedValue({ data: workspace });
    mocks.deleteScriptElement.mockResolvedValue({ data: workspace });
    mocks.updateScriptElement.mockResolvedValue({ data: workspace });
  });

  it('renders restored setting assets instead of the old image task table', async () => {
    render(<ProductionWorkbenchSettings />);

    expect(await screen.findByText('设定资产')).toBeInTheDocument();
    expect(screen.getByText('角色设定')).toBeInTheDocument();
    expect(screen.getByText('场景设定')).toBeInTheDocument();
    expect(screen.getByText('道具设定')).toBeInTheDocument();
    expect(screen.getByText('斌斌')).toBeInTheDocument();
    expect(screen.getByText('地下停车场')).toBeInTheDocument();
    expect(screen.getByText('灰色轿车后备箱')).toBeInTheDocument();
    expect(screen.queryByText('AI图片生产')).not.toBeInTheDocument();

    await waitFor(() => {
      expect(mocks.queryScriptWorkspace).toHaveBeenCalledWith(1);
    });
  });

  it('reuses existing element backend actions', async () => {
    render(<ProductionWorkbenchSettings />);

    await screen.findByText('斌斌');
    fireEvent.click(screen.getByRole('button', { name: /AI提取角色/ }));
    fireEvent.click(screen.getByRole('button', { name: '确认斌斌' }));

    await waitFor(() => {
      expect(mocks.extractScriptElements).toHaveBeenCalledWith(1, {
        elementType: 'CHARACTER',
      });
      expect(mocks.confirmScriptElement).toHaveBeenCalledWith(
        1,
        'CHARACTER',
        1,
      );
    });
  });
});
