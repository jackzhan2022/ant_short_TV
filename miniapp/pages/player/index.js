const api = require("../../utils/api");
const {
	PlayerState,
	shouldRefresh,
	dragDirection,
	groups,
} = require("../../utils/player-state");
const clock = (value) => {
	const seconds = Math.max(0, Math.floor(value || 0));
	return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, "0")}`;
};
Page({
	data: {
		top: 24,
		drama: null,
		episodes: [],
		currentIndex: 0,
		current: null,
		src: "",
		initialTime: 0,
		loading: true,
		buffering: false,
		error: "",
		unavailable: false,
		paused: false,
		muted: false,
		rate: 1,
		clean: false,
		controlsVisible: true,
		drawer: false,
		groups: [],
		group: 0,
		visibleEpisodes: [],
		scrollInto: "",
		autoNext: true,
		scrubbing: false,
		position: 0,
		duration: 0,
		timeLabel: "0:00",
		durationLabel: "0:00",
		progress: 0,
	},
	onLoad(options) {
		this.id = Number(options.id);
		this.requestedEpisode = Number(options.episodeId);
		this.alive = true;
		this.visible = true;
		this.urls = new Map();
		this.positions = new Map();
		this.sequence = 0;
		this.playbackSession = `${Date.now()}-${Math.random().toString(36).slice(2)}`;
		this.setData({ top: getApp().globalData.top });
		this.network = ({ isConnected }) => {
			if (
				isConnected &&
				this.visible &&
				this.data.error &&
				!this.data.unavailable
			)
				this.retry();
		};
		wx.onNetworkStatusChange(this.network);
		this.load();
	},
	onShow() {
		this.visible = true;
		if (
			this.state &&
			!this.data.loading &&
			!this.data.error &&
			!this.data.drawer
		) {
			this.wantPaused = this.wasPaused;
			if (shouldRefresh(this.urls.get(this.state.current.episodeId)))
				this.loadMedia();
			else {
				if (!this.wasPaused) this.context()?.play();
				this.armRefresh(
					this.urls.get(this.state.current.episodeId),
					this.state.generation,
					this.state.current.episodeId,
				);
			}
		}
	},
	onHide() {
		this.visible = false;
		this.wasPaused = this.data.paused;
		this.saveProgress();
		this.context()?.pause();
		clearTimeout(this.refreshTimer);
		clearTimeout(this.controlsTimer);
	},
	onUnload() {
		this.visible = false;
		this.saveProgress();
		this.context()?.stop();
		this.alive = false;
		if (this.state) this.state.generation++;
		clearTimeout(this.refreshTimer);
		clearTimeout(this.controlsTimer);
		wx.offNetworkStatusChange(this.network);
	},
	async load() {
		this.setData({ loading: true, error: "", unavailable: false });
		try {
			await api.flushProgress().catch(() => {});
			const [detail, history] = await Promise.all([
				api.detail(this.id),
				api.history().catch(() => []),
			]);
			if (!this.alive) return;
			if (!detail.episodes.length) throw new Error("当前短剧暂无可播放单集");
			const resume = history.find((row) => row.dramaId === this.id);
			if (resume && !resume.completed)
				this.positions.set(resume.episodeId, resume.positionSeconds);
			this.state = new PlayerState(
				detail.episodes,
				this.requestedEpisode || resume?.episodeId,
			);
			this.setData({
				drama: detail.drama,
				episodes: detail.episodes,
				groups: groups(detail.episodes),
			});
			await this.select(this.state.current.episodeId, false);
		} catch (error) {
			this.fail(error);
		}
	},
	context() {
		return this.state?.current
			? wx.createVideoContext(`episode-${this.state.current.episodeId}`, this)
			: null;
	},
	async select(episodeId, save = true) {
		if (
			!this.state?.episodes.some(
				(e) => String(e.episodeId) === String(episodeId),
			)
		)
			return;
		if (save) this.saveProgress();
		this.context()?.stop();
		clearTimeout(this.refreshTimer);
		const current = this.state.select(episodeId);
		this.firstFrame = false;
		this.wantPaused = false;
		this.endedCurrent = false;
		this.retryCount = 0;
		this.position = this.positions.get(current.episodeId) || 0;
		this.duration = current.durationSeconds || 0;
		this.startedAt = Date.now();
		this.lastSaved = 0;
		this.setData({
			current,
			currentIndex: this.state.index,
			src: "",
			loading: true,
			buffering: false,
			error: "",
			unavailable: false,
			paused: false,
			position: this.position,
			initialTime: this.position,
			duration: this.duration,
			timeLabel: clock(this.position),
			durationLabel: clock(this.duration),
			progress: this.duration ? (this.position / this.duration) * 100 : 0,
		});
		await this.loadMedia();
	},
	async loadMedia(force = false) {
		if (!this.state?.current) return;
		const { episodeId } = this.state.current;
		const generation = this.state.generation;
		// Each media refresh also invalidates earlier URL responses for the same episode.
		this.mediaRequest = (this.mediaRequest || 0) + 1;
		const request = this.mediaRequest;
		this.setData({ loading: true, error: "" });
		try {
			let media = this.urls.get(episodeId);
			if (force || shouldRefresh(media))
				media = await api.play(this.id, episodeId);
			if (
				!this.alive ||
				!this.state.accepts(generation, episodeId) ||
				request !== this.mediaRequest
			)
				return;
			this.urls.set(episodeId, media);
			this.setData(
				{
					src: media.url,
					initialTime: this.position || 0,
					loading: false,
					paused: !!this.wantPaused,
				},
				() => {
					if (!this.alive || !this.state.accepts(generation, episodeId)) return;
					this.context()?.playbackRate(this.data.rate);
					if (this.visible && !this.wantPaused && !this.data.drawer)
						this.context()?.play();
					else this.context()?.pause();
				},
			);
			if (this.visible) this.armRefresh(media, generation, episodeId);
			this.prepareNext();
		} catch (error) {
			if (
				this.alive &&
				this.state.accepts(generation, episodeId) &&
				request === this.mediaRequest
			)
				this.fail(error);
		}
	},
	armRefresh(media, generation, episodeId) {
		clearTimeout(this.refreshTimer);
		const delay = Math.max(
			1000,
			Date.parse(media.expiresAt) - Date.now() - 30000,
		);
		this.refreshTimer = setTimeout(() => {
			if (
				!this.visible ||
				!this.alive ||
				!this.state.accepts(generation, episodeId)
			)
				return;
			this.wantPaused = this.data.paused || this.data.drawer;
			this.context()?.pause();
			this.loadMedia(true);
		}, delay);
	},
	async prepareNext() {
		const next = this.state.episodes[this.state.index + 1];
		if (!next || !shouldRefresh(this.urls.get(next.episodeId))) return;
		try {
			const media = await api.play(this.id, next.episodeId);
			if (this.alive) {
				this.urls.set(next.episodeId, media);
				// Retain at most the current, previous and next signed addresses.
				for (const key of this.urls.keys()) {
					if (
						!this.state.episodes
							.slice(Math.max(0, this.state.index - 1), this.state.index + 2)
							.some((e) => e.episodeId === key)
					)
						this.urls.delete(key);
				}
			}
		} catch (_) {
			/* A failed preparation is retried on selection. */
		}
	},
	swipe(event) {
		const index = event.detail.current;
		this.nativeIndex = index;
		// Only touchEnd commits a gesture. Delayed programmatic change events must
		// never select an episode or replace a newer user selection.
		if (
			event.detail.source === "touch" &&
			!this.touch &&
			this.state &&
			index !== this.state.index
		)
			this.rebound(index);
	},
	rebound(index = this.state?.index) {
		if (!this.state || index === undefined) return;
		this.setData({ currentIndex: index }, () => {
			if (this.alive) this.setData({ currentIndex: this.state.index });
		});
	},
	touchStart(event) {
		if (!this.state || this.data.drawer) return;
		const point = event.touches[0];
		this.touch = {
			x: point.clientX,
			y: point.clientY,
			index: this.state?.index,
		};
	},
	touchEnd(event) {
		if (!this.touch || !this.state || this.data.drawer) return;
		if (this.touch.index !== this.state.index) {
			this.touch = null;
			this.rebound(this.nativeIndex);
			return;
		}
		const point = event.changedTouches[0];
		const direction = dragDirection(
			point.clientX - this.touch.x,
			point.clientY - this.touch.y,
		);
		const target =
			direction && this.state.episodes[this.touch.index + direction];
		if (
			(direction < 0 && this.touch.index === 0) ||
			(direction > 0 && this.touch.index === this.state.episodes.length - 1)
		)
			wx.showToast({
				title: direction < 0 ? "已经是第一集" : "已经是最后一集",
				icon: "none",
			});
		this.touch = null;
		if (target) this.select(target.episodeId);
		else this.rebound(this.nativeIndex);
	},
	touchCancel() {
		this.touch = null;
		this.rebound(this.nativeIndex);
	},
	togglePlay() {
		if (this.data.error) return this.retry();
		if (this.data.loading) return;
		this.wantPaused = !this.data.paused;
		this.setData({ controlsVisible: true });
		if (!this.wantPaused && this.endedCurrent) {
			this.endedCurrent = false;
			this.position = 0;
			this.context()?.seek(0);
		}
		if (this.wantPaused) this.context()?.pause();
		else if (shouldRefresh(this.urls.get(this.state.current.episodeId)))
			this.loadMedia(true);
		else this.context()?.play();
		this.setData({ paused: this.wantPaused });
	},
	scheduleControls() {
		clearTimeout(this.controlsTimer);
		this.controlsTimer = setTimeout(() => {
			if (this.alive && this.visible && !this.data.paused && !this.data.drawer)
				this.setData({ controlsVisible: false });
		}, 3000);
	},
	metadata(event) {
		if (!this.matches(event)) return;
		// initial-time is not re-applied consistently when a native video changes src.
		if (this.position > 0) this.context()?.seek(this.position);
		this.context()?.playbackRate(this.data.rate);
		if (!this.visible || this.wantPaused || this.data.drawer)
			this.context()?.pause();
	},
	onPlay(event) {
		if (!this.matches(event)) return;
		if (!this.visible || this.data.drawer || this.wantPaused)
			return this.context()?.pause();
		this.setData({ paused: false });
		this.scheduleControls();
	},
	onPause(event) {
		if (this.matches(event) && !this.data.loading)
			this.setData({ paused: true });
	},
	matches(event) {
		return (
			this.alive &&
			String(event.currentTarget.dataset.id) ===
				String(this.state?.current?.episodeId)
		);
	},
	timeUpdate(event) {
		if (!this.matches(event) || !this.visible) return;
		this.position = event.detail.currentTime;
		this.duration = event.detail.duration || this.duration;
		this.positions.set(this.state.current.episodeId, this.position);
		if (!this.firstFrame && this.position > this.data.initialTime + 0.05) {
			this.firstFrame = true;
			this.retryCount = 0;
			this.report("FIRST_FRAME", Date.now() - this.startedAt);
		}
		const data = {
			buffering: false,
			timeLabel: clock(this.position),
			durationLabel: clock(this.duration),
			position: this.position,
			duration: this.duration,
		};
		if (!this.scrubbing)
			data.progress = this.duration ? (this.position / this.duration) * 100 : 0;
		this.setData(data);
		if (Date.now() - this.lastSaved > 5000) this.saveProgress();
	},
	waiting(event) {
		if (this.matches(event)) {
			if (!this.data.buffering) this.report("STALL");
			this.setData({ buffering: true });
		}
	},
	async videoError(event) {
		if (!this.matches(event)) return;
		this.report("ERROR");
		this.context()?.pause();
		if (this.retryCount++ === 0) {
			this.wantPaused = false;
			await this.loadMedia(true);
		} else this.fail(new Error("视频暂时无法播放，请检查网络后重试"));
	},
	fail(error) {
		if (!this.alive) return;
		clearTimeout(this.refreshTimer);
		this.setData({
			loading: false,
			buffering: false,
			paused: true,
			error:
				error.status === 404
					? "本集已下架，请返回剧库查看最新目录"
					: error.message,
			unavailable: error.status === 404,
		});
	},
	retry() {
		if (!this.state) this.load();
		else {
			this.retryCount = 0;
			this.wantPaused = false;
			this.loadMedia(true);
		}
	},
	ended(event) {
		if (!this.matches(event) || !this.visible) return;
		this.position = this.duration;
		this.endedCurrent = true;
		this.saveProgress(true);
		const next = this.state.episodes[this.state.index + 1];
		if (next && this.data.autoNext) this.select(next.episodeId, false);
		else {
			this.wantPaused = true;
			this.setData({ paused: true });
			if (!next) wx.showToast({ title: "已看完当前上架剧集", icon: "none" });
		}
	},
	saveProgress(completed = this.endedCurrent || false) {
		if (
			!this.state?.current ||
			!this.duration ||
			!Number.isFinite(this.position)
		)
			return;
		const episodeId = this.state.current.episodeId;
		this.positions.set(episodeId, completed ? 0 : this.position);
		this.lastSaved = Date.now();
		if (completed) {
			const key = `watched:${this.id}`,
				watched = wx.getStorageSync(key) || [];
			if (!watched.includes(episodeId))
				wx.setStorageSync(key, watched.concat(episodeId).slice(-1000));
		}
		api
			.progress(this.id, {
				episodeId,
				positionSeconds: this.position,
				durationSeconds: this.duration,
				completed,
				sequence: ++this.sequence,
				playbackSession: this.playbackSession,
			})
			.catch(() => {});
	},
	report(event, elapsedMs) {
		if (this.state?.current)
			api
				.event({
					dramaId: this.id,
					episodeId: this.state.current.episodeId,
					event,
					...(elapsedMs === undefined ? {} : { elapsedMs }),
				})
				.catch(() => {});
	},
	seekStart() {
		this.scrubbing = true;
		this.setData({ scrubbing: true });
	},
	seek(event) {
		this.scrubbing = false;
		this.endedCurrent = false;
		this.setData({ scrubbing: false });
		this.position = (this.duration * event.detail.value) / 100;
		this.context()?.seek(this.position);
		this.setData({
			progress: event.detail.value,
			timeLabel: clock(this.position),
		});
		this.saveProgress();
	},
	mute() {
		this.setData({ muted: !this.data.muted });
	},
	rate() {
		const rates = [0.5, 0.8, 1, 1.25, 1.5, 2];
		wx.showActionSheet({
			itemList: rates.map((rate) => `${rate} 倍速`),
			success: ({ tapIndex }) => {
				this.setData({ rate: rates[tapIndex] });
				this.context()?.playbackRate(rates[tapIndex]);
			},
		});
	},
	clean() {
		this.setData({ clean: !this.data.clean });
	},
	autoNext(event) {
		this.setData({ autoNext: event.detail.value });
	},
	openDrawer() {
		clearTimeout(this.controlsTimer);
		this.drawerWasPaused = this.data.paused;
		this.context()?.pause();
		const group = Math.floor(this.state.index / 30);
		this.setData({ drawer: true, group });
		this.updateDrawer(group);
		this.setData({ scrollInto: `choice-${this.state.current.episodeId}` });
	},
	closeDrawer() {
		this.setData({ drawer: false });
		if (!this.drawerWasPaused && this.visible) {
			this.wantPaused = false;
			if (shouldRefresh(this.urls.get(this.state.current.episodeId)))
				this.loadMedia(true);
			else this.context()?.play();
		}
	},
	updateDrawer(group) {
		const watched = wx.getStorageSync(`watched:${this.id}`) || [];
		this.setData({
			visibleEpisodes: this.state.episodes
				.slice(group * 30, group * 30 + 30)
				.map((e) => ({
					...e,
					watched: watched.includes(e.episodeId),
					current: e.episodeId === this.state.current.episodeId,
				})),
		});
	},
	chooseGroup(event) {
		const group = Number(event.currentTarget.dataset.index);
		this.setData({ group, scrollInto: "" });
		this.updateDrawer(group);
	},
	chooseEpisode(event) {
		this.setData({ drawer: false });
		this.select(event.currentTarget.dataset.id);
	},
	noop() {},
	back() {
		if (getCurrentPages().length > 1) wx.navigateBack();
		else wx.redirectTo({ url: `/pages/detail/index?id=${this.id}` });
	},
	library() {
		wx.reLaunch({ url: "/pages/home/index" });
	},
});
