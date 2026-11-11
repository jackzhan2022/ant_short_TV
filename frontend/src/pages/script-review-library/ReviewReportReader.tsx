import XMarkdown from '@ant-design/x-markdown';
import { useEffect, useRef, useState } from 'react';
import styles from './index.module.css';

export default function ReviewReportReader({ markdown }: { markdown: string }) {
  const reader = useRef<HTMLElement>(null);
  const [sections, setSections] = useState<HTMLElement[]>([]);
  const [active, setActive] = useState(0);
  useEffect(() => {
    const element = reader.current;
    if (!element) return;
    const update = () => {
      const headings = [...element.querySelectorAll<HTMLElement>('h2')];
      for (const [index, heading] of headings.entries())
        heading.id = `review-section-${index}`;
      setSections(headings);
    };
    update();
    element.scrollTop = 0;
    setActive(0);
    const observer = new MutationObserver(update);
    observer.observe(element, { childList: true, subtree: true });
    return () => observer.disconnect();
  }, [markdown]);
  return (
    <div className={styles.reportLayout}>
      <nav className={styles.reportDirectory} aria-label="报告目录">
        <h2>报告目录</h2>
        {sections.length ? (
          sections.map((section, index) => (
            <button
              type="button"
              key={section.id}
              aria-current={active === index ? 'location' : undefined}
              onClick={() => {
                const element = reader.current;
                if (!element) return;
                setActive(index);
                element.scrollTo({
                  top:
                    section.getBoundingClientRect().top -
                    element.getBoundingClientRect().top +
                    element.scrollTop -
                    28,
                  behavior: window.matchMedia(
                    '(prefers-reduced-motion: reduce)',
                  ).matches
                    ? 'instant'
                    : 'smooth',
                });
              }}
            >
              <span className={styles.directoryNumber}>
                {String(index + 1).padStart(2, '0')}
              </span>
              {section.textContent}
            </button>
          ))
        ) : (
          <p>全文报告</p>
        )}
      </nav>
      <section
        className={styles.reportPaper}
        ref={reader}
        aria-label="审核报告正文"
        // biome-ignore lint/a11y/noNoninteractiveTabindex: Scrollable report must support keyboard reading.
        tabIndex={0}
        onScroll={() => {
          const top = reader.current?.getBoundingClientRect().top ?? 0;
          const current = sections.findLastIndex(
            (section) => section.getBoundingClientRect().top <= top + 80,
          );
          setActive(Math.max(0, current));
        }}
      >
        <XMarkdown content={markdown} />
      </section>
    </div>
  );
}
