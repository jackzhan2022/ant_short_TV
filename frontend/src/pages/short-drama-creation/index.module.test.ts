import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const stylesheet = readFileSync(
  resolve(process.cwd(), 'src/pages/short-drama-creation/index.module.css'),
  'utf8',
);

describe('short drama creation theme', () => {
  it('uses the shared application colors for structural UI', () => {
    expect(stylesheet).toContain('background: var(--app-color-bg-layout);');
    expect(stylesheet).toContain('color: var(--app-color-primary);');
    expect(stylesheet).toContain('var(--app-color-border);');
    expect(stylesheet).not.toMatch(/#7b22ff|#7e0cff|#7252f2|#3a5cff/);
  });
});
