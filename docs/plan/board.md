# 任务看板

> 会话间进度的唯一真相。任务模板见 `docs/开发工作流.md` 第四节 C。

## 进行中

（无——M1 第二批已完成并提交，见"已完成"）

## 待办

- 记一笔顶栏「编辑」是 disabled 死 UI（占位注释还写着"M1 第二批：分类管理"，该功能已做但未接通）；「不报销」chip 同为占位（M3）——接通分类管理或移除
- 日历 Tab 占位文案 "M1 第二批" 已过时：日历页未分配任何冻结里程碑（总方案 M0~M6 未列），需在 M2 规格时明确归属
- 首页流水行的转账记录显示「未分类？」——应显示「转账 → 账户」样式（polish，改 HomeGroups/HomeScreen 行渲染）
- 首页流水行对全额退款流水显示 "-0.00"——可加「退款」小标记（详情页已有只读展示，polish）
- 资产页账户行第二行显示英文枚举（DEBIT/CASH/CREDIT）——应显示中文类型名（polish，AccountType 加 label 映射）
- 添加账户弹窗：点预设 chip 会覆盖已输入的名称（小 UX，与一木对齐时可改为仅作 placeholder）
- Robolectric 怪癖：AlertDialog 内含文本输入时 Compose 不 idle（分类新增弹窗、资产页添加账户弹窗都无法截图测试，入口仅做存在性断言；PinPad/Budget 等用自绘输入绕开）。后续可试 Robolectric 升级或改用 compose ui-test 的 autoAdvance
- M1 引入 Roborazzi gradle 插件，把"每次测试都重录截图"改为 record/verify 双任务（D018）
- 升级 robolectric >4.17 时回归试 `@Config(sdk=[36])`（D016）
- **M2 规格**：update.json 协议 + UpdateChecker + 签名密钥生成与离线备份（**首次发版前 applicationId 终审 D013**）+ release minify 打开（D012）
- M5 提案候选：CSV 目前是「当前账本流水级备份」（带 id），全实体（账本/分类/账户）无损备份留给离线备份设计时统一考虑

## 复查结论（2026-10-06）

- **M0：完成**。验收 6/6（APK、单测全绿、1.json 入库、DAO 测试覆盖记账→汇总、Roborazzi PNG、文档三件套）
- **M1：复查时 17/18，收尾后 18/18**。复查列的 3 项缺项 + 用户反馈的 1 项（键盘加减乘除）已全部补上（本轮四次提交）：
  1. ~~编辑丢字段 P0 bug~~ → 已修（TxEdit 白名单合并 + 回归测试，模拟机端到端复验通过）
  2. ~~第一批验收标准「Roborazzi 截图（记一笔转账/资产页）」~~ → 已补 5 张
  3. ~~账单详情「退款」流程~~ → 已做只读展示（全额/部分退款标记 + 退款/优惠行，真实导入数据模拟机复验通过）
  4. ~~键盘只有 `+ −`~~ → 已补 `× ÷`（5 列运算块，引擎早就支持，是键位缺口）
- 收尾期间顺带修掉 2 个同族 bug：编辑时改日期不生效、`AddDate.epochMinuteFor` 选日期分支的时区编码错误（详见"已完成"）
- 复查复测通过：详情页两个开关落库、转账手续费落库（feeCents=300）、账本切换数据隔离（Bbook 空 → 切回数据回归）、余额联动（冒烟时 净资产=总资产-负资产）

## 提案区（等人裁决）

（无）

## 已完成

### M1 收尾 4/4：记账键盘加减乘除（2026-10-06）
- **问题**：`MoneyExpression` 引擎（乘除优先/分为单位/除零保护）在 M1 第一批就做了，但键盘只放了 `+ −`——×÷ 键位当时留了句"后续并入"，结果用户侧"加减乘除"只到加减
- 键盘重排为 **5 列**（对标一木 `03_记一笔_计算器`）：数字占 3 列，右侧 2 格运算块——第 1 行 `⌫` 占两格、第 2 行 `+ −`、第 3 行 `× ÷`、第 4 行动作键占两格；`Key(label, span, operatorBlock)` 描述键位，运算键用灰字形区分（页面底色就是 surfaceVariant，键不能再用它，否则看不出是键）
- 动作键三态补一档：表达式待算时 `=` 也用 primary 绿底（一木同款），不再是白底
- 键位加 `testTag("amount-key-<label>")`：`+`/`-` 的文本会与分类宫格「＋ 新增」撞车，测试改为按 tag 定位
- 测试：`AddScreenScreenshotTest` 新增 3 个——`captureCalculatorMultiplyDivide`（12×3÷4 → 表达式原样显示 → `=` → 9.00 → 键回「完成」，出图 `m1_add_calculator.png`）、`calculatorPrecedenceMultiplyFirst`（2+3×4=14.00）、`calculatorDivideByZeroKeepsExpression`（8÷0 求值返回 null → 表达式保持、仍显示 `=`）；键盘与引擎按 AddViewModel 的接线接起来测，等于覆盖 UI→引擎全链路
- 模拟机实测：选分类 → `12 × 3 ÷ 4` → 金额区显示表达式、右下键变 `=` → 按 `=` 得 9.00 → 完成 → DB `amountCents=900, type=EXPENSE, source=MANUAL`，日期为设备当日真 epoch 分钟；验证行已删
- 验证：`./gradlew build` 全绿；浅色/深色键盘截图人工过目

### M1 收尾 3/3：账单详情「退款」只读展示（2026-10-06）
- 详情页新增：退款标记（`已全额退款` / `部分退款`，全额 = amount 为 0 且退款 > 0）+ 「退款」「优惠」两行只读金额（>0 才显示）；交互（新增/编辑退款）留 M3
- `DetailScreen` 抽出无状态 `DetailContent(detail, …)`，新增 `DetailScreenScreenshotTest` 3 张截图（全额退款 / 部分退款+优惠 / 普通流水无退款行）+ 断言
- 语义依据 `docs/local/一木导出反分析.md`：退款是流水属性而非独立流水；全额退款行金额置 0、退款列记退款额
- 真实数据模拟机复验：复制真实导入的全额退款行（遮光布 amount 0 / refund 14.54 / IMPORT）→ 详情页显示「已全额退款 -0.00 / 退款 14.54 / 记录方式 账单导入」；验证后已删除临时行（库内 7 条退款流水原样）
- 验证：`./gradlew build` 全绿

### M1 收尾 2/3：转账 Tab / 资产页 Roborazzi 截图（2026-10-06）
- 补齐 M1 第一批验收标准缺的 2 页截图（共 5 张）：`m1_add_transfer.png`（支付宝→储蓄卡 + 手续费）、`m1_add_transfer_empty.png`（两个账户位 + ⇅ 互换键）、`m1_add_transfer_picker.png`（账户选择弹窗）、`m1_assets_screen.png`（净资产/总资产/负资产 + 分组 + 负数红显）、`m1_assets_empty.png`
- `AssetsScreen` 抽出无状态 `AssetsContent(state, onAddAccount)`（截图测试与预览不碰 DB/DataStore），`AssetsScreen` 只做 ViewModel 接线
- 断言：转账面板四要素（转出/转入/⇅/手续费）、选择弹窗回传账户 id、资产汇总数字与分组标题
- 资产页添加账户弹窗无法截图（AlertDialog + OutlinedTextField 在 Robolectric 不 idle，同已知怪癖）；截图产物人工过目通过

### M1 收尾 1/3：编辑丢字段 P0 + 日期时区 bug（2026-10-06）
- **P0 编辑丢字段**：抽出纯函数 `TxEdit`（`applyEdit` = 原记录 copy 覆盖「记一笔页能改的字段」白名单；`newTransaction` = 手记默认值），`AddViewModel.saveInternal` 两个分支改为调用它。退款/优惠/不计收支/不计预算/导入批次/报销/币种/汇率/地址/来源/创建者/软删全部原样保留
- 回归测试：`TxEditTest`（纯单测，含**反射白名单守卫**——applyEdit 改动的字段集必须恰好等于白名单，以后漏字段会红）+ `TxEditPersistenceTest`（Robolectric 落库往返，含 @ColumnInfo(defaultValue) 列）+ 批次归属断言
- **顺带修掉两个同族 bug**（复查时在 saveInternal/AddDate 里发现）：
  1. 编辑时改日期不生效（saveInternal 用 `original?.dateEpochMinute ?: ...` 把用户选的日期丢了）→ `AddDate.epochMinuteForEdit`：日期没动原值原样、换日期只挪天保留原时分
  2. **`AddDate.epochMinuteFor` 选日期分支是"墙钟编码"（epochDay×1440+时分），与导入器/HomeGroups/统计/详情页的"真 epoch 分钟"约定不一致**——UTC+8 下选日期记的账会偏 8 小时、落到隔壁日期（如 9月2日 16:38 存成 9月3日 00:38）。改为 `LocalDate.atTime(时分).atZone(zone)` 真 epoch 分钟，并补 24 小时×2 分钟的全天候往返不变量测试（旧测试只覆盖 23:30 一个点，正好躲过）
- 验证：`./gradlew build` 全绿（含 lint）；模拟机端到端走查见下条

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
