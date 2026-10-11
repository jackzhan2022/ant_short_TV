import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import AuthPageLayout from './AuthPageLayout';

const dimensions = ['videoWidth', 'videoHeight'] as const;
const originalDimensions = dimensions.map((key) =>
  Object.getOwnPropertyDescriptor(HTMLVideoElement.prototype, key),
);
afterEach(() => {
  vi.restoreAllMocks();
  dimensions.forEach((key, index) => {
    const descriptor = originalDimensions[index];
    if (descriptor) Object.defineProperty(HTMLVideoElement.prototype, key, descriptor);
    else Reflect.deleteProperty(HTMLVideoElement.prototype, key);
  });
});

it('shows an already-ready cached video even when loadeddata occurred before mount', () => {
  vi.spyOn(HTMLMediaElement.prototype, 'readyState', 'get').mockReturnValue(4);
  Object.defineProperties(HTMLVideoElement.prototype, {
    videoWidth: { configurable: true, value: 720 },
    videoHeight: { configurable: true, value: 800 },
  });
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
  Object.defineProperties(video, {
    readyState: { value: 2 },
    videoWidth: { value: 720 },
    videoHeight: { value: 800 },
  });
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

it('keeps the fallback when media loads without a decodable video track', () => {
  render(<AuthPageLayout>Register</AuthPageLayout>);
  const video = screen.getByTestId('login-background-video');
  Object.defineProperty(video, 'readyState', { value: 4 });
  fireEvent.loadedData(video);
  fireEvent.canPlay(video);
  fireEvent.playing(video);
  expect(video).toHaveStyle({ opacity: '0' });
  expect(screen.getByTestId('login-background-fallback')).toHaveStyle({ opacity: '1' });
});
