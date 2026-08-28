import { describe, expect, it } from 'vitest';
import defaultSettings from './defaultSettings';
import { appThemeToken } from './theme';

describe('defaultSettings branding', () => {
  it('uses the 剧智创 name and cropped public PNG logo', () => {
    expect(defaultSettings).toMatchObject({
      title: '剧智创',
      logo: '/juzhichuang-logo-mark.png',
    });
  });
});

describe('app theme tokens', () => {
  it('uses the sidebar palette as the global visual foundation', () => {
    expect(appThemeToken).toMatchObject({
      colorPrimary: '#5252ff',
      colorInfo: '#5252ff',
      colorLink: '#5252ff',
      colorBgLayout: '#f7f8fc',
      colorBgContainer: '#ffffff',
      colorText: '#292b3d',
      colorTextSecondary: '#6e718c',
      colorBorder: '#e7e9f0',
      borderRadius: 6,
    });
    expect(defaultSettings.colorPrimary).toBe(appThemeToken.colorPrimary);
  });
});
