import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import InspirationGallery from './InspirationGallery';

afterEach(() => vi.restoreAllMocks());

it('keeps a long gallery window bounded and preserves access to later cards while scrolling', async () => {
  let top = 0;
  vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(
    () => ({
      width: 1200,
      top,
      bottom: top + 10000,
      left: 0,
      right: 1200,
      height: 10000,
      x: 0,
      y: top,
      toJSON: () => ({}),
    }),
  );
  const getStyle = window.getComputedStyle.bind(window);
  vi.spyOn(window, 'getComputedStyle').mockImplementation((element) => {
    return new Proxy(getStyle(element), {
      get(target, property) {
        if (property === 'gridTemplateColumns')
          return '288px 288px 288px 288px';
        if (property === 'columnGap') return '16px';
        const value = Reflect.get(target, property);
        return typeof value === 'function' ? value.bind(target) : value;
      },
    });
  });
  render(
    <InspirationGallery
      items={Array.from({ length: 200 }, (_, index) => ({
        id: index + 1,
        title: `灵感 ${index + 1}`,
        url: '/unused',
        thumbnailUrl: `/thumb/${index + 1}`,
      }))}
      onSelect={vi.fn()}
    />,
  );
  expect(screen.getAllByRole('button').length).toBeLessThan(48);
  expect(screen.getByRole('button', { name: '灵感 1' })).toBeInTheDocument();
  expect(
    screen.queryByRole('button', { name: '灵感 161' }),
  ).not.toBeInTheDocument();
  top = -7300;
  fireEvent.scroll(window);
  await waitFor(() =>
    expect(
      screen.getByRole('button', { name: '灵感 161' }),
    ).toBeInTheDocument(),
  );
  expect(screen.getAllByRole('button').length).toBeLessThan(48);
  expect(
    screen.queryByRole('button', { name: '灵感 1' }),
  ).not.toBeInTheDocument();
});
