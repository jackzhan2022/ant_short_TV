import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useState } from 'react';
import ViewportMount from './ViewportMount';

afterEach(() => vi.unstubAllGlobals());

describe('ViewportMount', () => {
  it('keeps a jump target and mounts the editor once when near the viewport', () => {
    let intersect!: IntersectionObserverCallback;
    const disconnect = vi.fn();
    const observe = vi.fn();
    const observer = class {
      constructor(callback: IntersectionObserverCallback) { intersect = callback; }
      observe = observe;
      disconnect = disconnect;
    };
    vi.stubGlobal('IntersectionObserver', observer);
    const Editor = () => {
      const [draft, setDraft] = useState('original');
      return <input aria-label="draft" value={draft} onChange={(event) => setDraft(event.target.value)} />;
    };
    const { rerender, unmount } = render(<ViewportMount id="storyboard-9"><Editor /></ViewportMount>);
    const placeholder = document.getElementById('storyboard-9');
    expect(placeholder).toBeInTheDocument();
    expect(observe).toHaveBeenCalledWith(placeholder);
    expect(screen.queryByLabelText('draft')).not.toBeInTheDocument();
    act(() => intersect([{ isIntersecting: true }] as IntersectionObserverEntry[], {} as IntersectionObserver));
    fireEvent.change(screen.getByLabelText('draft'), { target: { value: 'unsaved edit' } });
    act(() => intersect([{ isIntersecting: false }] as IntersectionObserverEntry[], {} as IntersectionObserver));
    rerender(<ViewportMount id="storyboard-9"><Editor /></ViewportMount>);
    expect(screen.getByLabelText('draft')).toHaveValue('unsaved edit');
    expect(document.getElementById('storyboard-9')).toBe(placeholder);
    unmount();
    expect(disconnect).toHaveBeenCalled();
  });

  it('mounts eager cards immediately without waiting for observation', () => {
    const observer = vi.fn();
    vi.stubGlobal('IntersectionObserver', observer);
    render(<ViewportMount id="storyboard-1" eager><input aria-label="first editor" /></ViewportMount>);
    expect(screen.getByLabelText('first editor')).toBeInTheDocument();
    expect(observer).not.toHaveBeenCalled();
  });

  it('mounts all cards when IntersectionObserver is unavailable', () => {
    vi.stubGlobal('IntersectionObserver', undefined);
    render(<ViewportMount id="storyboard-2"><input aria-label="fallback editor" /></ViewportMount>);
    expect(screen.getByLabelText('fallback editor')).toBeInTheDocument();
  });
});
