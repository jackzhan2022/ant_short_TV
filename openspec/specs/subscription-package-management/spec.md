# subscription-package-management Specification

## Purpose
TBD - created by archiving change add-subscription-package-management. Update Purpose after archive.
## Requirements
### Requirement: Versioned package catalog
系统 SHALL 支持积分包和月、季、半年、年会员套餐的创建、编辑草稿、发布、上下架及历史版本查询。已被订单引用的价格和权益版本 MUST 不可变。

#### Scenario: Publish package
- **WHEN** 具备权限的运营提交合法套餐、价格和固定权益
- **THEN** 系统创建新的可售版本并自动分配版本号

#### Scenario: Edit referenced package
- **WHEN** 运营尝试修改已被订单引用的版本
- **THEN** 系统拒绝修改并要求创建新版本

### Requirement: Fixed entitlement validation
套餐 SHALL 允许一次性积分、周期积分和全局算力折扣三类系统可执行权益，以及权益目录中处于启用状态的展示权益。系统权益 MUST 提供符合类型规则的数值，展示权益 MUST 不携带数值且不得产生运行时效果；未登记、已停用、重复或结构不合法的权益 MUST 不得发布。

#### Scenario: Invalid entitlement
- **WHEN** 运营提交未登记、已停用、重复或类别和值不匹配的权益
- **THEN** 系统拒绝创建或发布并返回明确校验错误

#### Scenario: Configure system entitlement
- **WHEN** 运营在套餐草稿中选择启用的系统权益
- **THEN** 页面要求填写权益值，系统按原有规则保存并执行该权益

#### Scenario: Configure display entitlement
- **WHEN** 运营在套餐草稿中选择启用的展示权益
- **THEN** 页面不要求权益值，系统保存其 code 和当前名称快照且不赋予运行时效果

#### Scenario: Disable entitlement before draft publication
- **WHEN** 套餐草稿引用的展示权益在发布前被停用
- **THEN** 系统拒绝发布该草稿并提示对应权益已停用

### Requirement: Entitlement display snapshots
套餐版本 SHALL 保存每项权益的稳定 code 和名称快照。目录中的权益被改名或停用后，已创建的套餐版本、商品订单和订阅历史 MUST 继续显示其原始快照名称。

#### Scenario: Rename an entitlement after package publication
- **WHEN** 运营人员在套餐版本发布后修改其展示权益名称
- **THEN** 已发布套餐和基于该版本创建的订单仍显示发布时的权益名称

#### Scenario: Read legacy system entitlement
- **WHEN** 历史系统权益没有名称快照
- **THEN** 系统使用该系统权益 code 的统一字段字典名称作为展示回退

