let token = null;
let loginPending = null;
function raw(path, method, data, accessToken) {
	const { apiBase } = getApp().globalData;
	if (!/^https:\/\//.test(apiBase))
		return Promise.reject(new Error("请先配置小程序 HTTPS 接口域名"));
	return new Promise((resolve, reject) =>
		wx.request({
			url: `${apiBase}/api/miniapp${path}`,
			method,
			data,
			timeout: 20000,
			header: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
			success(res) {
				if (res.statusCode === 401)
					return reject(
						Object.assign(new Error("登录已过期"), { status: 401 }),
					);
				if (res.statusCode < 200 || res.statusCode >= 300 || !res.data?.success)
					return reject(
						Object.assign(
							new Error(res.data?.errorMessage || "加载失败，请重试"),
							{ status: res.statusCode, code: res.data?.errorCode },
						),
					);
				resolve(res.data.data);
			},
			fail() {
				reject(new Error("网络连接失败，请检查网络后重试"));
			},
		}),
	);
}
function login() {
	if (loginPending) return loginPending;
	loginPending = new Promise((resolve, reject) =>
		wx.login({
			success({ code }) {
				if (!code) return reject(new Error("微信登录失败，请重试"));
				raw("/auth/login", "POST", {
					appid: getApp().globalData.appid,
					code,
				}).then((result) => {
					token = result.accessToken;
					resolve(token);
				}, reject);
			},
			fail() {
				reject(new Error("微信登录失败，请重试"));
			},
		}),
	).finally(() => {
		loginPending = null;
	});
	return loginPending;
}
async function request(path, method = "GET", data) {
	const accessToken = token || (await login());
	try {
		return await raw(path, method, data, accessToken);
	} catch (error) {
		if (error.status !== 401) throw error;
		if (token === accessToken) token = null;
		return raw(path, method, data, token || (await login()));
	}
}
const query = (values) =>
	Object.entries(values)
		.filter(([, v]) => v !== undefined && v !== "")
		.map(([k, v]) => `${k}=${encodeURIComponent(v)}`)
		.join("&");
let flushing = null;
let clearing = null;
const writes = new Set();
function pending() {
	return wx.getStorageSync("pendingProgress") || {};
}
function sendProgress(id, data) {
	const task = request(`/history/${id}`, "PUT", data)
		.then(() => {
			const queue = pending();
			if (
				queue[id]?.playbackSession === data.playbackSession &&
				queue[id]?.sequence === data.sequence
			) {
				delete queue[id];
				wx.setStorageSync("pendingProgress", queue);
			}
		})
		.finally(() => writes.delete(task));
	writes.add(task);
	return task;
}
function flushProgress() {
	if (clearing) return clearing;
	if (flushing) return flushing;
	flushing = (async () => {
		// Drain the previous player before a new playbackSession can send progress.
		await Promise.allSettled([...writes]);
		for (const [id, data] of Object.entries(pending())) {
			try {
				await sendProgress(id, data);
			} catch (error) {
				if (error.status === 404) {
					const queue = pending();
					delete queue[id];
					wx.setStorageSync("pendingProgress", queue);
				} else throw error;
			}
		}
	})().finally(() => {
		flushing = null;
	});
	return flushing;
}
function clearHistory() {
	if (clearing) return clearing;
	clearing = (async () => {
		if (flushing) await flushing.catch(() => {});
		await Promise.allSettled([...writes]);
		await request("/history", "DELETE");
		wx.removeStorageSync("pendingProgress");
		wx.getStorageInfoSync()
			.keys.filter((key) => key.startsWith("watched:"))
			.forEach((key) => {
				wx.removeStorageSync(key);
			});
	})().finally(() => {
		clearing = null;
	});
	return clearing;
}
module.exports = {
	catalog: (params) => request(`/catalog?${query(params)}`),
	detail: (id) => request(`/dramas/${id}`),
	history: () => request("/history"),
	clearHistory,
	play: (id, episodeId) => request(`/dramas/${id}/episodes/${episodeId}/play`),
	progress: (id, data) => {
		if (clearing) return clearing;
		const queue = pending();
		queue[id] = data;
		const entries = Object.entries(queue);
		wx.setStorageSync(
			"pendingProgress",
			Object.fromEntries(entries.slice(-100)),
		);
		return sendProgress(id, data);
	},
	flushProgress,
	event: (data) => request("/playback-events", "POST", data),
};
