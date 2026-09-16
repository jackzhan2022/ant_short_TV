# Primary Asset Prompt Source Release

Deploy the backend application and `backend/skills/short-drama-asset-recognition-framework/SKILL.md` in the same release. Mixed workers are unsupported because older Skill content can continue producing episode-specific character prose that the updated save boundary rejects.

No database migration or historical prompt rewrite is required. Existing prompt values in primary `asset_visual_variant` rows remain stored for rollback compatibility, but the application ignores them for display, editing, and image generation. Primary visuals use the owning canonical asset prompt; non-primary visuals continue to use their own prompt.

After deployment, verify one primary and one non-primary character generation. The primary modal and created image task must contain the same canonical prompt. The non-primary task must contain the variant prompt and exactly one canonical-character reference image.

Rollback requires reverting the backend application and Skill together. Retained historical variant prompt columns allow the previous behavior to resume without a data restore.
