const api = require("../../utils/api");
Page({
	data: { items: [], loading: true, error: "", clearing: false },
	onShow() {
		this._alive = true;
		this.load();
	},
	onUnload() {
		this._alive = false;
		this._generation = (this._generation || 0) + 1;
	},
	async load() {
		this._generation = (this._generation || 0) + 1;
		const generation = this._generation;
		this.setData({ loading: true, error: "" });
		try {
			await api.flushProgress().catch(() => {});
			const items = await api.history();
			if (this._alive && generation === this._generation)
				this.setData({ items });
		} catch (error) {
			if (this._alive && generation === this._generation)
				this.setData({ error: error.message });
		} finally {
			if (this._alive && generation === this._generation)
				this.setData({ loading: false });
		}
	},
	resume(event) {
		const row = this.data.items[event.currentTarget.dataset.index];
		wx.navigateTo({
			url: `/pages/player/index?id=${row.dramaId}&episodeId=${row.episodeId}`,
		});
	},
	clear() {
		wx.showModal({
			title: "清空观看历史",
			content: "确定清空本小程序中的全部观看记录？",
			success: async (result) => {
				if (!result.confirm) return;
				this._generation = (this._generation || 0) + 1;
				this.setData({ clearing: true, loading: false, error: "" });
				try {
					await api.clearHistory();
					if (this._alive) this.setData({ items: [] });
				} catch (error) {
					wx.showToast({ title: error.message, icon: "none" });
				} finally {
					if (this._alive) this.setData({ clearing: false });
				}
			},
		});
	},
});
