function shouldRefresh(media, now = Date.now()) {
	return (
		!media ||
		!Number.isFinite(Date.parse(media.expiresAt)) ||
		Date.parse(media.expiresAt) - now <= 30000
	);
}
function dragDirection(dx, dy) {
	return Math.abs(dy) >= 70 && Math.abs(dy) > Math.abs(dx) * 1.2
		? dy < 0
			? 1
			: -1
		: 0;
}
function groups(episodes, size = 30) {
	const result = [];
	for (let start = 0; start < episodes.length; start += size) {
		const end = Math.min(start + size, episodes.length) - 1;
		result.push({
			start,
			end,
			label: `${episodes[start].episodeNo}–${episodes[end].episodeNo}`,
		});
	}
	return result;
}
class PlayerState {
	constructor(episodes, episodeId) {
		this.episodes = episodes;
		this.index = Math.max(
			0,
			episodes.findIndex((e) => String(e.episodeId) === String(episodeId)),
		);
		this.generation = 0;
	}
	get current() {
		return this.episodes[this.index];
	}
	select(episodeId) {
		const index = this.episodes.findIndex(
			(e) => String(e.episodeId) === String(episodeId),
		);
		if (index < 0) return null;
		this.index = index;
		this.generation++;
		return this.current;
	}
	move(delta) {
		const next = this.episodes[this.index + delta];
		return next ? this.select(next.episodeId) : null;
	}
	accepts(generation, episodeId) {
		return (
			generation === this.generation &&
			this.current &&
			this.current.episodeId === episodeId
		);
	}
}
module.exports = { PlayerState, shouldRefresh, dragDirection, groups };
