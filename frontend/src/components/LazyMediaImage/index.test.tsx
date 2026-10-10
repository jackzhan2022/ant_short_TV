import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { StrictMode } from 'react';
import LazyMediaImage from './index';

afterEach(() => vi.unstubAllGlobals());

it('gates actual antd image sources and keeps preview media absent until preview opens', async () => {
  let enter: IntersectionObserverCallback = () => {};
  const disconnect = vi.fn();
  vi.stubGlobal(
    'IntersectionObserver',
    vi.fn(function (
      this: any,
      callback: IntersectionObserverCallback,
      options: IntersectionObserverInit,
    ) {
      enter = callback;
      expect(options.rootMargin).toBe('200px');
      this.observe = vi.fn();
      this.disconnect = disconnect;
    }),
  );
  const view = render(
    <LazyMediaImage
      src="/thumbnail.jpg"
      alt="预览图"
      width={160}
      height={90}
      preview={{ src: '/full.jpg' }}
    />,
  );
  expect(screen.getByAltText('预览图')).not.toHaveAttribute('src');
  expect(view.baseElement.querySelector('img[src="/full.jpg"]')).toBeNull();
  act(() =>
    enter(
      [{ isIntersecting: true }] as IntersectionObserverEntry[],
      {} as IntersectionObserver,
    ),
  );
  expect(screen.getByAltText('预览图')).toHaveAttribute(
    'src',
    '/thumbnail.jpg',
  );
  expect(screen.getByAltText('预览图')).toHaveAttribute('loading', 'lazy');
  expect(screen.getByAltText('预览图')).toHaveAttribute('decoding', 'async');
  fireEvent.click(screen.getByAltText('预览图'));
  await waitFor(() =>
    expect(
      view.baseElement.querySelector('img[src="/full.jpg"]'),
    ).not.toBeNull(),
  );
  view.unmount();
  expect(disconnect).toHaveBeenCalled();
});

it('requires a new visibility entry after its source changes', () => {
  const callbacks: IntersectionObserverCallback[] = [];
  vi.stubGlobal(
    'IntersectionObserver',
    vi.fn(function (this: any, callback: IntersectionObserverCallback) {
      callbacks.push(callback);
      this.observe = vi.fn();
      this.disconnect = vi.fn();
    }),
  );
  const view = render(
    <LazyMediaImage
      native
      src="/first.jpg"
      alt="换源图"
      width={160}
      height={90}
    />,
  );
  act(() =>
    callbacks[0](
      [{ isIntersecting: true }] as IntersectionObserverEntry[],
      {} as IntersectionObserver,
    ),
  );
  view.rerender(
    <LazyMediaImage
      native
      src="/second.jpg"
      alt="换源图"
      width={160}
      height={90}
    />,
  );
  expect(screen.getByAltText('换源图')).not.toHaveAttribute('src');
  act(() =>
    callbacks[1](
      [{ isIntersecting: true }] as IntersectionObserverEntry[],
      {} as IntersectionObserver,
    ),
  );
  expect(screen.getByAltText('换源图')).toHaveAttribute('src', '/second.jpg');
});


it('removes a native image source when the image is unmounted so old pages stop loading media', () => {
  const view = render(<LazyMediaImage native src="/pending-cover.jpg" alt="旧页面封面" inView />);
  const image = screen.getByAltText('旧页面封面');
  expect(image).toHaveAttribute('src', '/pending-cover.jpg');
  view.unmount();
  expect(image).not.toHaveAttribute('src');
});


it('retains a valid native source when StrictMode detaches and reattaches its ref', () => {
  const view = render(<StrictMode><LazyMediaImage native src="/strict-cover.jpg" alt="有效封面" inView /></StrictMode>);
  expect(screen.getByAltText('有效封面')).toHaveAttribute('src', '/strict-cover.jpg');
  view.rerender(<StrictMode><LazyMediaImage native src="/strict-cover.jpg" alt="有效封面" inView /></StrictMode>);
  expect(screen.getByAltText('有效封面')).toHaveAttribute('src', '/strict-cover.jpg');
});
