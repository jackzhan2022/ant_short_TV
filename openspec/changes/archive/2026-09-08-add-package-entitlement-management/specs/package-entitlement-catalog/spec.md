## ADDED Requirements

### Requirement: Unified entitlement catalog
系统 SHALL 为平台运营提供统一权益目录，并同时列出系统预置权益和运营创建的展示权益。每项权益 MUST 包含稳定 code、名称、类别、状态和排序信息。

#### Scenario: View entitlement catalog
- **WHEN** 具备套餐查看权限的运营人员打开套餐管理的“权益管理”Tab
- **THEN** 系统按稳定顺序返回并展示三种系统权益及所有展示权益

#### Scenario: Access without view permission
- **WHEN** 不具备套餐查看权限的用户请求权益目录
- **THEN** 系统拒绝访问

### Requirement: Protected system entitlements
系统 SHALL 预置一次性积分、周期积分和全局折扣三种系统权益，并 MUST 禁止通过权益管理接口编辑、停用或删除这些定义。

#### Scenario: Attempt to modify a system entitlement
- **WHEN** 具备套餐编辑权限的运营人员尝试编辑或停用系统权益
- **THEN** 系统拒绝操作并返回系统权益不可修改的明确错误

### Requirement: Display entitlement lifecycle
具备套餐编辑权限的运营人员 SHALL 能够新增、编辑、启用和停用展示权益。展示权益 code MUST 由服务端生成且创建后不可变，系统 MUST 保留停用定义而不得物理删除。

#### Scenario: Create a display entitlement
- **WHEN** 运营人员提交合法且非空的展示名称和可选说明
- **THEN** 系统创建启用状态的展示权益并分配唯一稳定 code

#### Scenario: Edit a display entitlement
- **WHEN** 运营人员修改展示权益的名称、说明或排序
- **THEN** 系统保存修改且保持原 code 不变

#### Scenario: Disable a display entitlement
- **WHEN** 运营人员停用一个展示权益
- **THEN** 系统保留该定义及历史引用，并阻止其被新套餐草稿选择

#### Scenario: Manage catalog without edit permission
- **WHEN** 只有套餐查看权限的运营人员尝试创建、编辑或启停展示权益
- **THEN** 系统拒绝写操作且页面不提供对应操作入口

### Requirement: Entitlement management tabs
套餐管理页面 SHALL 参考模型管理页面使用同页 Tab 布局，至少包含“套餐列表”和“权益管理”，并在切换 Tab 时保持各自职责清晰且不嵌套页面容器。

#### Scenario: Switch to entitlement management
- **WHEN** 运营人员在套餐管理页选择“权益管理”Tab
- **THEN** 页面显示权益目录列表及其权限允许的维护操作

#### Scenario: Return to package list
- **WHEN** 运营人员从“权益管理”切换回“套餐列表”
- **THEN** 页面显示原有套餐列表、版本历史和草稿入口

### Requirement: Display entitlements have no runtime effect
展示权益 SHALL 仅用于商品和历史信息展示，系统 MUST NOT 将其用于权限判定、模型路由、计费折扣、积分发放、周期任务或权益发放记录。

#### Scenario: Fulfill a package containing display entitlements
- **WHEN** 已支付套餐同时包含系统权益和展示权益
- **THEN** 系统只执行对应的系统权益，展示权益不产生任何发放记录或运行时配置

#### Scenario: Package contains only display entitlements
- **WHEN** 套餐版本只包含展示权益并完成支付
- **THEN** 系统完成既有订单流程且不创建展示权益发放记录
