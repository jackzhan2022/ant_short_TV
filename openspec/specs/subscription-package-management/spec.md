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
套餐 SHALL 仅允许一次性积分、周期积分和全局算力折扣三类可发布权益；免费次数、并发、成员数等未实现权益 MUST 不得发布。

#### Scenario: Invalid entitlement
- **WHEN** 运营提交未实现的权益类型
- **THEN** 系统拒绝发布并返回明确校验错误

