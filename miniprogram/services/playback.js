const { ApiError, platformError } = require('./api');

function createPlayback(api, update, now = Date.now) {
  let generation = 0;
  let selected = null;
  let signed = null;
  let loading = false;
  let position = 0;
  let disposed = false;

  async function load(ticket, resumeAt, recovering = false) {
    loading = true;
    signed = null;
    update({ videoUrl: '', playLoading: true, playError: '', initialTime: resumeAt });
    try {
      const result = await api.play(selected.dramaId, selected.episodeId);
      if (disposed || ticket !== generation) return;
      if (Date.parse(result.expiresAt) <= now()) {
        if (!recovering) {
          return await load(ticket, resumeAt, true);
        }
        throw new ApiError('EXPIRED', '签名已过期');
      }
      signed = result;
      loading = false;
      update({ videoUrl: result.url, playLoading: false, playError: '', initialTime: resumeAt });
    } catch (error) {
      if (disposed || ticket !== generation) return;
      loading = false;
      signed = null;
      update({ videoUrl: '', playLoading: false, playError: error });
    }
  }
  return {
    select(dramaId, episodeId) {
      if (disposed) return Promise.resolve();
      selected = { dramaId, episodeId };
      position = 0;
      return load(++generation, 0);
    },
    retry() {
      if (disposed || !selected || loading) return Promise.resolve();
      return load(++generation, position);
    },
    timeUpdate(seconds) {
      if (Number.isFinite(seconds) && seconds >= 0) position = seconds;
    },
    async check(errorEvent = false) {
      if (disposed || loading || !signed) return;
      const error = errorEvent ? platformError('video', errorEvent.detail) : null;
      if (Date.parse(signed.expiresAt) <= now()) {
        // 已有签名到期开启一轮恢复；本轮返回过期签名时不继续重试。
        return load(++generation, position, true);
      } else if (errorEvent) {
        signed = null;
        update({ videoUrl: '', playLoading: false, playError: error });
      }
    },
    dispose() { disposed = true; generation += 1; signed = null; },
  };
}
module.exports = { createPlayback };
