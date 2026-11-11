import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const stylesheet = readFileSync(
  resolve(process.cwd(), 'src/pages/projects/production-workbench/storyboard.css'),
  'utf8',
);

describe('storyboard layout', () => {
  it('keeps two character columns and a larger prompt column on desktop', () => {
    expect(stylesheet).toMatch(/\.storyboard-reference-grid\s*{[^}]*grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\)/s);
    expect(stylesheet).toMatch(/\.storyboard-card-content\s*{[^}]*grid-template-columns:\s*minmax\(260px,\s*1fr\)\s*minmax\(350px,\s*1\.9fr\)\s*minmax\(\s*240px,\s*1fr\s*\)/s);
  });

  it('adapts card height to the viewport with responsive document flow', () => {
    expect(stylesheet).toMatch(/\.storyboard-card-content\s*{[^}]*height:\s*clamp\(560px,\s*calc\(100dvh - 350px\),\s*730px\)/s);
    expect(stylesheet).toMatch(/@media\s*\(max-width:\s*1100px\)[\s\S]*?\.storyboard-card-content\s*{[^}]*height:\s*auto/s);
  });

  it('keeps storyboard cards in normal document flow without scroll snapping', () => {
    expect(stylesheet).not.toMatch(/scroll-snap-type/);
    expect(stylesheet).not.toMatch(/scroll-snap-align/);
    expect(stylesheet).not.toMatch(/position:\s*sticky/);
    expect(stylesheet).not.toMatch(/storyboard-card\.is-scroll-released/);
    expect(stylesheet).toMatch(
      /\.storyboard-card-content\s*>\s*section:first-child\s*{[^}]*overflow-y:\s*auto[^}]*overscroll-behavior-y:\s*auto/s,
    );
    expect(stylesheet).toMatch(
      /\.storyboard-prompt-editor\s*{[^}]*overflow-y:\s*auto[^}]*overscroll-behavior-y:\s*auto/s,
    );
  });
});
