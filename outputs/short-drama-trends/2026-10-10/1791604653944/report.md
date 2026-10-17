# 第三方榜单储备与官网诊断

采集时间：2026-10-10 11:57（Asia/Shanghai）。

|来源|记录数|来源日期|
|---|---:|---|
|ReelShort Top · ReelPulse快照|10|2026-10-09|
|GoodShort Top · ReelPulse快照|10|2026-10-09|
|GoodShort Most Trending · ReelPulse快照|10|2026-10-09|
|DramaBox Top Hits · ReelPulse快照|10|2026-10-05|

合计40条榜单记录，按平台＋剧目ID去重40部；不是新增数量。

- REELSHORT_TOP：HTTP 403，响应明确提示地区限制；失败阶段ROBOTS_VALIDATE。
- DRAMABOX_TOP：HTTP 403，CloudFront 请求被拦截，具体规则未确认；失败阶段ROBOTS_VALIDATE。
- GOODSHORT_TOP：HTTP 403，通用 403，具体原因未确认；失败阶段ROBOTS_VALIDATE。

36个定向测试通过。原始验证库：D:\需求文档\ant_short_TV\backend\target\trend-smoke\1791604653944\trends.mv.db。

JSON已导出到此目录。仅本地储备与验证；未部署、未写业务MySQL、未接首页、未启用定时任务。请求ID仅供内部排障。
