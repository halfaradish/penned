# 任务看板

> 会话间进度的唯一真相。任务模板见 `docs/开发工作流.md` 第四节 C。

## 进行中

（无）

## 待办

- **M1 规格冻结**（specs/M1.md）：流水编辑/删除、账户管理与转账（含转账导出格式确认）、预算表、CSV 导入导出（含一木 xls 导入器）、密码锁、深色主题、账本管理（首页账本名动态化）
- **M2 规格**：update.json 协议 + UpdateChecker + 签名密钥生成与离线备份（**首次发版前 applicationId 终审 D013**）+ release minify 打开（D012）
- 升级 robolectric >4.17 时回归试 `@Config(sdk=[36])`（D016）
- M1 引入 Roborazzi gradle 插件，把"每次测试都重录截图"改为 record/verify 双任务（D018）

## 提案区（等人裁决）

（无）

## 已完成

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
