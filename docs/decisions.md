# 决策日志

> 一行一条：决策 · 为什么 · 日期。推翻旧决策不要删行，标注"已被 Dxxx 取代"。

- **D001 金额存 Long 分（cents）** · 避免浮点误差，一木导出最多 2 位小数 · 2026-10-05
- **D002 流水时间存 dateEpochMinute（本地墙钟分钟精度）** · 一木导出为分钟精度；记账场景不需要秒 · 2026-10-05
- **D003 全表 uuid 主键 + 软删 + createdAt/updatedAt + deviceId** · 为 M5 无服务端 op-log 同步预留，schema 一次到位 · 2026-10-05
- **D004 余额/统计重算不落库** · 同步冲突面最小化；净资产=总资产-负资产同理 · 2026-10-05
- **D005 流水 accountId 可空** · 一木真实数据存在无账户流水（导出 751 行账户全空）；纯现金用户也能记账 · 2026-10-05
- **D006 账户归属账本（Account.bookId）** · 一木资产页"按账本设置不同资产" · 2026-10-05
- **D007 退款/优惠是流水属性字段而非独立流水** · 一木导出中退款出现在支出行上（-0.0 证据）· 2026-10-05
- **D008 报销是流水上的状态机（NONE/PENDING/DONE）+ 报销账户/金额** · 一木资产页"可报/已报"汇总可由流水重算 · 2026-10-05
- **D009 收入分类不设"收入"根节点** · 一木导出里"收入"是显示域；我们用 CategoryType 区分收支两域，一级收入分类直接挂空父 · 2026-10-05（有意偏离一木存储的表现形式）
- **D010 依赖版本冻结：AGP 8.13.2 / Kotlin 2.4.20 / KSP 2.3.12 / Room 2.8.5 / Gradle 9.4.1** · dl.google.com 被墙走阿里云镜像 · 2026-10-05
- **D010a 修订：Compose BOM 降为 2025.11.01（ui 1.9.5）**，navigation 2.9.8 / activity 1.11.0 / lifecycle 2.9.4 同代配套 · BOM 2026.09 的 ui 1.12.1 要求 AGP 9.1+ 且 compileSdk 37，本机 SDK 只有 35/36 且 dl.google.com 不通装不了 37；ui 1.9.x 是最后支持 compileSdk 36 的线。Gradle wrapper 分发 URL 用腾讯镜像（services.gradle.org 的 distributions 路径被墙）· 2026-10-05
- **D016 Robolectric 测试 @Config(sdk=[35])** · SDK36 的 ApplicationSharedMemory 路径在 JVM 上触发 FileDescriptor 反射失败（robolectric#11434/#11387），robolectric 4.17 无解；已试 --add-opens 无效。升级 robolectric 时应回归试 36 · 2026-10-05
- **D017 release 变体单测禁用**（unitTests.all 里 name.contains("Release") → enabled=false）· 与 debug 跑同一批代码无增量价值；且 Robolectric compose 测试在 release 变体有 activity 解析问题 · 2026-10-05
- **D018 Roborazzi 截图约定**：需要 `systemProperty("roborazzi.test.record","true")` 才落盘（否则静默跳过）；`@Config(qualifiers="w400dp-h800dp")` 指定真机尺寸——默认 320x480 会把 weight(1f) 区域压成 0；`Color.hsl` 的 hue 单位是**度**不是 0~1 · 2026-10-05
- **D011 不引 material-icons-extended** · 体积原则；Tab/分类图标用自绘矢量 + 色块文字 · 2026-10-05
- **D012 release minify 在 M2 打开并做体积验收** · M0/M1 以 debug 构建为验收门，避免过早调 keep 规则 · 2026-10-05
- **D013 applicationId = dev.penned.app** · 占位；M2 首次发布前必须终审（改 id = 换应用身份，影响自更新）· 2026-10-05
- **D014 无 DI 框架** · 单模块小项目，手动构造 + viewModelFactory 足够；引 Hilt 是过度设计 · 2026-10-05
- **D015 Room schema export 开启，app/schemas/ 入库** · migration 测试的前提 · 2026-10-05
- **D019 Tab 图标手写 ImageVector，分类图标 = 哈希色块 + 首字**（黄金角散列色相）· 正式图标设计推迟到 M6 打磨 · 2026-10-05（"分类图标 = 哈希色相"部分已被 D024 取代：改为 8 色颜料盘；"手写 ImageVector、不引图标库"的部分继续有效）
- **D020 流水增加 excludeFromStats / excludeFromBudget 两个独立标志** · 一木账单详情页实测（模拟机探索报告 §二）；"不计入收支"与"不计入预算"是分开的开关 · 2026-10-05
- **D021 流水增加 source 来源字段**（MANUAL/VOICE/TEXT/SCREENSHOT/IMPORT/AUTO…）· 对应一木"记录方式"；统计"记录构成"与导入批次追溯都要用 · 2026-10-05
- **D022 转账增加 feeCents 手续费字段** · 一木转账 Tab 有"手续费"chip（模拟机探索 §一）；手续费记在转账流水上 · 2026-10-05
- **D023 引入 androidx.datastore-preferences 1.2.1** · 存当前账本 id、密码哈希等设置项（M1 冻结规格 S3/S 第二批）；经批准随 M1 第一批引入 · 2026-10-05
- **D024 界面重构：建立 token 层 + 共用组件库，分类色改 8 色颜料盘** · M1 功能齐了但观感停在 M3 默认值：分类色是哈希任意色相（首页头像彩虹、统计页紫/洋红/橙环），`Typography()` 全默认，无 Shapes/间距常量，卡片与背景只差 2% 亮度，品牌色当正文用（3.3:1 不达标）。定案：主色青绿不变（保身份），新增 `primaryInk` 作正文色；中性层重做（纸灰底 + 白卡 + 1dp 发丝线，全局不用阴影）；分类色改 8 色等彩度明度颜料盘（朱砂/赭石/藤黄/石绿/石青/靛青/紫檀/墨灰，头像 = 14% tint 底 + ink 首字），统计图表复用同一套；补 `error` 槽位顶掉 M3 默认紫。规范全文 `docs/界面重构方案.md`，本轮为 M1 后置重构（非跨里程碑功能扩张，规格外功能一律不进）· 2026-10-06
- **D025 inset 必须显式消费**：外层 Scaffold 的 padding 传给 NavHost 时用 `Modifier.padding(padding).consumeWindowInsets(padding)` · 普通 `Modifier.padding` 不标记 inset 已消费，内层 Scaffold/TopAppBar 会再加一遍状态栏/导航栏高度——真机上搜索页顶部出现 2× 状态栏的无内容死带（Robolectric 是零 inset 环境，截图看不出来）。此后新增下钻页一律用 `PennedTopBar`（自带 inset 处理）· 2026-10-06
- **D026 账户可增可改可删：删除=软删+撤销，但名下有流水的账户拒绝删除** · M1 的资产页只能加账户，账户条目没有任何删除入口（点不了/长按无反应/不能滑），误加只能删整个账本。定案：①删除走软删 + Snackbar「撤销」（与首页同一范式，10s）；②**名下有流水则拒绝**并提示条数——删了流水 `accountId` 会悬空，净资产凭空的少掉这笔钱而流水还留在列表与统计里，两端口径对不上（改账户归属或迁移流水留给 M3 资产模块）；③删除入口两处：行左滑、编辑页朱砂「删除账户」（有流水时置灰并把原因写在按钮下方，不做"点了才说不能删"的死按钮）；④添加账户由居中 `AlertDialog` 改为独立页面 `account?editId=`（兼作编辑页），顺带解决"弹窗内文本输入在 Robolectric 不 idle、无法截图测试"这个老坑；⑤类型仍是 5 个 `AccountType` 枚举 + 自由名称（每组末尾「自定义」格），**不加**头像颜色/图标列，避免 accounts 表迁移与 schema 变更。连带修掉：资产页分组标题按账户大类推导（`groupName` 从未写入，此前永远显示「默认分组」）、行副标题不再显示枚举名（此前「支付宝」那行写着"储蓄卡"）· 2026-10-06
- **D027 账户加「备注」与「生效账本」；金额全 App 加千分位** · 按参考图重做资产三页时用户点名的两行字段。①**备注**：`accounts.remark`，只作个人备忘、不参与任何计算，资产页行副标题显示。②**生效账本**：`accounts.scopeAllBooks`，`false`（= 迁移默认值，老账户行为不变）/`true`（每个账本都可见，**余额按跨账本流水合并**——同一个钱包在哪个账本看都是这个数）；**删账本不级联删共享账户**，否则它在别的账本的流水外键会悬空。只做二选一，不做"指定 A、B 两个账本"（要 join 表，留提案区）。两列在 DB v4 一次加完（`MIGRATION_3_4`，schema JSON 已更新、迁移测试覆盖默认值），并在真机数据上实测迁移（3 账户/754 流水完好）。③**千分位**：`Money.format` 默认输出 `3,500.00`（全 App 一致，大额更好扫读），**输入框回填一律 `grouped = false`**——光标与 `parseToCents` 都不吃逗号；CSV 导出走原始分不受影响 · 2026-10-07（"只做二选一、余额按可见账本合并"这两点已被 **D028 取代**：改为按账本多选，余额恒按账户全部流水合并）
- **D028 生效账本改为按账本多选（账户↔账本多对多）** · 用户建了第二个账本后在编辑账户页看不到它——二选一不够用。定案：①新表 `account_books(accountId, bookId, createdAt, updatedAt, deleted, deviceId)`，复合主键（沿用 `transaction_tags` 连接表先例 + D003 的软删/时间戳），`MIGRATION_4_5` + schema `5.json` + 迁移测试；②**所属账本（`AccountEntity.bookId`）恒定可见、不进这张表**，也不允许取消勾选（把它移出所属账本 = 迁移账户归属，是另一件事）；③可见性 = `scopeAllBooks = 1` 或 `bookId = 当前账本` 或 `account_books` 里有勾选且未删；④**余额口径推广为"初始余额 + 该账户名下全部流水"，与可见账本无关**——账户是同一个钱包，余额不该随可见性开关变化（D027 只对"全部账本"这么算，现在一律这么算；未共享的账户流水本来只在本账本，行为不变）；⑤保存引用先整批软删再 upsert 勾选的（取消勾选即收窄，幂等）；删账本时级联软删指向它的引用；⑥UI：`生效账本` 行摘要显示"全部账本"或列出生效账本名，抽屉里是「全部账本」开关 + 逐账本勾选（所属项置灰带「所属」标记）· 2026-10-07
