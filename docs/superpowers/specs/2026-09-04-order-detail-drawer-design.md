# Order Detail Drawer Design

## Goal

Optimize the platform order-detail drawer so operators can verify order state, payment amount, package version, and payment evidence quickly without introducing management actions.

## Scope

- Keep the current `查看详情` entry point and the existing order-detail API.
- Keep the drawer read-only. Do not add refund, close-order, or other transactional actions.
- Retain the existing three information groups: order, package version, and payment.
- Do not change backend fields, generated service code, or list-page filters.

## Information Architecture

The drawer is organized in this reading order:

1. Header: close control and `订单详情` title.
2. Order summary: merchant order number, prominent paid amount, and visually distinct order status.
3. `订单信息`: tenant, created time, completed time.
4. `套餐版本`: package name, version, package type, payment deadline.
5. `支付信息`: channel, payment status, provider trade number, payment time.

The merchant order number and provider trade number can be long. They must wrap safely rather than widen or overflow the drawer.

## Visual Direction

- Use the existing global theme and Ant Design tokens.
- Use a compact, operational tone: white drawer, muted secondary labels, subtle separators, and no decorative cards nested inside the drawer.
- Place amount and status in a light summary area below the header. Amount is the primary numerical signal; status uses the existing semantic status color.
- Render group fields as a responsive two-column description grid that becomes one column on narrow drawers.

## States

- While loading, retain Ant Design Drawer loading behavior.
- Missing optional data renders as `-`.
- All commercial order statuses continue to use the existing status-label and status-color mappings.

## Validation

- Existing detail-opening test remains valid.
- Add assertions for the summary amount and completed-status presentation.
- Verify rendering at desktop and narrow drawer widths; long identifiers must not overflow.
