import type { ProLayoutProps } from '@ant-design/pro-components';
import { appThemeToken } from './theme';

/**
 * @name
 */
const Settings: ProLayoutProps & {
  logo?: string;
} = {
  navTheme: 'light',
  colorPrimary: appThemeToken.colorPrimary,
  layout: 'side',
  siderWidth: 200,
  contentWidth: 'Fluid',
  fixedHeader: true,
  fixSiderbar: true,
  colorWeak: false,
  title: '剧智创',
  logo: '/juzhichuang-logo-mark.png',
  iconfontUrl: '',
  token: {
    // 参见ts声明，demo 见文档，通过token 修改样式
    //https://procomponents.ant.design/components/layout#%E9%80%9A%E8%BF%87-token-%E4%BF%AE%E6%94%B9%E6%A0%B7%E5%BC%8F
  },
  siderMenuType: 'group',
  splitMenus: false,
};

export default Settings;
