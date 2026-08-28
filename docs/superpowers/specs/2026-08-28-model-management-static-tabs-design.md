# Model Management Static Tabs Design

## Context

The model-management page derives its active tab from the URL query string and updates that string with `history.replace`. The page does not subscribe to that update, so clicking a tab can change the address without changing the displayed content. The Agent and Skill catalog already uses ordinary in-page tabs without URL-driven state.

## Decision

Use the same static tab pattern as the Agent and Skill catalog:

- Keep model-service-provider, AI-model, and call-log tabs in the existing permission-filtered item list.
- Manage the active tab with local React state, initialized to the first permitted tab.
- Bind `Tabs.activeKey` and `Tabs.onChange` to that local state so content changes immediately on click.
- Remove query-string parsing, `history.replace` calls, and URL-based unauthorized-tab fallback from the model-management component.
- Preserve legacy provider, model, log, and billing routes as redirects to the model-management landing route, without a `tab` query parameter.

## Non-Goals

- Do not change the three tab contents, their existing permissions, or model-pricing behavior.
- Do not make a selected tab bookmarkable.
- Do not alter unrelated AI-service-management routes such as operations or Agent and Skill.

## Verification

- Update component tests to assert that clicking each visible tab displays its matching content without changing the URL.
- Retain a permission-filtering test for users who can see only a subset of tabs.
- Update route tests to assert that legacy links redirect to the model-management landing route.
