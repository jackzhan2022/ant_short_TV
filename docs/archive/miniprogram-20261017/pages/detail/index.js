const { getClient, messageFor, ApiError } = require('../../services/api');
const { createPlayback } = require('../../services/playback');

Page({
  data: {
    drama: null, episodes: [], loading: false, error: '', selectedId: '',
    videoUrl: '', playLoading: false, playError: '', initialTime: 0,
  },
  onLoad(options) {
    this.dramaId = options.dramaId;
    this.player = createPlayback(getClient(), (state) => {
      if (this.disposed) return;
      if (state.playError) state.playError = messageFor(state.playError);
      this.setData(state);
    });
    this.load();
  },
  onShow() { if (this.player) this.player.check(); },
  onHide() {
    const video = wx.createVideoContext('drama-video', this);
    video.pause();
  },
  onUnload() {
    this.disposed = true;
    if (this.player) this.player.dispose();
  },
  async load() {
    if (this.data.loading) return;
    this.setData({ loading: true, error: '', drama: null, episodes: [], videoUrl: '', playError: '', selectedId: '' });
    try {
      if (!/^[1-9]\d*$/.test(this.dramaId || '')) throw new ApiError('NOT_FOUND', '资源不存在', 404);
      const api = getClient();
      // 没有独立详情接口：从真实上架列表取标题和描述，路由只传 dramaId。
      const [dramas, episodes] = await Promise.all([api.dramas(), api.episodes(this.dramaId)]);
      const drama = dramas.find((item) => String(item.dramaId) === this.dramaId);
      if (!drama) throw new ApiError('NOT_FOUND', '资源已下架', 404);
      if (this.disposed) return;
      this.setData({ drama, episodes });
    } catch (error) {
      if (!this.disposed) this.setData({ error: messageFor(error), videoUrl: '' });
    } finally {
      if (!this.disposed) this.setData({ loading: false });
    }
  },
  selectEpisode(event) {
    const episode = this.data.episodes[event.currentTarget.dataset.index];
    if (!episode) return;
    this.setData({ selectedId: String(episode.episodeId) });
    this.player.select(this.dramaId, episode.episodeId);
  },
  retryPlay() { return this.player.retry(); },
  onVideoError(event) { return this.player.check(event || true); },
  onVideoPlay() { return this.player.check(); },
  onTimeUpdate(event) { this.player.timeUpdate(event.detail.currentTime); },
});
