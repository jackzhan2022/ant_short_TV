# Gemini image supplier maintenance

This change uses the existing `Gemini` supplier, sets its Base URL to
`http://89.58.47.162:8080`, and adds `Gemini-image` as an image model with the
case-sensitive upstream model ID `gemini-image`, without
changing the default model. The administrator manages the API key in the
existing supplier editor; maintenance never writes or clears that field.

The adapter sends native `POST /v1beta/models/gemini-image:generateContent`
requests with `x-goog-api-key`, user `contents`, image response modalities, and
optional aspect ratio/image size. References become inline image parts; HTTP
references are downloaded with a timeout and a 20 MB limit. Inline image outputs
become data URLs accepted by the existing storage/accounting flow. Authentication,
rate-limit, timeout, and empty-image responses retain gateway error categories.

Pricing copies the currently effective cost and point components of model 8,
`gpt-image-2`. At inspection on 2026-11-19 the point rates were 25.2 per output
image and 0.84 per additional reference image. Cost rates were CNY 0.60 and
CNY 0.02 respectively; CALL rates were zero. The other two GPT image variants
had different point prices and are not the selected baseline.

Production differs from the working tree. To avoid deploying unrelated changes,
`prepare.py` compiles only `GeminiAdapter.java` against the exact deployed classes
and libraries, replaces only its class entry, checks all other JAR entries for
identical contents, and runs `VerifyGeminiNative.java` against the candidate.
The probe uses a local HTTP server, with no upstream calls or customer billing.

Upload these scripts, the adapter source, and the verification Java source to a
fresh restricted maintenance directory. Run `python3 prepare.py`, review its
receipt, then run `sudo python3 activate.py`. Activation locks deployments,
checks the deployed JAR hash and missing Gemini model, creates a private
configuration/price snapshot, and publishes the new model and copied price
components in one transaction. Existing price versions and models are unchanged.
It installs a separate release retaining the existing frontend, performs normal
graceful service stop/start, and verifies service health and copied components.
The previous release is retained. A startup failure automatically restores its
release link and disables the new model; investigate before reactivating it.

Receipts contain release paths and model IDs without credentials. The private
snapshot is kept under `/opt/antv/backups/`. Once a real key has been entered,
perform the provider smoke test from the admin UI; these checks do not prove
that the upstream account has access to the requested model.
