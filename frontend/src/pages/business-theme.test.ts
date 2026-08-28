import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const businessStylesheets = [
  'src/pages/commercial/index.less',
  'src/pages/projects/list/index.module.css',
  'src/pages/short-drama-creation/index.module.css',
];

describe('business page theme', () => {
  it('does not use the retired blue-purple page palette', () => {
    const content = businessStylesheets
      .map((file) => readFileSync(resolve(process.cwd(), file), 'utf8'))
      .join('\n');

    expect(content).not.toMatch(
      /#3265ff|#3a5cff|#7252f2|#7568d9|#7b22ff|#7e0cff|#8109ff|#8a20ff/,
    );
  });
});
