const { getClient, messageFor } = require('../../services/api');

Page({
  data: { dramas: [], loading: false, error: '' },
  onLoad() { this.load(); },
  onUnload() { this.disposed = true; },
  onPullDownRefresh() { this.load().finally(() => wx.stopPullDownRefresh()); },
  async load() {
    if (this.data.loading) return;
    this.setData({ loading: true, error: '', dramas: [] });
    try {
      const dramas = await getClient().dramas();
      if (!this.disposed) this.setData({ dramas });
    } catch (error) {
      if (!this.disposed) this.setData({ error: messageFor(error) });
    } finally {
      if (!this.disposed) this.setData({ loading: false });
    }
  },
  openDrama(event) {
    const drama = this.data.dramas[event.currentTarget.dataset.index];
    if (!drama) return;
    wx.navigateTo({ url: '/pages/detail/index?dramaId=' + encodeURIComponent(drama.dramaId) });
  },
});
