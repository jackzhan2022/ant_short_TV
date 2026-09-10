# Asset Settings Loading Design

## Goal

Make `/projects/:id/production-workbench/settings` visibly responsive while
its initial data is loading, and remove unrelated script-workspace data from
the page's request.

## Scope

The settings page will show an Ant Design skeleton while its initial asset
workspace request and pending-candidate request are in flight. If either
request fails, the page will stop loading and retain an actionable error state
instead of appearing indefinitely blank.

The backend will expose a project-scoped asset settings workspace endpoint.
It returns only character, scene, and prop assets with the visual variants,
generation summaries, and episode bindings already required by the settings
page. It will not include script content, script versions, episodes,
storyboards, script-analysis state, or global understanding.

The existing `/script-workspace` endpoint remains unchanged for the script and
storyboard pages. Settings-page mutations continue to use their existing
endpoints; after a mutation, the page refreshes its asset workspace and
pending candidates in parallel.

## API

`GET /api/projects/{projectId}/asset-settings-workspace`

Response:

```json
{
  "success": true,
  "data": {
    "projectId": 33,
    "characters": [],
    "scenes": [],
    "props": []
  }
}
```

The endpoint requires the same project-view authorization as the current
workspace read. Asset and visual fields preserve the existing frontend shape.

## Verification

- Backend controller/service coverage proves the new endpoint excludes the
  non-asset workspace fields while returning assets for an authorized project.
- Frontend coverage proves an initial request displays the skeleton and that
  the settings page reads the new lightweight endpoint.
- Existing settings interactions continue to refresh visible assets after a
  mutation.
