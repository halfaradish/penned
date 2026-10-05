# 任务看板

> 会话间进度的唯一真相。任务模板见 `docs/开发工作流.md` 第四节 C。

## 进行中

（无——M1 第一批已完成并提交，见"已完成"）

## 待办

- **M1 规格冻结**（specs/M1.md）：流水编辑/删除（账单详情页 + 批量编辑，参考 `docs/local/一木模拟机探索/探索报告.md` §二）、账户管理与转账（含手续费 D022）、预算表、CSV 导入导出（一木 xls 导入器 + 导入批次）、密码锁、深色主题、账本管理（popover 切换，探索报告 §七）、转账/债务 Tab、快捷 chips、宫格内新增分类、计算器键盘、自绘线条图标集评估
- **M2 规格**：update.json 协议 + UpdateChecker + 签名密钥生成与离线备份（**首次发版前 applicationId 终审 D013**）+ release minify 打开（D012）
- 升级 robolectric >4.17 时回归试 `@Config(sdk=[36])`（D016）
- M1 引入 Roborazzi gradle 插件，把"每次测试都重录截图"改为 record/verify 双任务（D018）

## 提案区（等人裁决）

（无）

## 已完成

### M1 第一批（2026-10-05，提交 6f2602d~7332724）
- S1 schema v2（D020~D022 字段 + budgets/import_batches + 迁移测试，2.json 入库）
- S2 计算器表达式求值（乘除优先/分为单位/除零保护，表驱动单测）
- S3 DataStore 设置（D023）+ 账本 popover + 管理页 + 首页按账本过滤
- S4 资产页：账户分组列表/分组预设添加账户/净资产与报销汇总重算
- S5 记一笔 v2：转账 Tab（转出/转入/互换/手续费）、账户 chip、+− 表达式键盘、三态完成键
- S6 账单详情（不计收支/不计预算开关）、编辑复用记一笔、软删除、长按批量删除
- 验证：./gradlew build 全绿；Roborazzi 断言+截图验收

### M0.5 记一笔页重设计（2026-10-05）
- 顶栏返回箭头（P0 修复）+ 支出/收入胶囊 Tab + 编辑占位；分类卡片 5 列、一级行内展开子分类条带（含 ··· 徽标与"+ 新增"占位）；中性圆底+首字（选中青绿反白）；信息条 = 备注 + 实时金额同行 + 今天/无账户 chips；键盘重排（再记/完成，取消走顶栏）；主题 surfaceVariant 中性化
- 验证：`./gradlew build` 全绿；Roborazzi 断言（返回/子分类展开/再记/完成）+ 截图 `m05_add_screen.png` 人工验收通过
- 涉及：AddViewModel（expandedParentId/selectParent/selectSub/saveAgain）、CategoryPanel（新）、AmountKeyboard、AddTransactionScreen（重构）、PennedIcons.Back、Theme；删除 CategoryGrid

### M0 地基（2026-10-05，一个会话完成 T1~T8）
- **T1 记忆系统**：AGENTS.md、decisions.md（D001~D019）、specs/M0.md、board.md ✓
- **T2 环境与版本冻结**：JDK21 + SDK(35/36) + Gradle 9.4.1；镜像方案；版本目录定稿（D010/D010a）✓
- **T3 Gradle 骨架**：wrapper 9.4.1（腾讯镜像分发）、settings（PREFER_SETTINGS 兼容本机全局 init 脚本）、app 模块 ✓
- **T4 Room schema v1**：7 表（books/accounts/categories/transactions/tags/transaction_tags/attachments），同步字段全表预留，种子"日常账本"+ 8 支出组 + 5 收入类；`app/schemas/…/1.json` 已导出入库 ✓
- **T5 UI 骨架**：底部 5 Tab（日历/资产/统计/我的为占位页）+ 首页（月汇总卡、日分组流水、空态）+ 记账页（支出/收入切换、分类宫格、备注、金额键盘 00/⌫/C/保存）+ 5 个自绘 Tab 图标 ✓
- **T6 测试基线**：16 个单测全绿——Money/MoneyInput 表驱动、HomeGroups 纯函数（日分组/月汇总/转账不计收支）、PennedDatabaseTest（Robolectric：种子幂等、插入→查询→软删、排序 join）、Roborazzi 截图（400x800 记账页 PNG）✓
- **T7 构建门**：`./gradlew build` 全绿；APK `app/build/outputs/apk/debug/app-debug.apk`（11.9MB debug 未压缩）✓
- **T8 看板与决策沉淀**：本文件 + D016~D019 ✓

### M0 踩坑记录（已沉淀为决策，细节见 decisions.md）
- Compose BOM 2026.09 → SDK37/AGP9.1 连锁不可用，降 2025.11.01（D010a）
- Robolectric SDK36 FileDescriptor 崩溃 → 测试固定 sdk 35（D016）
- `Color.hsl` hue 单位是度——传 0~1 分数导致全部红色（D018）
