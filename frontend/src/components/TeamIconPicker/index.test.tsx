import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { Button, Form } from 'antd';
import { describe, expect, it, vi } from 'vitest';
import TeamIconPicker from './index';
import { TeamIcon } from './teamIcons';

const openPicker = async () => {
  fireEvent.click(screen.getByRole('button', { name: '选择团队图标' }));
  return screen.findByRole('group', { name: '可选团队图标' });
};

describe('TeamIconPicker', () => {
  it('uses the default team icon without writing a value before selection', async () => {
    const onChange = vi.fn();
    render(<TeamIconPicker onChange={onChange} />);
    expect(screen.getByText('默认团队图标')).toBeInTheDocument();
    await openPicker();
    expect(screen.getByRole('button', { name: '团队' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(onChange).not.toHaveBeenCalled();
  });

  it('selects a built-in icon and highlights its controlled value', async () => {
    const onChange = vi.fn();
    const { rerender } = render(
      <TeamIconPicker value="icon:star" onChange={onChange} />,
    );
    await openPicker();
    expect(screen.getByRole('button', { name: '星星' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    fireEvent.click(screen.getByRole('button', { name: '相机' }));
    expect(onChange).toHaveBeenCalledWith('icon:camera');
    rerender(<TeamIconPicker value="icon:camera" onChange={onChange} />);
    expect(
      screen.getByRole('button', { name: '选择团队图标' }),
    ).toHaveTextContent('相机');
  });

  it('can restore the default icon instead of requiring a logo URL', () => {
    const onChange = vi.fn();
    render(<TeamIconPicker value="icon:camera" onChange={onChange} />);
    fireEvent.click(screen.getByRole('button', { name: '恢复默认图标' }));
    expect(onChange).toHaveBeenCalledWith(undefined);
  });

  it('saves and recalls icon keys through a real antd Form', async () => {
    const onFinish = vi.fn();
    render(
      <Form initialValues={{ logo: 'icon:star' }} onFinish={onFinish}>
        <Form.Item name="logo" label="团队图标">
          <TeamIconPicker />
        </Form.Item>
        <Button htmlType="submit">保存</Button>
      </Form>,
    );
    expect(screen.getByLabelText('团队图标')).toHaveAttribute('id', 'logo');
    await openPicker();
    fireEvent.click(screen.getByRole('button', { name: '相机' }));
    fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
    await waitFor(() =>
      expect(onFinish).toHaveBeenCalledWith({ logo: 'icon:camera' }),
    );
  });

  it('preserves an existing image URL until an explicit replacement', async () => {
    const onChange = vi.fn();
    render(
      <TeamIconPicker
        value="https://example.test/team-logo.png"
        onChange={onChange}
      />,
    );
    expect(screen.getByRole('img', { name: '团队图片标识' })).toHaveAttribute(
      'src',
      'https://example.test/team-logo.png',
    );
    expect(screen.getByText('当前图片 Logo')).toBeInTheDocument();
    await openPicker();
    expect(onChange).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '影视' }));
    expect(onChange).toHaveBeenCalledWith('icon:video');
  });

  it('shows a read-only preview without selection or reset controls', () => {
    const onChange = vi.fn();
    render(<TeamIconPicker value="icon:camera" onChange={onChange} disabled />);
    expect(screen.getByText('相机')).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
    expect(onChange).not.toHaveBeenCalled();
  });

  it('falls back for an unknown icon instead of treating it as an image URL', () => {
    render(<TeamIcon value="icon:unsupported" />);
    expect(
      screen.getByRole('img', { name: '团队图标：团队' }),
    ).toBeInTheDocument();
    expect(document.querySelector('img')).toBeNull();
  });

  it('falls back if a legacy image fails and can render a replacement image', () => {
    const { rerender } = render(
      <TeamIcon value="https://example.test/broken.png" />,
    );
    fireEvent.error(screen.getByRole('img', { name: '团队图片标识' }));
    expect(
      screen.getByRole('img', { name: '团队图标：团队' }),
    ).toBeInTheDocument();
    rerender(<TeamIcon value="https://example.test/new.png" />);
    expect(screen.getByRole('img', { name: '团队图片标识' })).toHaveAttribute(
      'src',
      'https://example.test/new.png',
    );
  });

  it('does not load unsafe or unsupported URL schemes', () => {
    render(<TeamIcon value="javascript:alert(1)" />);
    expect(document.querySelector('img')).toBeNull();
    expect(
      screen.getByRole('img', { name: '团队图标：团队' }),
    ).toBeInTheDocument();
  });
});
