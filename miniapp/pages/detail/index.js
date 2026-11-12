const api = require("../../utils/api");
const { groups } = require("../../utils/player-state");
Page({
	data: {
		drama: null,
		episodes: [],
		visibleEpisodes: [],
		groups: [],
		group: 0,
		resume: null,
		expanded: false,
		loading: true,
		error: "",
	},
	onLoad(options) {
		this.id = Number(options.id);
		this._alive = true;
	},
	onShow() {
		this._alive = true;
		this.load();
	},
	onUnload() {
		this._alive = false;
	},
	async load() {
		this.setData({ loading: true, error: "" });
		try {
			await api.flushProgress().catch(() => {});
			const [detail, history] = await Promise.all([
				api.detail(this.id),
				api.history().catch(() => []),
			]);
			if (!this._alive) return;
			const resume = history.find(
				(row) =>
					row.dramaId === this.id &&
					detail.episodes.some((e) => e.episodeId === row.episodeId),
			);
			const watched = wx.getStorageSync(`watched:${this.id}`) || [];
			const episodes = detail.episodes.map((e) => ({
				...e,
				watched: watched.includes(e.episodeId),
				current: e.episodeId === resume?.episodeId,
			}));
			this.setData({
				drama: detail.drama,
				episodes,
				groups: groups(episodes),
				resume: resume || null,
				group: 0,
				visibleEpisodes: episodes.slice(0, 30),
			});
		} catch (error) {
			if (this._alive)
				this.setData({
					error:
						error.status === 404
							? "这部短剧已下架，请返回剧库选择其他短剧"
							: error.message,
				});
		} finally {
			if (this._alive) this.setData({ loading: false });
		}
	},
	expand() {
		this.setData({ expanded: !this.data.expanded });
	},
	chooseGroup(event) {
		const group = Number(event.currentTarget.dataset.index);
		this.setData({
			group,
			visibleEpisodes: this.data.episodes.slice(group * 30, group * 30 + 30),
		});
	},
	start() {
		const episode =
			this.data.resume?.episodeId || this.data.episodes[0]?.episodeId;
		if (episode) this.play(episode);
	},
	choose(event) {
		this.play(event.currentTarget.dataset.id);
	},
	play(episodeId) {
		wx.navigateTo({
			url: `/pages/player/index?id=${this.id}&episodeId=${episodeId}`,
		});
	},
	onShareAppMessage() {
		return {
			title: this.data.drama?.title || "一起看短剧",
			path: `/pages/detail/index?id=${this.id}`,
		};
	},
});
