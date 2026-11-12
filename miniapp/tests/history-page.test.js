const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const path = require("node:path");
test("late history load cannot restore records after clear succeeds", async () => {
	let config, resolveHistory;
	const api = {
		flushProgress: async () => {},
		history: () =>
			new Promise((resolve) => {
				resolveHistory = resolve;
			}),
		clearHistory: async () => {},
	};
	const wx = {
		showModal: ({ success }) => success({ confirm: true }),
		showToast() {},
	};
	vm.runInNewContext(
		fs.readFileSync(path.join(__dirname, "../pages/history/index.js"), "utf8"),
		{
			require: () => api,
			Page: (value) => {
				config = value;
			},
			wx,
		},
	);
	const page = {
		...config,
		data: { ...config.data, items: [{ dramaId: 1 }] },
		setData(data) {
			Object.assign(this.data, data);
		},
	};
	page.onShow();
	await new Promise((resolve) => setImmediate(resolve));
	page.clear();
	await new Promise((resolve) => setImmediate(resolve));
	resolveHistory([{ dramaId: 1 }]);
	await new Promise((resolve) => setImmediate(resolve));
	assert.equal(page.data.items.length, 0);
});
