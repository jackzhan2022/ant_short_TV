import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import AuthPageLayout from './AuthPageLayout';

afterEach(() => vi.restoreAllMocks());

it('shows an already-ready cached video even when loadeddata occurred before mount', () => {
  vi.spyOn(HTMLMediaElement.prototype, 'readyState', 'get').mockReturnValue(4);
  render(
    <AuthPageLayout>
      <div>登录表单</div>
    </AuthPageLayout>,
  );
  expect(screen.getByTestId('login-background-video')).toHaveStyle({
    opacity: '1',
  });
  expect(screen.getByTestId('login-background-fallback')).toHaveAttribute(
    'aria-hidden',
    'true',
  );
});

it('recovers visibility from canplay or playing and keeps the error fallback', () => {
  render(
    <AuthPageLayout>
      <div>登录表单</div>
    </AuthPageLayout>,
  );
  const video = screen.getByTestId('login-background-video');
  fireEvent.canPlay(video);
  expect(video).toHaveStyle({ opacity: '1' });
  fireEvent.error(video);
  expect(video).toHaveStyle({ opacity: '0' });
  fireEvent.playing(video);
  expect(video).toHaveStyle({ opacity: '1' });
});

it('retains the fallback while only metadata is available', () => {
  vi.spyOn(HTMLMediaElement.prototype, 'readyState', 'get').mockReturnValue(1);
  render(
    <AuthPageLayout>
      <div>登录表单</div>
    </AuthPageLayout>,
  );
  expect(screen.getByTestId('login-background-video')).toHaveStyle({
    opacity: '0',
  });
  expect(screen.getByTestId('login-background-fallback')).toHaveAttribute(
    'aria-hidden',
    'false',
  );
});
