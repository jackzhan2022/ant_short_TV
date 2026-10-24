import XMarkdown from '@ant-design/x-markdown';
import { memo, useMemo } from 'react';
import styles from './index.module.css';

type ScriptBlock = {
  kind: 'markdown' | 'cast' | 'dialogue' | 'end';
  text: string;
  offset: number;
};

// Only style speakers explicitly named by the current scene's cast line.
// Unknown formats stay Markdown; the stored/copyable screenplay is never rewritten.
export function screenplayBlocks(content: string): ScriptBlock[] {
  if (!/^##\s/m.test(content) || /^\s*(```|~~~)/m.test(content)) {
    return [{ kind: 'markdown', text: content, offset: 0 }];
  }
  let cast: string[] = [];
  return Array.from(
    content.matchAll(/\S[\s\S]*?(?=\r?\n[ \t]*\r?\n|$)/g),
    (match) => {
      const text = match[0];
      const firstLine = text.split(/\r?\n/)[0].trim();
      if (/^##\s/.test(firstLine)) cast = [];
      let kind: ScriptBlock['kind'] = 'markdown';
      if (/^出场人物[：:]/.test(firstLine)) {
        cast = firstLine
          .replace(/^出场人物[：:]\s*/, '')
          .split(/[、,，]/)
          .map((name) => name.trim())
          .filter(Boolean);
        kind = 'cast';
      } else if (
        cast.some(
          (name) =>
            firstLine === name ||
            new RegExp(
              `^${name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*（(?:OS|VO)）$`,
              'i',
            ).test(firstLine),
        )
      ) {
        kind = 'dialogue';
      } else if (/^[—－-]{2,}\s*本集完\s*$/.test(text)) kind = 'end';
      return { kind, text, offset: match.index ?? 0 };
    },
  );
}

export function screenplayTitle(content?: string | null) {
  return content?.match(/^#\s+(.+)$/m)?.[1]?.trim();
}

const ScreenplayReader = memo(function ScreenplayReader({
  content,
  episodeNo,
  fileName,
}: {
  content: string;
  episodeNo: number;
  fileName: string;
}) {
  const blocks = useMemo(() => screenplayBlocks(content), [content]);
  return (
    <article className={styles.paper} aria-label={`第 ${episodeNo} 集剧本正文`}>
      <div className={styles.paperMeta}>
        <span>EPISODE {String(episodeNo).padStart(2, '0')}</span>
        <span>生成剧本 · 只读</span>
      </div>
      <div className={styles.source}>来源视频 / {fileName}</div>
      <div className={styles.screenplay}>
        {blocks.map((block) =>
          block.kind === 'dialogue' ? (
            <div key={block.offset} className={styles.dialogue}>
              <div className={styles.speaker}>
                {block.text.split(/\r?\n/)[0]}
              </div>
              <div className={styles.spoken}>
                {block.text.split(/\r?\n/).slice(1).join('\n')}
              </div>
            </div>
          ) : block.kind === 'markdown' ? (
            <XMarkdown
              key={block.offset}
              className={styles.markdown}
              escapeRawHtml
            >
              {block.text}
            </XMarkdown>
          ) : (
            <div
              key={block.offset}
              className={block.kind === 'cast' ? styles.cast : styles.endMark}
            >
              {block.text}
            </div>
          ),
        )}
      </div>
    </article>
  );
});

export default ScreenplayReader;
