import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const packageJson = JSON.parse(
  readFileSync(new URL('../package.json', import.meta.url), 'utf8'),
);
const configSource = readFileSync(new URL('./config.ts', import.meta.url), 'utf8');

describe('frontend runtime API mode', () => {
  it('forces development commands to use the real backend', () => {
    expect(packageJson.scripts.dev).toContain('MOCK=none');
    expect(packageJson.scripts.start).toContain('MOCK=none');
    expect(packageJson.scripts['start:no-mock']).toContain('MOCK=none');
    expect(packageJson.scripts['start:pre']).toContain('MOCK=none');
    expect(packageJson.scripts['start:test']).toContain('MOCK=none');
  });

  it('does not register a Umi runtime mock block', () => {
    expect(configSource).not.toMatch(/^\s*mock:\s*\{/m);
  });
});
