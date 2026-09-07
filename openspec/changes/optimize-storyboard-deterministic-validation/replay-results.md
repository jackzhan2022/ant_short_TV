## Project 26 Sanitized Offline Replay

- Fixture: `backend/src/test/resources/storyboard-replay/project-26-first-round.json`
- Episodes replayed: 57
- Provider contacts: 0 (the replay invokes only the deterministic normalizer)
- Successful without warning: 39
- Successful with action-density warning: 18
- Hard failures: 0
- Missing-sound cases normalized: 15 of 15
- Out-of-range model sound cases normalized: 1 of 1
- Final coverage-gap cases normalized: 1 of 1
- Expected business model calls for a live 57-episode generation: 57 instead of the previous 92
- Expected implicit correction calls removed: 35

The replay is sanitized and retains only episode numbers and defect categories. It does not contain production screenplay text, model credentials, provider request data, or user information.
