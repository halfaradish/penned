# 任务看板

> 会话间进度的唯一真相。任务模板见 `docs/开发工作流.md` 第四节 C。

## 进行中

（无——按参考图重做资产三页（AD-1~AD-6）已完成并提交，见"已完成"）

## 待办

- ~~首页冷启动首帧会闪一下"还没有记账"空态~~ → **已修**（UI-8：`rows` 改可空，读到账本前不渲染空态；模拟机复验首帧只显示"日常账本"）
- 记一笔顶栏「编辑」已接通分类管理（UI-4）；「不报销」chip 改只读禁用样式（如实表达"新记为不报销"，可编辑留 M3）
- 一木 xls 导入器不解析 地址/多币种/报销 列（真实 751 行文件这些列全空，无数据损失）——等真出现带这些列的文件再补解析（报销列还涉及 M3 的报销语义）
- ~~日历 Tab 占位文案 "M1 第二批" 已过时~~ → **已改**（UI-6：改为"日历视图还没做，先在首页按日回看流水"）；日历页归属仍待 M2 规格明确
- 首页流水行对全额退款流水显示 "-0.00"——可加「退款」小标记（详情页已有只读展示，polish）
- ~~添加账户弹窗：点预设 chip 会覆盖已输入的名称~~ → **已修**（UI-5：预设只记类型，名称留作用户输入/默认显示值）
- Robolectric 怪癖：AlertDialog 内含文本输入时 Compose 不 idle（分类新增/重命名、预算金额、账本命名这些弹窗都无法截图测试，入口仅做存在性断言）。**名单选择类已改底部抽屉且可正常 idle/截图**（`PennedSheetTest` 已验证）；**资产页添加账户已改独立页面并可正常截图**（AC-2，13 张新基线）；只剩"纯文本输入弹窗"这几种，后续可试 Robolectric 升级或改用 compose ui-test 的 autoAdvance
- 界面重构新增待办：`IconGalleryTest`/`PennedSheetTest` 是人工评审用截图（非回归基线），M6 打磨图标时可复用
- M1 引入 Roborazzi gradle 插件，把"每次测试都重录截图"改为 record/verify 双任务（D018）
- 升级 robolectric >4.17 时回归试 `@Config(sdk=[36])`（D016）
- **M2 规格**：update.json 协议 + UpdateChecker + 签名密钥生成与离线备份（**首次发版前 applicationId 终审 D013**）+ release minify 打开（D012）
- M5 提案候选：CSV 目前是「当前账本流水级备份」（带 id），全实体（账本/分类/账户）无损备份留给离线备份设计时统一考虑

## 复查结论（2026-10-06）

### 第三次复查：M1 漏项审计（找"引擎/后端做了但入口没接"同型缺口）

按 ×÷ 那一类（规格列了/字段建了/引擎写了，但用户用不到或行为不对）逐条核对 M1 规格 S1~S6 + 第二批清单 + D020~D023，结论：**又找到 2 个真缺口 + 3 处死 UI + 若干覆盖度不足**，已列待办首条起：

1. **「不计入收支」两向都错**（P0，统计口径 + 资产口径同时错）——见待办首条，含模拟机实证
2. **账本删除（软删）没做**（S3 明写，实现漏了）
3. 死 UI：首页「搜索」、我的「关于」、资产页账户行（`clickable {}`）
4. 文案/行为不一致：分类管理副标题写"隐藏"实际是软删；`CategoryEntity.hidden` 死字段
5. 详情页「全字段展示」缺 address/币种/报销；导入器不解析这些列（真实数据为空，无损失）
6. 深色逐页自检只覆盖 8 页；批次页无明细钻取

已核对**没问题**的：S1 schema v2/迁移、S2 计算器（含新补的 ×÷ 键位）、S3 账本新增/重命名/切换与数据隔离、S4 净资产/分组/预设添加账户/报销重算、S5 转账（含手续费）/账户 chip、S6 详情/编辑/删除/批量删除/两个开关落库、第二批 9 项（预算含"不计入预算"过滤、CSV 往返、xls 导入、批次删除、密码锁、深色、分类管理、日期 chip、统计页）、D021 source、D022 feeCents、D023 DataStore

### 第二次复查（M1 收尾前）

- **M0：完成**。验收 6/6（APK、单测全绿、1.json 入库、DAO 测试覆盖记账→汇总、Roborazzi PNG、文档三件套）
- **M1：复查时 17/18，收尾后 18/18**。复查列的 3 项缺项 + 用户反馈的 1 项（键盘加减乘除）已全部补上（本轮四次提交）：
  1. ~~编辑丢字段 P0 bug~~ → 已修（TxEdit 白名单合并 + 回归测试，模拟机端到端复验通过）
  2. ~~第一批验收标准「Roborazzi 截图（记一笔转账/资产页）」~~ → 已补 5 张
  3. ~~账单详情「退款」流程~~ → 已做只读展示（全额/部分退款标记 + 退款/优惠行，真实导入数据模拟机复验通过）
  4. ~~键盘只有 `+ −`~~ → 已补 `× ÷`（5 列运算块，引擎早就支持，是键位缺口）
- 收尾期间顺带修掉 2 个同族 bug：编辑时改日期不生效、`AddDate.epochMinuteFor` 选日期分支的时区编码错误（详见"已完成"）
- 复查复测通过：详情页两个开关落库、转账手续费落库（feeCents=300）、账本切换数据隔离（Bbook 空 → 切回数据回归）、余额联动（冒烟时 净资产=总资产-负资产）

## 提案区（等人裁决）

（无——界面重构与账户管理两个提案均已批准并完成，见"已完成"）

### 账户管理（2026-10-06，**已批准**）

- 提案：资产页只加不减——加错账户只能删整个账本；新增账户是一张竖排弹窗且截不了图
- 用户裁决（2026-10-06）：①自定义范围 = 每组加「自定义」格 + 自由名称（**不做**自选头像颜色/图标，避免 DB 迁移，也**不做**新建大类）②删除语义 = 名下有流水禁止删除并提示 ③账户行除左滑删除外，点进还能编辑 ④承载形式 = 独立页面
- 落地：D026；实施见"已完成"

### 按参考图重做资产三页（2026-10-07，**已批准**）

- 提案：用户给三张一木资产页截图（`docs/local/一木记账部分界面/资产/`），要求按图重做资产页/添加账户/表单页
- 用户裁决（2026-10-07）：①表单做 **名称 / 余额 / 所属分组 / 备注 / 生效账本** 五行 ②保存按钮留在**底部**（不用参考图的顶栏 ✓）③千分位**全局**加（`3,500.00`）
- 落地：D027；实施见"已完成"。参考图只借结构，不抄品牌图标与插画

### 后续提案候选（本轮明确不做）

- 账户停用/归档、把流水迁移到另一个账户（有流水的账户目前删不掉，出路在 M3 资产模块）
- 新建账户大类（充值账户/二手货物…）、自选头像颜色与图标（要扩枚举或加 accounts 列）
- 参考图里我们没做的表单行：简称、卡号、账户图标/夜间图标、收益同步、计入总资产（各自都要新列或新语义；「计入总资产」还牵动 D004 的重算口径）
- 生效账本只做了二选一（仅本账本 / 全部账本）；「指定 A、B 两个账本」要 join 表
- 初始余额不支持负数（信贷/负债的存量欠款暂时只能记 0）；要支持得先定"欠款怎么输入"的交互或键盘方案

## 已完成

### 按参考图重做资产三页（2026-10-07，6 个切片）

- **背景**：AC 轮做完后用户仍不满意，给出三张一木资产页截图（资产页 / 添加账户 / 新增账户表单）要求按图重做
- **用户裁决**：表单做 名称/余额/所属分组/备注/生效账本 五行；保存留底部（不用参考图顶栏 ✓）；千分位全局加。见提案区
- 切片与提交（每片 `./gradlew build` 全绿后独立提交）：
  - **AD-1** `082e67f` 金额千分位：`Money.format(cents, grouped = true)` 默认 `3,500.00`，输入框回填 `grouped = false`（`AddViewModel`/预算/账户表单三处），`MoneyTest` 补表驱动用例；CSV 导出走原始分不受影响
  - **AD-2** `f5e03bd` 资产页重做：整页 `LazyColumn` 滚动、浅青 hero（`primaryContainer`）+ 眼睛藏金额（自绘 `Eye`/`EyeOff`）、三张独立汇总卡、分组卡（组名 + 组小计 + 折叠箭头）、行金额正青绿负朱砂、60dp 圆形 FAB
  - **AD-3** `d692319` 添加账户类型抽屉 `AccountTypeSheet`（四个大类一次铺开 + 每组 5 列图标格 + 每组末尾「自定义」格）+ 路由 `account?editId=&group=&preset=` + 表单改两张卡片
  - **AD-4** `caa43ea` DB v4：`accounts` 加 `remark`/`scopeAllBooks` + `MIGRATION_3_4` + schema `4.json` + 迁移测试
  - **AD-5** `d6d2dd8` 生效账本语义：共享账户在每个账本可见、余额跨账本合并；`softDeleteByBook` 不动共享账户；表单补「备注」「生效账本」两行；资产页行显示备注副标题
  - **AD-6** 文档（第十二节重写 / D027 / 看板）+ 模拟机验证
- **测试**：`MoneyTest` +1；`PennedDatabaseTest` +1（共享账户跨账本可见 + 余额合并 + 删账本不动共享账户）；`MigrationTest` +1（3→4 保数据、新列默认值）；新增 `AccountTypeSheetTest`（3 条：分组/预设格齐全、点预设回报、点自定义回报）；`AccountEditScreenScreenshotTest` 改为 5 条（含生效账本说明行）；`AssetsScreenScreenshotTest` 增折叠与藏金额交互断言
- **模拟机实测**：真机数据上 v3→v4 迁移通过（3 账户/754 流水完好）→ 新资产页（眼睛/折叠/组小计）→ 类型抽屉 → 自定义进表单（自动聚焦）→ 五行填完保存落库 → **切到第二个账本验证共享账户可见且带备注** → 编辑页预填 → 页面内删除 → 浅/深色；截图 `docs/local/界面重构验证/ad_*.png`（不入库）。验完设备数据已还原（3 账户/754 流水/浅色/第二个账本恢复软删）
- **遗留（提案区候选）**：简称/卡号/账户图标/收益同步/计入总资产、「指定某几个账本」、账户归档与流水迁移、初始余额负数

### 账户可增可改可删 + 「添加账户」重做（2026-10-06，4 个切片）

> 版面（资产页/添加账户/表单）已在 2026-10-07 的 AD 轮按参考图重做，本条只作删除链路与当时版面的记录

- **用户报的三点缺陷**：①资产页账户无法删除——条目本身没有删除入口（点不了、长按没反应、不能滑），唯一删除途径是删整个账本（会连带清空流水，不可接受）②新增「+」无法自定义（对标一木：每组末尾有「自定义」格）③新增账户界面排版难看（`AlertDialog` + 8 个竖排文字 chip + 两个白框输入，一行一个控件）
- **用户裁决（2026-10-06）**：自定义只做到"每组「自定义」格 + 自由名称"（不做自选颜色/图标、不做新建大类）；**有流水的账户禁止删除并提示**；账户行除左滑删除外点进还能编辑；添加账户改独立页面。见提案区条目
- 切片与提交（每片 `./gradlew build` 全绿后独立提交）：
  - **AC-1** `edb3aea` 删除链路：`AccountDao.softDelete(id)` / `restoreAll(ids)`、`TransactionDao.countByAccount`（转入转出两腿都算）、`AssetsViewModel` 删除/撤销/提示、`AccountOps.deleteAccountIfEmpty`（左滑与编辑页共用的唯一删除入口）
  - **AC-2** `9f2bfab` 新增/编辑账户页 `AccountEditScreen`：类型分段 + 4 列图标网格 + 每组「自定义」格 + 行式表单（名称/初始余额）+ 底部保存与朱砂删除；路由 `account?editId=`；旧 `AddAccountDialog` 整个删掉
  - **AC-3** `7f2981d` 资产页列表：分组标题按账户大类推导、行去副标题、左滑删除 + 撤销（10s）、点行进编辑
  - **AC-4** 文档（`界面重构方案.md` 第十二节 / D026 / 看板）+ 模拟机验证
- **测试**：`PennedDatabaseTest` +2（账户软删后净资产少一份、撤销复原；`countByAccount` 覆盖转出/转入且忽略软删）；新增 `AssetsDeleteTest`（真库端到端：左滑软删 + 净资产联动 + 撤销复原、有流水拦截、点行打开编辑）；新增 `AccountEditScreenScreenshotTest`（4 张浅色 + 1 张深色）；改 `AssetsScreenScreenshotTest` 与 `DarkThemeScreensTest`
- **模拟机实测**：新增（预设格/自定义格两条路径）→ 编辑页预填 → 页面内删除（确认弹窗）→ 左滑删除 + Snackbar 撤销 → 有流水拦截提示 → 浅/深色，全部通过；截图 `docs/local/界面重构验证/ac_01..ac_12_*.png`（不入库）。验完设备数据已还原（3 个账户 / 754 条流水 / 浅色）
- **遗留（提案区候选）**：账户停用归档与流水迁移、自选头像颜色/图标（要 accounts 加列 + DB v4 迁移）、新建大类、初始余额不支持负数

### 界面重构（2026-10-06，用户批准后一次做完，9 个切片）
- **背景**：M1 功能面已齐，但界面上限被三件事压住——主题层是 M3 默认值、分类色是哈希算出来的彩虹、inset 算了两遍；交互范式陈旧（全弹窗、零手势、无转场、文字字形当图标）
- **规范先行**：`docs/界面重构方案.md`（配色/字阶/圆角/间距/五条原则/逐屏要点）+ D024（token 层与颜料盘，取代 D019 的哈希色相）+ D025（inset 必须显式消费）
- **用户裁决**：精致化现有青绿体系（不改主色与 App 身份）；交互四项全上；首页记一笔入口保持现状；全量 15 屏一次做完
- 切片与提交（每个切片 `./gradlew build` 全绿后独立提交）：
  - **UI-0** 文档：方案 + D024/D025 + 看板提案
  - **UI-1** token 层：中性层重做（纸灰底 + 白卡 + 1dp 发丝线，全局不用阴影）、新增 `primaryInk`（品牌色当正文只有 3.3:1）、补齐 `error` 槽位（顶掉 M3 默认紫）、8 色颜料盘取代哈希彩虹（朱砂/赭石/藤黄/石绿/石青/靛青/绛紫/紫檀，转账与未分类走中性）、字阶（系统字族 + 全量 tnum）、圆角/间距常量、`values-night` 消冷启动白闪
  - **UI-2** 组件库 + 图标集 + inset 修复：`ui/components/` 14 个共用件（顶栏/卡片/分组卡/空态/头像/金额/日头/芯片/按钮/弹窗/抽屉/分段胶囊/左滑删除）；`PennedIcons` 改统一线图标（1.9 描边）并补 20 个，界面里不再有 `‹ › + ✓ ⇅ ⌫` 文字字形图标；**`NavHost` 加 `consumeWindowInsets(padding)` 修掉真机上搜索页顶部 2× 状态栏死带**（9 个下钻页同型问题一并修好）
  - **UI-3** 首页 + 搜索：页头图标化、卡片精修、**日头吸顶**、**行左滑删除 + Snackbar 撤销**（新增 `TransactionDao.restoreAll` 反软删）、搜索页改填充式输入框 + 进页自动聚焦 + 提示紧贴输入框（消灭中段空白）
  - **UI-4** 记一笔：分段胶囊 + **「编辑」接通分类管理**（消掉 board 待办里的死 UI）、子分类条带改 chip 流 + 展开动效、实时金额 `displaySmall`、chips 图标化、键盘新键面（白底发丝线 + 按压态 + 触感）与退格图标、账户选择改底部抽屉
  - **UI-5** 统计 + 资产 + 详情：环形图/占比条/头像共用颜料盘（修掉紫/洋红/橙环）、环心显示合计、排行条用分类自家颜料；净资产 hero + 账户按类型固定配色；详情改金额 hero + 分组卡（明细/记录信息/统计口径）
  - **UI-6** 其余 7 屏：我的改分组设置卡、账本/预算/分类/数据管理统一卡片与共用行、密码锁键盘换键面、日历占位页文案去掉过期里程碑
  - **UI-7** 转场与动效：Tab 淡入淡出、下钻页共享轴滑动、列表 `animateItem`、读系统"动画时长缩放"为 0 时全关（`ui/theme/Motion.kt`）
  - **UI-8** 模拟机验证 + 收尾：**顺带修掉冷启动首帧闪"还没有记账"空态**（board 待办，模拟机首帧实证）
- **测试**：`PennedSheetTest`（底部抽屉在 Robolectric 下可 idle 可截图——此前"弹窗选不中"的顾虑解除）、`HomeSwipeDeleteTest`（左滑→软删→撤销→行回列表，走真库端到端）、`IconGalleryTest`（图标总览）；断言从文字改 `contentDescription`（图标化后文字不再存在）
- **期间修掉的真 bug**：① 撤销后行被"复活的滑动状态"立刻再删一次（`rememberSwipeToDismissBoxState` 是 rememberSaveable，删除后再入列会带着已滑出状态）→ 改自建 `remember` 状态 + 滑出后 `snapTo` 归位；② `expenseColor()`/`incomeColor()` 读系统深色而非 `PennedTheme` 入参，深色截图测试里拿到的是浅色变体 → 改 `CompositionLocal`；③ 添加账户预设 chip 覆盖已输入名称
- **模拟机实测**（`docs/local/界面重构验证/`，不入库）：浅色逐页走查 + 深色首页/记一笔/详情 + **搜索页顶部死带确认消失** + 左滑删除与撤销（DB 侧核实 deleted 翻转与恢复）+ 底部抽屉 + 冷启动首帧；测试期间软删的行已原样恢复
- **不做**：不动 schema / 不加依赖 / 不改镜像；不删任何入口（日历 Tab、搜索入口保留）；不抄一木任何资源
- 验证：`./gradlew build` 全绿（单测 + lint + Roborazzi）


### 首页布局改造：合并卡随列表滚动 + 右下角 FAB（2026-10-06）
- 问题：月汇总卡 + 预算行 + "添加一条新记账"三块固定占顶部约 200dp，且不随内容滚动（列表只有剩余空间能滚）
- **月汇总卡 + 预算卡两张**（同款三列布局：标签一行 labelSmall 等宽居中 + 数值一行 titleSmall）：
  - 月汇总卡：`支出 / 收入 / 结余`（不可点）
  - 预算卡：`预算 / 已用 / 剩余`（剩余正常绿、超支红负数）+ 只多一条 4dp 进度条；整卡可点 → 预算管理；未设预算时该槽位是瘦卡"设置本月预算，控制花销 →"
  - 删除旧 `SummaryCell`/`BudgetSummaryRow`，新增 `HomeSummaryCard`/`HomeBudgetCard`/`SummaryColumn`；超支态截图 `m1_home_budget_overspent.png`
- **布局骨架**：固定区只剩标题栏（账本名 + 搜索）；两张卡、添加按钮改为 LazyColumn 的头部 item（`key = month-card` / `budget-card` / `add-button`），空态也从"整屏居中"改成列表项 —— 全部随内容滚走
- **右下角 FAB**：`AnimatedVisibility(fadeIn/fadeOut)` 包裹的绿色圆按钮（`containerColor = primary`，白 "+"），落在底部导航栏正上方；显隐靠 `derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.key == "add-button" } }`，即"添加一条新记账"滚出视野后接管；`totalItemsCount > 0` 防首帧闪现；多选模式不显示
- 测试：新增 `HomeScreenScreenshotTest`（首页首次有浅色截图）——`m1_home_top.png`（合并卡 + 无 FAB）、`m1_home_scrolled.png`（滚动后卡片/按钮消失 + FAB 出现 + 点 FAB 触发 onAdd）、多选模式无 FAB/卡片/按钮；`DarkThemeScreensTest.darkHome` 断言随文案更新（本月支出 → 支出）
- 模拟机实测：顶部（合并卡、按钮在、无 FAB）→ 上滑（卡片与按钮滚走、绿色 FAB 出现）→ 点 FAB 进入记一笔 → 长按进多选（FAB 消失）→ 退出多选仍在下滑位置（FAB 正确复现）
- 验证：`./gradlew build` 全绿

### M1 收尾 9/9：首页搜索（2026-10-06）
- 背景：第三次复查把首页「搜索」当死 UI 摘掉（理由"M1 规格未列"），用户指出这是要用的功能 → 按一木首页顶栏语义实现**真搜索**
- 范围：当前账本内，关键词按字面匹配 **备注 / 一级分类名 / 二级分类名**，数字关键词额外按**金额精确匹配**（"16" → 16.00 元）；结果按日分组（复用 `HomeGroups.groupByDay`，与首页同一套行渲染 `TransactionRow` 抽成共用文件）；点结果进账单详情
- 细节：LIKE 通配符转义（`%` `_` `\` 按字面，SQL 里配 `ESCAPE '\'`）——否则输入 `%` 会命中全部；输入框用 BasicTextField（Robolectric 可 idle）；带「清除」与三种状态（未输入/无匹配/结果 + "找到 N 条"）；上限 200 条
- 新增：`domain/SearchQuery`（纯函数）、`TransactionDao.observeSearchWithCategory`、`ui/home/SearchScreen.kt`（SearchContent 无状态入口）、首页入口 + `search` 路由
- 测试：`SearchQueryTest`（转义/金额解析/空白）、`PennedDatabaseTest`「search matches note category parent and amount within the book」（含账本隔离 + `%` 不命中全部）、`SearchScreenScreenshotTest`（结果/未输入/无匹配三张截图 + 真实键盘输入过滤 → 清除回初始态）
- 模拟机实测（真实 751 行数据）：搜 `10pro` → 6 条（小米10pro手机膜 -3.45 / 小米10pro -1028.00 …按日分组带收支小计）；搜金额 `1028` → 精确 1 条；搜 `14.54` → 0 条（真实数据里 14.54 是**退款**金额而非流水金额，行为符合"按金额搜流水"的语义）
- 组合筛选（日期范围/类型/账户/标签）仍留 M3
- 验证：`./gradlew build` 全绿

### M1 收尾 8/8（下）：批次明细钻取 + 行渲染 polish（2026-10-06）
- **导入批次明细页**（`data/batches/{batchId}`）：批次行可点进明细（文件名/条数/导入时间 + 该批流水清单，只读；空态"这批流水已全部删除"）。DAO `TransactionDao.observeByBatch` + `ImportBatchDao.observeById`（过滤 deleted）+ repository 暴露
- 首页转账行：由「未分类？」改为「转账 · 转入 X」（`TxWithCategory` 投影补 `targetAccountName`，`LEFT JOIN accounts`；图标位显示「转」）
- 资产页账户类型：DEBIT/CASH/CREDIT 英文枚举 → 中文（现金/储蓄卡/信用卡/理财/负债）
- 测试：`ImportBatchDetailScreenshotTest`（列表 + 空态）、`ImportRepositoryTest`「batch detail lists its own transactions and empties after delete」、`PennedDatabaseTest`「recent query resolves transfer target account name」、暗色首页断言转账行文案、资产页断言中文类型
- 模拟机实测：真实 751 行批次 → 明细页显示「751 条流水 · 导入于 2026-09-29 16:19」+ 真实流水行（北苑餐厅 10月4日 · 晚餐 -16.00 …）
- 验证：`./gradlew build` 全绿

### M1 收尾 8/8（中）：深色逐页自检补齐（2026-10-06）
- 暗色截图 8 → 14 页：新增 `dark_detail`（含全额退款标记）/`dark_stats`（环形图+排行）/`dark_assets`/`dark_add_transfer`/`dark_add_calculator`（表达式 + `=` 态）/`dark_books`
- 断言：全额退款标记、支出构成、净资产、手续费 chip、表达式与 `=` 键、当前账本

### M1 收尾 8/8（上）：详情页全字段 + CSV 无损备份补齐（2026-10-06）
- 详情页补 报销状态（待报销/已报销）、地点(address)、原币种+汇率（有值才显示），「全字段展示」名副其实
- `PennedCsv` 备份格式补 `currencyCode/rate/address` 三列（原缺 → 导出的"无损备份"会丢这三个字段）；导入按列名取值、缺列容忍，旧 CSV 仍可导入
- 测试：`CsvTest` 往返加 t4 行（币种/汇率/地址/报销全部保真）+ `DetailScreenScreenshotTest` 新增 `m1_detail_full_fields.png`
- 一木 xls 导入器仍不解析这三列（真实文件这些列为空；报销列涉及 M3 语义）——留待有真实样本再补

### M1 收尾 7/8：死 UI 与文案清理（2026-10-06）
- 首页顶栏「搜索」：纯文本、无 clickable、无页面 → 当时按"规格未列"移除，**用户指出该功能要用 → 已在 9/9 实现真搜索**（见下条）
- 我的页「关于」：`onClick = {}` 死行 → 改为不可点的 `InfoRow`（无涟漪无 ›，版本信息保留）
- 资产页账户行：`clickable {}` 空动作 → 去掉 clickable（账户详情/编辑不在 M1 规格内）
- 我的页「分类管理」副标题："隐藏"改为"删除"，与实现（软删）一致；`CategoryEntity.hidden` 保留给 M3 隐藏分类（不是死字段，是预留）
- 截图复核：`m1_mine_screen.png`（无 › 的关于行 + 新副标题）

### M1 收尾 6/8：账本删除（软删，M1 S3 缺项）（2026-10-06）
- **删除交互定案**：账本连同其流水/账户/分类/预算/导入批次**级联软删**（同一事务）；至少保留一个账本（只剩一个时删除键置灰）；删的是当前账本时先把当前账本切到别的本
- DAO：`BookDao.softDelete`、`TransactionDao.softDeleteByBook/countInBook`、`AccountDao/CategoryDao/BudgetDao/ImportBatchDao.softDeleteByBook`
- UI：`BooksScreen` 抽出无状态 `BooksContent`，每行加「删除」+ 确认弹窗（显示该账本流水条数）
- 测试：`PennedDatabaseTest`「book soft delete cascades to its data and keeps other books」（A 全隐藏/B 不受影响/软删非物理删）+ `BooksScreenScreenshotTest`（列表截图 + 只剩一本时删除键不可点）
- 模拟机实测：新增 TestBook（自动成当前账本）→ 塞 1 条流水 → 管理账本删除 → 弹窗显示「1 条流水」→ 确认后 books/transactions 均 deleted=1、列表只剩日常账本且已切回当前账本、首页数据回归（本月支出 22.00）
- 验证：`./gradlew build` 全绿

### M1 收尾 5/8：不计入收支语义修正（P0，2026-10-06）
- **语义定案**：「不计入收支」只影响收支统计，不影响账户余额（钱实际动了；信用卡还款场景）
- 修「该生效没生效」：`TxWithCategory` 投影补 `excludeFromStats`（两条查询），`HomeGroups.groupByDay`/`monthTotals`/`categoryTotals` 三处合计排除该标记（列表仍显示行）
- 修「不该生效却生效」：`AccountDao.observeWithBalance` 收支子查询去掉 `t.excludeFromStats = 0`
- 测试：`HomeGroupsTest` 新增「excluded rows stay listed but never count toward totals」（日/月/分类三口径）+ `PennedDatabaseTest` 新增「exclude from stats keeps the money in the account balance」（余额 -66.00 = 两条都算）
- 模拟机复验（造 10.00 支出挂现金账户）：开关=0 → 首页本月支出 32.00、现金 -10.00；开关=1 → 首页 22.00、统计页 2026年9月 支出 22.00（环形图/排行同步排除）、现金仍 -10.00、净资产 990.00
- 验证：`./gradlew build` 全绿；探针行已删

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
