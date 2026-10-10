import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { expect, it } from 'vitest';

it('sizes the project operation panel to its action labels instead of a fixed width or project code', () => {
  const css = readFileSync(
    resolve(process.cwd(), 'src/pages/projects/list/index.module.css'),
    'utf8',
  );
  const panel = css.match(/\.menuPanel\s*\{([^}]+)\}/s)?.[1] || '';
  expect(panel).toMatch(/width:\s*max-content/);
  expect(panel).not.toMatch(/min-width:\s*120px/);
  expect(css).not.toContain('.projectCode');
});
