import { useEffect, useRef, useState } from 'react';
import styles from './index.module.css';
import LazyInspirationThumbnail from './LazyInspirationThumbnail';
import type { InspirationCreation } from './service';

type Props = {
  items: InspirationCreation[];
  onSelect: (item: InspirationCreation) => void;
};

const InspirationGallery = ({ items, onSelect }: Props) => {
  const gridRef = useRef<HTMLDivElement>(null);
  const [window, setWindow] = useState({
    start: 0,
    end: 24,
    top: 0,
    bottom: 0,
  });

  useEffect(() => {
    const grid = gridRef.current;
    if (!grid) return;
    let frame: number | undefined;
    const measure = () => {
      const bounds = grid.getBoundingClientRect();
      const layout = getComputedStyle(grid);
      const columns =
        layout.gridTemplateColumns.split(' ').filter(Boolean).length ||
        (globalThis.innerWidth > 1100
          ? 4
          : globalThis.innerWidth > 720
            ? 2
            : 1);
      const gap = Number.parseFloat(layout.columnGap) || 16;
      const width = bounds.width || Math.min(globalThis.innerWidth - 36, 1440);
      const rowHeight =
        (((width - (columns - 1) * gap) / columns) * 9) / 16 + gap;
      const totalRows = Math.ceil(items.length / columns);
      const firstRow = Math.max(
        0,
        Math.min(totalRows, Math.floor(-bounds.top / rowHeight) - 2),
      );
      const lastRow = Math.min(
        totalRows,
        firstRow + Math.ceil(globalThis.innerHeight / rowHeight) + 4,
      );
      // Grid gaps already separate the spacers from visible rows.
      setWindow({
        start: firstRow * columns,
        end: lastRow * columns,
        top: Math.max(0, firstRow * rowHeight - gap),
        bottom: Math.max(0, (totalRows - lastRow) * rowHeight - gap),
      });
    };
    const schedule = () => {
      if (frame !== undefined) cancelAnimationFrame(frame);
      frame = requestAnimationFrame(measure);
    };
    measure();
    const observer = new ResizeObserver(schedule);
    observer.observe(grid);
    globalThis.addEventListener('scroll', schedule, { passive: true });
    globalThis.addEventListener('resize', schedule);
    return () => {
      observer.disconnect();
      if (frame !== undefined) cancelAnimationFrame(frame);
      globalThis.removeEventListener('scroll', schedule);
      globalThis.removeEventListener('resize', schedule);
    };
  }, [items.length]);

  return (
    <div ref={gridRef} className={styles.inspirationGrid}>
      {window.top > 0 ? (
        <div aria-hidden style={{ gridColumn: '1 / -1', height: window.top }} />
      ) : null}
      {items.slice(window.start, window.end).map((item) => {
        const title = item.title?.trim() || `灵感 ${item.id}`;
        return (
          <button
            className={styles.inspirationCard}
            aria-label={title}
            key={item.id}
            onClick={() => onSelect(item)}
            type="button"
          >
            <LazyInspirationThumbnail
              alt={title}
              placeholderClassName={styles.inspirationPlaceholder}
              src={item.thumbnailUrl}
            />
            <span className={styles.inspirationOverlay}>
              <strong>{title}</strong>
              {item.promptSummary && <small>{item.promptSummary}</small>}
            </span>
          </button>
        );
      })}
      {window.bottom > 0 ? (
        <div
          aria-hidden
          style={{ gridColumn: '1 / -1', height: window.bottom }}
        />
      ) : null}
    </div>
  );
};

export default InspirationGallery;
