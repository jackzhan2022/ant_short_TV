const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const path = require("node:path");

function harness(respond) {
	let logins = 0;
	const store = new Map();
	const requests = [];
	const wx = {
		login({ success }) {
			logins++;
			queueMicrotask(() => success({ code: `code-${logins}` }));
		},
		request(options) {
			requests.push(options);
			queueMicrotask(() => {
				if (options.url.endsWith("/auth/login"))
					return options.success({
						statusCode: 200,
						data: { success: true, data: { accessToken: `token-${logins}` } },
					});
				const response = respond(options);
				if (response === "pending") return;
				if (response === "offline") options.fail();
				else
					options.success(
						response || { statusCode: 200, data: { success: true, data: [] } },
					);
			});
		},
		getStorageSync: (key) => structuredClone(store.get(key)),
		setStorageSync: (key, value) => store.set(key, structuredClone(value)),
		removeStorageSync: (key) => store.delete(key),
		getStorageInfoSync: () => ({ keys: [...store.keys()] }),
	};
	const sandbox = {
		module: { exports: {} },
		wx,
		getApp: () => ({
			globalData: {
				apiBase: "https://api.example.com",
				appid: "wx1234567890abcdef",
			},
		}),
	};
	vm.runInNewContext(
		fs.readFileSync(path.join(__dirname, "../utils/api.js"), "utf8"),
		sandbox,
	);
	return { api: sandbox.module.exports, store, requests, logins: () => logins };
}
test("simultaneous requests share login and expired-session refresh", async () => {
	const h = harness((options) =>
		options.header.Authorization === "Bearer token-1"
			? { statusCode: 401 }
			: null,
	);
	await Promise.all([h.api.history(), h.api.detail(1)]);
	assert.equal(h.logins(), 2);
	assert.equal(
		h.requests.filter((r) => r.url.endsWith("/auth/login")).length,
		2,
	);
});
test("offline progress survives and is flushed with the same sequence on reconnect", async () => {
	let offline = true;
	const h = harness(() => (offline ? "offline" : null));
	const data = {
		episodeId: 3,
		positionSeconds: 6,
		durationSeconds: 10,
		completed: false,
		sequence: 9,
		playbackSession: "device-session",
	};
	await assert.rejects(h.api.progress(1, data));
	assert.equal(h.store.get("pendingProgress")["1"].sequence, 9);
	offline = false;
	await h.api.flushProgress();
	assert.deepEqual(h.store.get("pendingProgress"), {});
});
test("successful older write cannot erase a newer queued progress snapshot", async () => {
	const h = harness(() => null);
	const first = h.api.progress(1, { sequence: 1, playbackSession: "session" });
	const second = h.api.progress(1, { sequence: 2, playbackSession: "session" });
	await Promise.all([first, second]);
	assert.deepEqual(h.store.get("pendingProgress"), {});
});
test("clear waits for in-flight progress and blocks old queued writes", async () => {
	let delayed;
	const h = harness((options) => {
		if (options.method === "PUT") {
			delayed = options;
			return "pending";
		}
		return null;
	});
	const write = h.api.progress(1, { sequence: 1, playbackSession: "session" });
	await new Promise((resolve) => setImmediate(resolve));
	const clear = h.api.clearHistory();
	const suppressed = h.api.progress(1, {
		sequence: 2,
		playbackSession: "session",
	});
	await new Promise((resolve) => setImmediate(resolve));
	assert.equal(
		h.requests.some((r) => r.method === "DELETE"),
		false,
	);
	delayed.success({ statusCode: 200, data: { success: true } });
	await Promise.all([write, clear, suppressed]);
	assert.equal(h.requests.filter((r) => r.method === "PUT").length, 1);
	assert.equal(h.requests.at(-1).method, "DELETE");
	assert.equal(h.store.has("pendingProgress"), false);
});
