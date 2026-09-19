import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import AssetImagePlaceholder from './AssetImagePlaceholder';

describe('AssetImagePlaceholder', () => {
  it('renders a labeled standard placeholder', () => {
    render(<AssetImagePlaceholder />);

    expect(screen.getByRole('img', { name: '暂无图片' })).toHaveTextContent(
      '暂无图片',
    );
  });

  it('keeps the compact placeholder labeled without visible text', () => {
    render(<AssetImagePlaceholder compact />);

    expect(screen.getByRole('img', { name: '暂无图片' })).not.toHaveTextContent(
      '暂无图片',
    );
  });
});
