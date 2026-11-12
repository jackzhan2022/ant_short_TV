const api = require("./api");
module.exports = function catalogPage(search = false) {
	return {
		data: {
			search,
			keyword: "",
			category: "",
			categories: [],
			items: [],
			page: 1,
			total: 0,
			loading: false,
			error: "",
			resume: null,
			hasSearched: !search,
		},
		onLoad() {
			this._generation = 0;
			this._alive = true;
			if (!search) this.load(true);
		},
		onShow() {
			this._alive = true;
			if (!search)
				api
					.flushProgress()
					.catch(() => {})
					.then(() => api.history())
					.then((rows) => {
						if (this._alive) this.setData({ resume: rows[0] || null });
					})
					.catch(() => {});
			if (!search && this._loadedAt && Date.now() - this._loadedAt > 30000)
				this.load(true);
		},
		onUnload() {
			this._alive = false;
			this._generation++;
		},
		onPullDownRefresh() {
			this.load(true).finally(() => wx.stopPullDownRefresh());
		},
		onReachBottom() {
			if (this.data.items.length < this.data.total && !this.data.loading)
				this.load(false);
		},
		async load(reset) {
			if (!reset && this.data.loading) return;
			if (reset) this.queryKeyword = this.data.keyword.trim();
			const generation = ++this._generation;
			const page = reset ? 1 : this.data.page + 1;
			this.setData({
				loading: true,
				error: "",
				...(reset ? { items: [], total: 0 } : {}),
			});
			try {
				const result = await api.catalog({
					page,
					pageSize: 20,
					keyword: this.queryKeyword || "",
					category: this.data.category,
				});
				if (!this._alive || generation !== this._generation) return;
				// Publishing while paginating can change ordering. Restart to avoid skipped or duplicated titles.
				if (!reset && this._version !== result.version) {
					this.setData({ loading: false });
					return this.load(true);
				}
				this._version = result.version;
				this._loadedAt = Date.now();
				this.setData({
					items: reset ? result.items : this.data.items.concat(result.items),
					categories: result.categories,
					page,
					total: result.total,
				});
			} catch (error) {
				if (this._alive && generation === this._generation)
					this.setData({ error: error.message });
			} finally {
				if (this._alive && generation === this._generation)
					this.setData({ loading: false });
			}
		},
		chooseCategory(event) {
			this.setData({ category: event.currentTarget.dataset.value });
			this.load(true);
		},
		input(event) {
			this.setData({ keyword: event.detail.value });
		},
		submit() {
			if (!this.data.keyword.trim()) return this.clear();
			this.setData({ hasSearched: true });
			this.load(true);
		},
		clear() {
			this._generation++;
			this.setData({
				keyword: "",
				items: [],
				total: 0,
				hasSearched: !search,
				loading: false,
				error: "",
			});
			if (!search) this.load(true);
		},
		retry() {
			this.load(this.data.items.length === 0);
		},
		openDrama(event) {
			wx.navigateTo({
				url: `/pages/detail/index?id=${event.currentTarget.dataset.id}`,
			});
		},
		openSearch() {
			wx.navigateTo({ url: "/pages/search/index" });
		},
		openHistory() {
			wx.navigateTo({ url: "/pages/history/index" });
		},
		resume() {
			const row = this.data.resume;
			if (row)
				wx.navigateTo({
					url: `/pages/player/index?id=${row.dramaId}&episodeId=${row.episodeId}`,
				});
		},
	};
};
