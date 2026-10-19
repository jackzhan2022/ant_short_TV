import { useEffect, useRef } from 'react';
import type { StoryboardNavigationItem } from './service';

export default function StoryboardPositionRail({ episodeNo, items, activeId, onNavigate }: {
  episodeNo: number;
  items: StoryboardNavigationItem[];
  activeId?: number;
  onNavigate: (id: number, position: number) => void;
}) {
  const rail = useRef<HTMLElement>(null);
  useEffect(() => {
    const root = rail.current;
    const marker = root?.querySelector('[aria-current="location"]');
    if (!root || !marker) return;
    const bounds = root.getBoundingClientRect();
    const target = marker.getBoundingClientRect();
    if (target.top < bounds.top) root.scrollTop += target.top - bounds.top;
    else if (target.bottom > bounds.bottom) root.scrollTop += target.bottom - bounds.bottom;
  }, [activeId]);
  return (
    <nav ref={rail} className="storyboard-position-rail" aria-label={`第${episodeNo}集分镜定位`}>
      {items.map((shot, position) => (
        <button
          key={shot.id}
          type="button"
          className={`storyboard-position-marker${shot.hasVideo ? ' has-video' : ''}${activeId === shot.id ? ' is-active' : ''}`}
          aria-label={`跳转到分镜${shot.storyboardNo || shot.shotNo}`}
          aria-current={activeId === shot.id ? 'location' : undefined}
          title={`分镜${shot.storyboardNo || shot.shotNo} · ${shot.hasVideo ? '已有视频' : '未生成视频'}`}
          onClick={() => onNavigate(shot.id, position)}
        ><span /></button>
      ))}
    </nav>
  );
}
