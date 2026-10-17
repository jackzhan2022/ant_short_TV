const config = require('../config/api');

class ApiError extends Error {
  constructor(code, message, status = 0) {
    super(message);
    this.code = code;
    this.status = status;
  }
}

function messageFor(error) {
  if (error.code === 'CONFIG') return error.message;
  if (error.status === 401 || error.code === 'UNAUTHORIZED') return '登录失效或小程序已停用，请重试或联系管理员。';
  if (error.status === 403 || error.code === 'FORBIDDEN' || error.code === 'TENANT_DISABLED') return '当前内容不可访问或服务已停用。';
  if (error.status === 404 || error.code === 'NOT_FOUND') return '该剧或选集已下架，暂时无法观看。';
  if (error.code === 'NETWORK') return '网络连接失败，请检查网络后重试。';
  if (error.code === 'PLAYBACK') return '视频播放失败，请点击重试。';
  if (error.code === 'EXPIRED') return '播放地址已过期，请点击重试。';
  return '暂时无法加载，请稍后重试。';
}

function createClient(wxApi, settings = config) {
  let session = null;
  let loginFlight = null;

  function baseURL() {
    const base = (settings.baseURL || '').trim().replace(/\/+$/, '');
    if (!base) throw new ApiError('CONFIG', '请先在 config/api.js 填写 Java 服务地址，再点击重试。');
    const env = wxApi.getAccountInfoSync().miniProgram.envVersion;
    if (!/^https:\/\/[^\s/?#]+(?:\/[^\s?#]*)?$/.test(base) &&
        !(env === 'develop' && /^http:\/\/[^\s/?#]+(?:\/[^\s?#]*)?$/.test(base))) {
      throw new ApiError('CONFIG', '服务地址无效；体验版和正式版必须使用 HTTPS。');
    }
    return base;
  }

  function transport(path, method, data, token) {
    return new Promise((resolve, reject) => {
      wxApi.request({
        url: baseURL() + '/api/miniapp' + path,
        method,
        data,
        timeout: 15000,
        header: Object.assign({ 'Content-Type': 'application/json' }, token ? { Authorization: 'Bearer ' + token } : {}),
        success(response) {
          const body = response.data;
          if (response.statusCode < 200 || response.statusCode >= 300 || (body && body.success === false)) {
            reject(new ApiError(body && body.errorCode || 'HTTP', body && body.errorMessage || '请求失败', response.statusCode));
          } else if (!body || body.success !== true || !Object.prototype.hasOwnProperty.call(body, 'data')) {
            reject(new ApiError('CONTRACT', '接口响应格式不正确'));
          } else resolve(body.data);
        },
        fail() { reject(new ApiError('NETWORK', '网络连接失败')); },
      });
    });
  }

  function authenticate() {
    if (session) return Promise.resolve(session);
    if (loginFlight) return loginFlight;
    loginFlight = (async () => {
      baseURL();
      const appid = wxApi.getAccountInfoSync().miniProgram.appId;
      if (!/^wx[0-9a-fA-F]{16}$/.test(appid)) throw new ApiError('CONFIG', '请在 project.config.json 配置真实 AppID，游客模式无法登录。');
      const code = await new Promise((resolve, reject) => wxApi.login({
        timeout: 10000,
        success(result) { result.code ? resolve(result.code) : reject(new ApiError('UNAUTHORIZED', '微信登录失败')); },
        fail() { reject(new ApiError('NETWORK', '微信登录失败')); },
      }));
      const result = await transport('/auth/login', 'POST', { appid, code });
      if (!result || typeof result.accessToken !== 'string' || !result.accessToken || result.tokenType !== 'Bearer' || typeof result.expiresAt !== 'string') {
        throw new ApiError('CONTRACT', '登录响应格式不正确');
      }
      // Java 返回 LocalDateTime，不猜测服务端时区；由服务端 401 驱动一次重认证。
      session = result;
      return session;
    })().finally(() => { loginFlight = null; });
    return loginFlight;
  }

  async function request(path) {
    const current = await authenticate();
    try {
      return await transport(path, 'GET', undefined, current.accessToken);
    } catch (error) {
      if (error.status !== 401 && error.code !== 'UNAUTHORIZED') throw error;
      if (session === current) session = null;
      const refreshed = await authenticate();
      try {
        return await transport(path, 'GET', undefined, refreshed.accessToken);
      } catch (retryError) {
        if ((retryError.status === 401 || retryError.code === 'UNAUTHORIZED') && session === refreshed) session = null;
        throw retryError;
      }
    }
  }

  function id(value) {
    const text = String(value);
    if (!/^[1-9]\d*$/.test(text)) throw new ApiError('CONTRACT', '资源 ID 无效');
    return text;
  }
  async function list(path) {
    const result = await request(path);
    if (!Array.isArray(result)) throw new ApiError('CONTRACT', '列表响应格式不正确');
    return result;
  }
  return {
    dramas: () => list('/dramas'),
    episodes: (dramaId) => list('/dramas/' + id(dramaId) + '/episodes'),
    async play(dramaId, episodeId) {
      const result = await request('/dramas/' + id(dramaId) + '/episodes/' + id(episodeId) + '/play');
      const env = wxApi.getAccountInfoSync().miniProgram.envVersion;
      if (!result || typeof result.url !== 'string' || !/^https:\/\/\S+$/.test(result.url) && !(env === 'develop' && /^http:\/\/\S+$/.test(result.url)) || !Number.isFinite(Date.parse(result.expiresAt))) {
        throw new ApiError('CONTRACT', '播放响应格式不正确');
      }
      return result;
    },
  };
}

let client;
function getClient() {
  if (!client) client = createClient(wx);
  return client;
}
module.exports = { ApiError, createClient, getClient, messageFor };
