import { existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const pageRoot = dirname(fileURLToPath(import.meta.url));

describe('AI service management model discovery', () => {
  it("keeps route components out of Umi's reserved models directory", () => {
    expect(existsSync(resolve(pageRoot, 'models/index.tsx'))).toBe(false);
  });
});
