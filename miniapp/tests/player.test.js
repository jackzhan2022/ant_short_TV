const test = require("node:test");
const assert = require("node:assert/strict");
const {
	PlayerState,
	shouldRefresh,
	dragDirection,
	groups,
} = require("../utils/player-state");

test("next follows actual published directory including gaps and stops at boundaries", () => {
	const state = new PlayerState(
		[
			{ episodeId: 10, episodeNo: 1 },
			{ episodeId: 30, episodeNo: 3 },
			{ episodeId: 50, episodeNo: 5 },
		],
		30,
	);
	assert.equal(state.current.episodeNo, 3);
	assert.equal(state.move(1).episodeId, 50);
	assert.equal(state.move(1), null);
	assert.equal(state.move(-1).episodeId, 30);
	assert.equal(state.select(999), null);
});
test("newer selections invalidate stale media requests even when returning to same episode", () => {
	const state = new PlayerState([{ episodeId: 10 }, { episodeId: 30 }], 10);
	const generation = state.generation;
	state.move(1);
	state.move(-1);
	assert.equal(state.accepts(generation, 10), false);
	assert.equal(state.accepts(state.generation, 10), true);
});
test("short and horizontal drags rebound; vertical threshold selects direction", () => {
	assert.equal(dragDirection(20, -25), 0);
	assert.equal(dragDirection(150, -80), 0);
	assert.equal(dragDirection(2, -100), 1);
	assert.equal(dragDirection(2, 100), -1);
});
test("signed URLs refresh before expiration including after a long pause", () => {
	assert.equal(
		shouldRefresh({ expiresAt: new Date(310000).toISOString() }, 300000),
		true,
	);
	assert.equal(
		shouldRefresh({ expiresAt: new Date(500000).toISOString() }, 300000),
		false,
	);
	assert.equal(shouldRefresh(null, 300000), true);
});
test("episode groups use actual directory index instead of arithmetic episode numbers", () => {
	const list = Array.from({ length: 61 }, (_, i) => ({
		episodeId: i + 1,
		episodeNo: i * 2 + 1,
	}));
	const result = groups(list, 30);
	assert.equal(result[1].start, 30);
	assert.equal(result[1].label, "61–119");
});
