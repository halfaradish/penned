# 任务看板

> 会话间进度的唯一真相。任务模板见 `docs/开发工作流.md` 第四节 C。

## 进行中

（无——M1 第二批已完成并提交，见"已完成"）

## 待办

- **P0 修复：编辑流水丢字段（2026-10-06 复查实证）**——AddViewModel.saveInternal 未从 original 复制 `refundCents/discountCents/excludeFromStats/excludeFromBudget/importBatchId/reimburse*`，编辑任意流水（哪怕只改备注）会把它们重置为默认值。实证：退款 2000→0、优惠 100→0、两个开关 true→false、批次归属 batch-x→null。影响：导入的 7 条退款流水编辑后退款归零；详情页开关一编辑就复位；编辑后流水脱离导入批次（批次计数失真、按批次删除漏删）。修复：saveInternal 两个分支补复制未编辑字段 + Robolectric 回归测试（编辑只改备注，其余字段不变）
- **M1 验收缺项：Roborazzi 截图「记一笔转账/资产页」**（specs/M1.md 第一批验收标准明确列出）——当前 8 张 M1 截图覆盖记一笔支出/日期/分类、我的、数据管理、批次、预算、分类、统计、锁屏、深色 8 页，但没有转账 Tab 与资产页。补 2 个截图测试即可
- **M1 收尾**：账单详情「退款」流程（specs/M1.md 第二批最后一项；第一批 S6 只写了"退款占位（M3）"，实际 DetailScreen 连占位都没有）。当前导入数据里 7 条退款流水在 UI 上完全不可见（含 5 条全额退款 amount=0）。建议 M1 收尾做只读展示（退款/优惠行），交互留 M3
- 记一笔顶栏「编辑」是 disabled 死 UI（占位注释还写着"M1 第二批：分类管理"，该功能已做但未接通）；「不报销」chip 同为占位（M3）——接通分类管理或移除
- 日历 Tab 占位文案 "M1 第二批" 已过时：日历页未分配任何冻结里程碑（总方案 M0~M6 未列），需在 M2 规格时明确归属
- 首页流水行的转账记录显示「未分类？」——应显示「转账 → 账户」样式（polish，改 HomeGroups/HomeScreen 行渲染）
- 添加账户弹窗：点预设 chip 会覆盖已输入的名称（小 UX，与一木对齐时可改为仅作 placeholder）
- Robolectric 怪癖：AlertDialog 内含文本输入时 Compose 不 idle（分类新增弹窗无法截图测试，入口仅做存在性断言；PinPad/Budget 等用自绘输入绕开）。后续可试 Robolectric 升级或改用 compose ui-test 的 autoAdvance
- M1 引入 Roborazzi gradle 插件，把"每次测试都重录截图"改为 record/verify 双任务（D018）
- 升级 robolectric >4.17 时回归试 `@Config(sdk=[36])`（D016）
- **M2 规格**：update.json 协议 + UpdateChecker + 签名密钥生成与离线备份（**首次发版前 applicationId 终审 D013**）+ release minify 打开（D012）
- M5 提案候选：CSV 目前是「当前账本流水级备份」（带 id），全实体（账本/分类/账户）无损备份留给离线备份设计时统一考虑

## 复查结论（2026-10-06）

- **M0：完成**。验收 6/6（APK、单测全绿、1.json 入库、DAO 测试覆盖记账→汇总、Roborazzi PNG、文档三件套）
- **M1：功能面 17/18 项完成**（第一批 S1~S6 全部 + 第二批 8 项），未完成/未落实 3 项：
  1. 账单详情「退款」流程（第二批清单最后一项，未做，含只读展示）
  2. 第一批验收标准「Roborazzi 截图（记一笔转账/资产页）」未落实
  3. **编辑丢字段 P0 bug**（复查实证，见待办首条）
- 本轮复查复测通过：详情页两个开关落库、转账手续费落库（feeCents=300）、账本切换数据隔离（Bbook 空 → 切回数据回归）、余额联动（冒烟时 净资产=总资产-负资产）
- 修复建议顺序：编辑丢字段（P0，动钱数据）→ 转账/资产截图 → 退款只读展示 → 死 UI 清理

## 提案区（等人裁决）

（无）

## 已完成

### M1 第二批（2026-10-06，提交 8b95392~f671308）
- **冒烟回归**：修复 2 个真 bug——currentBookId 从未初始化导致添加账户静默失败（启动时落盘默认账本）；添加账户弹窗内容超高且键盘盖住保存键（verticalScroll）
- **2.1 一木 xls 导入器 + CSV 导入导出**（3d72c13）：纯 Kotlin OLE2/BIFF8 解析器（无新依赖，FAT/MiniFAT 双路径、SST CONTINUE 跨片）；语义层（负号还原/退款/优惠/账户可空/分钟日期/坏行跳过）；分类账本账户按名匹配缺则创建；事务写入 source=IMPORT + importBatchId（schema v3 + 2→3 迁移测试）；RFC4180 CSV 编解码 + penned 备份格式；我的页/数据管理页（SAF）/导入批次管理页；**真实 751 行账单导入验证通过**（750 支出/1 收入/5 全额退款/7 退款行）；脱敏 fixture×2（FAT/MiniFAT）入库
- **2.2 预算 UI**（ceef93b）：BudgetDao（一账本一分类一预算）+ 月度分类支出聚合查询（排除不计预算）+ BudgetMath 纯函数（表驱动单测）+ 预算管理页（总/分类、进度条、超支红显）+ 首页「剩余预算」行接真数据
- **2.3 日期选择**（9fd12fb）：「今天」chip 接 M3 DatePickerDialog；AddDate 纯函数（选日+当前时刻落库 minute、编辑反推、UTC 毫秒换算，表驱动单测）；编辑预填
- **2.4 分类管理**（dc914db）：分类管理页（两域树形列表、新增一级/子级、重命名、软删）+ 记一笔宫格「+ 新增」与子分类条带「+ 新增」接通并自动选中
- **2.5 密码锁**（84708f2）：PinHasher（SHA-256(salt+pin) 存 salt:hash 无明文、常数时间比较）+ LockGate（冷启动锁定、后台恢复重锁、会话中设置不打断）+ 设置页（设置/改密/关闭）；修复步骤切换 PIN 缓冲未清空 bug
- **2.6 深色主题**（0a3c6e2）：Light/Dark 全槽位色板（青灰偏中性色，替代 M3 默认紫灰）；expenseColor/incomeColor 暗色提亮变体全调用点替换；8 页暗色 Roborazzi 截图 + 系统深色模式实测
- **2.7 统计页第一版**（f671308）：HomeGroups.categoryTotals 纯函数（topN+其他合并、fraction 归一）；月窗口查询；月切换 ‹ ›；月汇总卡 + Canvas 环形图 + 分类排行（占比条）；空态；**真实验证 2024-09 支出 2955.70 与库一致**
- 验证：`./gradlew build` 全绿（单测 + lint + Roborazzi）；每个切片独立提交；关键流程模拟机人工走查

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
