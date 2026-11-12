const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const { createRequire } = require("node:module");
const { spawnSync } = require("node:child_process");
const root = path.resolve(__dirname, "..");
const walk = (dir) =>
	fs
		.readdirSync(dir, { withFileTypes: true })
		.flatMap((entry) =>
			entry.isDirectory()
				? walk(path.join(dir, entry.name))
				: [path.join(dir, entry.name)],
		);
let scripts = 0,
	documents = 0;
for (const file of walk(root)) {
	if (file.endsWith(".json")) JSON.parse(fs.readFileSync(file, "utf8"));
	if (file.endsWith(".js")) {
		const result = spawnSync(process.execPath, ["--check", file], {
			encoding: "utf8",
		});
		if (result.status !== 0) throw new Error(result.stderr);
		scripts++;
	}
}
function validateWxml(file, page) {
	const source = fs.readFileSync(file, "utf8");
	const stack = [];
	// Tokenize tags while respecting quotes containing WXML expressions.
	for (let offset = 0; offset < source.length; offset++) {
		if (source[offset] !== "<" || !/[\w/]/.test(source[offset + 1])) continue;
		const start = offset;
		let quote = "";
		while (++offset < source.length) {
			const char = source[offset];
			if (quote) {
				if (char === quote) quote = "";
			} else if (char === '"' || char === "'") quote = char;
			else if (char === ">") break;
		}
		const tag = source.slice(start, offset + 1),
			name = tag.match(/^<\/?([\w-]+)/)[1];
		if (tag.startsWith("</")) {
			if (stack.pop() !== name)
				throw new Error(`Unbalanced WXML tag ${name}: ${file}`);
		} else if (!tag.endsWith("/>")) stack.push(name);
	}
	if (stack.length) throw new Error(`Unclosed WXML tags ${stack}: ${file}`);
	for (const [, expression] of source.matchAll(/{{([\s\S]*?)}}/g))
		new vm.Script(`(${expression})`);
	for (const [, handler] of source.matchAll(
		/\b(?:bind|catch):?[\w-]+="([\w]+)"/g,
	)) {
		if (typeof page[handler] !== "function")
			throw new Error(`Missing event handler ${handler}: ${file}`);
	}
	documents++;
}
const app = JSON.parse(fs.readFileSync(path.join(root, "app.json"), "utf8"));
for (const route of app.pages) {
	const file = path.join(root, `${route}.js`);
	let page;
	const sandbox = {
		require: createRequire(file),
		Page: (definition) => {
			page = definition;
		},
	};
	vm.runInNewContext(fs.readFileSync(file, "utf8"), sandbox, {
		filename: file,
	});
	JSON.parse(fs.readFileSync(path.join(root, `${route}.json`), "utf8"));
	validateWxml(path.join(root, `${route}.wxml`), page);
	if (route.includes("home") || route.includes("search"))
		validateWxml(path.join(root, "templates/catalog.wxml"), page);
}
console.log(
	`Validated ${app.pages.length} pages, ${scripts} JavaScript files, ${documents} WXML documents and JSON configuration. Native compilation/device checks require WeChat DevTools.`,
);
