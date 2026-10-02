# Verification

## Automated

- `npm test`: 83 files, 454 tests passed.
- `npm run lint`: passed; three pre-existing `document.cookie` warnings remain in
  `src/requestErrorConfig.test.ts`.
- `npm run build`: passed.
- `npx antd lint ./src`: exited successfully; existing static feedback API and
  deprecated tab warnings are outside this change.
- `npm run openapi`: regenerated services from the running changed backend's
  `/v3/api-docs` document.
- `openspec validate optimize-media-loading-and-project-covers --strict
  --no-interactive`: passed.
- Focused backend paging, authorization, cover, playback, storyboard-media,
  visual-candidate, production-task, generation, selection, and download tests
  passed.

## Browser Network And Layout

`MEDIA_BASE_URL=http://127.0.0.1:8000 node scripts/verify-media-loading.mjs`
passed at 1440x900 and 390x844. Sanitized evidence and 16 screenshots are in
`.temp/media-loading-qa/`.

- 200 inspiration records loaded with at most 36 desktop and 9 mobile cards
  mounted; returning to the first item preserved the scroll anchor and changed
  scroll height by zero.
- Initial business-video nodes and byte requests were zero. One explicit click
  loaded only the selected fixture, yielded `readyState=4`, kept controls and
  `preload=none`, and produced 394 distinct canvas colors.
- Opening another player cleared the prior source; closing before a delayed
  authorization response prevented a late player.
- Offscreen style images without a source numbered 12 desktop and 21 mobile.
- Cover polling stopped at 12 calls; failed covers did not request display or
  original bytes; a later READY page loaded the stable cover endpoint.
- Project/style page 2, independent later categories, later-page style
  selection, and the 20-ID storyboard media-summary boundary passed.
- Desktop/mobile screenshots contain no overlapping media controls. The mobile
  production header uses an auto-height grid and no longer clips project text.

## Backend Full Suite

- `mvn -q test`: passed with 293 Surefire reports and 1,419 tests, with zero
  failures and zero errors.
