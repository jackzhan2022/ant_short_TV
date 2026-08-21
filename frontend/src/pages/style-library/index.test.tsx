import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import StyleLibraryPage from './index';

const mocks = vi.hoisted(() => ({
  queryStyleLibrary: vi.fn(),
}));

vi.mock('./service', () => ({
  queryStyleLibrary: mocks.queryStyleLibrary,
}));

vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({ children }: any) => <main>{children}</main>,
}));

vi.mock('antd', () => ({
  Card: ({ children, cover }: any) => (
    <article>
      {cover}
      {children}
    </article>
  ),
  Empty: ({ description }: any) => <div>{description}</div>,
  Flex: ({ children }: any) => <div>{children}</div>,
  Image: Object.assign(({ alt, src }: any) => <img alt={alt} src={src} />, {
    PreviewGroup: ({ children }: any) => <div>{children}</div>,
  }),
  Input: {
    Search: ({ onSearch, placeholder }: any) => (
      <input
        aria-label={placeholder}
        onChange={(event) => onSearch(event.currentTarget.value)}
      />
    ),
  },
  Segmented: ({ onChange, options, value }: any) => (
    <div>
      {options.map((option: any) => (
        <button
          key={option.value}
          type="button"
          aria-pressed={value === option.value}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  ),
  Space: ({ children }: any) => <div>{children}</div>,
  Spin: ({ children }: any) => <div>{children}</div>,
  Tag: ({ children }: any) => <span>{children}</span>,
  Typography: {
    Paragraph: ({ children }: any) => <p>{children}</p>,
    Text: ({ children }: any) => <span>{children}</span>,
    Title: ({ children }: any) => <h1>{children}</h1>,
  },
}));

describe('StyleLibraryPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.queryStyleLibrary.mockImplementation(({ category, keyword } = {}) => {
      const all = [
        {
          id: 1,
          externalId: '864621266010645040',
          name: '3D风格-高清真实渲染',
          category: '3D风格',
          description: '高清 3D 真实渲染风格',
          imageUrl: '/api/style-library/images/864621266010645040',
          imageWidth: 2048,
          imageHeight: 1152,
        },
        {
          id: 2,
          externalId: '826515504516146783',
          name: '赛博朋克漫风格',
          category: '未分组',
          description: '赛博朋克漫画美学',
          imageUrl: '/api/style-library/images/826515504516146783',
          imageWidth: 2752,
          imageHeight: 1536,
        },
      ];
      return Promise.resolve({
        success: true,
        data: all.filter((item) => {
          const categoryMatch =
            !category || category === '全部' || item.category === category;
          const keywordMatch =
            !keyword ||
            item.name.includes(keyword) ||
            item.description.includes(keyword);
          return categoryMatch && keywordMatch;
        }),
      });
    });
  });

  it('renders style cards from the API', async () => {
    render(<StyleLibraryPage />);

    expect(await screen.findByText('3D风格-高清真实渲染')).toBeInTheDocument();
    expect(screen.getByText('高清 3D 真实渲染风格')).toBeInTheDocument();
    expect(screen.getByAltText('3D风格-高清真实渲染')).toHaveAttribute(
      'src',
      '/api/style-library/images/864621266010645040',
    );
  });

  it('filters by category and keyword without production actions', async () => {
    render(<StyleLibraryPage />);

    await screen.findByText('3D风格-高清真实渲染');
    fireEvent.click(screen.getByRole('button', { name: '未分组' }));

    await waitFor(() => {
      expect(screen.queryByText('3D风格-高清真实渲染')).not.toBeInTheDocument();
      expect(screen.getByText('赛博朋克漫风格')).toBeInTheDocument();
    });

    fireEvent.change(screen.getByLabelText('搜索风格名称或描述'), {
      target: { value: '不存在' },
    });

    await screen.findByText('没有匹配的公共风格');
    expect(screen.queryByText('应用')).not.toBeInTheDocument();
    expect(screen.queryByText('编辑')).not.toBeInTheDocument();
    expect(screen.queryByText('复制到项目')).not.toBeInTheDocument();
  });
});
