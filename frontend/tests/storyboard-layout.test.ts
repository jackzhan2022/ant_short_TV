import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const stylesheet = readFileSync(
  resolve(process.cwd(), 'src/pages/projects/production-workbench/storyboard.css'),
  'utf8',
);

describe('storyboard layout', () => {
  it('uses three asset columns with two-column and one-column container fallbacks', () => {
    expect(stylesheet).toMatch(
      /\.storyboard-reference-grid\s*{[^}]*grid-template-columns:\s*repeat\(3,\s*minmax\(0,\s*1fr\)\)/s,
    );
    expect(stylesheet).toMatch(
      /@container\s*\(max-width:\s*300px\)[\s\S]*?grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\)/,
    );
    expect(stylesheet).toMatch(
      /@container\s*\(max-width:\s*220px\)[\s\S]*?grid-template-columns:\s*minmax\(0,\s*1fr\)/,
    );
  });

  it('increases the desktop storyboard height by one quarter while keeping a 9:16 preview', () => {
    expect(stylesheet).toMatch(
      /\.storyboard-card-content\s*{[^}]*height:\s*730px/s,
    );
    expect(stylesheet).toMatch(
      /\.storyboard-preview-stage\s*{[^}]*height:\s*636px/s,
    );
    expect(stylesheet).toMatch(
      /\.storyboard-preview-media\s*{[^}]*width:\s*358px[^}]*height:\s*100%/s,
    );
    expect(stylesheet).toMatch(
      /\.storyboard-card-content\s*>\s*section:first-child\s*{[^}]*overflow-y:\s*auto/s,
    );
    expect(stylesheet).toMatch(
      /@media\s*\(max-width:\s*1100px\)[\s\S]*?\.storyboard-card-content\s*{[^}]*height:\s*auto/s,
    );
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
