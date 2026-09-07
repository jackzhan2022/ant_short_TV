import { act, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import LazyInspirationThumbnail from './LazyInspirationThumbnail';

const callbacks: IntersectionObserverCallback[] = [];

class MockIntersectionObserver {
  constructor(callback: IntersectionObserverCallback) {
    callbacks.push(callback);
  }

  disconnect() {}

  observe() {}

  unobserve() {}

  takeRecords() {
    return [];
  }

  root = null;

  rootMargin = '';

  thresholds = [];
}

describe('LazyInspirationThumbnail', () => {
  afterEach(() => {
    callbacks.length = 0;
    vi.unstubAllGlobals();
  });

  it('assigns the thumbnail URL only after its placeholder is observed', () => {
    vi.stubGlobal('IntersectionObserver', MockIntersectionObserver);
    render(<LazyInspirationThumbnail alt="灵感 A" src="/api/inspiration-creations/1/thumbnail" />);

    const image = screen.getByAltText('灵感 A');
    expect(image).not.toHaveAttribute('src');
    act(() => {
      callbacks[0]([{ isIntersecting: true }] as IntersectionObserverEntry[], {} as IntersectionObserver);
    });

    expect(image).toHaveAttribute('src', '/api/inspiration-creations/1/thumbnail');
    expect(image).toHaveAttribute('decoding', 'async');
  });

  it('renders a placeholder when thumbnail generation is not ready', () => {
    render(<LazyInspirationThumbnail alt="灵感 B" />);

    expect(screen.getByLabelText('灵感 B 缩略图未就绪')).toBeInTheDocument();
  });
});
