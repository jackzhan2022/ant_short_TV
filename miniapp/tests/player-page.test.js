const test = require("node:test");
const assert = require("node:assert/strict");
const vm = require("node:vm");
const fs = require("node:fs");
const path = require("node:path");
const state = require("../utils/player-state");
const tick = () => new Promise((resolve) => setImmediate(resolve));

async function harness() {
	const episodes = [1, 3, 5].map((n) => ({
		episodeId: n * 10,
		episodeNo: n,
		title: `第${n}集`,
		durationSeconds: 20,
	}));
	const calls = [],
		progress = [],
		timers = new Map();
	let timerId = 0,
		config;
	const api = {
		flushProgress: async () => {},
		detail: async () => ({ drama: { title: "春山" }, episodes }),
		history: async () => [],
		play: async (_id, episodeId) => ({
			url: `https://cdn.example/${episodeId}`,
			expiresAt: new Date(Date.now() + 300000).toISOString(),
		}),
		progress: async (_id, data) => {
			progress.push(data);
		},
		event: async () => {},
	};
	const wx = {
		createVideoContext(id) {
			return Object.fromEntries(
				["play", "pause", "stop", "seek", "playbackRate"].map((method) => [
					method,
					(arg) => calls.push({ id, method, arg }),
				]),
			);
		},
		onNetworkStatusChange() {},
		offNetworkStatusChange() {},
		getStorageSync() {
			return [];
		},
		setStorageSync() {},
		showToast() {},
	};
	vm.runInNewContext(
		fs.readFileSync(path.join(__dirname, "../pages/player/index.js"), "utf8"),
		{
			require: (name) => (name.endsWith("/api") ? api : state),
			Page: (value) => {
				config = value;
			},
			wx,
			getApp: () => ({ globalData: { top: 24 } }),
			setTimeout(fn) {
				timers.set(++timerId, fn);
				return timerId;
			},
			clearTimeout(id) {
				timers.delete(id);
			},
		},
	);
	const page = {
		...config,
		data: structuredClone(config.data),
		setData(data, callback) {
			Object.assign(this.data, data);
			callback?.();
		},
	};
	page.onLoad({ id: "1", episodeId: "10" });
	await tick();
	return { page, api, calls, progress, timers };
}
test("rapid swipe ignores stale URL and pauses the old video before selecting another", async () => {
	const { page, api, calls } = await harness();
	let resolveOld;
	api.play = async (_id, id) =>
		id === 30
			? new Promise((resolve) => {
					resolveOld = resolve;
				})
			: {
					url: `fresh-${id}`,
					expiresAt: new Date(Date.now() + 300000).toISOString(),
				};
	page.urls.clear();
	const oldSelection = page.select(30);
	await page.select(50);
	resolveOld({
		url: "stale-30",
		expiresAt: new Date(Date.now() + 300000).toISOString(),
	});
	await oldSelection;
	assert.equal(page.data.current.episodeNo, 5);
	assert.equal(page.data.src, "fresh-50");
	assert.ok(calls.some((c) => c.id === "episode-10" && c.method === "stop"));
});
test("background return refreshes expired source at saved position and respects pause", async () => {
	const { page, api, calls } = await harness();
	page.position = 7;
	page.duration = 20;
	page.setData({ paused: true });
	page.onHide();
	page.urls.set(10, {
		url: "expired",
		expiresAt: new Date(Date.now() - 1000).toISOString(),
	});
	api.play = async () => ({
		url: "refreshed",
		expiresAt: new Date(Date.now() + 300000).toISOString(),
	});
	const before = calls.length;
	page.onShow();
	await tick();
	assert.equal(page.data.src, "refreshed");
	assert.equal(page.data.initialTime, 7);
	assert.equal(page.data.paused, true);
	assert.equal(
		calls.slice(before).some((c) => c.method === "play"),
		false,
	);
});
test("ending chooses the next published episode and preserves completion on leaving last episode", async () => {
	const { page, progress } = await harness();
	page.ended({ currentTarget: { dataset: { id: 10 } } });
	await tick();
	assert.equal(page.data.current.episodeNo, 3);
	await page.select(50);
	page.ended({ currentTarget: { dataset: { id: 50 } } });
	page.onHide();
	assert.equal(progress.at(-1).completed, true);
});
test("short drag rejects native swiper change and delayed programmatic events cannot change episode", async () => {
	const { page } = await harness();
	page.touchStart({ touches: [{ clientX: 100, clientY: 100 }] });
	page.swipe({ detail: { current: 1, source: "touch" } });
	page.touchEnd({ changedTouches: [{ clientX: 100, clientY: 80 }] });
	await tick();
	assert.equal(page.state.current.episodeNo, 1);
	await page.select(50);
	page.swipe({ detail: { current: 1, source: "" } });
	await tick();
	assert.equal(page.state.current.episodeNo, 5);
});
test("vertical gesture commits next published episode even when native threshold differs", async () => {
	const { page } = await harness();
	page.touchStart({ touches: [{ clientX: 100, clientY: 100 }] });
	page.touchEnd({ changedTouches: [{ clientX: 101, clientY: 10 }] });
	await tick();
	assert.equal(page.state.current.episodeNo, 3);
});
