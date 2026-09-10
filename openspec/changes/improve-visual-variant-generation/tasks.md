## 1. Prompt derivation and image-task rules

- [ ] 1.1 Add focused backend tests covering empty-variant prompt derivation, preservation of non-empty prompts, and character primary-image validation.
- [x] 1.2 Implement server-side visual-variant prompt derivation from canonical asset prompt, variant name, appearance, and type-specific constraints.
- [x] 1.3 Persist derived prompts only for empty variants and expose them through the asset settings workspace.
- [x] 1.4 Update character visual-variant image task creation to resolve the usable canonical primary image, pass it as `referenceImages`, and reject requests without it.
- [ ] 1.5 Keep scene and prop visual-variant image tasks text-based and verify existing generation behavior remains compatible.

## 2. Asset settings experience

- [x] 2.1 Add frontend tests for canonical prompt visibility, automatic variant prompt fill, and missing-character-primary-image feedback.
- [x] 2.2 Add a compact canonical asset prompt view/edit control to the settings experience.
- [x] 2.3 Show the selected variant's persisted or newly derived prompt in the existing visual generation prompt input and preserve user edits.
- [ ] 2.4 Pass the canonical character primary image as the reference image for character variant generation and present the server validation message when it is unavailable.

## 3. Verification

- [ ] 3.1 Run focused backend and frontend regression tests for asset settings and image-task submission.
- [ ] 3.2 Run frontend type checking and production build.
- [ ] 3.3 Run OpenSpec strict validation for `improve-visual-variant-generation`.
