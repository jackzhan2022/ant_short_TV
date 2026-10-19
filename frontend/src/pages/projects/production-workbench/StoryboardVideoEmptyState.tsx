export default function StoryboardVideoEmptyState() {
  return (
    <div className="storyboard-video-empty">
      <svg width="60" height="54" viewBox="0 0 64 56" fill="none" aria-hidden="true">
        <path d="M33 44H8a3 3 0 0 1-3-3V9a3 3 0 0 1 3-3h43a3 3 0 0 1 3 3v20" stroke="currentColor" strokeWidth="5" strokeLinecap="round" strokeLinejoin="round" />
        <path d="m24 17 13 8-13 8V17Z" fill="currentColor" />
        <path d="m49 31 3.5 8.5L61 43l-8.5 3.5L49 55l-3.5-8.5L37 43l8.5-3.5L49 31Z" fill="currentColor" />
      </svg>
      <span>请先完成分镜视频生成</span>
    </div>
  );
}
