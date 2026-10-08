import { render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import Footer from './index';

afterEach(() => vi.unstubAllGlobals());

it('keeps the copyright while hiding app and framework version information', () => {
  vi.stubGlobal('__APP_VERSION__', '6.0.2');
  vi.stubGlobal('__UMI_VERSION__', '4.6.75');
  vi.stubGlobal('__UTOO_VERSION__', '1.4.23');
  render(<Footer />);
  expect(
    screen.getByText(`剧智创 © ${new Date().getFullYear()}`),
  ).toBeInTheDocument();
  for (const label of ['ver', '6.0.2', 'Umi', '4.6.75', 'Utoo', '1.4.23']) {
    expect(screen.queryByText(label, { exact: true })).not.toBeInTheDocument();
  }
  expect(screen.queryByRole('link')).not.toBeInTheDocument();
});
