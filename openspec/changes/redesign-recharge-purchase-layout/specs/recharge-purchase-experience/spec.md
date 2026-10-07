## ADDED Requirements

### Requirement: Recharge page presents the approved storefront layout
充值中心 SHALL 在 `/recharge` 页面主内容中展示品牌页头、团队余额、当前会员摘要、内嵌商品选购和订单确认区，视觉层级 SHALL 采用冻结参考中的「品牌旗舰」方案。用户 MUST 能够直接浏览商品，无需先打开选购弹窗。页面 SHALL 保留返回工作台、订阅与订单以及统一积分流水的访问路径。

#### Scenario: Open the recharge storefront
- **WHEN** 已选择团队的有查看权限用户打开 `/recharge`
- **THEN** 页面直接显示主选购区和当前团队信息
- **AND** 订阅与订单、积分流水仍可在同页访问
- **AND** 页面不提供设计稿方案轮播或原型主题调节工具

### Requirement: Membership and point packages have persistent separate entry points
页面 SHALL 提供始终可见的「会员套餐」和「积分包」一级入口，初次进入 SHALL 默认选择会员套餐及月度周期。商品分组 MUST 由 `packageType` 决定，不得用商品名称或积分包的周期字段推断其类型。切换入口 SHALL 保留各分组仍然有效的选择。

#### Scenario: Only point packages are for sale
- **WHEN** 目录仅返回 `POINT_PACKAGE` 商品
- **THEN** 会员入口仍然可见且默认展示当前周期无可售套餐的空态
- **AND** 用户可以显式切到积分包查看可售商品
- **AND** 页面不生成示例会员或自动隐藏会员入口

#### Scenario: A point package carries a billing period
- **WHEN** 一件积分包的 `billingPeriod` 为 `YEAR` 或其他周期值
- **THEN** 该商品仍然仅出现在积分包入口

### Requirement: Membership period selection reflects the catalog
会员选购 SHALL 提供月度、季度、半年、年度四种周期，分别对应 1、3、6、12 个月。有效 `periodMonths` SHALL 优先用于分组；缺失时 SHALL 使用已知 `billingPeriod` 作为回退。不能识别的周期 MUST 显示为待确认信息并禁止购买，不得错误归到月度。每个周期 SHALL 展示该周期全部可售版本，而不是固定一个演示方案。

#### Scenario: Select a supported membership period
- **WHEN** 用户选择季度且目录包含两个 3 个月会员版本
- **THEN** 页面显示这两个会员并同步当前选中商品和订单摘要
- **AND** 页面不显示其他周期或积分包商品

#### Scenario: Period metadata conflicts
- **WHEN** 商品具有有效 `periodMonths=12` 但其他周期字段与之不一致
- **THEN** 商品按年度展示，周期计算使用 12 个月

#### Scenario: Period month count is missing
- **WHEN** 商品的 `periodMonths` 缺失且 `billingPeriod=HALF_YEAR`
- **THEN** 商品按半年展示，周期计算使用 6 个月

#### Scenario: Unsupported membership period
- **WHEN** 目录包含无法归入四种周期的会员
- **THEN** 页面保留该商品的周期待确认提示并禁止为它提交订单
- **AND** 其他正常商品仍可选购

### Requirement: Production offers and labels come from authoritative data
正式页面 MUST 使用目录接口返回的名称、版本 ID、价格、币种与权益，不得硬编码设计原型的示例会员、价格、余额、日期或改名。选中卡 SHALL 显示明确选择标记；在无后台推荐依据时 MUST NOT 自动添加「推荐方案」。仅当有效 `listPrice > price` 时 SHALL 展示划线原价或优惠标签。

#### Scenario: Render catalog values instead of prototype values
- **WHEN** 接口返回的名称、价格和积分数量不同于设计参考
- **THEN** 卡片和确认区展示接口数据
- **AND** 界面没有示例商品、假付款码或原型提示文案

#### Scenario: List price does not represent a discount
- **WHEN** `listPrice` 缺失、等于售价或低于售价
- **THEN** 页面不显示划线原价或优惠标签
- **AND** 已选状态仍可以独立显示

### Requirement: Entitlement presentation distinguishes recurring and one-time benefits
页面 SHALL 根据权益类型展示一次性积分、每月周期积分、全局 AI 积分折扣和展示权益名称快照。用于主要摘要的权益 MUST NOT 在权益列表中完整重复。仅在有效数值齐备时 SHALL 展示可推导的周期积分合计、折合月价或单位积分价格；零积分或缺失字段 MUST NOT 导致除零、`NaN`、`undefined` 或虚构额度。

#### Scenario: Membership has recurring points and a discount
- **WHEN** 某季度会员含每月 3,500 积分及折扣系数 0.85
- **THEN** 页面分别展示「每月 3,500 积分」「周期发放合计 10,500 积分」及「8.5 折」
- **AND** 明确积分按月发放，不表示为付款后一次到账 10,500 积分

#### Scenario: Membership also includes one-time points
- **WHEN** 同一会员同时包含一次性积分和周期积分
- **THEN** 页面分别标注一次性发放与每月发放
- **AND** 不把一次性额度计入每月额度或周期发放合计

#### Scenario: Point package and display-only benefits
- **WHEN** 积分包包含一次性积分和没有数值的展示权益
- **THEN** 页面展示一次性额度、已发放积分永久有效及展示权益名称
- **AND** 有效正积分和价格存在时可以展示带币种的单位积分价格
- **AND** 不重复主积分权益，也不为展示权益补造数值

### Requirement: Selection and order summary remain consistent
页面 SHALL 以可售 `packageVersionId` 管理选中商品。初次进入非空分组时 SHALL 按接口顺序选择第一项；返回分组时 SHALL 恢复仍有效的选择。切换类别、周期或商品 SHALL 立即同步确认区的名称、权益摘要、价格、币种和目标团队。刷新后已移除的选择 MUST 被清除或替换为当前分组有效商品；空分组 MUST 清空可支付摘要并禁用确认操作。

#### Scenario: Switch between product types
- **WHEN** 用户先选择年度会员，再切到积分包并选择另一件商品
- **THEN** 确认区展示积分包的一次性额度与售价
- **AND** 返回会员入口时恢复年度周期及仍可售的会员选择

#### Scenario: Selected offer is removed
- **WHEN** 刷新后的目录已不包含选中的版本
- **THEN** 页面不能继续为该旧版本确认付款
- **AND** 当前分组无其他商品时显示空态并禁用付款

### Requirement: Billing permission and loading states are explicit
有查看权限但无 `canManageBilling` 的成员 SHALL 能浏览目录与权益，确认购买操作 MUST 被禁用并说明仅团队管理员可购买。目录加载中、读取失败及成功返回空目录 SHALL 采用不同状态；读取失败 SHALL 提供只读重新加载操作，不得自动创建或重试订单。

#### Scenario: Viewer without billing permission
- **WHEN** 普通成员查看并选择可售商品
- **THEN** 可浏览价格与权益，付款按钮不可用且说明购买权限限制
- **AND** 不发送创建订单请求

#### Scenario: Catalog loading fails
- **WHEN** 目录接口返回错误
- **THEN** 页面显示加载失败原因和重新加载入口
- **AND** 不把请求失败显示为暂无套餐，也不保留可提交的旧金额

### Requirement: Payment presentation follows the created order
页面 SHALL 继续支持微信 Native 二维码、有效期、逐秒剩余时间与查单。付款区 MUST 使用已创建订单返回的金额和币种，不得随当前选购状态重新计算。恢复历史待支付订单缺少商品名称时 SHALL 使用中性订单名称与商户订单号。已有待支付订单仅在用户选择继续支付时 SHALL 打开二维码。

#### Scenario: Continue an existing order after catalog changes
- **WHEN** 商品目录已改变或商品下架，用户继续支付一个仍有效的待支付订单
- **THEN** 展示该订单原有金额、币种、订单号、有效期及二维码
- **AND** 不创建新订单，不套用当前选中商品的名称和价格

#### Scenario: Payment finishes or expires
- **WHEN** 服务端确认订单完成或确认已过期、失败、关闭
- **THEN** 停止该订单轮询和倒计时
- **AND** 完成时刷新会员、排队订阅、订单、余额与积分流水
- **AND** 未完成时显示实际结果并保留可用的选购入口，不提示权益到账

#### Scenario: Close payment or leave the page
- **WHEN** 用户关闭付款弹窗、离开页面或切换团队
- **THEN** 停止旧弹窗的计时与跟踪并忽略其迟到响应
- **AND** 关闭弹窗本身不取消仍有效的服务器订单

### Requirement: Team account context and history remain isolated
页面 SHALL 按当前团队展示余额、当前会员、待生效订阅、订单和唯一的统一积分流水。团队变化时 MUST 清理旧选购与付款状态，并忽略旧团队请求的迟到结果。日期 SHALL 以应用约定的可读格式显示；缺失数据 SHALL 使用占位，不推测发放日期或立即生效承诺。

#### Scenario: Switch team during a request
- **WHEN** 团队 A 的目录、创建订单或查单请求未完成时用户切换至团队 B
- **THEN** 团队 A 的响应不更新团队 B 的摘要、二维码、余额或会员状态
- **AND** 团队 B 的操作仅使用团队 B 的数据和权限

#### Scenario: Account and ledger sections survive the redesign
- **WHEN** 用户查看会员详情、待生效订阅或积分明细
- **THEN** 页面显示现有接口数据与可读日期
- **AND** 不通过拼接商业发放记录再次展示同一笔积分流水

### Requirement: Purchase layout supports responsive and keyboard interaction
页面 SHALL 在 320px、390px、768px 和 1440px 视口下保持内容可读及控件可达。商品名称、价格、周期控件与错误信息 MUST 正常换行，不遮挡主操作。类别、周期和商品选择 SHALL 有可感知的选中态及键盘操作；付款弹窗 SHALL 具有可见焦点、焦点管理和可用关闭操作。

#### Scenario: Use a narrow viewport with long content
- **WHEN** 手机视口显示长商品名、多个权益及错误消息
- **THEN** 卡片、权益和确认区按纵向布局排列，页面无内容裁切或主操作遮挡
- **AND** 用户仍能切换两类商品和全部四种周期

#### Scenario: Complete selection using the keyboard
- **WHEN** 用户使用键盘选择类别、周期、商品并打开或关闭付款弹窗
- **THEN** 所有操作都有可见焦点与明确标签，弹窗关闭后焦点返回触发操作
- **AND** 选中态在深浅模式下都具有可辨识的文字和颜色对比
