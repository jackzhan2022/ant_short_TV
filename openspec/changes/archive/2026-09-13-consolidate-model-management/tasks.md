## 1. Navigation and naming

- [x] 1.1 Update AI service routes, redirects, and locale strings so the visible menu is “模型管理” and legacy provider, model, billing, and log links resolve to the appropriate model-management context.
- [x] 1.2 Build a permission-aware model-management tab container for 模型服务商, AI 大模型, and 调用日志, including a stable default tab and unauthorized-tab fallback.
- [x] 1.3 Update affected Provider and Model page titles, headers, form labels, and action copy to use 模型服务商 and AI 大模型.

## 2. Model price experience

- [x] 2.1 Confirm or implement the data source for per-model current cost-price and point-price summaries without changing price-version behavior.
- [x] 2.2 Refactor the existing billing content into a reusable, fixed-model component and present it in a model-pricing dialog without a model selector.
- [x] 2.3 Add current cost-price and current point-price columns plus a model-pricing action to the AI-model list, and refresh their summaries after a successful publish or revoke.
- [x] 2.4 Remove the standalone billing page from the visible navigation while retaining the compatible redirect path.

## 3. Verification

- [x] 3.1 Add or update route and tab tests for menu consolidation, renamed labels, permission-filtered tabs, and legacy-link redirects.
- [x] 3.2 Add or update model-list and pricing-dialog tests for price summaries, fixed model context, publish/revoke permission gating, and summary refresh.
- [x] 3.3 Run the focused frontend test suite, TypeScript checks, Biome lint, and Ant Design lint for the touched code.
