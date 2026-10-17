# 短剧趋势采集链路（多平台）

## 范围与数据口径

- 手动触发 → 检查 robots.txt → 读取公开榜单所有分页 → 解析 → 保存原始响应 → 完整性校验 → 发布不可变快照 → 分页查询。
- 注册10个平台、17个独立来源。已实现红果4榜、FlexTV可见专区，以及ReelPulse公开日期快照（ReelShort TOP、GoodShort TOP/Most Trending、DramaBox Top Hits）的解析器。其余官方入口仅检查可访问性并记录缺口，不把主页推荐当作榜单。
- 不进行登录、验证码、签名接口或403绕过，也不下载剧集/封面媒体。原站限制与第三方公开快照是独立来源记录，不能把第三方采集结果标成官方采集成功。
- HONGGUO_HOT：综合热播榜；HONGGUO_REAL：真人；HONGGUO_AI：AI；HONGGUO_COMIC：漫剧。
- 内容类型由独立榜单类别确定。综合榜内剧目尚未逐条核对类型，保存为 UNKNOWN，而不是根据剧名猜测。
- “8023万热度”保留 metricText=8023万热度、metricValue=8023、metricUnit=万热度。不是播放量，也不是8023点指数。无法解析的新单位只保留原文，不编造数值。
- observedAt 是本次采集开始的时间；数据库 datetime 按UTC保存，observedDate 按Asia/Shanghai计算。查询日期筛选的是采集日期，不是新增播放时间或月榜。
- sourceUpdatedText 保留官网“10月9日已更新”等文字。不擅自给没有年份的来源文案补历史日期。
- 网页分页读取不是平台原子快照；在采集过程中发生分页数量、更新时间、重复榜位/剧目或榜位缺失时拒绝发布。

## 上线

1. 在标准发布流程备份数据库并应用 Flyway V134、V135；不要手动改已有迁移。
2. 启动新后端。无需新环境变量或数据源账号。
3. 平台管理员角色通过迁移获得 PLATFORM_TREND_COLLECT；团队管理员不自动拥有采集权限。
4. 普通登录用户只读共享公开榜单。原始HTML仅保存在后台数据库，不在普通查询接口返回。
5. 本期不添加自动定时采集，不改变现有首页路由或HTML预览中的参考数据。

## 接口

沿用系统登录Cookie、CSRF机制，不提供匿名触发或任意URL参数。

| 方法 | 地址 | 用途 |
|---|---|---|
| GET | /api/short-drama-trends/sources | 来源列表（登录） |
| POST | /api/platform/short-drama-trends/collections/HONGGUO_HOT | 采集一个来源（平台权限＋CSRF） |
| GET | /api/platform/short-drama-trends/collections/{runId} | 采集状态、错误码、快照ID（平台权限） |
| GET | /api/platform/short-drama-trends/coverage | 每个来源的最新采集结果及最近成功快照（平台权限） |
| GET | /api/short-drama-trends/snapshots | 快照列表（登录） |
| GET | /api/short-drama-trends/snapshots/{snapshotId}?page=1&pageSize=20 | 快照及分页条目（登录） |

快照列表参数：

```text
market=DOMESTIC|OVERSEAS
platform=HONGGUO|DOUYIN|KUAISHOU|TENCENT|IQIYI|YOUKU|REELSHORT|DRAMABOX|GOODSHORT|FLEXTV
contentType=MIXED|LIVE_ACTION|AI|COMIC|UNKNOWN
sourceType=OFFICIAL|THIRD_PARTY
from=2026-10-01&to=2026-10-09
page=1&pageSize=20
```

尚未成功采集的平台/市场返回空列表，不返回手工种子数据。类型筛选针对榜单类别，不把综合榜偷偷当作纯真人榜。列表是**历史快照列表**，不是跨平台或跨日期混排的总榜。pageSize最多100，保留原榜位。

POST是同步、有限分页的试运行接口：请求成功接收不代表采集成功，调用方必须检查 data.status（SUCCEEDED/BLOCKED/NEEDS_SOURCE/FAILED）与 errorCode/error。失败记录无snapshotId，先前成功快照仍可查询。每来源最少间隔60秒；进程内串行，正式多实例/高频调度前需独立任务队列与数据库租约。不要把本期接口直接挂到自动刷新。

## 存储

- trend_collection_run：每次手动请求、执行人、状态、开始结束时间和错误。
- trend_collection_page：每次收到的HTTP响应，page_number=0为robots.txt，其他为榜单页；保存URL、状态、抓取时间、正文，失败页面也保留。
- trend_snapshot：来源、范围、时间、页数、条目数量、SHA-256内容指纹和解析器版本。
- trend_snapshot_entry：外部剧目ID字符串、原榜位、剧名、类型、原始热度和单位、标签、来源详情URL。

同一来源＋同一采集日期＋相同规范化条目指纹复用快照，但采集记录和原始响应独立留存。所有分页成功后快照及条目在一个事务中入库。被中断的进程可能遗留RUNNING记录，不能视为成功；本期没有自动恢复任务。

原始HTML可能含来源生成的访问标识，不应公开导出；目前仅平台数据库保留。上线前依据运维容量制定保留周期，当前没有自动删除任务。

## 验证与复跑

仓库根目录执行：

```powershell
mvn -f backend/pom.xml '-Dtest=ForeignTrendParserTest,HongguoTrendParserTest,TrendCollectionServiceTest,TrendControllerTest,TrendSecurityIntegrationTest,TrendHttpClientTest' test
```

真实采集验证是显式开启的测试，默认CI不会联网。下面默认只采集红果4榜：

```powershell
mvn -f backend/pom.xml '-Dtest=TrendLiveCollectionTest' '-Dtrend.live=true' test
```

全部17来源（覆盖10平台）的真实采集审计：

```powershell
mvn -f backend/pom.xml '-Dtest=TrendLiveCollectionTest' '-Dtrend.live=true' '-Dtrend.live.scope=all' test
```

所有注册来源都会留下采集记录；遇到robots限制、403、未确认榜单时记录BLOCKED/NEEDS_SOURCE，而非伪造数据。测试通过仅意味着已实现的解析器采集成功且所有来源状态符合预期，**不代表10个平台全部拿到了榜单**。

结果写入 backend/target/trend-smoke/<本次时间戳>/：

- trends.mv.db：独立、持久化的H2验证数据库（不是业务MySQL）。
- runs.json：每个来源的采集结果。
- coverage.json：平台/来源元数据、最新采集状态、最近成功快照。
- <来源ID>.json：成功入库后通过查询服务回读的快照和条目。本次支持来源均不超过100条；采集服务仍按实际公开分页读取，详情API提供分页。

只有测试通过且各run状态为SUCCEEDED，才能认定对应来源的真实采集→解析→入库→查询链路通过。本次不自动启动/重启业务后端，不自动向业务数据库导入测试数据。

## 2026-10-09 验收记录

- 红果综合、真人、AI、漫剧四个来源均通过真实网络采集，每榜5页、100条，合计400条榜单记录，按平台剧目ID去重为300部。各榜间存在重复剧目，不能把400条称为400部。
- 本次19个定向测试全部通过，包含真实HTTP读取、H2持久化和回读、全量Flyway迁移、登录/平台权限/CSRF、失败保留、快照去重、分页、robots和响应大小限制。未运行整个项目的全量回归。
- 可复核样本：backend/target/trend-smoke/1791549099565/；该目录是隔离验证产物，不是已部署业务数据。
- 本次未重启业务服务、未应用业务MySQL迁移、未将HTML参考预览切换为线上接口。

## 多平台字段与限制（2026-10-10）

- sourceType：OFFICIAL / THIRD_PARTY；sourceId保留具体采集渠道，不混淆第三方快照与官方页面。
- sourceDate：来源明确标注的快照日期，与observedDate采集日期分开。ReelPulse旧日期快照不会被重写成采集当日榜单。
- language：当前海外仅en，不声称代表平台全球语言区。
- rankingKind：RANK为明确榜位；SECTION_ORDER为平台专区展示顺序；UNVERIFIED为尚未确认榜单的公开入口。
- coverage：FULL_PUBLIC_PAGES为已读取全部公开分页；PARTIAL_PUBLIC_PAGE表示仅可见页；THIRD_PARTY_TOP10表示第三方公开10条快照，不代表平台全部条目。
- reportedTotal：来源页面声明的总量（FlexTV文案为约100），不是采集成功数量；entryCount才是实际保存数量。
- metricKind：PLATFORM_HEAT / PLATFORM_VIEWS / EPISODE_COUNT。集数不得用于冒充播放量，26M保存原值26与单位M views，不与26次混淆。
- episodeCount：第三方页面明确提供时单独保存；未知内容类型仍为UNKNOWN，不按剧情/标签推断真人或AI。
- errorCode：ROBOTS_DENIED、ROBOTS_UNAVAILABLE_<状态码>、HTTP_<状态码>、NO_VERIFIED_CHART、FETCH_FAILED、PARSE_OR_STORAGE_FAILED。诊断性文本在error中。

采集器保守遵守自定义采集器及公开AI客户端的robots限制；不通过改User-Agent、代理或换路径绕过限制。未来接入受限来源应取得平台授权或使用获准数据服务。

## 2026-10-10 多平台验收记录

- 已检查10个平台、17个来源；9个来源成功，覆盖5个平台，共458条榜单记录，按平台＋剧目ID去重为358部。
- 红果4榜各100条；FlexTV官网公开可见18条（页面标称约100）；ReelPulse提供ReelShort TOP10、GoodShort TOP10/Most Trending各10条、DramaBox Top Hits 10条。
- ReelShort/GoodShort第三方来源日期为2026-10-09；DramaBox为2026-10-05。采集时间为2026-10-10，未将历史快照改写为当日榜单。
- 抖音尚未确认公开榜单解析规则；快手、腾讯视频、爱奇艺按robots限制停止；优酷robots返回302无法核验规则。ReelShort/DramaBox/GoodShort官方robots读取403，其官方来源记录仍保持BLOCKED，不被第三方成功记录覆盖。
- 29个定向测试全部通过（包含真实采集审计、全量Flyway迁移到V135、权限、原始指标、快照日期、覆盖完整性与访问限制校验）。没有执行整个项目的全量回归。
- 原始验证库：backend/target/trend-smoke/1791600551438/trends.mv.db。可分享的结构化结果与HTML报告：outputs/short-drama-trends/2026-10-10/1791600551438/。报告不导出原始HTML访问标识。
- 首次扩展验证暴露独立H2文件库反复开关时的Windows文件锁问题；已仅在测试连接配置中保持库打开，结束时显式SHUTDOWN，复跑通过。未改动业务数据库配置。
- 当前没有部署/重启业务后端，没有向业务MySQL写数据，也没有将首页预览切换成实时接口。


## 第三方储备 + 官网请求诊断（2026-10-10）

本轮沿用已核验的4个独立来源：REELPULSE_REELSHORT_TOP、REELPULSE_DRAMABOX_TOP、REELPULSE_GOODSHORT_TOP、REELPULSE_GOODSHORT_TRENDING。每个来源保留sourceType=THIRD_PARTY、sourceUrl、sourceDate及实际覆盖范围；官网失败仍是独立BLOCKED记录，不改写为第三方成功。重复采集同一天同内容快照复用snapshotId，跨日保留观察快照；榜单记录数不等于新增剧目数。

新增迁移V136（仅本地测试应用，尚未应用业务MySQL）：
- trend_collection_run.failure_stage：ROBOTS_FETCH / ROBOTS_VALIDATE / ROBOTS_POLICY / PAGE_FETCH / PAGE_VALIDATE / PARSE / SNAPSHOT_PUBLISH。成功时为空。网络异常保留失败阶段，但不伪造HTTP状态码或响应。
- trend_collection_page.diagnostics_json：请求地址、页码（0=robots）、HTTP状态、响应类型、采集时间、请求耗时、响应头白名单、响应标识和基于证据的分类。历史记录读取时没有的耗时/响应头仍缺省。
- 只留server、date、content-type、retry-after、x-amz-cf-id、x-amz-cf-pop、x-cache、cf-ray、x-request-id、location。每个值最多512字符；location移除userinfo、query、fragment。不保存Cookie/Authorization/任意自定义头到诊断字段。原始响应HTML仍只在原始采集记录内，不经诊断API输出。
- provider只表示响应呈现的服务器线索，不证明真实源站。GEO_RESTRICTED仅用于CloudFront响应明确说明地区限制；EDGE_REQUEST_BLOCKED不等同已确定某条WAF规则；openresty通用403保留HTTP_FORBIDDEN_UNCLASSIFIED，不猜测IP封禁。

平台管理员诊断API（与采集接口相同PLATFORM_TREND_COLLECT权限）：

```text
GET /api/platform/short-drama-trends/collections/{runId}/diagnostics
```

返回run和requests；普通登录用户不能读取该诊断。含请求ID的导出文件仅作内部排障材料，不作为公开榜单数据发布。未检测出口IP或请求平台白名单，未修改User-Agent、重定向、robots策略及访问权限。

只验证这三个平台的储备与诊断，不重跑其他平台：

```powershell
mvn -f backend/pom.xml '-Dtest=TrendLiveCollectionTest' '-Dtrend.live=true' '-Dtrend.live.scope=overseas-reserve' test
```

执行顺序为四个第三方榜单，然后三个官网检查。产物新增diagnostics.json；仍使用独立的本地H2验证库，不写业务MySQL。网络失败时保留已落盘的本轮证据和以前的储备文件；不同运行目录的条目不要相加当作独立剧目。

当前为手动执行，不包含自动定时任务、生产部署、首页对接或授权数据接口。官网robots失败时仍停止，不继续请求榜单。

### 本轮验收结果

- 2026-10-10 11:57（北京时间）真实读取7个来源：4个ReelPulse榜单成功、每榜10条，合计40条，按平台＋剧目ID去重40部；不是40部新增短剧。
- ReelShort/GoodShort来源日期仍为2026-10-09；DramaBox仍为2026-10-05。
- 官网三个robots均为403：ReelShort=GEO_RESTRICTED（CloudFront明确地区限制），DramaBox=EDGE_REQUEST_BLOCKED（未确认具体规则），GoodShort=HTTP_FORBIDDEN_UNCLASSIFIED（openresty线索，无Request ID）。三者均止于ROBOTS_VALIDATE；未请求榜单。
- 36个定向测试通过，0失败/错误/跳过，包括真实HTTP、持久化回读、全量Flyway到V136、诊断权限、历史记录兼容、头部最小化与失败阶段。
- 原始验证库：backend/target/trend-smoke/1791604653944/trends.mv.db。导出数据和报告：outputs/short-drama-trends/2026-10-10/1791604653944/（report.html、report.md、summary.json、diagnostics.json、runs.json、coverage.json、四个榜单JSON）。旧一轮储备文件保持不变。
- 没有执行全项目回归；没有生产部署、业务库迁移、首页切换或定时任务。
