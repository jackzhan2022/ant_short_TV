# Media Endpoint and Consumer Matrix

| Domain | Existing browser collection | Existing boundary | Consumers / migration |
| --- | --- | --- | --- |
| Projects | GET /api/projects | Unbounded accessible array | project list; project selectors; preserve permission-filtered count |
| Styles | GET /api/style-library | Unbounded public array | style library; creation style selector; independent categories |
| AI images | GET /api/projects/{id}/ai-image-tasks | All tasks and per-task results | storyboard media; asset candidates; paged summary/results |
| AI videos | GET /api/projects/{id}/ai-video-tasks and /{task}/results | All matching tasks/results | storyboard media, current binding and candidate pages |
| Voice | GET /api/projects/{id}/ai-voice-tasks and /{task}/results | Unbounded | shot table; storyboard current-page summaries |
| Subtitles | GET /api/projects/{id}/storyboard-subtitles | Unbounded | shot subtitle table, selected subtitle independent |
| Shot compose | GET /api/projects/{id}/shot-compose-tasks and /{task}/results | Unbounded | shot table request currently drops current/pageSize |
| Episode compose | GET /api/projects/{id}/episode-compose-tasks | Unbounded | existing browser service; paged summary |
| Episode versions | GET /api/projects/{id}/episode-video-versions | Unbounded per episode | video version service; preserve current selection |
| Exports | GET /api/projects/{id}/episode-export-records | Unbounded per project | existing browser service; paged records |
| Storyboards | GET /api/projects/{id}/storyboard-workspace | Already SQL paged 20/max100 | keep boundary; add GET /storyboard-media for page IDs |
| Asset visuals | GET /script-elements/{type}/{id}/visual-workspace | One asset on demand, candidate expansion | keep current variant; page historical candidates only when opened |
| Task center | GET /api/tenants/{id}/production-tasks and content sections | Already paged; details need audit | preserve existing protocol; lazy images and explicit play |
| Inspirations | GET /api/inspiration-creations and management list | Already paged; observer gated public thumbnails | preserve 8-item public default; bounded gallery rendering |
| Cover display | New GET /api/projects/{id}/cover and /cover/status | New stable reference, authorized status | project list and script page share compressed object |

New browser collection pages use ApiResponse<MediaPage<T>>, where MediaPage is
{data,current,pageSize,total}. Legacy workflow-only helper methods may retain
their internal signatures; migrated browser controllers must not expose an
unbounded array mode. Generated frontend clients are regenerated from OpenAPI.

Rendering audit covers native img/video/source, antd Image preview sources,
StableImage, and CSS background media. Login background autoplay is the explicit
scope exception. Original downloads and model inputs retain existing identities.

Runtime evidence and remaining coverage are recorded in verification.md as each
domain is tested. This matrix does not claim tests or migrations have completed.
