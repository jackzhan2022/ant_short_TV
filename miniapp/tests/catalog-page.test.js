const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const path = require("node:path");
test("editing the search input does not mix a new keyword into existing result pagination", async () => {
	const queries = [];
	const api = {
		catalog: async (params) => {
			queries.push(params);
			return {
				items: [{ id: params.page }],
				total: 21,
				categories: [],
				version: "v1",
			};
		},
	};
	const sandbox = { module: { exports: {} }, require: () => api };
	vm.runInNewContext(
		fs.readFileSync(path.join(__dirname, "../utils/catalog-page.js"), "utf8"),
		sandbox,
	);
	const config = sandbox.module.exports(true);
	const page = {
		...config,
		data: { ...config.data },
		setData(data) {
			Object.assign(this.data, data);
		},
	};
	page.onLoad();
	page.setData({ keyword: "春山" });
	await page.load(true);
	page.input({ detail: { value: "夏夜" } });
	await page.load(false);
	assert.equal(queries[1].keyword, "春山");
	await page.load(true);
	assert.equal(queries[2].keyword, "夏夜");
});
