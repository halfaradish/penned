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
- **D019 Tab 图标手写 ImageVector，分类图标 = 哈希色块 + 首字**（黄金角散列色相）· 正式图标设计推迟到 M6 打磨 · 2026-10-05
- **D020 流水增加 excludeFromStats / excludeFromBudget 两个独立标志** · 一木账单详情页实测（模拟机探索报告 §二）；"不计入收支"与"不计入预算"是分开的开关 · 2026-10-05
- **D021 流水增加 source 来源字段**（MANUAL/VOICE/TEXT/SCREENSHOT/IMPORT/AUTO…）· 对应一木"记录方式"；统计"记录构成"与导入批次追溯都要用 · 2026-10-05
- **D022 转账增加 feeCents 手续费字段** · 一木转账 Tab 有"手续费"chip（模拟机探索 §一）；手续费记在转账流水上 · 2026-10-05
- **D023 引入 androidx.datastore-preferences 1.2.1** · 存当前账本 id、密码哈希等设置项（M1 冻结规格 S3/S 第二批）；经批准随 M1 第一批引入 · 2026-10-05
