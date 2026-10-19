# 任务看板

> 会话间进度的唯一真相。任务模板见 `docs/开发工作流.md` 第四节 C。

## 进行中

**预发布通道（D114，用户点名）——已实现；annotated tag 与签名 Secrets 门均已修复并过全量 gate（1047/0，lint 0 error / 34 warnings），待重发 beta（2026-10-19）**：
用户要求能发 `v0.2.0-dev` / `-beta` / `-beta.1` 类非正式版：正式版（纯数字）不收非正式版通知，非正式版始终更新到日期最新；
并指出「beta 收正式版后会掉回正式通道、收不到 0.3.x-beta」的漏洞。落地口径：
- **双清单**：`update.json` 只由正式版流水写；`update-prerelease.json` 每次发版覆盖（正式版 base 不倒退时也写，beta 用户跟着升正式版）。清单 v1.1 加可选 `channel` / `publishedAt` / `tag`；0.1.0 只认正式清单，服务端隔离是它唯一防线。
- **客户端粘性订阅**：`SettingsRepository` 新增 `update_channel` + `update_channel_explicit`；`UpdateChannelPolicy` 四条例（首次按安装包初始化 / 显式优先 / 正式版上手动装 beta 自动升舱 / **beta 升正式版不降级**）；`AboutViewModel` 每次检查前解析通道，关于页加「接收预发布版本」开关（`PennedSwitchRow`，切换取消进行中的检查/下载并清旧清单）；`UpdateChecker` 加 `acceptsPrerelease`，正式订阅读 `update.json`、预发布订阅读 beta 清单（正式地址兜底），正式订阅拒绝 `channel=prerelease` 或版本名带 `-` 的清单。
- **流水门**：`.github/workflows/release.yml` 重写为通道解析 → tag 必须在 main + annotated → `versionCode > max(两份清单)` → 按通道生成 → `--prerelease` 建 Release → 回写 main（预发布硬断言 `update.json` 不变，失败重试）；通道判定抽到 `.github/scripts/release_channels.py`（`plan` + `--self-test` 5 正常 / 5 失败，本地已跑绿）。`bumpVersion` 校验版本名格式并打印通道；`makeUpdateJson` 加 `-Pchannel/-Pout/-PpublishedAt/-Ptag`。
- **测试**：新增 `UpdateChannelTest`（格式表 + policy 10 例）；扩 `UpdateManifestTest`（v1.1 字段 / 未知 channel 无效）、`UpdateCheckerTest`（正式拒绝 beta、预发布双收、地址回退）、`AboutViewModelTest`（稳定版包 + prerelease 订阅读 beta 清单、开关落 explicit、正式包拒绝 beta）、`AboutScreenScreenshotTest`（开关开/关与接线）。全量 **1036 → 1047 测试 / 0 失败**：首轮全量撞到三处已知 flake（`AddDebtFlowTest` ComposeTimeout、`AddTimeFlowTest` IllegalState、`AppearanceScreenTest` DataStore rename IOException），单跑三处全过并复跑全量绿；改完 UI 签名后全量再撞 `AddDebtFlowTest` 同一 flake 一次，单跑全量单测 **1047/0** 后 `./gradlew build test lint` 以该结果通过（lint **0 error / 34 warnings**，与基线同数）。截图 `ui16_about_prerelease` 已过目（版本卡四行、开关在「当前版本」与「检查更新」之间）。
- **文档**：D114、M2 S1/S2/S6/S7、界面规范 §13·补四十、AGENTS、开发工作流、本看板。
- **实现坑（写下来省得下次再踩）**：`AboutViewModel.init` 若在 Main 上持锁解析 DataStore，而 `autoCheckOnLaunch()` 又被测试/启动路径用 `runBlocking` 调，会互相等死（本次首轮全量卡在 `AboutViewModelTest.auto check…` 10+ 分钟）；`currentChannel()` 的整段读-判定-写已放 `Dispatchers.IO` + `Mutex`，并给通道切换也放 IO。另：`AboutContent` 的 `modifier` 必须是第一个可选参数，否则 lint 多一条 `ModifierParameter`（本次已按"`prereleaseEnabled` 改成必填、`modifier` 留在最后"修掉，警告数保持 34）。
- **首次真实 beta tag（2026-10-19）**：`main` 与 annotated tag `v0.2.0-beta` 已 push；tag 执行对象为 `1f210dd`、指向提交 `a1fe132`。release run `39144887744` 在第一步版本门失败：tag `v0.2.0-beta` 与 `version.properties` 的 `v0.1.0` 不一致。失败发生在构建与建 Release 之前，**没有产生 beta APK / `update-prerelease.json` / GitHub Release**。修复路径：在 `main` 上 `bumpVersion -PversionName=0.2.0-beta.1`（当前 `versionCode=1`，应变为 2）→ 提交 → 先推 `main`，再新建并推 annotated tag `v0.2.0-beta.1`；旧失败 tag 可在确认无 Release 后删除，若复用旧名则必须先删本地和远端 tag，不能直接强推覆盖。
- **annotated tag 误判（2026-10-19）**：第二次 release run `39192839376` 对应 `0.2.0-beta.1（3）`，远端 tag `41cdb2f` 经 Git 与 GitHub API 双重核对均为 annotated tag（object type `tag` → commit `b8a3ec9`），但 `actions/checkout@v4` 在 tag 事件按 commit SHA 抓取，生成同名**本地轻量 tag**，导致 workflow 的 `git cat-file -t` 看到 `commit` 并误报。`.github/workflows/release.yml` 已改为在类型检查前显式执行 `git fetch --force origin refs/tags/${TAG}:refs/tags/${TAG}`；本地用同样的“commit SHA → 同名 tag”再拉取流程复现：修复前为 `commit`，修复后为 `tag`，tag 注释可读。顺带修掉测试与发版版本号的耦合：`AboutViewModel` 可注入 `versionName/versionCode`，测试固定 `0.1.0（1）` 而生产默认仍读 `BuildConfig`；全量 **1047/0**、lint **0 error / 34 warnings**。仍无 beta APK / 预发布清单 / GitHub Release；下一次需把该修复提交进 `main`，再用包含修复的提交重打 beta tag。
- **签名 Secrets 门退出码（2026-10-19）**：重打 tag 后版本门已过，run 进入「检查签名 Secrets」；四个 secret 在日志中均显示 `***`（表示都有值），却仍以 exit 1 失败。根因是脚本把成功标记初始化为 `ok=1`，全部存在时最后 `exit $ok` 反而退出 1。现改为 `missing=0`，缺项才置 1，最后 `exit "$missing"`；这不是 Repository secrets 配置错误。
- **CI 插件解析根因与修复（2026-10-19）**：run `39196638942` 前 8 步全过，`assembleRelease` 在解析 KSP `2.3.12` 插件时失败。临时 diag run `39198597884` 在同一 runner 上实测：阿里云 `gradle-plugin` 对 KSP POM 返回 **502**，Maven Central 200、Gradle Plugin Portal 303、腾讯 maven-public 200；Gradle 遇首个仓库 502 会**终止插件解析**，不会继续尝试后面的仓库。按 D115 改为 `com.google.devtools.ksp` 组走 `exclusiveContent` → Maven Central，保留原镜像顺序与腾讯兜底；全新 `GRADLE_USER_HOME` 的 `help --info` 已确认 KSP 全链路从 Maven Central 解析成功。临时 `diag.yml` 已删除。
- **未做（不造假）**：真实 beta 尚未发布；模拟机闭环（0.1.0 正式机不提示 beta → 手装 beta → beta.1 自动提示 → 正式版把 beta 带回）待跑；`makeUpdateJson` 已用本机签名 APK 干跑出 `channel=stable` 清单（`build/tmp/updateTest.json`，未入库）；`release_channels.py --self-test` 5 正常 + 5 失败本地已绿，CI 已实际跑到首个 tag 的版本门。

**AI 设置·供应商预选（用户点名）——已交付（2026-10-18，两片）**：四档预置（DeepSeek / OpenCode Zen / OpenCode Go / 自定义），
**四样配置每家各存一份、切家不串台**，老配置按地址自动认领（决策 **D113**，规范 **§13·补三十九**）。切片：`2818b76` 纯逻辑
（`domain/LlmProvider.kt` + `SettingsRepository` 分区槽位 / `adoptLegacyLlmConfigIfNeeded` / `activeLlmConfig` 收口）→
`1dd845e` UI 闭环（配置卡首行「供应商」+ 一行口径提示 + `PennedSheet` 选择抽屉 + 只补空 + 占位示例跟着供应商走）。
测试 **1016 → 1036 / 0 失败**（+`LlmProviderTest` 5 / `IntelligenceSettingsTest` +8 / `AiSettingsViewModelTest` +2 / `AiSettingsScreenTest` +4）。
**模拟机复验（GxuDev，真 Key）**：① 打开页面即**把老配置认领成 DeepSeek**（地址/Key/模型/视觉模型原样，Key 仍密文）；
② 抽屉四档 + 当前行「已选」；③ 切到 OpenCode Zen → 地址与模型换成预置、**Key 空（没串台）**、提示换成 Zen 那条；
④ 切回 DeepSeek → **值原样回来**，「测试连接」照样「连上了：deepseek-flash 回话正常」；⑤ 截图识别复跑 **16 笔 /
支出 252.27 / 收入 88.00**（与修复后一致），走的就是新的 `activeLlmConfig()`；⑥ 走查前后库内基线 **760 活跃 / 15 软删 /
3 账户 / 2 账本 / 1 批次 / v9** 一字未动（预览没导入）。截图 `docs/local/界面重构验证/s3_0*.png`。
**仍未验（缺料，不造假）**：OpenCode Zen / Go 两条**连接性没验**（没有这两家的 Key；地址与模型清单只做过公开可读的
`/models` 核对）；账单导入的两条 AI 建议按钮仍未在真账单文件上点过（同一条文本链路，测试连接已证）。
**本会话观察到的 flake（都不是本次改动引入，单跑必过、全量复跑即绿）**：`AboutViewModelTest`（"auto check runs once a
day…"）、`AddEditSubCategoryTest`（宫格展开）、`QuickEntryFlowTest`（一句话记账流）、`ScreenshotImportViewModelTest`
（"重复行不进库…"）、`AddTimeFlowTest`（选时分保存）——清一色 Robolectric + Compose 的 `ComposeTimeoutException`
或紧随其后的状态断言，出没随机、单类运行不复现；全量复跑一次即 1036/0。
**另一条工具口径（省得下次再踩）**：核对模拟机库内基线**别拉库文件**（`run-as cat` 出来的 `.db` 在宿主上会被报
`database disk image is malformed`，两次都是）——设备自带 `/system/bin/sqlite3`，直接
`adb shell 'run-as dev.penned.app sqlite3 databases/penned.db "select count(*) from transactions where deleted=0; …"'`
最稳（只读，不用 force-stop）。

**真机 AI 链路验收（2026-10-18，GxuDev，真 Key）—— 补上 M4 一直 deferred 的那条，并修掉两处实测 bug**：用户在模拟机上配好了 AI 设置（`https://api.deepseek.com` + `deepseek-flash`，开关开），本会话按「先实测、再动代码」跑通：
- **测试连接**：✅「连上了：deepseek-flash 回话正常」（文本链路 = 真网真 Key 可用）。
- **截图识别**：**原来 100% 失败**——根因是 `LlmClient` 有图时把提示词整个丢掉（DeepSeek 回 400），修好后两张真图都出结果，且**逐笔对得上**：微信列表图 16 笔（合计 252.27 / 88.00 与图分毫不差）、支付宝列表图 5 笔（合计 204.71 自洽）。修复与口径见 **D112**；同时把 400 从「服务端出错（5xx）」分档成「请求被拒（4xx）」。
- **已知边界（模型侧，不修）**：图里**末行只露半截**（时间被裁）时模型会整行丢掉（支付宝图 6 行只回 5 行）；图内表头合计不参与对账（D111 ⑦）。
- **仍未验**：M4-6 两条 AI 建议按钮没在真账单文件上点过（同一条文本链路，测试连接已证）；M4-7 的「真机真图 ≥6 张」只到 2 张（`docs/local/微信支付宝账单截图/` 共 4 张，另 2 张未跑）；M4-8 造通知仍留真机（本模拟机镜像不投递）。

**M4 智能 · 交付进度（每轮开工先读这一段）** —— 规格 `docs/specs/M4.md` **v1.0 已冻结**，按批次串行落地（批次内可换序，批次间不许跳批）。
- **批次一**：`M4-0` ✅ 已交付（提交 `8a23c67`）｜`M4-1` ✅ 已交付（提交 `fc340bb`）｜`M4-2` ✅ 已交付（提交 `9fd1ed1`）——**批次一收官**
- **批次二**：`M4-3` ✅ 已交付（提交 `7595559`）｜`M4-4` ✅ 已交付（提交 `5ffb5b6`）——**批次二收官**
- **批次三**：`M4-5` ✅ 已交付（提交 `be81a2a`）｜`M4-6` ✅ 已交付（提交 `b1a4a17`）——**批次三收官**
- **批次四**：`M4-7` ✅ 已交付（提交 `6b05383`）｜`M4-8` ✅ 已交付（提交 `7128d0a`）——**批次四收官 = M4 九片全部交付（2026-10-17～10-18）**
- **后续（2026-10-18）**：M4 之后接了一条**截图列表导入**（D111，用户点名）——9 片之外的单片，两片已交付（`dcde043` / `55f34f6`），
  M4 九片本身不变；测试基线随之 **975 → 1016**（`./gradlew build test lint` 全绿、lint 34 warnings 无新品类）。
- **里程碑终态**：`./gradlew build test lint` **975 测试 / 0 失败 / 0 error / 34 warnings**（无新品类）；**零新增依赖**（`libs.versions.toml` 与 `app/build.gradle.kts` 依赖清单自 M4 起点零变化）；Manifest 只多四处（`POST_NOTIFICATIONS` + 通知监听 `<service>` + M4-7 的 `ACTION_SEND` filter）；外发只有 AI（只收 https、默认关、拒绝时一个包都不出）与 M2 的更新检查两条；库 **v9**（`9.json` 入库）；**全部软删 + 唯一硬删例外**（候选池 `clearProcessed`，D104）；金额一律 Long 分。**未 push、未打 tag、未发版**。
- **测试基线**：M4 开工前 **723 条 / 0 失败**；`M4-0` 后 767；`M4-1` 后 785；`M4-2` 后 826；`M4-3` 后 850；`M4-4` 后 882；`M4-5` 后 906；`M4-6` 后 920；`M4-7` 后 939；`M4-8` 后 **975 条 / 0 失败 / lint 0 error（34 warnings 无新品类）**。
- **M4-8 产出**（UI 片 + 系统接线）：`domain/NotifyParse.kt`（**代码内常量规则表**，不落库不热更新：微信/支付宝特征词 + 金额正则 `¥?([\d,]+\.\d{2})` 交 `MoneyParse` + 方向判定 + 关键词规则带分类；**解析不出金额或方向直接丢弃**；`alertText` 固定文案「识别到一笔 12.50 的支出，点这里确认」；`whitelist` 解析逗号串。**DEBUG 包里多一条 `com.android.shell` 规则**，专为 `adb` 造通知那条验收——release 不含）；`data/notify/AutoEntryService.kt`（读 `EXTRA_TITLE/TEXT/**BIG_TEXT**/SUB_TEXT/SUMMARY_TEXT`——`cmd notification post -S bigtext` 与很多支付通知把正文放在 BIG_TEXT，只读 TEXT 会读不到金额；门：`auto_entry_enabled` + 白名单；去重同 App+同分钟+同原文；落候选池带 `occurredAtEpochMinute`/`note`/`categoryId`（命中规则）；发本 App 提醒（渠道 `auto_entry`），小图标用新加的 `res/drawable/ic_notify_pending.xml`——**状态栏按 alpha 做单色遮罩，拿 `ic_launcher` 会渲染成一块白方块**；`PendingIntent` 带 `EXTRA_OPEN_PENDING`）；`ui/intelligence/AutoEntryScreen.kt`（状态卡两态 +「去开启」跳 `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` + 总开关默认关 + 白名单只列**已安装**的常见支付类 + 待确认入口带条数 + 隐私说明；33+ 开开关时顺手申请 `POST_NOTIFICATIONS`——**只服务提醒，拒绝不拦落库**）；`ui/intelligence/PendingScreen.kt`（候选行 = 来源 + 方向 + **时间** + 金额 + 原文一行 + 左滑「丢弃」；「全部丢弃」；页脚「清空已处理」= **全库唯一硬删**（D104）；空态给"去开自动记账"）；`ui/intelligence/NotificationOpenRequest.kt`（intent 标记 + 通知使用权判定）；`TxSource.NOTIFICATION` + 详情页「通知记账」；我的页「智能」卡第四行 + 路由 `auto-entry`/`pending` + 通知点击补跳。测试 **+36**（939 → **975**）：`NotifyParseTest` 8（**含微信 6 / 支付宝 6 两张表**，脱敏样张）/`AutoEntryServiceTest` 7（白名单、总开关、去重、解析不出不落库、ACCEPTED 回写、硬删）/`AutoEntryScreensTest` 10 + 截图浅 4 张 + 深 2 张（`dark_auto_entry`/`dark_pending`）/`NotificationOpenRequestTest` 2/`NotificationWiringTest` 4（服务真被系统"通知使用权"列表按 action + 权限 + 显示名收进去、`POST_NOTIFICATIONS` 在清单里、33+ 未授权才申请）/`CsvTest` +1（`NOTIFICATION` 往返、旧值不受影响）/`DetailScreenScreenshotTest` +2（「通知记账」vs「自动记账」两条标签）/`DarkThemeScreensTest` +2。规范 **§13·补三十七** 已写。
- **M4-8 走查与修正（本片自己抓到三处）**：① 隐私说明把 `**不进 AI**` **直接印在页面上**（Markdown 星辰号没被解释）——改成真正加重（`buildAnnotatedString` + `SemiBold`），同页测试文案随之更新；② 候选行**漏了「时间」**（规格行 = 来源 + 金额 + 方向 + **时间** + 原文）——补行尾 `MM-dd HH:mm`（有发生时刻用它、没有才退到入池时刻，跨时区表驱动测）；③ 提醒小图标原用 `ic_launcher`（108dp 实心方块）→ 新增单色 `ic_notify_pending.xml`。**深色截图逐张过目**（`dark_auto_entry`/`dark_pending` 与浅色同口径；像素扫描排除"看起来有边框"的误判——只有 `SwipeActionRow` 卡片右缘那条 1px 红发丝是真件，见提案区）。
- **M4-8 真机走查（GxuDev）**：装包后**通知使用权**授予 → 我的 → 智能 → 自动记账：状态卡转「已获得通知使用权」、白名单出现「Shell」「微信」「支付宝」等已安装项、开关与条数正常；设置项落盘核对（`auto_entry_enabled`/`auto_entry_packages`）。**库内基线原样**（760 活跃 / 15 软删 / 3 账户 / 2 账本 / 1 批次 / user_version 9）。**deferred**：`adb shell cmd notification post -S bigtext -t '微信支付' …` 在我们这个模拟机镜像上**没送达已绑定的监听服务**（`dumpsys notification` 显示服务已绑定、logcat 无回调）→ 「造 3 条通知 → 待确认 3 条 → 入账 1 条 → 首页该行详情显示『通知记账』」这半步真机没验成，留给真机；**解析/落库/提醒/去重这半边由 `AutoEntryServiceTest` 用 `StatusBarNotification` 直接喂**（不依赖系统投递），清单侧由 `NotificationWiringTest` 断。
- **M4-8 已知坑（后续片/真机别踩）**：① `NotificationListenerService` 只读 `EXTRA_TEXT` 会**漏掉一大批**（微信/支付宝与大文本通知走 `EXTRA_BIG_TEXT`）；② `cmd notification post` 的 `-S bigtext` 也是塞进 `EXTRA_BIG_TEXT`；③ VM 里多处写入必须全走 `_state.update{}`——白名单曾因 `_state.value = _state.value.copy(...)` 在另一协程里**把刚加载的 App 列表覆盖成空**（logcat 抓到 `loadApps final=[shell]` 后组合里 `list=[]`）；④ `@Composable` 返回 String 的辅助函数会吃 `ComposableNaming`、可选参数里 `modifier` 不在最前会吃 `ModifierParameter`、`minSdk 26` 下 `SDK_INT >= O` 是死代码（`ObsoleteSdkInt`）——本片三处 lint 都按"删死代码/改名"修的，没加 suppress；⑤ Markdown `**` 在 `Text` 里**不会**变粗（本片与 M3 几个删除确认文案都有这个印字问题，见提案区）。
- **M4-7 产出**：`data/llm/ImagePrep.kt`（解码 + **长边 1280 / JPEG 85** + base64；原图只走 cacheDir、识别完即丢）；`ui/intelligence/ScreenshotImportScreen.kt`（相册多选（`PickMultipleVisualMedia`）/ 拍照（`TakePicturePreview`）/ 出图前明示 / 识别中 / 失败卡（重试 + 去 AI 设置）/ 成功提示；VM 逐张**顺序**识别、成功落候选池、失败不落库；`ScreenshotShareRequest` 单例接系统分享（`MainActivity.onNewIntent`））；记一笔预填扩 `presetSource`/`presetType`（落库带 `source = SCREENSHOT`），add 路由加 `pendingId/pendingSource/pendingType` 三参数，保存回来把候选标 `ACCEPTED` + 回填 `txId`（`PennedApplication.appScopeLaunch` 一小段应用级协程）；Manifest 加 `ACTION_SEND` + `image/*`；我的页「智能」卡与首页长按抽屉各加「截图导入」行。测试 +19：`ImagePrepTest` 7（压缩口径）/ `ScreenshotImportViewModelTest` 6（假服务器 + PNG fixture 端到端）/ `ScreenshotImportScreenTest` 6 + 截图。
- **M4-7 deferred（没有料，不许造假）**：**真机真图 ≥6 张**（微信/支付宝支付成功、银行、美团、非账单图）——`docs/local/` 里没有支付类截图；真机只验了入口与"未配置 --> 引导"这条路径，识别链路全部由假服务器 + 现场生成的 PNG 覆盖。另：**没有可用 Key**，模拟机上跑不了真机 AI 链路（https-only 也挡住了本机假服务器）。——**2026-10-18 已补验：见「进行中」最上面的真机 AI 链路验收（并修掉 D112 两处）**。
：`domain/LlmSuggestions.kt`（列映射建议的本地校验——**列名必须在表头里真实存在**、时间/金额缺一即作废；商户分类建议**只按名在本地匹配**（一级/二级路径），匹配不上留空；商户去重+分批 ≤100；**数字掩码** `maskNumbers`/`maskRow`）；`data/llm/LlmImportAssist.kt`（两条建议的 prompt 与调用：列映射默认只发表头，样例行走**掩码**；商户只发商户名+分类名）；`LlmClient.complete()`（**新增"只要 JSON 原文"的出口**——建议类出参形态与"一笔账"不同，不能硬套 `LlmDraftParser`）；预览页补**通用模式列映射卡**（十行字段可手改 + 「让 AI 认列」+「再看 3 行样例」+ 采用建议）与 **AI 卡**（列映射建议行 + 商户建议 + 「AI 补分类」+「存为关键词规则」）；`BillRowParser.parseGeneric`（手改/AI 映射走同一条解析）。测试 +14：`LlmSuggestionsTest` 7 / `LlmImportAssistTest` 4（含**外发边界**：请求体与 prompt 里金额/时间/单号一个都不出现，掩码后仍留形状）/ 预览页通用模式 2 + 截图。
- **M4-6 已知坑**：① **Android 的 `org.json` 把非 ASCII 写成 \\uXXXX**——断"请求体里有没有某个中文/数字"必须先解析回 JSON 再取 `messages[0].content[0].text`（直接 `body.contains("中文")` 必然假红）；② `LlmClient` 原来只有"按一笔账 schema 校验"的出口，建议类 JSON 会被判 `BAD_RESPONSE`——已加 `complete()` 分成两条出口；③ 预览页变长后测试里的点按要先 `performScrollTo()`（否则点在屏外、回调不触发——4 条用例一起红过）。
- **M4-5 产出**（UI 片）：`domain/AccountGuess.kt`（新建账户类型按名猜 + 分组名）；`data/importer/BillImportRepository.kt`（账户映射表构建 / 分类按名匹配**不新建** / 分块 500 落库 / 批次四字段 / **分批查全单号** `existingExternalIds` / 无单号兜底键）；`ui/data/BillImportScreen.kt`（预览页七段：识别结果 + 编码切换、计数分列、收支合计、目标账本 + 账户映射（新建可改类型/不指定/已有账户）、分类来源说明、导入进度；账户映射与账本选择抽屉）；数据管理页第三个入口 + 路由 `data/bill`（uri 走 `BillImportRequest` 带过去）。测试 +24：`AccountGuessTest` 6 / `BillImportRepositoryTest` 8 / `BillImportScreenTest` 9 / 深色 `dark_bill_preview`；规范 **§13·补三十四** 已写。
- **M4-5 真机走查（GxuDev，**真实账单两份**）**：微信 `wechat_bill.xlsx` → 预览 42 行 / 将导入 42 / **支出 526.06 · 收入 888.00（与文件头逐分对上）** → 导入 → 库内 +42、批次 `WECHAT|42|0|0`、42 条单号落列、账户按名新建 2 个（零钱→资金、零钱通→**理财**）；支付宝 `alipay_bill.csv` → 预览 **GB18030** / 表头第 24 行 / 517 行 / 将导入 507 / 跳过 10（关闭退款 9 · 不计收支 0 · 坏行 1）/ **支出 2,702.12 · 收入 1,501.11** / 分类按名命中 2 · 待补 505 → 导入 → +507、批次 `ALIPAY|507|10|0`、账户新建 3 个；**再导一次 → 将导入 0 · 重复 507（按钮「导入 0 条」禁用）**；批次整体删除 → 行数回落。截图 `docs/local/界面重构验证/m4_5_*.png`。
- **M4-5 真机抓出的两个真 bug（已修 + 回归测试）**：① 单号查重只查了**前 500 个**（`.take(CHUNK)`）→ 507 行的文件导第二次只认出 500 条重复、又插进去 7 条；修法 = `existingExternalIds` 内部按 500 **分批查全**（SQLite 变量上限 999），补 1200 条用例。② 计数卡的"将导入"用的是**解析出的候选数**而不是去重后的数 → "将导入 507 + 重复 500 > 总行数 517"；修法 = `counts.copy(importable = keep.size, duplicate = dup.size)`。
- **⚠️ 走查事故与恢复（必须记一笔）**：在批次页点「删除」时列表因上一条删除而重排，我按旧坐标点下去**误删了用户自己的批次**（`账单_1005183742`，751 条真实流水被软删）。**已按 `importBatchId + deleted=1 + 同一 updatedAt` 精确定位并全部恢复**（751 行 + 批次行 `deleted=0`），最终库内 **760 活跃 / 15 软删 / accounts 3 / books 2 / 批次 1（用户自己的）/ rules 0**，与走查前基线完全一致。**已知残留**：这 751 行的 `updatedAt` 变成了恢复时刻（原值被软删覆盖，无法还原）——数据本身（金额/时间/分类/账户/备注）一字未动。**教训**：模拟机上删任何东西前，先重新 dump 拿坐标；批量删除这类动作优先走 SQL 并先算好影响行数。
- **M4-4 产出**（无 UI 片，不落库）：`data/importer/BillFileReader.kt`（四种容器：CSV 严格 UTF-8→GB18030 探测 / `.xlsx` 走 `java.util.zip` + DOM（sharedStrings、inlineStr、str、数值、按 `r` 属性定位稀疏格）/ `.xls` 走 `Ole2Reader` + 既有 `Biff8Reader` / `.zip` 取唯一 csv）；`data/importer/BillFormats.kt`（两家规则表 + 表头定位 + **按签名打分**的来源识别 + 通用模式字段表）；`data/importer/BillRowParser.kt`（逐行 → `BillRow` + **每行只归一类**的分桶）；`domain/BillRows.kt`（单元格归一 / 金额 / 时间文本与**序列日期** / 方向 / 状态 / 备注拼法 / `&` 取段 / 单号）；`domain/BillDedupe.kt`（单号优先 + 无单号兜底键 + 文件内自身去重 + 分列计数）。测试 +32：`BillRowsTest` 14 / `BillFileReaderTest` 14（**xlsx fixture 现场 ZipOutputStream 生成**、编码三态、稀疏行、xls 用仓库既有脱敏 fixture）/ `BillRowParserTest` 4（两家样本形状全链路 + 重叠计数 + 拖尾 `	` + `&` 取段）。**gate 882 条 / 0 失败 / lint 0 error**。
- **M4-4 真实样本核对（用 `docs/local` 的两份真实导出跑临时探针，跑完即删、只打印聚合）**：微信 xlsx → 来源 WECHAT、表头第 18 行、42 数据行、将导入 42、**支出 40 笔 526.06 / 收入 2 笔 888.00 —— 与文件头声明逐分对上**；支付宝 csv → 来源 ALIPAY、表头第 24 行、517 数据行、将导入 507、状态跳过 9、坏行 1（**真实数据里有一行 0 元支出**，按"金额 ≤ 0 = 坏行"判掉）、支出 500 笔 2702.12 / 收入 7 笔 1501.11（收入与文件头一致）。
- **⚠️ 对账口径勘误（M4-4 发现的现实冲突，M5 前请过目）**：支付宝文件头声明的「支出：502笔 2698.76元」与**逐行重算 2705.13 差 6.37**，而文件自己的「特别提示 6」就写了"因统计逻辑不同，明细金额直接累加后可能会和统计金额不一致"——**所以 M4-5 的"合计与文件头逐分对上"这条验收对支付宝不成立**。M4-4 实测我的读取层是对的：500 笔导入 = 2705.13 − 3.01（交易关闭）− 0.00（零元行）= 2702.12，逐分自洽。**M4-5 按这个口径做对账**：分列行数与文件头对（502/7/8、517 都成立）、合计以**明细累加**为准并把文件头声明并列显示（不一致时写明原因）；微信侧照旧逐分对。这条与 M4 附 A 里"两份都逐分对上"的旧说法冲突，以本条为准（规格冻结不改，记在这里）。
- **M4-3 产出**（UI 片）：`domain/QuickParse.kt`（本地解析器：金额取末 + 量词排除、收入词、日期词七类含"最近一次"规则、备注剔词压空格；**日期词先长后短**——`大前天` 曾被 `前天` 抢走）；`ui/intelligence/QuickEntryScreen.kt`（输入 + 粘贴 + 解析 → 结果卡五行逐行可改：类型就地切 / 金额键盘 / 分类抽屉（复用宫格）/ 日期走**抽出来的共用 `AddDateTimeDialog`**（记一笔与一句话同一套，D097）/ 备注抽屉；失败态"没找到金额"；保存 `source=TEXT` 账户留空）；首页**长按悬浮入口** → 「快捷记账」抽屉 → 一句话（截图导入在 M4-7 补第二行，**不放死行**）；保存回首页的提示条 = **「已记账」+「撤销」**（`flashUndoable` 一路，撤销=删掉这笔，删除本身还留一次撤销）。`TxEdit.newTransaction` 加 `source` 参数（默认 MANUAL，别的入口带自己的来源）。
- **M4-3 已知坑**：① **日期词必须"先长后短"**（`大前天` 含 `前天`，短的先判会读成 -2）；② **别再写 `runBlocking { flow.first() }` 等 VM 状态**（主线程被占住 = 死等，M4-1 踩过、M4-3 的日记变体又踩一次）；③ **Room 挂起调用回来时续体可能落在查询线程上**——解析/保存要放 `viewModelScope`（Main.immediate），在 `rememberCoroutineScope` 里直接调导航会炸 "must be called on the main thread"（流程测试实拍）；④ **分类抽屉里点一级不能直接选中就关**：有子分类要先展开（早先写错 → 二级分类永远选不到，流程测试抓出来的）；⑤ 结果卡五行的文本会与卡片合并语义同名（测试用 `testTag` + `hasAnyAncestor` 限定，别用裸文本匹配）；⑥ 真机走查时**软键盘会盖住保存键**（dump 的坐标是应用窗口坐标，点之前先 BACK 收键盘）。
- **M4-0 产出**（无 UI 片）：库 **v8→v9**（新表 `keyword_rules` / `pending_entries`；`transactions.externalId` + **非唯一**索引 `(bookId, externalId)`；`import_batches` 加 `sourceKind`/`skippedCount`/`duplicateCount`，默认 `'YIMU'`/0/0）；`KeywordRuleDao`（排序/启停/软删/`swapPriority`/`nextPriority`/`insertAtEnd`）、`PendingEntryDao`（**清空已处理 = 硬删**，D104）、`TransactionDao.externalIdsInBook`（≤500 分批）；纯函数 `KeywordRules.match`/`targetName` + `MoneyParse.toCents`/`centsFromNumber`；`SettingsRepository` 加 7 个 AI/自动记账键（`llm_enabled`、`auto_entry_enabled` 默认 **false**）；`PennedCsv` 末尾补 `externalId`（旧 24 列文件仍能导）。`9.json` 入库、`MIGRATION_8_9` + migration 测试（旧数据零变化、旧批次 `sourceKind='YIMU'`）。**已在 GxuDev 真机验证迁移**（装包后 user_version 8→9、`keyword_rules`/`pending_entries` 建好、**760 活跃 / 15 软删 / accounts 3 / books 2 一字不变**）。
- **M4-1 产出**（UI 片）：我的页「智能」分组卡 + 第一行「分类关键词」（`MineScreen`/`PennedApp` 新路由 `keywords`）；规则页 `ui/intelligence/KeywordRulesScreen.kt`（列表行 = 关键词 + 「→ 分类：食品餐饮 / 午餐」/「→ 标签：出差」+ 行尾开关 + 左滑动作区「上移/删除」+ 删除可撤销 + 空态给出路 + 口径说明行）；新增抽屉（关键词输入 + 分类/标签二选一 + **复用记一笔的 `CategoryPanel`**（新增 `showAddCell=false` 开关）+ 标签 chip 组 + 保存禁用态）；domain 补 `KeywordRules.targetName`（分类给父级路径、目标被删返回 null → 显示"目标已删除，删了重加一条吧"）。规范 **§13·补三十一** 已写。测试 +18：`KeywordRulesTest`（+3 targetName）/ `IntelligenceDaoTest`（+1 insertAtEnd 撞车）/ `KeywordRulesScreenTest`（9：渲染/开关语义/空态/动作区/抽屉接线/我的页入口浅深/抽屉浅深）/ `SwipeActionRowTest`（2 组件回归）。截图 11 张（`m4_keyword_rules*` / `m4_mine_intelligence*` / `dark_keyword_rules`）+ 我的页基线 `m1_mine_screen`、`dark_mine` 重录并逐张过目。
- **M4-2 产出**（UI 片 + 网络层）：`domain/LlmEndpoints.kt`（配置/端点校验：**只接受 https**、归一、失败各有文案）、`domain/LlmDraft.kt` + `LlmDraftParser`（出参强校验：缺金额即失败、类型缺省=支出、时间解析不出=null **不猜**、```json 围栏与前后闲聊当噪声、**时间是本地墙钟 → 过时区换算成真 epoch 分钟**）；`data/llm/LlmClient.kt`（OpenAI 兼容 `POST {base}/chat/completions` + Bearer；文本/图像共用；超时 10/60；401/403/429/5xx/网络/**非 https 不发包**分门别类）；`ui/intelligence/AiSettingsScreen.kt`（总开关 + 地址（http 当场报错）+ Key（密文/显示）+ 模型 + 视觉模型 + 测试连接三态 + 清除 Key + `AiPrivacyCopy` 一处隐私文案）；我的页「智能」卡第二行 + 路由 `ai-settings`；`PennedNameField` 加 `visualTransformation`（密文）。**测试基建**：`FakeHttpServer` 扩成读 `Content-Length` + 记请求体（M4-6 断外发边界要用）。测试 +41：`LlmDraftParserTest` 11 / `LlmEndpointsTest` 7 / `LlmClientTest` 10（含"非 https 一个包都不出去"）/ `AiSettingsScreenTest` 7 / `AiSettingsViewModelTest` 5（清除 Key 真的从存储删掉）。截图 `m4_ai_settings_*`（空/填好/显示 Key/http 报错/测试成功/失败）+ `dark_ai_settings`；我的页基线随第二行重录。
- **M4-0/M4-1/M4-2 已知坑（后续片别踩）**：① `swapPriority` **不能**写成单条 `UPDATE … CASE`（SQLite 逐行更新，子查询会读到同语句里已改写的另一行）；② `insertAtEnd` 必须把"取 MAX+1"与插入放**同一个事务**（两步写法连续新增会全拿 0 分）；③ `MIGRATIONS` 是整条链，旧 `MigrationTest` 里"版本 = N"的硬编码断言会随新迁移一起变；④ 枚举加值不改 schema（转换器按 `.name` 存 TEXT）；⑤ **`SwipeActionRow` 的隐藏动作能被点到**——内容层没有整行点击（如"开关行"）时，点行右侧会悄悄上移/删除；已修（`ActionBlock(enabled = isOpen)`）并有组件回归测试；⑥ `WhileSubscribed` 的 `stateIn` **别**在 `runBlocking { flow.first() }` 里等（主线程被占住 = 死等）；VM 的写入跑在 Robolectric 主 looper 上，轮询里必须 `idle()`；⑦ `LlmDraft` 的时间**必须过时区换算**（墙钟编码在 UTC+8 会偏 8 小时）。
- **测试基建修复（本会话，非本片引入）**：`SettingsRepository` 原先用 `by preferencesDataStore(name)`——那是**进程级单例**，绑死第一个 Context 的目录；Robolectric 每个测试类一个沙箱，后续类写设置会去**已被回收的目录**里写，抛 `IOException: Unable to rename …preferences_pb.tmp`（全量 `--rerun` 三次里偶发一次，指到的类随机，连 `StatsSettingsTest`/`AccountArchiveFlowTest` 这种既有类都被打红）。**已修**：改成按 `applicationContext` 缓存 `PreferenceDataStoreFactory` 实例（文件名/路径不变，老用户设置照旧读；生产仍是单例，测试各写各的）。**修后连续 3 次全量 `--rerun` 全绿**。
- **口径备注（M4-2 走查时确认）**：AI 设置页的「测试连接」**不要求先开总开关**——它是本页的显式一击、也是"先配好再开"的验收动作（属 §二·4 的"显式触发"）；四条入账路径才由总开关拦。
- **M4-0~M4-2 遗留（不阻塞）**：`ImportBatchEntity.SOURCE_PENNED_CSV` 已定义但还没有写入点（既有的 penned CSV 导入批次仍是默认 `YIMU`，而批次页目前不显示来源）——M4-5 接 `WECHAT/ALIPAY/GENERIC` 时顺手定，或挂提案区。
- **缺料提醒**：`M4-7` 的真机验收要 6 张支付类截图（`docs/local/` 没有）；`M4-2/M4-6/M4-7` 的真机 AI 链路要一个可用 Key + https 端点——**本会话没有 Key**，M4-2 真机只验到"未配置/非 https/连不上"三条路径（假服务器链路已全覆盖）；M4-6/M4-7 开工时若仍没有 Key，同样只跑假服务器并在 board 记 deferred。——**2026-10-18：Key 与端点已就位并补验（见「进行中」顶部，D112）**。

**M4 智能 · 规格已冻结（v1.0，2026-10-17）** —— `docs/specs/M4.md` 已从规划稿 v0.1 收口为 **v1.0 冻结稿**，`M4-0` 无前置、可开工（本会话**只做规格**：`app/` 一行代码未动、依赖清单零变化、读取层探针测试验证后已删除）。范围 = **9 片分 4 批（3+2+2+2）**：批次一底座（`M4-0` v8→v9 一次迁移 + 底座纯函数 + 智能设置项 / `M4-1` 分类关键词规则 / `M4-2` AI 设置页 + `LlmClient`）、批次二文本与文件读取（`M4-3` 一句话记账 / `M4-4` 账单文件读取层与字段口径——**无 UI、不落库**、允许拆两次会话）、批次三预览与 AI（`M4-5` 账单导入预览页与落库——允许拆两次会话 / `M4-6` LLM 辅助导入）、批次四系统来源（`M4-7` 截图导入 / `M4-8` 自动记账 + 待确认）。**依赖**：批次内可换序、批次间串行；`M4-1`/`M4-2` 依赖 `M4-0`，`M4-3`/`M4-4` 只依赖 `M4-0`，`M4-5` 依赖 `M4-4`，`M4-6` 依赖 `M4-5` + `M4-2`，`M4-7`/`M4-8` 依赖 `M4-1` + `M4-2`。**本次冻结新定 9 条**（B1 **分类不新建**、匹配不上留空 / B2 新建账户**按名猜类型 + 预览页可改** / B3 掩码**照留、各建一个** / B4 **保留映射表 + 预览页明示"将新建 N 个"** / C1 加 **`TxSource.NOTIFICATION`**（周期补记仍是「自动记账」，D079）/ C2 BYOK **只接受 https** / C3 「清空已处理」**硬删**、不留痕 / C4 通知点击**过密码锁**、解锁后补跳 / C5 我的页新增**「智能」分组卡**）+ **R1~R16 全部按原推荐生效**（R14 关闭为"**必须支持 `.xlsx`**"——用户手上的微信导出就是 xlsx）。**冻结前实测**（都写进 M4 附 A/附 B）：微信 xlsx 是标准 OOXML（sharedStrings、**时间列是 Excel 序列日期数值**、金额是数值、`/` 是空值占位、表头在第 18 行）、支付宝 csv 是 **GB18030 无 BOM**（每行两个尾随 `\t`、表头在第 24 行、8 行"不计收支"与退款/关闭**重叠**）；两份文件头自带的对账声明与逐行重算**逐分对上**（微信一个月 + 支付宝一整年）；`java.util.zip` + DOM 在 `-no-jdk` 单测与 Robolectric 下**都跑通**（探针删除、仓库无残留）；基线 `./gradlew build test lint` 全绿（723 测试 / 0 失败）。**决策 D100~D105**；规范待各片开工补（**§13·补三十一起**）。**下一条 = M4-0 开工**（开工前待办见「待办 · M4 开工前」，不阻塞 M4-0）。

**记一笔与提示条（用户反馈两条）——已交付（2026-10-17）**：①**记账时刻只留一个入口**——「时间」chip 撤掉（**取代 D094 的那枚 chip**），点「日期」在同一个弹窗里调日期 + 时分（弹窗换成自家 `PennedDialog`：M3 日历剩网格 + 「🕐 时间」+ 时/分 3 行滚轮；M3 `DatePickerDialog` 会把额外内容插在标题与日历中间、且默认宽度装不下 336dp 网格——真机会裁掉周六那列）；chip 调过时分才带 `HH:mm`。②**提示条挪到首页顶部 + 按当前风格重设**（浅色卡 `surfaceContainerHigh` + 零阴影 + `primaryInk` 动作键，取代 D031 深色反向层；**悬浮记账入口不再被挡**——"提示条在就隐藏入口"那半条删掉；其余页仍在底部）；后续用户反馈"停留太长"→ 带「存为模板」那条 10s 缩到 **5s**（D099）。两片独立提交（`1ee927e` 时间入口 → `cc792dc` 提示条），`./gradlew build test lint` 全绿；模拟机（GxuDev）走查：弹窗宽版后网格不裁、16:38 落库逐分对上、编辑不动原值原样、横幅在顶部（y≈212）+ 入口照常在底部（y≈2009）；**走查开工重记基线 758 活跃 / 15 软删（用户自己又记了两笔）、结束后还原到同一基线**（临时流水按 SQL 清掉）。细节见「已完成 · 记一笔与提示条」；决策 **D097/D098**，规范 **§13·补三十**。**下一条 = M4-0 开工（规格已冻结 v1.0）或用户继续点名的打磨项**。

**记一笔与首页优化（用户点名三条）——已交付（2026-10-17）**：记一笔**时间到分**（「时间」chip + 「时｜分」双列滚轮、复用共用滚轮；未选 = 现在、编辑不动原值原样）/ **信息条改单行横滑**（`LazyRow`，取代 D044 的 FlowRow 换行）/ **首页月标题行 68→48dp**（去上下内边距，行高 = 「⋯」48dp 热区）。三片独立提交（`99e7864` 时间 → `6076b2c` 信息条 → `cc0491f` 月标题行），`./gradlew build test lint` 全绿（720 测试）；模拟机（GxuDev）走查：16:38 落库逐分对上、编辑不动原值原样、改 09:05 只动时间、信息条手指横滑到最后一枚、月标题行与折叠/「⋯」正常；**走查后库内还原基线：756 活跃 / 15 软删、accounts 3/4、books 2/1、user_version 8**（临时流水按 SQL 清掉）。细节见「已完成 · 记一笔与首页优化」；决策 **D094/D095/D096**，规范 **§13·补二十九**。**下一条 = M4-0 开工（规格已冻结 v1.0）或用户继续点名的打磨项**。

**M4 智能 · 规格规划稿（2026-10-17）—— 已收口为 v1.0 冻结稿**：本段是历史记录，最新状态见上方「进行中 · M4 智能 · 规格已冻结（v1.0）」。规划稿当时的口径：范围 = 截图导入 / 一句话记账 / 自动记账（通知监听）/ 智能标签 + **分类关键词规则**（M3 §六 移交清单里的第六项）+ **非标准账单文件导入**（用户 2026-10-17 新增需求），8 片分 3 批；用户已定三条（**纯 BYOK 多模态**、**自动记账走待确认列表**、**智能标签 = 与分类关键词同一张规则表双目标**）。**冻结时的变化**：8 片 → **9 片**（账单导入拆成"读取层 M4-4"与"预览页与落库 M4-5"）、3 批 → **4 批**（3+2+2+2）、待裁决 R1~R16 **全部生效**（R14 反转为"必须支持 `.xlsx`"）+ 新增 **B1~B4、C1~C5 九条裁决**。

**日历优化轮 2（用户点名的五条）——已交付（2026-10-16）**：日流水**跟手覆盖**（白卡面板 + 进度 p 1:1 驱动 + 松手吸附 + 回顶才展开）/ 收缩动画（视口高、本周行上移、非本周行渐隐全由 p 推导）/ **非本月无色灰字**（不再整格变淡）/ **去掉顶部 ‹ › 翻月按钮** / **月小字总结挪进月份行**（年月居左 + 总结紧贴其下左对齐）。三片独立提交（`bf89bda` 纯逻辑 → `93e728c` 视觉 → `b9b5e72` 交互），`./gradlew build test lint` 全绿，`ContrastTest` 换成"非本月灰字"那条，模拟机（GxuDev）走查前后库内 **756 活跃 / 15 软删、accounts 3/4、books 2/1、user_version 8** 不变。细节见「已完成 · 日历优化轮 2」；决策 **D093**（**取代 D091 ⑤ 整格变淡、D092 ① 里 ‹ › 端点禁用那半条**），规范 **§13·补二十八** 已按新版式重写。**下一条 = M4（未开工）或用户继续点名的打磨项**。

**M3 规格已冻结（2026-10-12）**：`docs/specs/M3.md` —— 会员功能解锁（标签 / 退款 / 报销 / 债务 / 模板 / 周期 / 组合筛选 / 账单组合 / 资产模块 / 理财快照 / 房贷年费 / 主题 / 首页显示 / 统计设置 / 愿望清单 / 分享卡片 / 日历，共 `M3-0`~`M3-17` 十八片）。**§五 7 条裁决已并入**：债务**实体式** / 退款优惠**保存净额** / 周期**打开即补** / 主题**只换品牌色相 4 套** / 愿望清单 = **攒钱目标** / **日历进 M3** / **18 片分 6 批、每批 3 片**（决策 D068~D074）。**M3 十八片（`M3-0`~`M3-17`）全部交付**（批次一~六收官，库 v8）：标签 / 退款 / 报销 / 债务 / 模板 / 周期 / 组合筛选 / 账单组合 / 资产三件 / 理财快照 / 房贷年费 / 多主题 / 首页显示 / 统计设置 / 愿望清单 / 数据小卡片 / 日历。**下一步 = M4（未开工）或用户点名的打磨项**。

（M2 应用内更新与 M2-10 首次真实发布均已收尾，见「已完成」；遗留与测试坑见下方「待办 · M2 遗留与测试坑」）

## 待办

- **M4 智能切片清单**（规格 `docs/specs/M4.md` **已冻结 v1.0**；开工顺序 = **4 批（3+2+2+2）**，批次内可换序、批次间串行；每片一个会话、独立提交、`./gradlew build test lint` 全绿；`M4-4`/`M4-5` 允许拆两次会话）：
  - **批次一 · 底座（P0，先做）**
    - `M4-0` schema 地基 + 底座纯函数 + 智能设置项（v8→v9 一次迁移：建 `keyword_rules` + `pending_entries`、`transactions.externalId` + **非唯一**索引、`import_batches` 加 `sourceKind/skippedCount/duplicateCount`；新增 `MoneyParse` 宽松金额解析与 `KeywordRules.match` 两个纯函数；`PennedCsv` 补 `externalId` 列）——**无前置，可开工**
    - `M4-1` 分类关键词规则（规则表引擎 + 我的页「智能」分组卡 + 规则页 + 四处接入）——依赖 `M4-0`
    - `M4-2` BYOK：AI 设置页 + `LlmClient`（**只接受 https**；假服务器测试）——依赖 `M4-0`
  - **批次二 · 文本与文件读取（P0）**
    - `M4-3` 一句话记账（FAB 长按 + 智能卡行 + 就地结果卡 + 本地解析器 + AI 兜底按钮）——依赖 `M4-0`
    - `M4-4` 账单文件读取层与字段口径（CSV/xlsx/xls/zip 四容器 + 编码探测 + 两家规则表 + 字段口径 + 去重；**无 UI、不落库**）——依赖 `M4-0`
  - **批次三 · 预览与 AI（P0）**
    - `M4-5` 账单导入预览页与落库（第三个导入入口 + 计数分列 + 列映射 UI + 账户映射与新建 + 分块落库 + 批次）——依赖 `M4-4`
    - `M4-6` LLM 辅助导入（列映射建议 + 商户分类建议 + 存为关键词规则；`FakeHttpServer` 扩成读请求体以断言外发边界）——依赖 `M4-5` + `M4-2`
  - **批次四 · 系统来源（P1）**
    - `M4-7` 截图导入（BYOK 多模态 + 相册/拍照/分享三入口 + 压缩 1280/85 + 落候选池预填记一笔）——依赖 `M4-1` + `M4-2`
    - `M4-8` 自动记账 + 待确认（通知监听 service + 白名单 + 候选池 + `TxSource.NOTIFICATION` + 通知过密码锁 + 清空已处理硬删）——依赖 `M4-1` + `M4-2`
- **M4 开工前还缺什么**（本会话冻结规格时核对后的实际清单；**结论：M4-0 与 M4-4 都没有阻塞项，可以直接开工**）
  - **阻塞 M4-0**：**无**。`M4-0` 的表/列/索引/DAO/两个纯函数/设置项键名/迁移版本都已定死，`9.json` 与 `MIGRATION_8_9` 的写法照 `PennedDatabase.MIGRATIONS` 既有姿势；依赖清单零变化（`java.util.zip`、`javax.xml.parsers`、`org.json`、`BitmapFactory` 都是平台自带，实测在单测与 Robolectric 下可用）。
  - **阻塞 M4-4**：**无**（读取层与口径已按真实样本实测写死）。三条**假设**都有兜底、不需要你做什么：微信 **CSV** 版导出没有样本（旧版微信给 CSV；列名与 xlsx 同族，靠支付宝 GBK 样本 + 现场生成的脱敏 fixture 覆盖）；微信"不计收支"与退款/关闭行在本月样本里没有（规则照支付宝口径，靠 fixture 钉住）；支付宝网页版 `.zip` 包没有样本（按"取其中唯一 csv"实现 + 自测）。
  - **不阻塞**（但会在别的片卡住验收，请你按需补）：
    - **M4-7 的真机验收要 6 张支付类截图**（微信支付成功 / 支付宝支付成功 / 支付宝转账 / 银行 App / 美团订单 / 一张非账单图）——`docs/local/` 现有 548 张图里只有一木界面参考与自家验证截图，**没有支付类截图**；没有就只跑假服务器链路（验收会写明"真图待补"）。
    - **M4-2 / M4-6 / M4-7 的真机 AI 链路要一个可用的 BYOK Key + https 端点**（假服务器链路不受影响）；没有就在 board 记一笔"仅假服务器验收"。
    - 走查机器与基线：`GxuDev`（**用户本人也在用，每轮开工重记库内基线**）或 `PennedM2`；真实账单文件只从 `docs/local/微信支付宝导出账单/` 读，**不入库、不进测试**。
    - 通知文案的脱敏 fixture 是**自造的**（真实微信/支付宝通知文案没有样本）——真机验证走 `adb shell cmd notification post`，规则表口径按 R6 的"代码内常量、可后补"。

- **M3 切片清单**（规格 `docs/specs/M3.md`（**已冻结**，§五裁决已并入）；开工顺序 = **6 批 × 3 片**（§二·8：批次内可换序、某片偏大可拆、依赖紧的可合并）；每片一个会话、独立提交、`./gradlew build test lint` 全绿）：
  - **批次一 · 把一笔账记全（P0）**
    - ~~`M3-0` schema 地基（v5→v6 一次迁移）：新表 `tx_templates` / `template_tags` / `recurring_rules` / `bill_filters` / `asset_snapshots` / `debts`；新列 `transactions.reimburseCents` / `recurringRuleId` / `debtId`、`accounts` 年费四列；`6.json` 入库 + `MIGRATION_5_6` + migration 测试~~ → **已完成（2026-10-12，见「已完成 · M3」）**
    - ~~`M3-1` 标签（无迁移）：`TagDao` + 记一笔「标签」抽屉（两级、新建即选、多选）+ 标签管理页（重命名/软删/隐藏）+ 详情页标签行 + **分类隐藏（`categories.hidden` 启用，同口径，顺带修"副标题写隐藏、实际软删"）**~~ → **已完成（2026-10-12，见「已完成 · M3-1」）；附带勘误：`categories.hidden` 并不存在，本片用 `MIGRATION_6_7` 新增了一列（D074）**
    - ~~`M3-2` 退款/优惠录入（M1 遗留 D057）：两个 chip + **保存净额**（D069：金额存净额、编辑反推原价、往返幂等）~~ → **已完成（2026-10-12，见「已完成 · M3-2」，含看板遗留的首页全额退款标记）**
  - **批次二 · 记全剩下三种（P0）**
    - ~~`M3-3` 报销录入：`reimburseCents` 启用 + 「不报销」chip 改可点（三态 + 报销账户）+ 资产页报销卡改口径（缺省 0 = 按净额全额）~~ → **已完成（2026-10-12，见「已完成 · M3-3」；连带的导出行李也做了：报销账户/金额两列进导入）**
    - ~~`M3-4` 债务（**实体式** D068；表已在 v6，本片无迁移）：记一笔第四 Tab + 债务列表/详情 + 净资产并入（账户净额 − 应付 + 应收）+ 负债类账户允许负初始余额~~ → **已完成（2026-10-15，见「已完成 · M3-4」；一次会话交付完，没有拆成两次）**
    - ~~`M3-5` 模板记账：`tx_templates`/`template_tags` + 管理页 + 记一笔「模板」chip 预填 + 保存后「存为模板」~~ → **已完成（2026-10-15，见「已完成 · M3-5」）**
  - **批次三 · 时间与找账（P0/P1）**
    - ~~`M3-6` 周期记账：规则表 + 期次推进纯函数（月末对齐）+ **打开即补**（D070）+ 管理页启停 + 幂等去重~~ → **已完成（2026-10-15，见「已完成 · M3-6」）**
    - ~~`M3-7` 组合筛选 + 精准导出（关键词/类型/日期/账户/分类/标签/金额区间 + 结果导出 CSV）~~ → **已完成（2026-10-15，见「已完成 · M3-7」）**
    - ~~`M3-8` 账单组合：`bill_filters` 保存 / 应用 / 重命名 / 删除（多值条件逗号分隔 + 编解码纯函数）~~ → **已完成（2026-10-15，见「已完成 · M3-8」）；批次三收官**
  - **批次四 · 资产与钱（P1）**
    - ~~`M3-9` 资产模块：账户停用（`archived` 启用）+ 流水迁移账户（解 D026「有流水删不掉」）+ 自定义分组（`groupName` 启用）~~ → **已完成（2026-10-15，见「已完成 · M3-9」）**
    - ~~`M3-10` 理财快照与盈亏：`asset_snapshots` + 理财详情/记快照 + 资产页理财卡接真数据~~ → **已完成（2026-10-15，见「已完成 · M3-10」）**
    - ~~`M3-11` 分期房贷计算器（等额本息/本金，表驱动）+ 信用卡年费（账户四列 + 资产页提示，不做通知）~~ → **已完成（2026-10-15，见「已完成 · M3-11」）；批次四收官**
  - **批次五 · 个性化（P2）**
    - ~~`M3-12` 多主题（D071：只换品牌色相 4 套 青绿/靛青/墨灰/赭石；顺带把启动图标对齐 `#0F766E`）~~ → **已完成（2026-10-15，见「已完成 · M3-12」）；批次五开工**
    - ~~`M3-13` 首页数据自定义（月卡汇总块勾选 + 预算块开关）~~ → **已完成（2026-10-15，见「已完成 · M3-13」）**
    - ~~`M3-14` 自定义统计（模块开关 + 顺序 + 标签/资产模块）~~ → **已完成（2026-10-15，见「已完成 · M3-14」）；批次五收官**
  - **批次六 · 产出与视图（P2）**
    - ~~`M3-15` 愿望清单（D072：攒钱目标；**单独迁移 v7→v8**——M3 唯一允许的迁移，`wishlist_items` 建表）~~ → **已完成（2026-10-15，见「已完成 · M3-15」）；库版本 7 → 8**
    - ~~`M3-16` 数据小卡片（月汇总 / 占比 / 小票三款，Canvas→Bitmap→FileProvider 分享）~~ → **已完成（2026-10-15，见「已完成 · M3-16」）**
    - ~~`M3-17` 日历视图（D073：**进 M3**；默认不做农历/节气）~~ → **已完成（2026-10-15，见「已完成 · M3-17」）；M3 收官**
- **M2 遗留与测试坑**（M2 切片本身已收尾，逐片细节见「已完成 · M2 应用内更新」）：
  - `Available` 态没有「重新检查」（清单变旧只能重启 App 刷新——M2-8 真机发现，未改）
  - 托管渠道与镜像待定：现为 GitHub 单地址顶着（D066 明知偏离两条规格线）——0.2.0 前换国内主地址 + `urls` 补足 2 条
  - 测试坑①AGP 给 Kotlin 单测传 `-no-jdk`，`com.sun.net.httpserver` 编译期不可见 → 假服务自写 `FakeHttpServer`（ServerSocket）；②Robolectric 读不到 FileProvider 的 `paths` 元数据，content URI 只能真机验；③`preferencesDataStore` 是静态单例，同测试类各方法共用一份文件，节流键会串（测试先复位）
  - 测试开关 `-PtestManifestUrls` / `-PtestCleartext` 默认关，只在本地闭环用（发布物不受影响）
- ~~**M2 规格**：update.json 协议 + UpdateChecker + 签名密钥生成与离线备份（**首次发版前 applicationId 终审 D013**）+ release minify 打开（D012）~~ → **已收尾**（切片与实测见「已完成 · M2 应用内更新」，遗留见上方「M2 遗留与测试坑」）；applicationId 已终审 `dev.penned.app`（D057，D013 关账）
- ~~**启动图标还是旧品牌色**（UI-16 顺带发现）~~ → **已改**（M3-12：`ic_launcher.xml` 底色 `#2F9E8F` → `#0F766E`，模拟机桌面已核对）；要不要做自适应图标（前景/背景分层）与图标自定义（权益表 free 档）另说——等你点名。
- ~~首页冷启动首帧会闪一下"还没有记账"空态~~ → **已修**（UI-8：`rows` 改可空，读到账本前不渲染空态；模拟机复验首帧只显示"日常账本"）
- ~~记一笔顶栏「编辑」已接通分类管理（UI-4）；「不报销」chip 现为只读禁用样式~~ → **chip 已接通**（M3-3：三态 + 报销账户/金额抽屉，自绘收据图标；顶栏「编辑」早就是分类管理入口）
- ~~一木 xls 导入器不解析 报销 列~~ → **已补**（M3-3：`报销账户` 按名匹配建账户、`报销金额` 落 `reimburseCents`；**状态一律 NONE**——导出里根本没有状态列，猜「已报/待报」会让资产页可报/已报凭空多数字；脱敏 fixture 给「班费支出」填了两列钉住这条口径）。报销明细列仍不做（D008 排除单据明细）；**地址/多币种两列仍等真实样本**（真实 751 行全空）
- ~~日历 Tab 占位文案 "M1 第二批" 已过时~~ → **已改**（UI-6：改为"日历视图还没做，先在首页按日回看流水"）；~~日历页归属仍待 M2 规格明确~~ → **M2 结清**（不在 M2）；**M3 里的归属 = `M3-17`（§五·6 已裁决：进 M3，D073）**，默认不做农历/节气
- ~~首页流水行对全额退款流水显示 "-0.00"~~ → **已做**（M3-2 随片定：行上加「退款 / 全额退款 / 优惠」小标记，且**净额为 0 时金额不再写符号**——"-0.00" 在 `MoneyText` 里一律退成 "0.00"，D075）
- 统计页新增（AI 轮）后的待办：分类详细页流水行没有左滑删除/长按多选（设计如此：详细页只读 + 点进详情）；若希望"在分类里直接删"再补
- 统计页的**期间聚合仍是"拉窗口流水 + Kotlin 重算"**（D004）——实测全库 754 条无压力；将来单账本上万条时，年统计/`近七日`这类窗口要考虑走 DAO 聚合（提案区候选）
- 首页**月卡标题行只显示 `yyyy/m`，月合计在展开块里**（AF 轮用户裁决"不要标题行"，AG 轮改回标题行但仍只要 `yyyy/m`，AQ 轮把汇总搬进卡头展开块）——标题行右侧若要再放个"支 X"是另一回事；顶部已不再有单独的月汇总卡
- **月合计与预算「已用」只覆盖加载窗口**：首页流水是 `observeRecentWithCategory(limit = 500)`（最近 500 条），月卡的汇总块、日头条都由它重算，所以窗口外的老月份合计会偏小；预算的「已用」走的是单独的整月 SQL 聚合（不受窗口影响），本月两者一致。单账本流水上万条时统一走 DAO 聚合（提案区候选）
- **批量删除不加确认**（AH 轮用户只要求单条滑删；长按多选 → 「删除 N 条」仍是直接删 + 10s 撤销）——若要一并加确认，说一声
- ~~**资产页账户行仍是旧左滑手势**（滑过阈值 → 确认弹窗，`SwipeToDeleteRow`）~~ → **已改**（UI-13：与首页同款动作区，只放「删除」；旧组件已删；顺带更正——"有流水拒绝删除"的提示本来就是 Snackbar，不用挪）
- ~~**编辑子分类流水时，记一笔页宫格不显示所属分类**~~ → **已修**（UI-13/D055：`loadForEdit` 改从 `categoryDao().get()` 解析父分类，不再读 `WhileSubscribed` 的空表；新增 `AddEditSubCategoryTest` 两条）
- 首页行**不做位移补位**（UI-14 的取舍）：删/增行时其余行立即归位（被删行仍淡出、新行仍淡入）。要恢复"邻居滑过来补位"就得让折叠另走一条路（例如表头高度不做动画），别再把两条时间线叠一起
- Robolectric 的临时目录会攒（`%TEMP%
obolectric-*`，其中两个 `robolectric-nativeruntime*` 各 40–200MB）：攒到上万条后 Windows 报"系统资源不足"，整批测试会挂一条；定期清一下，或以后加个清理钩子
- 日头条/行内时段是首页与搜索页共用组件（AF 轮）——搜索页因此也变成灰底青绿条的日头条，且搜索页的行分隔线缩进已同步；若以后搜索页要自己的版式，从这里分叉
- ~~添加账户弹窗：点预设 chip 会覆盖已输入的名称~~ → **已修**（UI-5：预设只记类型，名称留作用户输入/默认显示值）
- Robolectric 怪癖：AlertDialog 内含文本输入时 Compose 不 idle（分类新增/重命名、预算金额、账本命名这些弹窗都无法截图测试，入口仅做存在性断言）。**名单选择类已改底部抽屉且可正常 idle/截图**（`PennedSheetTest` 已验证）；**资产页添加账户已改独立页面并可正常截图**（AC-2，13 张新基线）；只剩"纯文本输入弹窗"这几种，后续可试 Robolectric 升级或改用 compose ui-test 的 autoAdvance
- 界面重构新增待办：`IconGalleryTest`/`PennedSheetTest` 是人工评审用截图（非回归基线），M6 打磨图标时可复用
- M1 引入 Roborazzi gradle 插件，把"每次测试都重录截图"改为 record/verify 双任务（D018）
- 升级 robolectric >4.17 时回归试 `@Config(sdk=[36])`（D016）
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

**M4 智能规格 —— 已裁决（2026-10-17，见 `docs/specs/M4.md` §五）**：原 **R1~R16 全部按推荐生效**（用户 2026-10-17 逐条确认"都不改，全部生效"；其中 **R14 关闭为"必须支持 `.xlsx`"**——用户手上的微信导出就是 xlsx），另新增 **B1~B4**（分类**不新建**、匹配不上留空 / 新建账户**按名猜类型 + 预览页可改** / 掩码**照留、各建一个** / **保留映射表 + 预览页明示"将新建 N 个"**）与 **C1~C5**（加 `TxSource.NOTIFICATION` / BYOK **只接受 https** / 「清空已处理」**硬删**不留痕 / 通知点击**过密码锁** / 我的页**「智能」分组卡**）九条裁决。规格已冻结 v1.0，**开工入口 = 「待办 · M4 智能切片清单」**；R1~R16 的逐条口径与代价见规格 §五，这里不再挂 M4 的待裁决项。

（其余无待裁决——统计页交互升级（AI 轮）已于 2026-10-07 批准，见"已完成"；首页月卡折叠 + 悬浮入口（AQ 轮）是用户直接下的需求，已实施）

### 月标题行还想更矮（2026-10-17，待点名）

优化轮 3 把首页月标题行从 68dp 压到 48dp——**48 是「⋯」的 48dp 触控热区（D044）的地板**。想再矮（44/40/36dp）只能把「⋯」（首页显示设置）的触控热区降到 48 以下，或把它挪出标题行另找入口——这是规范里"图标按钮 = 48dp"那条约定的例外，**先记着，等用户点名**。

### 统计页交互升级（2026-10-07，**已批准**）

- 提案：统计界面不好用——只有月统计（无近七日/年/自定义）、支出与收入分两段展示、收支数据点不进详细。参考图：`docs/local/参考/支出占比+数据.jpg`、`收入占比+数据.jpg`、`支出数据详细-食品餐饮.jpg`、`食品餐饮数据详细-晚餐.jpg`
- 用户裁决（2026-10-07）：①时间维度 = **近七日 / 月统计 / 年统计 / 自定义**；②占比与数据**合成切换式**（照参考图）；③数据行**只加笔数**「110笔 60.10%」，**环比上月/同比去年不做**；④柱状图 = **分类详细页按日 + 年统计 12 个月趋势**（自绘 Canvas，不引依赖）；⑤参考图右上角「全部账单」**本轮不做**
- 落地：D032/D033/D034（AI-6 补）+ 实施见"进行中"/"已完成"。参考图只借结构（切换式、hero 三数、下钻链路），不抄其配色/图标/字体
- **本轮明确不做（提案区候选）**：环比上月/同比去年、数据卡的排序与账单图标（⇄/☰）、月份横滑快切条（参考图 `2024 3月 2月…`）、近七日的 ‹ › 前后翻（固定"到今天"）、环形图外置标签 + 引导线、参考图右上角「全部账单」、「未分类」行可点、大窗口的 DAO 侧聚合（仍拉流水 + Kotlin 重算）、IA 文档里其余统计模块（标签占比/报表统计表格/桑基图/本月总结）

### 首页按月包卡（2026-10-07，**已批准**）

- 提案：用户给一张一木「近7天账单」截图（`docs/local/参考/记账条目-参考.png`），要求首页条目按月分类、一月一张"卡片"，并精修日条目与行
- 用户裁决：①**卡里不设标题行**——一个月的数据直接包进一张卡，月收支合计不在首页出现（问了两轮，第二次明确"不要标题行"）②日头条配色走**品牌青绿**（不引参考图的琥珀暖色，不动色板）③日头条**继续吸顶**（不回归）④参考图里的另两处细节**照加**：行内时段 `15:59`、日头条加星期 `周二`
- 用户复看真机后的二次裁决（2026-10-07，AG 轮）：**月标题行加回来，但只显示 `yyyy/m`**（不带收支）；日头条**配色要能一眼分清**（换成 `primaryContainer` 青绿浅底）、**高度压一点**、**字号不动**（字面确认）；月卡圆角要看得出来（AF 版是直角观感，根因见 D030）
- 落地：D029（AF）、D030（AG 返工）；实施见"已完成"。参考图只借结构（月卡分组、日头条版式、行内时段与星期），不抄琥珀配色/插画图标/品牌字体

### 账户管理（2026-10-06，**已批准**）

- 提案：资产页只加不减——加错账户只能删整个账本；新增账户是一张竖排弹窗且截不了图
- 用户裁决（2026-10-06）：①自定义范围 = 每组加「自定义」格 + 自由名称（**不做**自选头像颜色/图标，避免 DB 迁移，也**不做**新建大类）②删除语义 = 名下有流水禁止删除并提示 ③账户行除左滑删除外，点进还能编辑 ④承载形式 = 独立页面
- 落地：D026；实施见"已完成"

### 按参考图重做资产三页（2026-10-07，**已批准**）

- 提案：用户给三张一木资产页截图（`docs/local/一木记账部分界面/资产/`），要求按图重做资产页/添加账户/表单页
- 用户裁决（2026-10-07）：①表单做 **名称 / 余额 / 所属分组 / 备注 / 生效账本** 五行 ②保存按钮留在**底部**（不用参考图的顶栏 ✓）③千分位**全局**加（`3,500.00`）
- 落地：D027；实施见"已完成"。参考图只借结构，不抄品牌图标与插画

### 生效账本改多选（2026-10-07，**已批准**）

- 提案：用户新建第二个账本后在编辑账户页看不到它，且没有多选入口
- 用户裁决：要能勾选多个账本（所属账本之外还想让账户出现在指定账本里）
- 落地：D028；实施见"已完成"

### 截图列表导入（2026-10-18，用户裁决「做账单列表导入」）—— 已完成 2/2 片

**用户四条裁决**：① 走**预览页一次导入**（照账单导入那套）② **不做对账** ③ 带状态的行**全都导入、状态写进备注** ④ 预览行**只能删/切方向**。

- **片 1 `dcde043`**（纯逻辑）：`domain/TextTime.kt`（宽松时间：`10月18日 09:23` / `10-16 22:42` / `昨天 23:38` / 下午晚上 / 全角；无年按最近一次过去推；**认不出不猜**）、`domain/LlmRowsParser.kt`（`{"rows":[…]}` / `items` / 裸数组 / 围栏闲聊 / 模型回单对象；**坏行丢但计数**、上限 300 行、`mergedNote()` 商户+状态）。
- **片 2 `55f34f6`**（闭环）：prompt 改数组（一屏多笔按屏幕顺序、时间换算完整日期、状态进 note）；`LlmClient.extractImageRows`；
  `ScreenshotImportViewModel` 多图合并 + 逐张失败不整体作废 + 去重（库内+批内）+ 账户选择 + 一次导入 + 撤销；
  预览页（计数/合计/账户/行列表/主按钮）；落库走 `BillImportRepository.import(txSource, fallbackAccountId)` +
  `ImportBatchEntity.SOURCE_SCREENSHOT`；`BillDedupe.duplicateFlags`（逐行标重复保留行序）。
- **顺手修的两个真 bug**（都有"没修复就红"的测试）：`AddViewModel.init` 顺序（通知里的收入预填会变支出）、候选池路由硬编码 `NOTIFICATION`。
- **真图实测（4 张，只读，跑完即删的探针）**：全部解码成功、**长边正好 1280**（1080×3946 那张 `inSampleSize=2` → 350×1280；
  2340 的三张 → 591×1280）、payload 84~106KB；合成 16 行回复 → 预览 16 行 → 落库 16 行（批次 `SCREENSHOT`）→ 撤销归零。
- **真机走查（GxuDev）**：我的 → 智能 → 截图导入（新文案在）+ 选真图 → 无 Key 时给引导卡；**库内基线 760 活跃 / 15 软删 / 3 账户 / 2 账本 / 1 批次 / 0 候选 前后一致**，推到模拟机的测试图与临时文件已删。
- **deferred（没有料，不许造假）**：**模型在这 4 张真图上的识别质量**——没有可用 Key，https-only 也挡住本机假服务器；
  识别链路由假服务器 + 合成回复覆盖，真机只验了"壳 + 无 Key 引导"。**有 Key 时自测口径**：一张列表图应出 N 笔（与屏幕行数对得上）、
  金额/时间与图里逐笔对、重复行被标出来。
- **测试**：975 → **1016**（片 1 +23、片 2 +18）；lint 34 warnings 无新品类。

### M4-7 截图导入 × 真实截图（2026-10-18，**口径冲突，等点名**）

用户给了 4 张真实截图（`docs/local/微信支付宝账单截图/`，微信 2 + 支付宝 2，**只读、不入库、不入测试**）。实测结论：**这 4 张全是"账单列表"（多笔），不是单笔支付成功页**，与规格 §三 M4-7 的口径「**一张图只取一笔**（图里有多笔时只认主笔）」正面冲突。

- **实测事实**：① 4 张合计约 **35 行**可见（微信长截图 1080×3946 一张就有 16+ 行，另有 1080×2340 三张各 5~8 行）；现在的 prompt 只要**一个** JSON 对象、出参 `LlmDraftParser` 也只吃**单个**对象（回数组会只读到第一个 `{…}`）→ **每张图最多 1 笔、其余全丢**；且"认主笔"没有判据（模型可能给首行、也可能给金额最大那行，不可预期）。② 若模型照 prompt 的「认不出是账单就回 `{"type":"UNKNOWN"}`」办（列表图确实不是"一张支付截图"），`parse` 因**缺金额**返回 null → 走 `BAD_RESPONSE` → 用户看到 **"没看出这是账单——换一张试试"**（失败卡）——这是很可能的实际结果。
- **时间口径对不上**：两家列表里的时间**都不是** `yyyy-MM-dd HH:mm`——微信写 `10月18日 09:23`、支付宝写 `10-16 22:42` 与 `昨天 23:38`（无年）；`LlmDraftParser.parseTimeMinute` 只认四位年 → **一律解析成 null**。更关键：**记一笔页的预填不带时间**（M4-7 规格只写了"金额/方向/备注"，`add` 路由参数也只有 `pendingId/pendingSource/pendingType/amountCents/note`）→ 这批候选入账后**日期一律是"现在"**（9 月那几行会差一个月）。
- **降采样后可读性（实测复刻 ImagePrep 口径）**：长边 1280 下，1080×2340 的三张 → **591×1280**（标题字高 ~19-20px，金额 ~25px，**小字 ~8px**），1080×3946 的长截图 → 先 `inSampleSize=2` 解码再缩 → **350×1280**（标题 ~14px、金额 ~15px）。商户名与金额仍可读（我逐张看过产物），**小字（时间/分类/状态）基本读不出来**；上传体积 66~82KB（base64 89~110KB）。
- **其它形态噪声**：微信长截图带状态栏/工具栏/「贴纸」浮层/月份分组表头；支付宝带「餐饮美食/生活服务」分类、「自动扣款成功/等待确认收货」状态、以及「本月已省 0.13元」这种干扰数字；商户名被截断带 `…`（`广西大学热水（钧富凰）水表/...`）。**好消息**：金额与方向都在（`-67.07` / `+88.00` / `¥683.81`），`MoneyParse`（吃 ¥/千分位）与方向判定够用。
- **重叠/去重**：两张微信截图是**同一个月、相隔 37 分钟**拍的（表头 387.57 → 398.57，后者多一行），两张支付宝也覆盖同一个 10 月——若一次导入多张，**行级重叠会重复入账**；CSV 路的 `BillDedupe` 口径（单号优先 + 无单号兜底键）在这里没有单号可用，需要新的兜底键（日期 + 金额 + 商户）。
- **两条出路（等裁决）**：**A. 列表图批量识别**——prompt 改"回数组，每行一笔"，出参走新的数组解析 → 一批候选进「待确认」+ 行级去重 + 逐行带时间（正解，要改规格这条口径）；**B. 只认单笔、列表图劝退**——prompt 里明说"多行列表就回 UNKNOWN"，改动最小、最优诚实，但这批真实场景（补记历史账单）就用不上。**我推荐 A（先做最小版：数组出参 + 直接进候选池，不做逐张预填 + 去重）**。
- 顺带两条与 A 无关也该修的：**时间解析补 `M月d日 HH:mm` / `MM-dd HH:mm` / `昨天|今天|前天`**（无年按"最近一次"推）；**预填把时间带过去**（现在 `occurredAtEpochMinute` 只存在候选行里、没进记一笔页）。

### 测试基建：全量门在机器负载下会随机红（2026-10-18，待点名）

**现象**：`./gradlew build test lint`（`org.gradle.parallel=true`，lint 与测试抢 CPU）在负载高时会随机红 **1~2 个** Compose 流程用例——今天一口气红了 **8 个不同类**（`AddDebtFlowTest` / `AddReimburseFlowTest`（2 次）/ `AddEditSubCategoryTest` / `TagsFlowTest`（2 次）/ `AddHiddenCategoryTest` / `BillFiltersFlowTest` / `AboutViewModelTest`），**每一支单独 `--rerun` 都绿**，失败签名集中在自家 helper 的固定时长轮询上（`等数据库结果超时` / `等界面超时` / `Failed to inject touch input`）。跑绿仍是常态（今天多次全绿：975 / 998 / 1016 各一轮）。

**为什么值得单独记一条**：这不是本特性引入的（这些类一行没碰，M4 期间就记过一次同型偶发），但每次都会让人重跑 1~2 分钟、还会让人怀疑是不是自己改坏了。

**三条候选修法**（等点名，别顺手改）：① 把这些 helper 的固定轮询换成"**推 looper + 条件等待**"（`shadowOf(Looper.getMainLooper()).idle()` + 更长的 deadline——今天的坑就是 DB 写入落在背线程、主 looper 没被推）；② 给测试任务限制并行分叉（`maxParallelForks = 1`，慢一点但稳）；③ DoD 里把"全绿"拆成"`test` 单独跑全绿 + `lint` 单独跑全绿"（现在 `build test lint` 三条同跑才有互抢）。

### M4-8 走查发现的两处小件（2026-10-17，待点名）

- **`SwipeActionRow` 在卡片里有一条 1px 红发丝**：隐藏动作块（红底「删除/丢弃/上移」）压在内容层底下，卡片圆角裁剪的边缘会漏出约 1px 的红色——像素扫描实锤：`m4_keyword_rules.png` 右缘 x=369..383 有 42 个粉点、`m4_pending_list.png` 同样位置 168 个（含金额文字的抗锯齿）；浅色几乎看不出，深色更明显。**影响面**：首页列表 / 资产页 / 关键词规则页 / 待确认页（同一组件）。**改法二选一**：关闭态干脆不画动作块（`derivedStateOf { offset.value < -0.5f }` 门控），或给内容层加 1px 外扩的底。**为何本片没顺手改**：动的是四个页面共用的组件，M4 收口期不扩面——等点名再单独一片，补组件回归测试。
- **Markdown `**` 被直接印在页面上**：`AutoEntryScreen` 的隐私句（本片已修，改成 `buildAnnotatedString` + `SemiBold`）；**同型还在**：`DebtEditScreen`（删除确认"流水量保留"）、`RecurringEditScreen`（"已生成的流水保留"）、`TemplateEditScreen`（"已用它记下的流水不受影响"）、`ScreenshotImportScreen`（"入账永远要你点保存"）、`BillImportScreen`（AI 外发口径那句）。改法：一处补一个 `renderMarkdownBold(text)` 小工具（或统一去掉星辰号）——**一次清一轮，别一处处点**。

### 日历：白卡/流水列表上也能横滑切月（2026-10-16，待裁决）

优化轮 2 起横滑的响应区是**网格那一行**（pager 所在处）——收起态只有一行高，手指落在下方白卡上横滑不切月（会被当成点行/滚动）。要全页可滑得让 pager 的命中区盖住整页（代价：与流水行的左滑动作区、列表竖滑三者再分一层手势仲裁），**先记着，等用户点名**。

### 后续提案候选（本轮明确不做）

- **日历优化轮没做的想法**（都够一条独立切片，别顺手塞）：
  - **热力的"按住看详情"气泡 / 趋势线**（照统计页的按住浮层先例，格子上按住显示"那天支/收 + 笔数"）——本轮明确不做（格子已经能看到两个数）
  - **月总结点一下跳统计页**（把"这个月的账"接过去）——要定交接后的期与返回行为
  - **收起态做成"按周翻页"**（左右滑切周而不是切月）——与"滑切月"是两套语义，本轮统一成"切月，周条跟着选中日走"（D092）
  - **热力标尺改成"账本内全局分位"**（跨月可比）——那样小月会整片几乎同色；本轮按当月各向最大额分档（D091）
  - **日历里的农历/节气/节日**仍是 M3-17 的边界（要新数据来源）

- ~~账户停用/归档、把流水迁移到另一个账户（有流水的账户目前删不掉）~~ → **已进 M3 规格**：`M3-9`（`accounts.archived` 与 `groupName` 列 v1 就有，零迁移）
- 新建账户大类（充值账户/二手货物…）、自选头像颜色与图标（要扩枚举或加 accounts 列）
- 参考图里我们没做的表单行：简称、卡号、账户图标/夜间图标、收益同步、计入总资产（各自都要新列或新语义；「计入总资产」还牵动 D004 的重算口径）
- 把账户**移出**所属账本（= 迁移账户归属，是另一件事；现在所属账本恒定可见且不可取消勾选）
- ~~初始余额不支持负数~~（信贷/负债的存量欠款暂时只能记 0）→ **已进 M3 规格**：`M3-4` 放开 `DEBT`/`CREDIT` 两类账户的负初始余额（"欠款怎么输入"= 余额框直接输 `-1200`）
- **动效候选（AP 轮明确不做）**：预测性返回手势（`PredictiveBackHandler`，返回手势拖到一半能看见下页跟着走）、共享元素转场（分类头像/标题从列表 morph 到详情页）、**记一笔改从底部升起的模态**（现在是和别的下钻页一样的右推入）、**Tab 之间跟手横滑**（现在固定淡入淡出）；另有"转场时长/视差比例做成可调设置项"这一档（现在写死在 `MotionSpec`）

## 已完成

### M4 智能（9 片 / 4 批，2026-10-17~10-18）—— 规格 v1.0 冻结后全量交付

- **一句话**：从"记一笔只能手打"到"文本 / 账单文件 / 截图 / 通知四条来源都能进账"，**AI 全程只做辅助**（分类永远本地映射、金额/时间/单号不出设备、默认关、只收 https），敏感面收在"候选池 + 人工确认"里。

| 片 | 交付内容 | 提交 | 测试（累计） | 走查 | deferred |
|---|---|---|---|---|---|
| `M4-0` | 库 v8→v9（`keyword_rules` / `pending_entries` / `transactions.externalId` 非唯一索引 / 批次三列）+ `KeywordRules.match` + `MoneyParse` + 7 个设置键 + CSV 补 `externalId` | `8a23c67` | 767 | GxuDev 装包验迁移：user_version 8→9、760 活跃 / 15 软删一字不变 | — |
| `M4-1` | 分类关键词规则：我的页「智能」卡 + 规则页（增删启停上移/删除、分类或标签双目标）+ 四处接入 | `fc340bb` | 785 | GxuDev 真机走查（含"肯打鸡"链路）；**顺手修 `SwipeActionRow` 隐藏动作可被点到** | — |
| `M4-2` | BYOK：`LlmClient`（只 https）+ 出参强校验 + AI 设置页（Key 密文 / 测试连接三态 / 隐私文案一处） | `9fd1ed1` | 826 | GxuDev 验"未配置 / 非 https / 连不上"三条路径 | **真机 AI 链路**（无 Key；https-only 挡本机假服务器） |
| `M4-3` | 一句话记账：本地解析器 + 结果卡五行逐行可改 + 首页长按快捷入口 + 已记账/撤销 | `7595559` | 850 | GxuDev 走查（软键盘盖保存键的坑已记） | AI 兜底按钮的真机链路（同上无 Key） |
| `M4-4` | 账单读取层：四容器（CSV 编码探测 / xlsx / xls / zip）+ 两家规则表 + 字段口径 + 去重（无 UI、不落库） | `5ffb5b6` | 882 | 真实两份账单跑临时探针核对（微信逐分对上；**支付宝头部与明细不一致 → 口径勘误 D110**） | — |
| `M4-5` | 账单导入预览与落库：第三入口 + 预览七段 + 账户映射/新建 + 分块落库 + 批次计数 | `be81a2a` | 906 | **GxuDev 两份真实账单全链路**：微信 42 行对账通过；支付宝 507 行导入、再导 0 重复；修两个真 bug（分批查全 / 去重后计数） | — |
| `M4-6` | LLM 辅助导入：列映射建议 + 商户分类建议 + 存为关键词规则（只发表头 / 掩码样例） | `b1a4a17` | 920 | 假服务器端到端（含**外发边界**断言） | 真机 AI 链路（无 Key） |
| `M4-7` | 截图导入：压缩 1280/JPEG85 + 相册多选/拍照/系统分享三入口 + 失败卡 + 候选池与预填回写 | `6b05383` | 939 | GxuDev 验入口与"未配置 → 引导"路径；假服务器 + 现场生成 PNG 覆盖识别链路 | **真机真图 ≥6 张**（`docs/local/` 没有支付类截图） |
| `M4-8` | 自动记账：通知监听 + 解析规则表 + 候选池 + 提醒 + 待确认页 + `TxSource.NOTIFICATION` | `7128d0a` | 975 | GxuDev 授权/白名单/开关/条数 + 设置落盘；库内基线原样 | **`adb` 造通知的全链**（本模拟机镜像不投递；解析/落库/提醒由单测直喂 `StatusBarNotification` 覆盖） |

- **跨片核验（里程碑级）**：① **零新增依赖**（`gradle/libs.versions.toml` + `app/build.gradle.kts` 依赖清单自 M4 起点零变化，实测 `git diff` 为空）；② **Manifest 只多四处**：`POST_NOTIFICATIONS`、通知监听 `<service>`（带 `BIND_NOTIFICATION_LISTENER_SERVICE` + label）、M4-7 的 `ACTION_SEND` filter——没有存储/相机/短信/无障碍权限；③ **外发只有两条**：AI（`LlmClient`，只 https、默认关、未配置或非 https 时**一个包都不出**）与 M2 的更新检查（`OkHttpFetcher`）；④ **AI 默认关**（`llm_enabled` / `auto_entry_enabled` 默认 false，有测试钉）；⑤ **金额一律 Long 分**（新加的 `MoneyParse` 走 BigDecimal，全仓无 `toDouble` 存钱）；⑥ **库 v9 + `9.json` 入库** + `MIGRATION_8_9` + migration 测试；⑦ **全表软删 + 唯一硬删例外**（`pending_entries.clearProcessed`，D104 记明）。
- **规范**：`docs/界面规范.md` **§13·补三十一 ~ 补三十七** 七节（分类关键词 / AI 设置 / 一句话 / 账单导入预览 / 通用列映射 + AI 辅助 / 截图导入 / 自动记账与待确认）。
- **决策**：**D100~D110**（D100 冻结、D101 xlsx+GB18030、D102 分类不新建、D103 MoneyParse 分家、D104 硬删例外、D105 NOTIFICATION/https/密码锁、**D106 DataStore 缓存 + SwipeActionRow 关闭态**、**D107 Excel 序列日期折分**、**D108 LLM 两条出口**、**D109 通知监听口径**、**D110 对账口径修正**）。
- **体量**：release APK **2.46 MB**（2,581,175 B，本地 `assembleRelease`，R8 通过）；与线上 0.1.0 的对比待有网/CI 时核。
- **移交 M5 的三件事**：① **`M4-7` 真机真图验收**（6 张支付类截图，缺料）；② **真机 AI 链路验收**（要一个可用 Key + https 端点）；③ **`adb` 造通知全链**（换真机或别的模拟机镜像）。另有两处**非阻塞**的视觉小件记在提案区（`SwipeActionRow` 卡片右缘 1px 红发丝；几个页面把 Markdown `**` 印在脸上）。
- **未做的事（按规格）**：不 bumpVersion、不打 tag、不 push、不发 Release；不动 `AGENTS.md`、不动已冻结规格；不做跨片偷跑。真实账单**只读、只对账**，原始行不入库不入屏不进测试。

### 记一笔与提示条（时间入口合并 + 提示条顶部重设，2026-10-17，用户反馈两条）

- **两片独立提交**：
  1. `1ee927e` **时间入口合并**（D097）：删「时间」chip（`AddDate.timeChipLabel` 一并删）、`chipLabel(day, today, time)` 调过时分才带 `HH:mm`；`AddTransactionScreen` 的日期弹窗改自家 `PennedDialog`（日历只剩网格：`title/headline/showModeToggle` 全关 + `containerColor` 透明）+ 发丝线 + 「🕐 时间」+ `TimeWheel(rows = 3)`（`WheelColumn` 新加 `rows`，默认 5）；`TimePickerDialog.kt`/`TimePickerTest.kt` 删、`TimeWheel.kt`/`TimeWheelTest.kt` 新；**弹窗宽度 `usePlatformDefaultWidth = false` + `widthIn(max = 400dp)`**（真机 320dp 弹窗会把 336dp 网格的周六列裁掉——走查抓到）
  2. `cc792dc` **提示条**（D098）：`PennedSnackbar` 换成 `surfaceContainerHigh` 浅色卡（零阴影、`primaryInk` 动作键，不再用 M3 `Snackbar` 的 6dp 阴影）；首页 host 挪 `TopCenter`、`HomeAddEntry` 不再跟着隐藏；`ContrastTest` 两组新配对。**后续（D099）**：用户反馈"停留太长"——带「存为模板」那条 10s → **5s**（`Indefinite` + `withTimeoutOrNull(HOME_FLASH_MILLIS)`，提前点动作立刻走；撤销条仍 10s），`AddFlowNavigationTest` 加"7s 内必须收掉"的停表断言
- **测试**：`./gradlew build test lint` 全绿；`AddDateTest`/`TimeWheelTest`/`AddScreenScreenshotTest`（含 `dateDialogAdjustsTimeToo`）/`AddTimeFlowTest` 全改走新入口；`HomeScreenScreenshotTest.captureHomeWithFlashBanner`（断"横幅在屏上半部 + 记账入口仍可见"）、`DarkThemeScreensTest.darkHomeFlash`、`AddFlowNavigationTest` 同两条断言
- **模拟机（GxuDev，用户真实数据）走查**：信息条只剩「今天 10月17日」→ 点它开弹窗（日历 + 时分滚轮同屏）→ 滚到 16:38 → chip 变「今天 10月17日 16:38」→ 落库逐分对上；编辑回填、不动直接存一字不差、改 09:05 只动时间；保存后横幅在顶部（y≈212）且 `添加一条新记账` 照常在底部（y≈2009）。**开工重记基线 758 活跃 / 15 软删（用户自用新增两笔）、结束后还原一致：accounts 3/4、books 2/1、user_version 8**
- **截图归档**：`docs/local/界面重构验证/fb_*.png`（10 张，不入库）
- **本轮新增的坑（写给下个会话）**：M3 `DatePickerDialog` 的 content 会被**插在它自己的标题与日历之间**（实测时间条与标题重叠）——要"日历 + 附加内容"就别用它，改自家 `PennedDialog`；M3 日历网格最小 336dp，弹窗默认宽度（280~320dp）**真机会裁掉最后一列**，得 `usePlatformDefaultWidth = false` + 满宽；Robolectric 里弹窗刚关时主窗口节点还算"not displayed"——点完「确定」要先 `waitUntil` 弹窗文本消失再断 chip，且无状态宿主得把回传的时分喂回去才会重渲 chip
- **遗留（明确没做）**：提示条**不可点掉**（10s/4s 自动走；要"点一下关掉"再说）；时分滚轮 60 行要滚好几下才到尾（"现在"是默认值，多数人不会去动它）

### 记一笔与首页优化（时间到分 + 信息条单行横滑 + 月标题行压缩，2026-10-17，用户点名三条）

- **三片独立提交**：
  1. `99e7864` **时间到分**：`AddDate` 三个时分纯函数（`timeChipLabel` / `timeFromMinute` / `epochMinuteFor`、`epochMinuteForEdit` 多一个 `time` 参数——未选完全退化原行为、编辑"日与时都没动"原值原样）+ `ui/components/WheelColumn.kt`（从 `YearMonthWheel` 抽出单列滚轮，零视觉改动）+ `TimePickerDialog`/`TimeWheel` + `PennedIcons.Clock` + 「时间」chip（四个 Tab）+ VM 接线（`timeOfDay`、`loadForEdit` 回填、「再记」保留）；表驱动：`AddDateTest` 12 条、`TimePickerTest` 7 条、`AddTimeFlowTest` 真库长链 1 条
  2. `6076b2c` **信息条单行横滑**：`FlowRow` → `LazyRow`（内部滚动 tag `add-chip-row`），取代 D044/规范里"FlowRow 换行"那半条；`AddChipRowTest` 钉单行高与首末两枚可达；7 个既有测试文件按"视口外不 compose"补 `performScrollToNode`（`revealChip` / `awaitChip`）
  3. `cc0491f` **月标题行压缩**：`MonthTitleRow` 去上下内边距 68→48dp（行高 = 「⋯」48dp 热区）、加 `HOME_MONTH_TITLE_TAG` + `HomeMonthToggleTest` 高度回归一条
- **测试与截图**：`./gradlew build test lint` 全绿（720 测试）；截图基线全量重录并逐张过目（记一笔浅/深/1.5×、时间滚轮/弹窗/已选 chip、首页浅/深/1.5×、多月份卡）
- **模拟机（GxuDev，用户真实数据）走查**：时间 chip 默认「现在」→ 滚轮真手指滚两列选 16:38 → chip 回显 → 落库 `dateEpochMinute` = 当天 16:38（与 SQL 逐分对上）、首页行显示 16:38；详情 → 编辑 chip 回填 16:38、**不动直接存分钟一字不差**、改 09:05 后库内 = 当天 09:05；信息条一行、手指横滑到「不报销」再滑回；月标题行 48dp、折叠/展开与「⋯」抽屉正常。**临时流水事后按 SQL 清掉，库内还原基线：transactions 756 活跃/15 软删、accounts 3/4、books 2/1、user_version 8**
- **截图归档**：`docs/local/界面重构验证/opt3_*.png`（不入库）
- **本轮新增的坑（写给下个会话）**：`LazyRow` 视口外的 chip **不 compose**——按文本找节点的断言会"找不到"或"找不到也算过"，凡点/断长行里的 chip 一律先 `performScrollToNode`；滚轮的时/分两列都是数字，**点数字要按列限定祖先**（`hasAnyAncestor(hasTestTag(...))`——08:16 这种时刻两列各有一个「16」）；`adb exec-out cat` 只拷 `penned.db` 会**丢 WAL 里的新数据**，要连 `-wal`/`-shm` 一起拷（SQLite 打开时自动重放）——只有 db 文件的话"刚存的流水查不到"
- **遗留（明确没做，想清楚再说）**：①月标题行**更矮**需把「⋯」热区降到 48 以下（D044 的例外，见提案区）②时间弹窗**没有"现在"快捷键**（要退回"不指定时间"只能重开页面）——够一条独立切片再说

### 日历优化轮 2（跟手覆盖 + 灰字 + 去按钮 + 总结挪位，2026-10-16，用户点名五条）

- **三片独立提交**（纯逻辑 / 视觉 / 交互）：
  1. `bf89bda` **纯逻辑**：`CalendarCollapse` 换成"进度模型"——`consumeUp/consumeDown`（吃 `min(位移, 剩余行程)`、返回**实际吃掉的量**，剩下才给列表滚）、`snapTarget`（速度方向明确以速度为准，否则过半收满）、几何三件（`viewportHeight` / `weekShift` / `otherRowAlpha`，全由 p 一个值推导）+ `CalendarMath.weekIndexOf`（某天在本月网格的行序号，网格外夹两端）；表驱动单测重写（换算/吸附/几何/越界）
  2. `93e728c` **视觉**：月份行去 ‹ ›（连端点禁用与 `onShiftMonth` 链路、`CalendarMath.shiftMonth` 一起删）、**年月居左 + 月总结紧贴其下**（同一枚 xs 内边距 → 与白卡左缘对齐）、**非本月无色灰字**（`onSurfaceVariant`，删 `outOfMonthAlpha`、删"三个月三根尺"的 `heatFor`——每页各按自己的月算热力）、**日流水改白卡面板**（`PennedCard` + 零阴影，包日头条/流水行/空态）；`ContrastTest` 换成"非本月灰字：下限 2:1 且比正文淡"；截图基线重录（含 400dp 长金额退化 `m3_calendar_long_amounts`）
  3. `b9b5e72` **交互**：`p` **每帧 1:1 跟手**（页级竖滑兜底 + 列表 `nestedScroll` onPre/**onPost**Scroll，三条入口喂同一根 p）、**松手吸附**（页级"只看不消费"的观察者喂 `VelocityTracker` 统一收尾；只有 p 停在 (0,1) 才吸附）、点箭头走同一根 p 的补间（`PennedMotion.base`，缩放 0 直接落位）、**pager 常驻**（收起 = 整月网格裁到一行，删周条分支 / `shiftDirection` / 二值 gate / `weekStart`/`weekDays`）、视口 `clipToBounds` + 内容 `wrapContentHeight(unbounded = true)`（不然视口收成一行会把整月压扁）
- **测试**：`CalendarCollapseTest`（7：换算表、吸附表、几何表、越界夹取）、`CalendarMathTest`（+周序号；删 `shiftMonth`/`weekStart`/`weekDays` 的旧断言）、`CalendarPagerTest`（17：**按几何断言**"收满了没有"——视口高 == 一行高/整月高；跟手中途不松手、慢拖弹回、快甩收满、短列表也能收、白卡贴视口下缘且 p<1 不滚、收满后剩余位移给列表滚、回顶才展开、收起态横滑切月、跨月那一行）、`CalendarScreenTest`（去按钮的负向断言 + 窄列长金额退化）、`ContrastTest`、`DarkThemeScreensTest`（深色收起态重录）；`./gradlew build test lint` 全绿
- **模拟机（GxuDev，用户真实数据）走查**：月份行新版式 ✓（无 ‹ ›）；非本月 9/29 12.00、9/30 27.30 **灰字无色**、点 9/30 照样跳 9 月（总结 **支 49.30 / 余 -49.30** 与库内一致）；4.5s 长上滑**中途两帧**拍到"视口收缩 + 非本周行渐隐 + 白卡顶上来"，松手收满成一行（那一行 = 选中日所在周）；收起态从**白卡**（当天只 1 条、列表滚不动）上滑也能收、从那一行往下拖能展开；收起态横滑切月（10 月→9 月→8 月，选中日跟着同号走）；点标题 → 滚轮首帧落在当前年月 → 跳 2025-07（空月 0.00 三项）；「今」回本月 + 今天。**走查前后库内数字一致（只动 UI）：transactions 756 活跃/15 软删、accounts 3/4、books 2/1、user_version 8**
- **截图归档**：`docs/local/界面重构验证/cal2_01…cal2_12.png`（12 张，不入库）
- **本轮新增的坑（写给下个会话）**：`VelocityTracker` 要**至少 3 个按下采样点**才算得出速度（2 个点直接回 0）——真机手指一动一串 move，Robolectric 里得拆两次 `moveBy`；`input swipe` 的起点要落在**节点真实 bounds**（这次横滑落在白卡上 = 被当成点击、点开了账单详情）；fixture 用 12 条流水时"收起 + 一划就见顶"，验"离顶还远不弹开"要更长列表（测试里另开 `longRows()`）
- **遗留（明确没做，想清楚再做）**：①**白卡/流水列表上横滑不切月**（横滑响应区 = 网格那一行；要扩到整页得把 pager 与列表的手势再分一层，见提案区）②收入热力**真机没数据可验**（用户账本没有收入流水，只由截图/纯函数覆盖）③点击气泡/趋势线仍不做

### 日历优化轮（M3-17 打磨，2026-10-16，用户点名六条）

- **三片独立提交**（口径层 / 月视图视觉 / 交互）：
  1. `adfff69` **口径层**（纯函数 + 表驱动，无 UI）：`domain/CalendarHeat`（四档分档：当月标尺、支出/收入各用各的尺、`ceil(额×4/最大额)`、有账至少 1 档、**两面都有账支出优先**）、`HomeGroups.monthTotalsOf`（月总结**只算当月**，邻月日子不进）/ `balanceOf`、`domain/CalendarPages`（页↔月映射：页序新→旧、下界 = 最早有账的月兜底 12 个月、上界 = 本月、弹窗年月列按"能翻到"收窄）、`CalendarMath.followDay`（翻月跟随选中日：同号、没有就夹月末）/ `weekStart` / `weekDays`、`ui/calendar/CalendarCollapse`（收起状态机：往下看累计 ≥24dp 才收、回到列表顶才展开）
  2. `165194f` **月视图视觉**：`ui/theme/Heat.kt`（四档淡底 + **成对字色**，浅色换深一档 / 深色换亮一档；色相只来语义色、随主题不变）、月小字总结行（收绿/支红/余灰、空月 0.00 三项、超宽整行去千分位）、**三层叠加规则**（热力 = 整格底 / 选中 = 品牌 2dp 描边 + 字重 / 今天 = 品牌实心圈）、非本月**统一变淡** `outOfMonthAlpha = 0.45`（整格一个因子）；`ContrastTest` +3 方法（热力 16 对成对、四档逐档递增、品牌色件压热力底 3:1 四色相全跑、变淡下限 2.0:1）
  3. `2163885` **交互**：`HorizontalPager`（新→旧 + `reverseLayout`、`settledPage` 才回写、`testTag` 可滑）+ ‹ › 端点禁用 + 标题可点开**共用滚轮**（`YearMonthPickerDialog` 从 `ui/stats/` 挪到 `ui/components/`，统计页一起改引用）+ 上滑收起成周条 / 回顶展开（`nestedScroll.onPreScroll` + 网格竖滑，同一个 gate）+ 条外日子画空格
- **测试**（688 条全绿）：`CalendarHeatTest`（4）、`CalendarPagesTest`（4）、`CalendarSummaryTest`（3）、`CalendarCollapseTest`（3）、`CalendarViewModelTest`（4）、`CalendarPagerTest`（8）、`CalendarMathTest`（+2）、`HomeGroupsTest`（+3）、`CalendarScreenTest`（+3 截图/边界，含"邻月不进总结""热力+今天+选中同屏""空月"）、`DarkThemeScreensTest`（+1 深色周条）
- **截图基线**（浅/深 × 两套主题）：`m3_calendar_month` / `m3_calendar_heat_stack` / `m3_calendar_empty_month` / `m3_calendar_neighbour_months` / `m3_calendar_swipe` / `m3_calendar_picker_jumped` / `m3_calendar_week` / `m3_calendar_week_cross_month` / `m3_calendar_ochre`（赭石浅）/ `m3_calendar_indigo_dark`（靛青深）/ `dark_calendar` / `dark_calendar_week`
- **模拟机（GxuDev，用户真实数据；走查前后库内数字一致：transactions 756 活跃/15 软删、accounts 3 活跃/4 软删、books 2/1、user_version 8）**：
  - 10 月热力与真实金额对得上：10/4 16.00 顶档、10/8 6.00 第 2 档；上/下月的 9/29 12.00 与 9/30 27.30 按**9 月自己的尺**上色并变淡
  - 月总结与库内逐分对上：10 月 **支 22.00 / 余 -22.00**、9 月 **支 49.30 / 余 -49.30**、2025-02 **支 103.90 / 余 -103.90**（`sum(amountCents)` 直查对比）
  - 右滑到 9 月（选中日 10/16 → 9/16，翻月跟随 ✓）；本月往未来滑**原地不动** ✓
  - 点标题 → 滚轮（年份 2024/2025/2026、月份到 10 月为止）→ 确定跳到 **2025 年 10 月**（空月 0.00 三项）→ 连按 8 次 ‹ 到 **2025 年 2 月**（热力四档分明：42.20 顶档 / 24.20 / 15.50 / 8.00）
  - 上滑收起成一周（周条 = 选中日那周，邻月日子变淡）→ **收起态横滑切月**：往右拉 → 9 月（标题 2026年9月、总结 49.30、选中日 10/16 → 9/16 跟着走）→ 周条里点 10/1（列表跟着换）→ 下滑展开 + 可点入口收起/展开各走一遍
  - 截图 9 张归档 `docs/local/界面重构验证/m3_17opt_*.png`（不入库）
- **本轮新增的坑（写给下个会话）**：
  - **`coerceIn(min, max)` 在"新→旧"的列表上要写 `coerceIn(last(), first())`**：写反了直接 `IllegalArgumentException: Cannot coerce value to an empty range`（本轮 VM 层撞到，5 条测试一起红）
  - **Robolectric 里弹窗打开后主窗口的节点搜不到**（`onNodeWithText("8")` 只命中弹窗那一份、`hasAnyAncestor(isDialog())` 一个都命中不了）——别拿它当"页面上没有"的证据；要验弹窗内容就**单独渲染内容件**（`YearMonthPickerTest` 先例），入口只做"点了会开 / 取消不改期 / 确定回传"的接线断言
  - **滚轮首帧落位**：`LazyListState(initialFirstVisibleItemIndex = 选中项)` 在"窗口刚建/树很重"时可能还没量好就用上，滚轮停在顶部并把第一格当成"中间那格"报出去——日历里表现为"点标题开弹窗、确定却跳到 2 月"；修法 = 首帧显式 `scrollToItem` + 落位完成后才允许上报
  - **`input swipe` 的起点要落在节点真实 bounds 里**：周条上方一点点就是星期表头，落在那里等于没划（同一个手势换个起点就成功）；一律 `uiautomator dump` 取 bounds，别从截图估坐标
  - **`Modifier.aspectRatio` 是宽/高**、**窄格子文案按字宽算** 这两条 M3-17 的老坑继续有效（本轮把格子几何抽进 `CalendarStyle`：`cellAspect` / `cellGap` / `rowHeight(格子外宽)`，网格、周条、高度动画三处共用一份公式）
- **收尾补的两处**（真机走查发现，不在三片的原始范围里）：**收起态横滑切月**（收起时 pager 不在树上，周条自己接一枚横滑判定 `shiftDirection`，方向与 pager 一致——不做"按周翻页"那套第二语义）、**空态区域也接竖滑**（当天没记录时列表整块不渲染、`nestedScroll` 没有挂点，空态那一整块单独接同一枚手势：上滑收起 / 在顶下滑展开）。两条都有单测（`swipeOnCollapsedWeekStripChangesMonth` / `emptyDayAreaAlsoCollapsesAndExpands`）与真机截图
- **手势要覆盖"没数据时"的表面**：本轮真机走查才发现的坑——单测里列表总塞满 fixture，空态那条路只有手动划才看得出来
- **遗留（明确没做，想清楚再做）**：见下方「提案区」的日历三条；`CalendarPagerTest` 里"收起后列表可滚"用的是 12 条 fixture（真实数据一天只有 1~3 条，列表根本滚不动——**可滚动性只在单测里覆盖**）

### M3-17 日历视图（2026-10-15，M3 收官）

- **交付**（无 schema 变更）：
  - **口径层**：`domain/CalendarMath`——周一开头的 `WEEKDAY_LABELS`、`firstDayOffset`（周一=0）、`daysInMonth`、`rowCount`/`cellCount`（按需 5/6 行）、`gridDays`/`gridStart`、`inMonth`、`shiftMonth`、`monthTitle`、`monthWindow`/`gridWindow`（墙钟分钟窗口）
  - **页面**：`ui/calendar/CalendarScreen`（`CalendarViewModel`：看哪个月 + 选哪天 + 网格窗口流水）——月份行 `‹ 2026年10月 ›` + 「今」、星期表头、月网格（日期 + 支红/收绿小字 + 今天圈 + 选中底 + 上下月灰字可点）、选中那天的日头条 + 当日流水（复用首页 `TransactionRow`/`DayHeaderRow`）
  - **路由**：`calendar` 由 `StubScreen` 占位换成真页面（点行进账单详情）
  - **口径复用**：每日合计走 `HomeGroups.groupByDay`（首页日头条同一份函数）——不自己写第二套按日求和
- **测试**：新增 10 条（**648 条全绿**）——`CalendarMathTest`（5）、`CalendarScreenTest`（4：月网格截图 + 当日小计、点选换列表与空态、翻月与「今」、真库真导航 + **口径回归**（格子小计 == groupByDay 合计））、`DarkThemeScreensTest`（+1 日历深色）；截图 `m3_calendar_month` / `m3_calendar_today` / `dark_calendar`；`./gradlew build test lint` 全绿
- **模拟机（GxuDev，用户真实数据；走查前后库内数字一致：transactions 756 活跃/15 软删、accounts 3 活跃/4 软删、user_version 8）**：
  - 日历 Tab（不再是占位页）：本月网格带出真实数据的小计（9/29 12.00、9/30 27.30、10/4 16.00、10/8 6.00），今天（15）带圈
  - 点 10/8 → 当日日头条「支 6.00」+ 流水行（食品餐饮 · 饮料酒水 16:41 -6.00）→ 点行进账单详情 → 「今」回今天（空态）
  - 截图 3 张归档 `docs/local/界面重构验证/m3_17_*.png`
- **本轮新增的坑（写给下个会话）**：
  - **`Modifier.aspectRatio(x)` 是"宽/高"**：想"竖着更长"要写小于 1 的值（我先写了 1.32 反而更扁，格子里的两行小字全被裁掉）
  - **窄格子里的文案要按字宽算**：57dp 宽的日历格放不下"收 3,000.00"（带前缀+千分位会被截成"收 300…"）——**格子只给颜色不给前缀、金额不带千分位**；精确数在下面的日头条里
  - 日历的 `MonthTitle` 断言用 `CalendarMath.monthTitle(YearMonth.now())`（跟着"今天"走），别写死月份
- **下一条**：M3 收官。后续候选见「待办」与「提案区」（M4 未开工；M2 遗留：`Available` 态没有「重新检查」、托管渠道待换国内主地址）。

### M3-16 数据小卡片（2026-10-15）

- **交付**（无 schema 变更）：
  - **版式层**：`domain/ShareCardLayout`——三款卡片（月汇总 / 当期占比 / 单笔小票）的**计划**：画布尺寸（1080 宽）、一串 `CardText`（文案 + 坐标 + 字号 + **语义角色**）、进度条色块与环形分段；纯函数，颜色只给角色
  - **渲染层**：`ui/share/ShareCardRenderer`——Canvas 出图（品牌条 + 底色 + 色块 + 环形 + 文字）、落 `cacheDir/cards/`、`shareIntentFor(uri)`（拼装）/ `shareIntent(context, file)`（FileProvider 解析）、`share()` 拉起系统面板；`ShareCardSharing` 三处入口共用一步
  - **主题**：`PennedTheme` 暴露 `currentThemeAccent()`；颜色经 `PennedBrands.preview(accent, dark)` 映射（换主题卡片跟着变；颜料细件色走 `PennedPigment` 原始色，渲染器不在 composition 里）
  - **入口**：首页「⋯」抽屉第一行「分享本月小卡片」、统计设置页「分享」卡第一行「分享当期小卡片」、账单详情页底部「分享小票」；`file_paths.xml` 加 `cards/`（同一个 FileProvider）
- **测试**：新增 10 条（**638 条全绿**）——`ShareCardLayoutTest`（6）、`ShareCardRenderTest`（4：三款出图 + 换主题 + 落盘 + Intent 拼装）；出图 `m3_card_summary` / `m3_card_share` / `m3_card_receipt` / `dark_card_summary`；`./gradlew build test lint` 全绿
- **模拟机（GxuDev，用户真实数据；走查前后库内数字一致：transactions 756 活跃/15 软删、accounts 3 活跃/4 软删、user_version 8）**：
  - 首页「⋯」→「分享本月小卡片」→ **系统分享面板拉起**（Sharing image）+ `cache/cards/month-summary.png` 45,616 字节
  - 账单详情页「分享小票」→ `receipt.png` 40,646 字节 + 面板；统计设置页「分享当期小卡片」→ `period-share.png` 48,795 字节 + 面板
  - 截图 4 张归档 `docs/local/界面重构验证/m3_16_*.png`
- **本轮新增的坑（写给下个会话）**：
  - **KDoc 里的 `*.png` 会开嵌套注释**：`/*` 在 KDoc 里合法，写 `cacheDir/cards/*.png` 会把注释提前"打开一层"、结尾的 `*/` 只关掉内层 → `Syntax error: Unclosed comment`
  - **Robolectric 读不到 FileProvider 的 `paths` 元数据**（`getUriForFile` 抛 "Failed to find configured root"）：把 Intent 拼装与 URI 解析拆两层，单测验拼装、真 URI 留给模拟机
  - **`captureRoboImage(path, bitmap)` 这个重载不存在**：出图留档自己写 PNG 到 `build/test-results/roborazzi/`
  - **卡片的坐标要按"字宽"留量**：占比卡图例最初排了"名称 + 占比 + 金额"三列，1080 宽下金额与占比叠字——**图例只留名称 + 占比**
- **下一条**：`M3-17` 日历视图（月网格 + 每日支/收 + 点选看当日流水 + 「今」；日期纯函数表驱动；每日合计与首页日头条同口径；不做农历/节气）——**M3 最后一片**。

### M3-15 愿望清单：攒钱目标（2026-10-15）

- **交付**（**M3 唯一一次迁移：v7→v8**）：
  - **数据层**：`WishlistItemEntity`（`wishlist_items`：id/bookId/name/targetCents/savedCents/startEpochDay?/dueEpochDay?/accountId?/note?/status/sortOrder + 同步四件套）+ `WishlistDao`（`observeByBook`（带跟随账户的**重算余额**投影，与资产页同一份口径）/`get`/`upsert`/`softDelete`/`restoreAll`/`softDeleteByBook`/`addSaved`（±，只动 savedCents）/`setStatus`/`countInBook`/`nextSortOrder`）；`MIGRATION_7_8` 建表 + 两个索引，`PennedDatabase` version 8 + `MIGRATIONS` 追加 + `8.json` 入库
  - **口径层**：`domain/WishlistMath`（`savedCents` 两态 / `progress` 夹取 / `remainingCents` 与 `overCents` / `achieved` 两条路 / `amountsLabel` / `statusLabel`）+ `WishlistProgress`；`WishlistStatus`（ACTIVE/DONE）
  - **页面**：我的 → 规划 → **愿望清单**（卡片列表：进度条 + 已存/目标 + 状态文案 + 达成 chip + 跟随账户小字；左滑删除 10s 撤销；空态）；**新建/编辑页**（名称/目标/存入方式两段/已存或跟随账户/截止日/备注 + 编辑时「标记达成」与「删除目标」）；**存入抽屉**（存入/取出 + 金额，写明不生成流水）
- **测试**：新增 18 条（**629 条全绿**）——`WishlistMathTest`（6）、`WishlistDaoTest`（5）、`MigrationTest`（+1：7→8 建表 + 旧数据保留 + version=8）、`WishlistScreenTest`（5：三态截图 / 空态 / 抽屉正负号 / 编辑页 / 真库真导航）、`DarkThemeScreensTest`（+1）；截图 `m3_wishlist*` / `dark_wishlist`；`./gradlew build test lint` 全绿
- **模拟机（GxuDev，用户真实数据）**：
  - **装包即触发 v7→v8 迁移**：`user_version 7 → 8`，transactions **756 活跃/15 软删**、accounts **3 活跃/4 软删**全都没变，`wishlist_items` 表已建且为空
  - 走查：我的 → 规划 → 愿望清单（空态）→ 新建（HuanDianNao / 8000）→ 保存 → 「已存 0.00 / 目标 8,000.00 · 还差 8,000.00」→「存入一笔」500 → 「已存 500.00 / 还差 7,500.00」+ 进度条一小截；库内一行（savedCents 50000）、**transactions 仍 756**
  - 收工：探针目标软删 → `wishlist_items 0 活跃/1 软删`；截图 9 张归档 `docs/local/界面重构验证/m3_15_*.png`
- **本轮新增的坑（写给下个会话）**：
  - **`performTextInput` 是"追加"不是"替换"**：同一个输入框第二轮输入前要 `performTextClearance()`（抽屉测试里"500"+"100" 变成了 500100）
  - **占位文字不是输入框**：`onNodeWithText("0.00")` 命中的是 decorationBox 里的占位 Text，打字打不进去——一律用 `onAllNodes(hasSetTextAction())[i]` 定位
  - **NavHost 长表单上的打字不稳**（nav 转场期间旧页还在树上）：本片把"保存落库"那条链路改到 VM 上验（`WishlistEditViewModel.save` + `awaitUi` 轮询），表单本身用截图验
  - **跨线程回调的局部变量不可见**：`var saved = false` 在 VM 协程里写、测试线程里读 → 用 `AtomicBoolean`
  - **机器内存吃紧时 Gradle/测试 JVM 会崩**（`insufficient memory` / `hs_err_pid*.log`）：跑全量前先关模拟机；崩完的 `hs_err_pid*.log`/`replay_pid*.log`/`.kotlin/` 不入库（`.gitignore` 已加 `.kotlin/`）
- **下一条**：`M3-16` 数据小卡片（月汇总 / 占比 / 单笔小票三款：Canvas → Bitmap → cacheDir → FileProvider → ACTION_SEND；用当前主题 token；分享 Intent 组成测试）。

### M3-14 自定义统计：模块开关与顺序（2026-10-15）

- **交付**（无 schema 变更：设置存 DataStore）：
  - **口径层**：`domain/StatsLayout`（`StatsModule` 七块 + `StatsLayout`（顺序/开关/上移/下移/visibleModules）+ `StatsLayoutCodec`（两个逗号串；顺序**认不出的段忽略、缺的模块按枚举补在末尾**，开关**没存过 = 全开、空串 = 全关**））、`domain/AssetTrendMath`（`affectsAccount` / `dayDeltaCents` / `series` / `summary`）、`StatsMath.tagTotals`
  - **数据层**：`TransactionDao.observeTagTotals`（标签聚合：多标签各算一次、软删标签不进榜、不计收支剔除、按类型）、`observeOpeningAssetCents`（期初 = 可见账户初始余额 + 窗口前净变化，**只算落在账户上的流水**）；`TxWithCategory` 增 `feeCents` / `targetAccountId`
  - **统计设置页**（统计右上「⋯」）：七行 = 模块名 + 说明 + 上移/下移（首行不给上移、末行不给下移，置灰不消失）+ 开关；**改动即生效**
  - **统计页**：模块按设置顺序渲染、关掉的不渲染也不计算；**全关 = 空态**；新增三块 `标签占比` / `标签数据`（不可下钻）/ `资产走势`（期初·期末·最高·最低 + 变化，**不画折线**，副标题写明"账户合计"）
- **测试**：新增 20 条（**610 条全绿**）——`StatsLayoutTest`（6）、`AssetTrendMathTest`（5）、`StatsModuleDaoTest`（2）、`StatsSettingsTest`（6：设置页截图 + 开关/上移回调 + 默认全模块 + 自定义顺序与开关 + 全关空态 + 真库真导航）、`DarkThemeScreensTest`（+1 统计设置深色）；截图 `m3_stats_settings` / `m3_stats_all_modules` / `m3_stats_custom_layout` / `m3_stats_all_off` / `dark_stats_settings`；`./gradlew build test lint` 全绿
- **模拟机（GxuDev，用户真实数据；本轮开机后基线 756 活跃/15 软删、accounts 3 活跃/4 软删、user_version 7，**走查后一致**——本片只动 DataStore；模拟机这次从快照起，M3-11 的探针行已不在快照里）**：
  - 统计右上「⋯」→ 统计设置（七行齐全、首行无上移、末行无下移）→ 关「趋势」+「数据」上移 → 回统计页：趋势卡消失、数据卡排到占比之前（键 `stats_enabled` / `stats_order` 落库可查）→ 再改回默认
  - 新三块：**资产走势**给出 `期末 4,000.00 / 期初 4,000.00 / 变化 0.00`（与资产页净资产对上）；`标签占比` 在无标签账本上给「本期没有标签记录」占位卡
  - **走查抓到并修掉的一条**：资产走势最初把"无账户的流水"也算进账户合计（用户 756 笔导入流水大多没有账户）→ 显示 −24,108.29；**只算落在账户上的流水**后与资产页的 4,000.00 对上（`affectsAccount`，DAOTest 与域测试都钉住）
  - 截图 7 张归档 `docs/local/界面重构验证/m3_14_*.png`
- **本轮新增的坑（写给下个会话）**：
  - **`combine` 只到 5 个类型化参数**：7 个流要合并时先拼成中间 data class（`ViewSlices`），否则编译不过
  - **`setTagsForTx` 是整批替换**（M3-5 的口径）：测试里给一笔挂两个标签要**一次传进去**，分两次调用只剩后一个
  - **字符串 `contains("TREND")` 会命中 `ASSET_TREND`**：断言枚举开关一律用解码后的集合判，别用字符串包含
  - **机器内存吃紧时 Gradle 守护进程会直接崩**（`insufficient memory` / `hs_err_pid*.log`）：先关模拟机腾内存再跑全量（模拟机重启约 2 分钟，比反复崩划算）
- **下一条**：`M3-15` 愿望清单（D072：攒钱目标 + 进度条 + 不生成流水；**单独迁移 v7→v8**——M3 唯一允许的迁移）。

### M3-13 首页数据自定义（2026-10-15）

- **交付**（无 schema 变更：设置存 DataStore）：
  - **口径层**：`domain/HomeDisplay`——`HomeStatItem`（支出/收入/结余/预算进度）+ `HomeDisplaySettings`（`visibleItems` 兜底、`isVisible`、`toggle` 全关时兜底落库）+ `HomeDisplay.encode/decode`（逗号串；**没存过 = 全开**、空串 = 全关）
  - **设置层**：`SettingsRepository.homeStatItems`（键 `home_stat_items`）；`HomeViewModel.display`（流映射）+ `setStatItem`
  - **首页**：月卡头右侧「⋯」（`PennedIcons.MoreVert`）→ `PennedSheet`「首页显示设置」（四开关 + 兜底说明 + 选好了，**勾选即生效**）；`MonthOverview` 按勾选渲染（三格顺序固定；`预算进度` 关掉 = 整个预算块不渲染）
- **测试**：新增 13 条（**590 条全绿**）——`HomeDisplayTest`（6：没存过 vs 全关、往返与顺序、认不出的段忽略、兜底只留支出、全关后再勾一项兜底落库、toggle）、`HomeDisplaySheetTest`（6：全开/部分/全关兜底三张渲染截图 + 「⋯」入口回调 + 抽屉开关回调 + 真库真导航）、`ContrastTest` 等既有护栏不变；截图 `m3_home_display_all/partial/fallback/sheet.png`；`./gradlew build test lint` 全绿
- **模拟机（GxuDev，用户真实数据；走查前后库内数字**一致**：transactions 756 活跃/16 软删、accounts 3 活跃/5 软删、user_version 7——本片只动 DataStore）**：
  - 月卡头「⋯」→ 关「收入」→ 卡上那格**立刻消失**（支出/结余/预算块还在）；再关「结余」「预算进度」「支出」→ 库内键成空串、卡上**兜底只剩「支出」**
  - **兜底落库**（走查抓到并修掉的一条）：全关状态下勾「收入」→ 库内键变 `EXPENSE,INCOME`、卡上「支出」**不消失**（改之前支出会跟着消失——抽屉开关与卡上渲染不一致）
  - 全部勾回 → 键 `EXPENSE,INCOME,BALANCE,BUDGET_PROGRESS` → force-stop 重开仍是全开；截图 9 张归档 `docs/local/界面重构验证/m3_13_*.png`
- **本轮新增的坑（写给下个会话）**：
  - **`@After` 里 `runBlocking` 写 DataStore 会死锁**：JUnit 的 @After 跑在 Robolectric 的 SDK 主线程上，`runBlocking` 一堵，DataStore 的内部协程就永远等不到（实测整条测试挂死 5 分钟，线程栈停在 `resetDisplay` 的 runBlocking）——**复位写在测试体里**；同理 `waitUntil { runBlocking { 读 DataStore } }` 也会一直不满足
  - **抽屉退场动画期间它的开关行也在语义树里**：关掉抽屉后立刻断言「某文案不存在」会误判（抽屉里那行同名）——**先等抽屉内容消失再断言**（或等目标文案计数归零）
  - **Gradle 被强杀会留下"拿着 classes.jar"的测试 worker**：`bundleDebugClassesToRuntimeJar` 报「另一个程序正在使用此文件」时，查 `Get-Process java` 里 JDK 17 起的 `GradleWorkerMain` 并结束它（不是重新 `--stop` 能解决的）
- **下一条**：`M3-14` 自定义统计（模块显示开关 + 顺序，入口 = 统计页右上「⋯」；关掉的模块不渲染也不计算）。

### M3-12 多主题：品牌色相 4 套（2026-10-15）

- **交付**（无 schema 变更：主题存 DataStore）：
  - **色板层**：`ui/theme/Brand.kt`——`ThemeAccent`（青绿/靛青/墨灰/赭石）+ `DarkMode`（跟随系统/浅色/深色）+ `BrandColors`（品牌一族 7 槽）+ `PennedBrands.light/dark/preview/fromKey`；青绿的值**直接引用 `Color.kt` 的现状 token**（默认主题逐位不漂）
  - **主题层**：`PennedTheme(darkTheme, accent)`——中性层/语义色照旧，只有品牌一族与日头条底（`surfaceContainerLow`）跟着色相走；`semanticFor()` 让 `primaryInk` 跟着走
  - **设置层**：`SettingsRepository` 加 `theme_accent` / `dark_mode` 两键；`PennedThemeFromSettings(app)`（MainActivity 与流程测试共用一份读法）
  - **主题外观页**（我的 → 外观 → 主题外观）：四套色相的预览行（日头条小样 + 主按钮 + 胶囊 + 「已选」）+ 深浅模式三段；**选中即生效、无保存键**
  - **顺手**：`ic_launcher.xml` 底色 `#2F9E8F` → `#0F766E`（看板遗留，UI-16 顺带发现）
- **测试**：新增 5 条（**577 条全绿**）——`AppearanceScreenTest`（4：四套浅色截图 / 四套深色截图 / 深浅分段 / 真库真导航链路（选靛青 → DataStore → `colorScheme.primary` 变靛青；选深色 → 变深色档））、`ContrastTest`（+1 默认青绿护栏；`pairs(accent)` 改成**四套 × 浅深全跑** + 新色相 hex 与文档对齐）；截图 8 张 `m3_theme_<色相>_<浅深>.png`；`./gradlew build test lint` 全绿
- **模拟机（GxuDev，用户真实数据；走查前后库内数字**一致**：transactions 756 活跃/16 软删、accounts 3 活跃/5 软删、categories 60/1、books 2/1、user_version 7——本片只动 DataStore）**：
  - 我的 → 主题外观：四套逐一点过（浅/深各看一遍，截图 8 张归档 `docs/local/界面重构验证/m3_12_*.png`）
  - **靛青浅色 / 赭石深色**各回首页看一遍：日头条底 + 3dp 竖条、悬浮记账入口、底栏指示器、月卡进度条都跟着换；**支出红、收入青绿、图标底淡灰绿不动**（分类身份优先）
  - **冷启动生效**：force-stop 重开 → 仍是赭石深色（DataStore 两键 `OCHRE`/`DARK` 已落库）
  - **收工复原**：改回**青绿 + 跟随系统**（用户设备不该留在别人的主题上）；桌面图标底色已是 `#0F766E`（截图留档）
- **本轮新增的坑（写给下个会话）**：
  - **`wt2.sh tap` 的子串匹配又咬了一次**：点「深色」命中了下面那句说明里的"选浅色/**深色**则固定不动"（`findnode` 取最后一个命中）——**同名/含同名的节点要点坐标**（先用 `texts` 打印坐标再 `input tap`）
  - **`PennedTheme` 多了一个参数但都有默认值**：既有截图/预览/测试不传 `accent` 就是青绿（默认主题逐位不变），所以**老基线不用重录**——这是"默认值 = 现状"换来的
  - **主题相关的东西一律走 `Brand.kt`**：`ui/theme` 之外不许出现色值（`TokenLiteralGuardTest` 会拦）；主题外观页要画**别的色相**，用 `PennedBrands.preview(accent, dark)` 拿整套色，别去读 `MaterialTheme`（那是当前主题）
- **下一条**：`M3-13` 首页数据自定义（月卡头四项勾选 + 「余额/预算」块开关，入口 = 月卡头「⋯」）。

### M3-11 分期房贷 + 信用卡年费（2026-10-15）

- **交付**（无 schema 变更：`accounts` 年费四列在 M3-0 就建好了）：
  - **口径层**：`domain/LoanMath`（两种方式 `plan` 逐期计划 + 三数；`equalInstallmentPaymentCents` 月供公式；`periodDates` 逐期还款日，**月对齐复用 `RecurringMath.advance`**）+ `domain/FeeMath`（`feeDateIn` 短月夹月末 / `isDue` 到期判定 / `label` 行副标题 / `feeCategoryId` 分类挑选）
  - **分期房贷页**（我的 → 工具 → 分期房贷）：表单卡（方式分段 + 金额/利率/期数 + 首期还款日 DatePicker）→ 结果卡（首期月供大数 + 末月月供/总利息/总还款三格）→ 计划表（前 12 期，含每期日期与本金/利息/剩余；超 12 期给"用周期记账"的指路）→ 「按首期记一笔」
  - **按首期记一笔**：跳记一笔**预填**（金额 = 首期月供、日期 = 首期还款日、备注「房贷首期 · 方式」，分类留给用户选）——**不生成整个计划**；add 路由加三个参数（`amountCents/day/note`）
  - **信用卡年费**：编辑账户页（仅信用卡）加「年费 / 收费月 / 收费日」三行（月/日走数字抽屉 1~12 / 1~31 + 「不设」）；`AccountWithBalance` 投影带年费四列；资产页信用卡行副标题 `年费 ¥480.00 · 每年 3 月 12 日`，到期未标记时行内一枚「记年费」胶囊 → 写一条支出流水（分类找含「年费」→「手续费」，都没有就不落分类）+ 标记 `lastFeePaidYear`（同一事务）；`AccountDao.setLastFeePaidYear`
  - **自绘图标**：`PennedIcons.Calculator`（计算器，我的页入口用）
- **测试**：新增 14 条（**572 条全绿**）——`LoanMathTest`（+3：逐期还款日 31 夹月末/跨年与闰年/非正期数）、`FeeMathTest`（+1：分类挑选优先级）、`LoanScreenTest`（4：截图 + 改期数 + 非法输入提示 + 「按首期记一笔」回调）、`LoanFeeFlowTest`（2 条真库真导航：预填不落库 / 记年费落库 + 标记）、`AssetsScreenScreenshotTest`（+1：到期 → 已标记两态）、`AccountEditScreenScreenshotTest`（+1：年费三行 + 月份抽屉）、`DarkThemeScreensTest`（+2：房贷页 / 年费到期行，并把编辑账户页基线补上年费卡）；`./gradlew build test lint` 全绿
- **模拟机（GxuDev，用户真实数据；开工基线 756 活跃/15 软删，分类 60/1、账户 3/4、账本 2/1、标签 0/2、债务 0/1、user_version 7）**：
  - **房贷手算对账**：等额本息 `首期月供 5,307.27 / 末月 5,305.19 / 总利息 910,615.12 / 总还款 1,910,615.12`（总还款 = 本金 + 总利息）；切**等额本金** → `6,863.90 / 2,789.11 / 737,039.61`，首期本金含零头 `2,780.57`（= 277,777 + 280）；计划表 12 期按月对齐跨年（2026-10-15 → 2027-09-15）
  - **按首期记一笔**：进记一笔页预填 `房贷首期 · 等额本息 / 5307.27 / 今天 10月15日 / 无账户 / 分类未选` → 顶栏返回丢弃 → **流水数不变（756）**
  - **信用卡年费**：自建探针信用卡（年费 480 元 / 1 月 1 日）→ 资产页副标题 + 「记年费」→ 点按后卡余额 `-480.00`、净资产 `4,000 → 3,520`、提示条「已记年费 480.00，今年的提示不再出现」、提示消失；库内 `lastFeePaidYear=2026` + 一条 `EXPENSE 48000`（`note = 信用卡年费 · 探针信用卡`，**categoryId 为 null**——用户账本里没有名字含「年费/手续费」的分类，按 D084 不擅自新建）
  - 编辑账户页年费三行 + 月份抽屉（1~12 + 「不设」+ 已选标记）
  - **复原**：探针流水 + 探针账户软删后 **transactions 756 活跃 / 16 软删、accounts 3 活跃 / 5 软删、其余同开工、user_version 仍 7**，用户数据完好。截图 11 张归档 `docs/local/界面重构验证/m3_11_*.png`
- **本轮新增的坑（写给下个会话）**：
  - **可点行的语义节点是合并的**：`首期还款日` 那行（可点）在 uiautomator/Compose 里读作 `首期还款日, 2026年10月15日`——`onNodeWithText("首期还款日")` **精确匹配不到**（要 `substring = true`，或改等旁边一个独立标题）；写测试与走查脚本时别拿可点行的标签当锚
  - **走查脚本 `probe` 里的 `am force-stop` 会把 App 杀掉**：拉库前 force-stop 是必须的，但拉完要记得**重新 launch**（本轮连着两次"以为 App 崩了"，其实是被自己的拉库脚本停掉）
  - **等额本息月供恒定 ⇒ 同一个金额文本会出现十几次**（首期月供 + 末月月供 + 每期计划行）：断言要 `onAllNodesWithText(...).onFirst()`，不能 `onNodeWithText`
- **下一条**：`M3-12` 多主题（D071：只换品牌色相 4 套 青绿/靛青/墨灰/赭石；顺手把启动图标底色对齐 `#0F766E`）——批次五开工。

### M3-10 理财快照与盈亏（2026-10-15）

- **交付**（无 schema 变更：`asset_snapshots` 在 M3-0 就建好了）：
  - **口径层**：`domain/InvestMath`（`latest` 最近快照 / `totalCents` 无快照退回余额 / `costCents` = 初始余额 + 净转入 / `profitCents` / `summary` 三数一次算齐）+ `InvestSnapshot`/`InvestSummary` 两个数据类
  - **数据层**：`SnapshotDao`（`observeByAccount`（日期倒序）/`observeForBook`（本账本可见账户的快照，理财卡汇总用）/`get`/`upsert`/`softDelete`/`countForAccount`）；`AccountWithBalance` 增 `netTransferCents` 与 `initialBalanceCents`（成本口径的原料，转账口径与余额重算同一份 SQL）；`AccountDao.observeWithBalanceByAccount`（详情页单账户）
  - **资产页**：「理财」卡接真数据（总额 = Σ 各理财账户总额、盈亏 = Σ 盈亏，负盈亏朱砂色）+ 卡可点进详情
  - **理财详情页**：Hero（账户 + 「最近快照 X」/「还没有快照：总额按账户余额算」+ 总额大数 + 成本/盈亏一行）+ 快照列表（日期 + 备注 + 现值，左滑删除）+ 「记快照」抽屉（日期 DatePicker + 现值 + 备注；**同一天再记 = 更新那一条**）
- **测试**：新增 14 条（**545 条全绿**）——`InvestMathTest`（5：总额两态、最近快照、成本含负净转入、盈亏含负、三数一次算齐）、`SnapshotDaoTest`（3：按账户倒序 + 软删、本账本可见范围（账户软删后快照也不进）、同日 upsert 覆盖）、`InvestFlowTest`（1 条真库真导航：无快照走余额 → 记快照 1300 → 三数按快照算）、`InvestScreenshotTest`（4：详情 / 无快照空态 / 负盈亏 / 记快照抽屉）、`DarkThemeScreensTest`（+1）；`./gradlew build test lint` 全绿
- **模拟机（GxuDev，用户真实数据；开工基线 756 活跃/15 软删，分类 60/1、账户 3/3、账本 2/1、标签 0/2、债务 0/1、user_version 7）**：建探针理财账户（初始 1000）→ 资产页理财卡 `总额 1,000.00 / 盈亏 0.00`（无快照退回余额）；点卡进详情 → 「还没有快照：总额按账户余额算」+ `成本 1,000.00`；记快照 `1200` → 详情页 `最近快照 2026年10月14日` + `盈亏 200.00`；回资产页 → 理财卡 `总额 1,200.00 / 盈亏 200.00`（两处同一口径）。**复原**：探针快照与理财账户软删后 **asset_snapshots 0 活跃 / 1 软删、accounts 3 活跃 / 4 软删、transactions 756 活跃**，用户数据完好。截图 6 张归档 `docs/local/界面重构验证/m3_10_*.png`
- **本轮新增的坑（写给下个会话）**：
  - **走查脚本 `wt2.sh tap` 是子串匹配取最后一个命中**：点「理财」卡时它命中了列表里的账户行（「理财账户」）而不是卡片标题——**同名/含同名文字的多个入口要点坐标**（`input tap`）
  - **抽屉里输入后要先把 IME 收起来再点按钮**：`input keyevent 111`（ESC 收键盘）→ 再点；`keyevent 4`（back）会把抽屉整个关掉
- **下一条**：`M3-11` 分期房贷 + 信用卡年费（`LoanMath` 两公式 + 年费到期纯函数；批次四最后一片）。

### M3-9 资产模块：停用 / 迁移 / 自定义分组（2026-10-15）

- **交付**（无 schema 变更：`accounts.archived` 与 `groupName` 是 v1 预留列）：
  - **数据层**：`AccountDao` 增 `setArchived` / `setGroupName` / `migrateOutgoing` / `migrateIncoming`（两端都搬）；`AccountWithBalance` 增 `archived`（投影带上）
  - **口径层**：`AccountOps` 增 `ARCHIVED_SECTION_TITLE` / `partitionArchived` / `selectableAccounts`（纯函数，表驱动）
  - **资产页**：停用账户收进「已停用」段（默认折叠、段小计照算、行尾「已停用」标记）；净资产 hero 不变（钱还在）
  - **编辑账户页**：新增「停用账户」开关（含说明）+ 「把名下 N 笔流水转到…」（抽屉选目标 → 确认弹窗写明条数与「初始余额一并并过去、不可撤销」 → 迁完余额归零、删除解禁）；「所属分组」抽屉列出已有自定义组 + 「新建分组」自由文本
  - **账户选择器**（记一笔 / 模板 / 周期）：停用不列，**本笔已选中的一律保留**（编辑用停用账户记的流水时能看到自己）
- **测试**：新增 12 条（**531 条全绿**）——`AccountArchiveDaoTest`（4：停用不改余额与净资产、迁移两端都搬 + 原账户流水数归零 + 余额联动、迁移不碰软删流水、groupName 落库/清除）、`AccountOpsTest`（4：分段、选择器保留已选中、分组标题优先、自定义组排在大类后）、`AccountArchiveFlowTest`（2 条真库真导航：停用 → 已停用段 + 选择器不列、迁移 → 归零 + 删除）、`AssetsScreenScreenshotTest`（+2：已停用段折叠/展开）、`AccountEditScreenScreenshotTest`（+1：三处新入口）；`./gradlew build test lint` 全绿
- **模拟机（GxuDev，用户真实数据；开工基线 756 活跃/14 软删，分类 60/1、账户 3/1、账本 2/1、标签 0/2、债务 0/1、bill_filters 0/1、user_version 7）**：建两个探针账户 `ProbeA`（初始 100）/`ProbeB`，从 ProbeA 记一笔 1.00；编辑 ProbeA → 「所属分组 → 新建分组 `TestGroup`」→ 资产页出现 **`TestGroup` 分组**（排在大类之后）；再编辑 ProbeA → **停用开关** → 保存 → 资产页**「已停用」段**（默认折叠、展开后行尾「已停用」）+ 记一笔的账户选择器**不列 ProbeA**；编辑 ProbeA → **「把名下 1 笔流水转到…」→ ProbeB → 迁移**（弹窗写明「初始余额也一并并过去」）→ 库内流水归 ProbeB、`ProbeA 初始 0 / ProbeB 初始 100`、**删除键解禁并删除成功**。**复原**：探针流水 + 两个探针账户软删后 **transactions 756 活跃 / 15 软删、accounts 3 活跃 / 3 软删、其余同开工**，用户数据完好。截图 16 张归档 `docs/local/界面重构验证/m3_9_*.png`
- **本轮新增的坑（写给下个会话）**：
  - **`PennedSwitchRow` 只有 `Switch` 本身可点**（行不可点）：测试里要按 `isToggleable()` 语义定位开关，点行上的文字不会切换；另外这类开关**只改页面状态、要点「保存」才落库**（开关不是即时生效）
  - **长表单页 `performScrollTo()` 在 Robolectric 下会报 no-parent-scroll 错误**：改用真实手势 `onRoot().performTouchInput { swipeUp() }` 再点
  - **外部 `sqlite3` 改库不会立刻反映到 UI**：Room 的 `WhileSubscribed(5_000)` 缓存还在窗口内时不会重查（走查里用 SQL 改 groupName 后页面没变，force-stop 重开才对）——**产品内改动都走 Room，不受影响**；走查脚本要记得改完重开
  - **`ConfirmDialog` 的确认键默认文案是「删除」**：迁移这类非删除动作要显式传 `confirmLabel`（本片传了「迁移」）
- **下一条**：`M3-10` 理财快照与盈亏（`asset_snapshots` + 理财详情/记快照 + 资产页理财卡接真数据）。

### M3-8 账单组合（2026-10-15）

- **交付**（无 schema 变更：`bill_filters` 在 M3-0 就建好了）：
  - **口径层**：`domain/BillFilterCodec`（多值条件编解码：空列表 ↔ null、值里的反斜杠/逗号转义、解码忽略空段；`TxFilter` ↔ `BillFilterEntity` 往返；关键词首尾空白不入库）
  - **数据层**：`BillFilterDao`（`observeByBook`/`get`/`upsert`/`softDelete`/`rename`/`nameTaken`（排除自己）/`countInBook`）
  - **筛选页**：有条件时右上多一个「存为组合」→ **命名抽屉**（输入 + 取消/保存，重名/空名就地提示、抽屉不关）
  - **账单组合页**（我的 → 账单组合）：行 = 名称 + 条件摘要（与筛选页同一处 `TxFilters.summary`）；点行 = 进筛选页**应用**（`search?filterId=`，条件回填、结果即出）；左滑 = 重命名 / 删除（10s 可撤销）；空态「去筛选」
- **测试**：新增 17 条（**519 条全绿**）——`BillFilterCodecTest`（6：空列表↔null、解码空段、转义往返、全字段往返、全空组合、关键词空白）、`BillFilterDaoTest`（4：排序与过滤、重名判定排除自己、重命名/软删保留条件、编解码落库往返）、`BillFiltersFlowTest`（3 条真库真导航：存为组合→列表摘要→点行回填且结果一致、重名提示、VM 层重命名/删除/撤销）、`BillFiltersScreenshotTest`（3：列表/空态/动作区）、`DarkThemeScreensTest`（+1 dark_bill_filters）；`./gradlew build test lint` 全绿
- **模拟机（GxuDev，用户真实数据；开工基线 756 活跃/14 软删，分类 60/1、账户 3/1、账本 2/1、标签 0/2、债务 0/1、bill_filters 0/0、user_version 7）**：筛选页设「类型 = 支出」→ 右上「存为组合」→ 命名抽屉输入 `FoodExpense` → 保存 → 库里 `FoodExpense|EXPENSE`；我的 → 账单组合 → 行 `FoodExpense` + 摘要 `支出` → **点行 → 筛选页条件回填（支出 chip 选中）+ `755 笔 · 收入 0.00 · 支出 29,608.29`，与手工筛选逐字一致**；左滑 → 重命名 → 抽屉改名 `FoodOnly` → 库内生效；左滑 → 删除 → 列表回空态 + 提示条「已删除 1 个组合 [撤销]」（撤销后行回来、库内 `deleted=0`）。**复原**：探针组合软删后 **bill_filters 0 活跃 / 1 软删**；**transactions 756 活跃 / 14 软删、周期规则 0、其余同开工**，用户数据完好。截图 13 张归档 `docs/local/界面重构验证/m3_8_*.png`
- **本轮新增的坑（写给下个会话）**：
  - **`AlertDialog` 里放 `BasicTextField` 在 Robolectric 下永远 idle 不了**（`Compose did not get idle ... infinite composition loop`，60s 超时；探针测试复现）："输入 + 确认"的浮层一律用 `PennedSheet` 抽屉（本项目既有形态），别用弹窗
  - **VM 层测试不能直接 `runBlocking` 读**：`viewModelScope` 走 `Main.immediate`，`runBlocking` 占住主线程会把它饿死（`rows` 永远是空）；要么先 `composeRule.setContent {}` 建个空宿主 + `waitForIdle()` 轮询（本片做法），要么在 UI 里驱动
  - **模拟机上点 IME 上方的按钮要点「收起键盘后的坐标」**：抽屉里的输入框聚焦后 IME 盖住底部按钮区（dump 报的坐标是 IME 之下的），先 `input keyevent 111`（ESC 收键盘）再点；`keyevent 4` 会把抽屉整个关掉（改名就白改了）
- **下一条**：`M3-9` 资产模块（账户停用 / 流水迁移 / 自定义分组；批次四第一片）。

### M3-7 组合筛选 + 精准导出（2026-10-15）

- **交付**（无 schema 变更）：
  - **口径层**：`domain/TxFilter`（条件模型 + `TxFilters`：`minuteRange` 日期→分钟窗口（本地墙钟、两端含当天）、`expandCategoryIds` 分类含二级展开（软删子级不算）、`summary` 条件摘要、`dateLabel`/`amountLabel`、`exportFileName` 文件名）
  - **数据层**：`TransactionDao.observeFiltered`（一条组合查询：关键词 LIKE（`SearchQuery.likePattern` 转义）+ 数字按金额精确 + 类型 + 日期窗口 + 分类/账户/标签（`hasX` 标志位 + 占位列表，避开空 `IN` 的语法错）+ 金额区间 + 三个开关；**不设 LIMIT**——结果集就是导出集）+ `entitiesByIds`（导出取整行实体）
  - **筛选页**（原搜索页升级，标题「筛选」）：搜索框 + 一行条件 chip（类型/日期/分类/账户/标签/金额/更多，选中转态并回显值）+「清空」；六个条件抽屉（类型单选 / 日期快捷+自定义两端 / 分类·账户·标签多选（分类写 `父 · 子`）/ 金额最低最高 / 更多三态行）；结果 = 顶部 `N 笔 · 收入 X · 支出 Y` + 「导出」+ 沿用首页的按日分组行渲染
  - **精准导出**：SAF `CreateDocument(text/csv)`，文件名预填 `penned-<摘要>-<日期>.csv`，写出**当前结果集**（按屏幕顺序），列口径与「数据管理 · 导出」同一个 `PennedCsv`；完成后提示条「已导出 N 条」
- **测试**：新增 19 条（**502 条全绿**）——`TxFilterTest`（7：分钟窗口、分类含二级、摘要顺序与收敛、日期/金额文案、导出文件名非法字符、isActive/activeCount 空列表语义）、`TransactionFilterDaoTest`（4：每个条件单独用 12 例、两两组合与空结果、软删/别账本不出现、导出实体与 CSV 行数一致）、`FilterFlowTest`（4 条真库真导航：关键词+汇总、分类含二级+清空、标签、类型）、`SearchScreenScreenshotTest`（+3：条件态 / 分类抽屉 / 更多抽屉；旧的 4 条按新 API 改）、`DarkThemeScreensTest`（+1 dark_filter）；`./gradlew build test lint` 全绿
- **模拟机（GxuDev，用户真实数据；开工基线 756 活跃/14 软删，分类 60/1、账户 3/1、账本 2/1、标签 0/2、债务 0/1、user_version 7）**：首页搜索 → 筛选页；**类型 = 支出 → `755 笔 · 收入 0.00 · 支出 29,608.29`** → 点「导出」→ SAF 存到 Downloads → **文件 `penned-支出-20261014.csv` 共 756 行 = 表头 + 755 条，与屏幕上的 755 笔一致**（本片验收点：选中结果数与导出条数一致）；**分类选一级「食品餐饮」→ `531 笔 · 支出 5,122.18`**（含二级子树；改选某个二级只剩 1 笔，粒度生效）；清空后回到初始提示。筛选是页面状态、不写库；导出的 CSV 已删。**收工库内与开工一致（756 活跃 / 14 软删，其余同）**，用户数据完好。截图 9 张归档 `docs/local/界面重构验证/m3_7_*.png`
- **本轮新增的坑（写给下个会话）**：
  - **LIKE 的 ESCAPE 在 Kotlin raw string 里只能写单反斜杠**：写成两个反斜杠会被 SQL 当成两个字符（`ESCAPE expression must be a single character`）；照抄 `SearchQuery` 那条老查询的写法即可。**用脚本批量改这个字符串极易改坏**（本片踩了三次，连老查询一起改坏过），改完务必 `grep -n ESCAPE` 逐行验一遍
  - **结果集别加 LIMIT**：导出 = 所见；原搜索页的 `LIMIT 200` 在"精准导出"语义下会静默丢数据（本片把上限拿掉了）
  - **测试/脚本里点多选抽屉的行要看清匹配**：Compose 的 `onNodeWithText(食品餐饮)` 只匹配父行（子行是 `食品餐饮 · 午餐`），而走查脚本 `wt2.sh tap` 是**子串匹配取最后一个**——同一个词会点到子行（模拟机上因此先得到"1 笔"而不是 531 笔），要按坐标或精确匹配
- **下一条**：`M3-8` 账单组合（`bill_filters` 保存/应用/重命名/删除 + 多值条件编解码纯函数）。

### M3-6 周期记账（2026-10-15）

- **交付**（无 schema 变更：`recurring_rules` + `transactions.recurringRuleId` 在 M3-0 就建好了）：
  - **口径层**：`domain/RecurringMath`——`advance`（日/周/月/年 + **月对齐**：以起始日的"日"为锚定日，短月夹到月末、后续期回锚定日；年周期同样处理 2/29）、`dueDates`（打开即补的到期列表：≤ 今天、受结束日与单次上限 400 约束、不产未来）、`periodMinute`（期次落库 = 该期日期 **09:00 本地**，幂等的关键）、`periodLabel`（每天/每 3 个月）
  - **数据层**：`RecurringDao`（`RuleWithSummary` = 规则 + 分类/账户名；`observeByBook`/`enabledAll`/`get`/`upsert`/`softDelete`/`setEnabled`/`advance`）；`TransactionDao.minutesForRule`（幂等判据）；`data/RecurringGenerator`（`catchUp()`：扫启用中的规则 → 补到期期次 → 推进 `nextEpochDay`，返回补记条数）
  - **启动路径**：挂在 `PennedApp` 的**独立协程**（与 M2 启动自动检查同处、互不阻塞），补记条数 > 0 时首页给一条短提示「周期记账已补记 N 笔」
  - **管理页**（我的 → 周期记账）：行 = 名称 + `每月 · 下次 10月16日`（第一行）+ 摘要（第二行）+ 行尾启停开关；左滑删除（提示条写明**已生成的流水保留**、可撤销）；FAB/空态新建
  - **新建/编辑规则页**：类型分段 + 名称 + 金额 / 分类 + 账户（转账 = 两端 + 手续费）/ 周期四格 + 每隔 N + 起始日 + 结束日 + 备注；编辑时多「已启用/已停用」行与「删除规则」
- **测试**：新增 25 条（**483 条全绿**）——`RecurringMathTest`（8：按天/按周跨月、月末夹到短月、夹过后回锚定日、连续 12 个月落点、2/29 年周期、到期列表只列 ≤ 今天、结束日与单次上限、周期文案）、`RecurringGeneratorTest`（5：补到期 + 未来不产 + 重复启动不重复 + 指针推进、月对齐 1/31→2/28、停用/软删/结束日都不补、删规则留流水、单次上限后下次继续）、`RecurringScreensScreenshotTest`（5：列表/空态/新建/保存置灰/编辑）、`RecurringFlowTest`（5 条真库真导航：启动即补 + 提示 + 再补为 0、停用不补、新建规则列表行与开关、删除规则、保存置灰）、`DarkThemeScreensTest`（+2）；`./gradlew build test lint` 全绿
- **模拟机（GxuDev，用户真实数据；开工基线 756 活跃/11 软删，分类 60/1、账户 3/1、账本 2/1、标签 0/2、债务 0/1、user_version 7）**：我的 → 周期记账（空态）→ 新建 `Rent`（2000 / 食品餐饮·粮油调味 / 储蓄卡TestCard / 每月 / 起始日今天）→ 列表行 `每月 · 下次 10月14日`；**重启 App → 自动补出 1 笔**（库里 `recurringRuleId` + `source=AUTO` + `dateEpochMinute` 落在当天 09:00），首页 `支 2,000.00`、支出 22.00→**2,022.00**；再放一条"每天"规则（起始昨天）→ 重启 → 首页提示**「周期记账已补记 2 笔」**、支出 2,024.00；**再重启一次：不再补（幂等）**、提示也不再出现。**复原**：3 笔生成流水 + 2 条规则软删后首页回到 22.00；**收工库内：transactions 756 活跃 / 14 软删（+3 探针）、周期规则 0 活跃 / 2 软删、其余同开工**，用户数据完好。截图 9 张归档 `docs/local/界面重构验证/m3_6_*.png`
- **本轮新增的坑（写给下个会话）**：
  - **同一个 path 上挂"列表 + 带 query 的编辑页"会撞**：`composable("recurring")` + `composable("recurring?editId={editId}")` 时 `navigate("recurring")` 会落到**编辑页**（导航图按 path 匹配，query 段不参与区分）——编辑页要换独立路径（`recurring/edit?editId=`），与 `data/batches/{batchId}` 的先例一致
  - **我的页的靠下入口在测试里要先 `performScrollTo()` 再点**：mine 页是 `Column(verticalScroll)`，"周期记账"行在折叠线以下，直接 `performClick` 会打空（节点存在、坐标在视口外）
  - **转场动画期间 `assertIsDisplayed` 会抖**：新页面刚推入时按子串轮询到节点出现再断言存在（`assertExists`），别在动画帧上断言 displayed
- **下一条**：`M3-7` 组合筛选 + 精准导出（搜索页升级为筛选页 + 结果汇总 + CSV 导出）。

### M3-5 模板记账（2026-10-15）

- **交付**（无 schema 变更：`tx_templates`/`template_tags` 在 M3-0 就建好了）：
  - **口径层**：`domain/Templates`（`TemplatePrefill` 数据类 + `of(template, tagIds)` / `of(tx, tagIds)` / `typeLabel` / `summaryText`）——**用模板 = 进记一笔预填，确认后才入库**；反向的「存为模板」从流水抽草稿，**金额取落库净额**（退款/优惠不是模板的一部分）
  - **数据层**：`TemplateDao`（`TemplateWithTags` = 模板 + 分类/账户名 + 标签名 `、` 连接；`observeByBook`/`get`/`upsert`/`softDelete`/`countInBook`/`setTagsForTemplate`（单事务全量替换）/`tagsForTemplate`）——软删**不摘标签关系**（撤销删除时标签一起回来）、**不碰任何流水**
  - **管理页**（我的 → 模板记账）：行 = 头像 + 名称 + `类型 · 金额 · 分类 · 账户`（转账写两端，有标签再接一段）；左滑 = **上移**（第一条没有）+ 删除（可撤销）；FAB/空态按钮新建
  - **新建/编辑模板页**：类型分段 + 名称 + 金额 / 分类 + 账户（转账 = 两端 + 手续费）/ 备注 + 标签（抽屉 + 新建标签弹窗）；保存置灰口径 = 名称 + 金额 + （分类 或 转账两端）；编辑时多「删除模板」
  - **记一笔**：信息条新增「模板」chip（支出/收入/转账；债务 Tab 没有）→ 模板抽屉 → 点一行预填（金额/分类/账户或两端/备注/手续费/标签），末尾「管理模板」出口
  - **存为模板**：保存成功 → `savedStateHandle` 写消息 + **刚保存的流水 id** → 首页提示条 `已记账 [存为模板]`（10s，可取消）→ 点它进 `template?fromTx=` 预填草稿（名称留空）；**编辑已有流水不给这个动作**
- **测试**：新增 19 条（**458 条全绿**）——`TemplatePrefillTest`（5：模板→预填字段照搬 / 转账两端 + 手续费 / 金额 0 文本为空 / 流水→草稿取净额 / 列表副标题三种形态）、`TemplateDaoTest`（3：列表带分类账户标签名 + sortOrder 排 / 标签替换幂等 + 删模板不碰流水 / 软删与别账本过滤）、`TemplatesFlowTest`（2 条真库真导航：我的→模板记账→新建→保存→记一笔用模板→字段预填并照模板落库；手工记一笔→首页「存为模板」→草稿预填→补名保存）、`TemplateScreensScreenshotTest`（7：列表/空态/动作区/新建/保存置灰/编辑转账/记一笔抽屉）、`DarkThemeScreensTest`（+2：模板列表 / 模板编辑）；`./gradlew build test lint` 全绿
- **模拟机（GxuDev，用户真实数据；开工基线 756 活跃/8 软删，分类 60/1、账户 3/1、账本 2/1、标签 0/2、债务 0/1、user_version 7）**：我的→模板记账→新建 `Lunch`（16.50 / 食品餐饮·粮油调味 / 微信）→ 列表行 `支出 · 16.50 · 粮油调味 · 微信`；记一笔「模板」chip → 选 Lunch → **金额 16.50 + 二级分类 + 微信账户全部预填** → 完成 → 首页 `食品餐饮 · 粮油调味 -16.50`、月汇总 22.00→**38.50**、预算已用同步；保存后提示条 `已记账 [存为模板]` → 点它 → 模板新建页**照刚保存的流水预填**（6.00 / 食品餐饮 / 名称留空）。**复原**：3 笔探针流水 + 1 个模板软删后首页回到 22.00；**收工库内：transactions 756 活跃 / 11 软删（+3 探针）、模板 0 活跃 / 1 软删、其余同开工**，用户数据完好。截图 17 张归档 `docs/local/界面重构验证/m3_5_*.png`
- **本轮新增的坑（写给下个会话）**：
  - **首页浮动记账入口在提示条显示期间是隐藏的**（`HomeAddEntry(visible = !selectionMode && !snackbarVisible)`）：M3-5 起"保存成功"的提示条带「存为模板」动作、时长 10s，**此后保存完的测试不能再等 `添加一条新记账` 出现**（会被提示条挡到 10s 后）——改等提示条文本（`已记账`/`已保存`）；连带把 M3-2/M3-3/M3-4 的四条 flow 测试一起改了判据
  - **模板编辑页有三个可输入行**（名称/金额/备注）：测试里 `onNode(hasSetTextAction())` 会撞多节点，按 `onAllNodes(hasSetTextAction())[0]/[1]` 取
  - **`data class X` 与 `object X` 不能同名**：预填映射对象改名 `Templates`（数据类保留 `TemplatePrefill`）
- **下一条**：`M3-6` 周期记账（批次三第一片；期次推进纯函数 + 打开即补 D070 + 管理页启停 + 幂等去重）。

### M3-4 债务（2026-10-15）

- **交付**（无 schema 变更：`debts` 表与 `transactions.debtId` 在 M3-0 的 v6 就建好了，D068）：
  - **口径层**：`domain/DebtMath`——「剩余」= 本金 − 已归还；**已归还只认清偿方向的流水**（BORROW 认支出、LEND 认收入；借入/借出那一笔不算）——SQL（`DebtDao` 的 `CASE WHEN type = (CASE WHEN direction = …)`）与 `settleType` 是**一对孪生实现**，改一处必须改另一处；`payableCents`/`receivableCents`（未结清剩余合计，**多还为负照负算**）、`netAssetCents` = Σ账户 − 应付 + 应收；`flowType`（两格 × 两面 = 四种流水类型）、`actionLabel`/`flowLabel`（借入/还款 vs 借出/收到还款）；`domain/DebtText`（日期与两数摘要文案，表驱动）
  - **数据层**：`DebtDao`（`DebtWithRepaid` = 债务 + 已归还；`observeByBook`/`observeById`/`upsert`/`setClosed`/`softDelete`）；`TransactionDao.observeByDebt`（债务详情还款记录）；`TxWithCategory` 增 `debtDirection`/`debtCounterparty`（4 条列表 SQL 加 `LEFT JOIN debts`）——首页/详情行标题写「借入 · 张三 / 还款 · 张三」而不是"未分类"
  - **资产页**：「债务」卡接真数据（应付/应收）且**整卡可点**进债务列表；净资产卡公式换成 `DebtMath.netAssetCents`
  - **债务列表页**：应付/应收两段（空段不画）+ 段小计 + 行（对方 + 已结清标记 + `本金 X · 已还 Y · 到期 Z` + 右端「剩余」）+ FAB 新建；空态给「新建债务」按钮
  - **新建/编辑债务页**：方向分段（我欠对方 / 对方欠我）+ 对方 + 本金 + 起始日/到期日（可清除）+ 备注；编辑时多「标为结清」开关与「删除债务」（确认弹窗写明名下流水保留）
  - **债务详情页**：Hero（对方 + 立场 + 已结清 + 「剩余」大数**不带正负号**、方向靠颜色 + 两数摘要）+ 债务信息 + 还款记录（动作词 + 日期·账户·备注 + 金额；空态一句出路）+ 「记一笔还款 / 记一笔收款」→ **跳记一笔预填**（债务 + 清偿那一格 + 剩余额 + 上次用过的账户）
  - **记一笔第四 Tab「债务」**：`AddTab` 四格（**不往 `TxType` 里加值**——它要落库）；面板 = 选择债务（对方 + 立场·剩余）→ 借入/归还两格（两面用词不同，每格一行说明）→ 资金账户；信息条一行口径说明；保存生成带 `debtId` 的普通流水且**默认 `excludeFromStats` + `excludeFromBudget` 都不计**；空态给「新建债务」出路；选择器里也有「新建债务」出口
  - **账户编辑**：信贷/负债两类账户放行**负初始余额**（存量欠款），行尾「±」翻号（数字键盘不保证有负号）+ 一句说明
- **测试**：新增 30 条（**439 条全绿**）——`DebtMathTest`（6：剩余表 / 两格×两面四种类型与用词 / 已归还只认清偿方向 / 应付应收含结清与多还 / 净资产表 / 走查场景）、`DebtTextTest`（4：完整日期 / 到期日跨年 / 两数摘要 / 主按钮用词）、`DebtDaoTest`（4：我欠人只认还款、人欠我只认收款、软删/无流水/别的债务/别账本边界、单笔观察+结清+软删）、`AddDebtFlowTest`（2 条真库真导航：债务 Tab 借入 → 落库带 debtId + 两个开关都不计 + 统计 0/0 + 预算 0 + 账户 +1000 + 剩余 1000 + 首页行「借入 · 张三」；债务详情「记一笔还款」预填剩余额与账户 → 还款 400…（1000 全额）→ 已还 1000/剩余 0/账户回 0 + 两笔记录都在）、`DebtScreensScreenshotTest`（7：列表两段/空态/新建/保存置灰/编辑结清/详情/详情空态）、`AddDebtScreenshotTest`（4：借入面板 / 人欠我那一面 / 空态 / 选择器抽屉）、`DarkThemeScreensTest`（+3：债务列表 / 详情 / 债务 Tab）、`AssetsScreenScreenshotTest`（净资产 527.50 = 727.50 − 500 + 300，债务卡 500/300）；`TxEditTest` 白名单加 `debtId`；`./gradlew build test lint` 全绿
- **模拟机（GxuDev，用户真实数据；开工基线 756 活跃/4 软删，分类 60/1、账户 3/0、账本 2/1、标签 0/2、债务 0/0）**：
  - 新建债务 ZhangSan 本金 1000（我欠对方）→ 资产页 **净资产 4,000.00 → 3,000.00、应付 1,000.00**（欠款成立、钱还没到）
  - 记一笔「债务」Tab **借入 1000 → 现金** → **现金 0.00 → 1,000.00、总资产 5,000.00、净资产回到 4,000.00（借入不改变净资产）**；首页行 `借入 · ZhangSan +1,000.00`，月卡**支出 22.00 / 收入 0.00 不变**（借入不是收入）
  - 债务详情「记一笔还款」→ 预填 1000.00 + 现金 → 改成 **400 → 完成** → **剩余 600.00、已还 400.00、还款记录两条（借入 +1,000.00 / 还款 −400.00）**；资产页 **应付 600.00、现金 600.00、净资产仍 4,000.00**
  - **走查抓到一条真 bug 并已修**：第一版只关 `excludeFromStats`，还款（支出型）**算进了预算已用**（22.00 → 422.00）——预算口径读的是 `excludeFromBudget`（两个开关是两回事）。改成新建债务流水两个开关都关，重装复验：还款后 **预算已用仍 22.00** ✓（测试里也补了这条回归断言）
  - 负债类负余额：新建「欠款」账户 → 余额 1200 → 点「±」→ **-1200** → 保存 → 资产页 `欠款 -1,200.00`、**负资产 1,200.00、净资产 2,800.00**（4,600 − 1,200 − 600）
  - **复原**：探针（2 笔债务流水 + 1 笔债务 + 1 个账户）软删后资产页回到 4,000.00 / 0.00 / 0.00。**收工库内：transactions 756 活跃 / 8 软删（+4 探针）、分类 60/1、账户 3/1、账本 2/1、标签 0/2、债务 0/1、user_version 7**，用户数据完好。截图 24 张归档 `docs/local/界面重构验证/m3_4_*.png`（不入库）
- **本轮新增的坑（写给下个会话）**：
  - **`input text` 打不进中文输入法的组合串**：Gboard 把 ASCII 攒在候选栏里（"Zhang Sana b ca"），不按 **ENTER（keyevent 66）** 提交就永远不进输入框；`input text` 对**数字**字段倒是立刻生效（数字直接提交）。要清空重打就 `keyevent 67` 连发
  - **`excludeFromStats` 与 `excludeFromBudget` 是两个独立开关**：统计页/首页月卡读前者，预算已用读后者；"这笔不算收支"不等于"不进预算"（本片还款就是这么漏进预算的）
  - **走查脚本的 `tap` 首次常被吞**：`±` 这类小按钮要点两次（`wt2.sh tap` 连点两次；`tap1` 只点一次）——`input tap` 单点也可能被吞，验证一次没反应就再点一次
- **下一条**：`M3-5` 模板记账（批次二最后一片；`tx_templates`/`template_tags` + 管理页 + 记一笔「模板」chip 预填 + 保存后「存为模板」）。

### M3-3 报销录入（2026-10-12）

- **交付**（无 schema 变更；`reimburseCents` 在 M3-0 就建好了）：
  - **口径层**：`domain/ReimburseMath`——`claimCents`（报销金额缺省 0 = **按流水净额全额**，超额照填的算）、`isFullClaim`、`claimLabel`（`全额 100.00` / `40.00`）；资产页 SQL 与它同一口径（`observeReimburseSums` 改 `CASE WHEN reimburseCents > 0 THEN reimburseCents ELSE amountCents END`，两处必须一起改）
  - **记一笔**：「不报销」只读 chip 改成可点的**报销 chip**（自绘收据图标；标签随状态走 不报销/待报销/已报销）→ 报销抽屉：三态 chip **就地改、抽屉不关**（照标签抽屉范式）、`报销账户` 行（两层抽屉不叠：收起本抽屉再起账户选择，可空）、`报销金额` 行（点它**收起抽屉并把金额键盘指到第四格** `AmountField.REIMBURSE`，与 M3-2 的退款/优惠同一套路由）；状态为不报销时两行不出现
  - **保存/编辑**：`TxEdit.Editable` 增报销三字段（无默认值）；编辑回填状态/账户/金额（金额 0 回填空 = 按全额）；「再记」清金额但**保留状态与账户**（一摞单子连着记，不必每笔重选）；切到转账三项全清
  - **详情页**：「报销」行 = `状态 · 报销金额 · 收到款的账户`（如 `待报销 · 100.00 · 微信`），账户为空只省那一段；金额走 `ReimburseMath.claimCents`（0 时显示按全额的数）
  - **导入器**：一木 xls 的 `报销账户`/`报销金额` 两列进导入（状态留 NONE，理由见「待办」那条）；脱敏 fixture 同步补数据
- **测试**：新增 21 条（**409 条全绿**）——`ReimburseMathTest`（3：口径表 6 例 / isFullClaim / 文案）、`TransactionReimburseDaoTest`（4：可报已报各自求和、等于/超额/未填状态/软删/别的账本五类边界、空库、改状态金额后重算）、`AddReimburseFlowTest`（2：待报销+账户落库与详情行、自定金额走键盘 + 改已报销后可报已报换边）、`AddReimburseViewModelTest`（3：键盘第四格与「全额」的净额基准、保存/清理、再记保留状态 + 转账清空）、`AddReimburseScreenshotTest`（5：chip 态、抽屉、不报销时无两行、金额行交出键盘、键盘提示条）、`DetailScreenScreenshotTest`（报销行两例）、`DarkThemeScreensTest.darkAddReimburse`、`YimuXlsParserTest`/`ImportRepositoryTest` 各一条（报销列解析与落库 + 状态 NONE + 资产页数字不受导入影响）；`TxEditTest` 白名单同步；`./gradlew build test lint` 全绿
- **模拟机（GxuDev，用户真实数据）**：记一笔 支出 100 + 微信 + **待报销** → 资产页「可报 0.00→**100.00**」；详情行 `待报销 · 100.00 · 微信`；编辑改**已报销** → 资产页「可报 100.00→**0.00**、已报 0.00→**100.00**」（两张数字随状态换边，正是本片验收）；删除探针后 可报/已报/首页月汇总/预算已用/账户余额全部复原（22.00 / 4,000.00 / 微信 3,000.00）。**收工库内：transactions 756 活跃 / 4 软删（探针行 `amountCents 10000 + reimburseState DONE + reimburseAccountId=微信 + reimburseCents 0` 已核）、分类 60/1、账户 3/0、账本 2/1、标签 0/2、user_version 7**，用户数据完好。截图 8 张归档 `docs/local/界面重构验证/m3_3_*.png`
- **本轮新增的坑（写给下个会话）**：
  - **模拟机重启可能载入旧快照、userdata 会回滚**：这轮 `emulator -avd GxuDev -no-snapshot-save` 起来后 curl 到的是 **M3-1 时代的库**（M3-2 的探针行不见了、计数 756/3 而不是 756/4）——**每轮开工照样现场重记基线**（本次即按 756/3 重新记），别拿上一轮的收工数字当基准；要避免回滚就加 `-no-snapshot-load`
  - **模拟机这次起不来（软件 OpenGL 加载失败 + crashpad 弹窗）**：`Failed to load opengl32sw ... Software OpenGL failed` 之后进程崩掉。改用 **`-no-window -gpu swiftshader_indirect`** 一次成功（无窗口下 `screencap` / `uiautomator dump` / `input tap` 全部照常）
  - **两层弹出层不能同时开**：报销抽屉里点「报销账户」要**先关抽屉再起账户选择**（同标签抽屉与新建弹窗的做法）；两个 ModalBottomSheet 叠在一起时点按会落在遮罩上
- **下一条**：`M3-4` 债务（实体式 D068；表已在 v6 无迁移；可按"数据+列表 / 记账 Tab+详情"拆两次会话）。

### M3-2 退款 / 优惠录入（2026-10-12）

- **交付**（无 schema 变更，D069「保存净额」）：
  - **口径层**：`domain/RefundMath`——净额 = 原价 − 退款 − 优惠（下限 0，退超了不拦、最多记 0）、编辑反推原价 = 净额 + 退款 + 优惠、全额退款判定、「实付」行文案；UI 一处不算数（M3 §二·3）
  - **记一笔**：信息条新增「退款」「优惠」两个 chip（自绘图标：回退箭头 / 百分号），点开**把同一个金额键盘指过去**（`AmountField` 三格路由，含表达式求值与 `⌫`/`C`）；键盘上方一条「正在输入：退款 30 [回到金额]」提示条；信息条下方一行「实付 70.00（100.00 − 退款 30.00）」算式；保存时 `amountCents` = 净额、两个原值各存各的（`TxEdit.Editable` 增两个**无默认值**字段——调用点必须显式传，免得"忘了传"被静默清零）；编辑回填金额框 = 反推原价，往返幂等；「再记」连退款/优惠一起清零并回到主金额
  - **详情页**：`DetailRow` 支持 `onClick`（整行可点 + 尾部箭头），「退款」「优惠」两行点进编辑页；`RefundBadge` 的「已全额退款」判定改走 `RefundMath.isFullyRefunded`（口径与落库同一个来源）
  - **首页流水行**（看板遗留随本片定）：时间行上加「退款 / 全额退款 / 优惠」小标记（一行一个，退款优先）；`MoneyText` **净额为 0 时不写符号**（"-0.00" → "0.00"）；投影 `TxWithCategory` 增 refundCents/discountCents（4 条列表 SQL 同步）
- **测试**：新增 18 条（**388 条全绿**）——`RefundMathTest`（6：净额表 10 例 / 反推后重存净额不变 / 无退优惠时反推 = 净额 / 退超时抬到退款额且第二次起稳定 / 全额判定 / 实付文案）、`AddRefundFlowTest`（真库真导航：填退款 → 净额落库 → 首页行带标记 → 详情点退款行进编辑 → 反推原价 → 再存不变）、`AddRefundViewModelTest`（4：键盘切格与表达式求值 / 实付行 / 全额退款存得下 / 叠加净额 + 再记清空）、`AddRefundScreenshotTest`（3：退款态、叠加态、chip 指路）、`HomeRefundMarkerTest`（标记口径表 + 首页截图）、`DetailScreenScreenshotTest.refundRowsOpenTheEditor`、`DarkThemeScreensTest.darkAddRefund`；`TxEditTest`（白名单加 退款/优惠）与 11 处测试 fixture 同步；`./gradlew build test lint` 全绿
- **模拟机（GxuDev，用户真实数据）**：记一笔 支出 100 + 微信 + 退款 30 → **首页月汇总 22.00→92.00、预算已用 22.00→92.00（+70 净额，不是 +100）**，行显示 `-70.00` + 「退款」标记；**资产页微信 3,000.00→2,930.00**；详情「部分退款」徽标 + 退款行（带箭头）→ 点它进编辑页，金额框回填 `100.00`、退款 chip `30.00`、实付行 `70.00` → 直接「完成」再存一次，详情仍是 `-70.00` / 退款 30.00（往返幂等在真机成立）；详情页删除后**月汇总/预算/净资产/微信全部复原**（4,000.00 / 22.00）。**收工库内：transactions 756 活跃 / 4 软删（+1 探针，amountCents 7000 + refundCents 3000 已核）、分类 60/1、账户 3/0、账本 2/1、标签 0/2、user_version 7**，用户数据完好。截图 8 张归档 `docs/local/界面重构验证/m3_2_*.png`（不入库）
- **本轮新增的坑（写给下个会话）**：
  - **VM 层测试要先等数据到位再动手**：账本 id 走 DataStore（异步读），`stateIn` 订阅 + 一次 `idle()` 只能保证"有人收"，**不保证已经有值**——`saveInternal` 里的 `categories.value.firstOrNull {…} ?: return false` 会静默返回 false，表现成"保存没反应"。本片加 `awaitVm { categories.value.isNotEmpty() }`（`idle()` + 真时间轮询）后再操作
  - **断言金额文本要防重名**：首页月份卡的「支出」合计与流水行金额常是同一个字符串（如 `-70.00`），`onNodeWithText` 会因多节点直接抛错——用 `onAllNodesWithText(...).onFirst()`，或改成断言只有一处会出现的量（如 `-0.00` 不存在）
  - **测试方法名不能带 `/`**（JVM 名字里 `/` 非法，反引号包裹也没用）：`退款/优惠` 这类写法编译即报 "Name contains illegal characters"
  - adb 走查：点数字键盘要**限定在键盘区（y ≥ 1500）按精确文本找**（页面上"100.00"这类文本会把子串匹配带偏），本片脚本 `build/wt2.sh keys 1 0 0` 就是干这个的
- **下一条**：`M3-3` 报销录入（批次二第一片；`reimburseCents` 启用 + 「不报销」chip 改可点三态 + 资产页报销卡改口径）。

### M3 会员功能解锁（2026-10-12，M3-0）

- **规格冻结**：`docs/specs/M3.md` 从草案转冻结——用户 2026-10-12 裁决 §五 7 条，其余按推荐执行；裁决并入规格（§五 改为「裁决记录」表）并记 D068~D073。18 片改**6 批 × 3 片**：一 地基+标签+退款优惠 / 二 报销+债务+模板 / 三 周期+筛选+组合 / 四 资产三件 / 五 个性化三件 / 六 愿望清单+分享卡片+日历。
- **M3-0 交付（schema 地基，无 UI）**：
  - **6 张新表**：`tx_templates` / `template_tags`（模板，M3-5）、`recurring_rules`（周期，M3-6）、`bill_filters`（账单组合，M3-8；条件列一次建齐，含逗号分隔多值列与三个三态开关）、`asset_snapshots`（理财快照，M3-10）、`debts`（债务，D068；**表随本片建好，M3-4 不再迁移**）
  - **7 个新列**：`transactions.reimburseCents`（缺省 0 = 按净额全额，M3-3）/ `recurringRuleId` / `debtId`（各带索引）；`accounts.annualFeeCents` / `feeMonth` / `feeDay` / `lastFeePaidYear`（M3-11）
  - **两个新枚举**：`RecurringPeriod`、`DebtDirection`（+ 类型转换器）
  - `MIGRATION_5_6`、DB version 6、`app/schemas/.../6.json` 入库（16 张表）；**迁移列表抽成 `PennedDatabase.MIGRATIONS`**——生产构建与 migration 测试共用一份，以后加迁移不会漏在测试里
- **验证**：`./gradlew build test lint` 全绿；`MigrationTest` 6/6（新增「migrate 5 to 6」：建 v5 库塞旧数据 → 迁移 → 旧数据保留、新列默认值正确、`debts` 可写入读回）；Room 自身 schema 校验通过（迁移 DDL 与 6.json 完全一致）。
- **给后续会话的注意**：v6 之后**任何机器上装过 M3 的包，库就已是 v6**——再装回 0.1.0（v5 代码）会打不开数据库（Room 无降级路径，直接崩）。模拟机走查一律用当前最新包；`GxuDev` 上用户真实数据第一次被 M3 包打开时就是一次真实迁移，走查前后仍按老规矩核对活跃/软删条数。

### M3-1 标签与分类隐藏（2026-10-12）

- **交付**：
  - **数据**：`TagDao`（两级观察 / upsert / 隐藏 / 软删连带子标签与流水关联 / `tagsForTx` / `setTagsForTx` 整批替换 / 用量计数 / 按标签查流水）；`MIGRATION_6_7` 给 `categories` 加 `hidden`（**勘误**：不是 v1 预留字段，真加一列，D074），DB v7 + `7.json` 入库 + migration 测试
  - **记一笔**：信息条新增「标签」chip（非转账）→ 标签抽屉（两级缩进展示、多选、空态给出路、「新建标签」入口、底部「选好了」收尾）→ 新建弹窗（名称 + 位置：一级 / 某个一级标签之下，**建完即选中**）；保存时**流水与标签关系单事务**提交；编辑回填（`tagsForTx` 直查关系表，不读 WhileSubscribed 的空表）
  - **详情页**：`明细` 卡新增「标签」行（多个 `·` 连接，无标签不出现）
  - **标签管理页**（我的 → 标签管理，新路由 `tags`）：两级列表 + 新增 + 重命名 + 隐藏开关 + 删除（弹窗里给「已用于 N 笔流水」与「删除会一并删掉它的 N 个二级标签」两条事实）+ 页脚写清隐藏 vs 软删的区别
  - **分类隐藏**：分类管理页编辑弹窗加「隐藏」开关 + 列表「已隐藏」标记；隐藏的分类**连子树一起**不进记账宫格（正在编辑的那条流水的分类及其父级一律保留，UI-13 同型坑）；「分类管理副标题写隐藏实际软删」核对后**已不存在**，不带文案改动
  - **口径**：隐藏 = 不再出现在记账宫格与筛选、历史流水与统计照常；一级标签/分类隐藏时它的二级一起收起（父 chip 不画、二级没有入口——模拟机走查发现）
- **测试**：新增 24 条（369 条全绿）——`TagDaoTest`（8：两级/隐藏/整批替换幂等/删除连带/用量/按标签查流水）、`TagsViewModelTest`（3）、`TagsFlowTest`（4：选标签→保存→详情→编辑回填、新建即选中并落库、抽屉多选可取消、隐藏父标签时二级不进选择器）、`AddHiddenCategoryTest`（2）、`DetailTagsTest`、`MigrationTest` v6→v7、`TagsScreenTest` 浅色截图、`TagPickerScreenshotTest`（chip/抽屉/空态三张）、`DetailScreenScreenshotTest` 标签行、`DarkThemeScreensTest.darkTags`；`./gradlew build test lint` 全绿
- **模拟机（GxuDev，用户真实数据）**：装 M3 包时**真实库 v5→v7 迁移成功**（user_version 7、6 张新表 + 新列都在、756 活跃/2 软删/60 分类/3 账户/2 账本**全部不变**）；走查 记一笔选标签 → 保存 → 首页 → 详情「标签 trip」→ 编辑回填「标签 · 1」；标签管理新建二级（位置「trip 之下」）、隐藏（列表出「已隐藏」）、抽屉里隐藏的父与其二级都不出现、取消隐藏后两级都回来；分类隐藏后记账宫格收掉那一格与它的子树、取消隐藏恢复；删除标签（二级→一级）后管理页回空态；单条滑删探针流水 + 撤销条正常。**收工库内：756 活跃 / 3 软删（+1 探针流水）、标签 0 活跃 / 2 软删（探针标签）、分类 60/1 且 hidden 全 0、关联表 0 行、user_version 7**——用户数据完好。截图 44 张归档 `docs/local/界面重构验证/m3_*.png`（不入库）
- **本轮新增的坑（写给下个会话）**：
  - **Robolectric**：含文本输入框的 AlertDialog 依旧不 idle（已知）→ 本片把"弹窗里的动作"放到 **VM 层**测（`TagsViewModelTest`，用 `shadowOf(Looper.getMainLooper()).idle()` + 真时间轮询）；**踩到的假通过**：`selectableTags` 这类 `WhileSubscribed` 的 stateIn，只订了上游流时自己的 `.value` 恒是初值（空表），"断言看不见"什么都没证——**要断言哪个流就订哪个流**
  - **True-time vs 虚拟时间**：`waitUntil` 花的是虚拟时间，背景线程（Room）可能一秒真时间都没分到 → 等"新页面两次查询回填"这类条件要用真时间轮询（`waitForIdle` + `Thread.sleep`）
  - **adb 走查**：`input text` 在中文 IME 下只是拼音**组合**，要再 `input keyevent 66`（回车）提交才进输入框；**直接 pull `penned.db` 会读到旧快照**（Room 的 WAL 还没 checkpoint）——先 `am force-stop` 再 pull，或把 `-wal/-shm` 一起 pull；点开关别猜坐标，dump 里找 `checkable=true` 的节点
- **下一条**：`M3-2` 退款 / 优惠录入（保存净额口径，D069）。

### 自动发包：push tag 即发版（2026-10-11，D067）

- **用户裁决**：「现在的发包链路太长了，改为自动发包，上传 tag 到 GitHub 时触发，发布信息即 tag 信息」。
- **落地**：`.github/workflows/release.yml`——tag 校验（tag ≠ version.properties 直接红）→ Secrets 还原 keystore + local.properties → 签名构建 + apksigner 验签门 → `makeUpdateJson`（urls = 本次 Release 资产直链；changelog = tag 注释逐行；minVersionCode 沿用上一份清单）→ `gh release create`（标题 = tag 名、说明 = tag 注释、附件 APK + update.json）→ update.json 提回 main（bot 提交，无变化跳过）。YAML 与 tag 注释/清单生成两段均在本地干跑过（v0.1.0 tag 实测）。
- **此后发版** = `bumpVersion` → commit → `git tag -a vX -m "每行一条说明"` → `git push origin main vX`，其余全自动（约 3~6 分钟，Actions 页可看）。
- **待用户一次性配置**：仓库 Secrets 配 4 个值（`PENNED_STORE_FILE_B64` = `penned-signing/keystore.base64.txt` 的内容、口令/别名在 `local.properties`）——配置好前的 tag 推送会在 CI 上红着并提示缺哪个。
- **文档**：D067、AGENTS 发布流程改写、本看板。

### 关于页改版 + 版本回到 0.1.0 + 自动检查开关下线（2026-10-10，UI-16）

- **背景**：M2 收尾后用户三点：①**版本号改回 0.1.0**（「目前才算是发布的第一版」）；②关于页「太丑」，照 QQ / 微信的关于页提升质感与高级感；③「启动时自动检查更新」开关去掉——「现在默认自动检查，开关放到后期的工程中」。
- **落地**（D064）：
  - **版本**：`version.properties` → `0.1.0（1）`（M2 期间为跑闭环临时升到 0.2.0/0.3.0，首发号就此定死；唯一出处不变，此后只走 `bumpVersion`）
  - **关于页**：品牌头（72dp App 标记 = `primary` 实心圆角块 + 白色自绘 `Pen`（与启动图标同一支笔）+ `一笔一记` + 定位语 + **极淡单色渐变底**）→ 版本 / 更新两行卡（`当前版本` 信息行 + `检查更新` 整行入口）→ 居中页脚隐私句；状态机收进「行尾摘要 + 需要动作时才展开的详情块」，Idle 的铺满大按钮退场，`Checking`/`Verifying`/`UpToDate` 不开详情块；克制口径不变（零阴影、无边框白卡、大面积色只用极淡档）
  - **开关下线**：`AboutViewModel` 不再读 `autoUpdateCheck`、`autoCheckOnLaunch()` 恒传开；`SettingsRepository` 的键与读写方法保留待接回；`AboutViewModelTest` 留一条回归（存储里是旧版的 `false` 也照样检查）
  - **顺手**：`checkNow()` 加「下载/校验在跑时不重开检查」守卫（整行可点后点一下会把下载界面顶掉）；`Failed` 详情块去掉与行尾重复的标题；两个新自绘图标 `Pen` / `Refresh`（**教训**：描边 V 形箭头在 20dp 下糊成「缺角的圆」，箭头要用实心形）
- **测试**：截图 12 张（`ui16_about_*` + `dark_about_idle/available`，补十那套 `m2_about_*` 作废）+ 新增两条接线断言（整行点击 → 检查；「重试」走续传、「重新检查」重新拉清单）；`./gradlew build test lint` 全绿。
- **模拟机（GxuDev，真机数据 756 活跃 / 2 软删、3 账本、3 账户，走查前后一致）**：`adb install -r -d` 降级装 0.1.0 → 我的页「版本 0.1.0 · 检查更新」→ 关于页浅色（`ui16_a1_about`）→ 点「检查更新」→ 行变「检查中…」且箭头退场（`a2`）→ 本机没起假清单服务 → 失败态「未成功 / 更新清单下载失败 / 重试 + 重新检查」（`a3`）→ 深色（`a4`）；已还原浅色并 force-stop，截图归档 `docs/local/界面重构验证/ui16_*.png`（4 张，不入库）。
- **文档**：`docs/界面规范.md` §13·补十一、`decisions.md` D064、`docs/specs/M2.md` S2/S6 修订、本看板。
- **后续（2026-10-11，D065）**：用户复核真机截图后问「`重试` 和 `重新检查` 是不是重复了」，裁决**合并**——失败态只留一个主按钮「重试」（`retry()` 按"手上有没有清单"自己分路），重新拉清单走上面那行「检查更新」；测试钉一条「重新检查」不得存在。
- **后续（2026-10-12）**：关于页加「项目主页」行（用户要求）——同一张卡的第三行，副标题显示仓库地址（剥 `https://`），地址走 `BuildConfig.REPO_URL`（`defaultConfig` 常量，与清单地址同源），点按 `ACTION_VIEW` 跳浏览器；截图测试补断言（行存在 + 回调接线）与 `ui16_about_idle` 基线更新；真机验证跳转正确（Chrome 拉起、地址对）、库内数据未动。

### M2 应用内更新（2026-10-10，M2-0~M2-9）

- **背景**：M1 收官后按方案第七节打通"能自己迭代"的通道——全 App 唯一的服务端功能（纯静态托管，无鉴权/无数据库/无上报）。
- **切片与提交**：M2-0 规格冻结（`docs/specs/M2.md`，含协议/客户端流程/发布工程/验收与不做清单）→ M2-1 依赖与权限基建（okhttp 4.12.0 + INTERNET/REQUEST_INSTALL_PACKAGES + FileProvider + debug 明文放行 + 清单地址 BuildConfig；**D060 钉编译工具链 JDK 17**）→ M2-2 纯逻辑（清单解析/版本比较/分级/sha256/节流/文案/签名判定）→ M2-3 检查器（`HttpFetcher` + OkHttp + 多地址回退 + 节流）→ M2-4 下载器（流式 + Range 续传 + 进度 + 摘要）→ M2-5 安装链路（签名一致性 + FileProvider URI + Intent + 未知来源引导）→ M2-6 关于/更新页（状态机 + 两个可取消弹窗 + 启动自动检查挂 PennedApp 层）→ M2-7 发布工程（version.properties/bumpVersion/makeUpdateJson/签名/minify）→ M2-8 闭环验证 → M2-9 文档。
- **用户裁决**（贯穿全程）：批准引入 OkHttp（D058，选 4.12.0 因本机缓存离线可解析、不引 mockwebserver）；我生成 keystore 你备份；applicationId 终审 `dev.penned.app`（D057）；**本 App 从不强制更新**——所有更新弹窗可取消、`minVersionCode` 只升级文案（D059）。
- **口径**：见 D061（协议与客户端）、D062（发布工程与密钥）、D063（闭环验证结论与测试开关）。
- **实测（M2-8）**：
  - **debug 原地升级**（GxuDev，**用户真实数据 756 活跃 / 2 软删全程未动**）：启动自动检查弹可取消提示 → 关于页 → 下载 13.6MB → 校验（摘要 + 真机签名一致性）→ 未授权引导 → 系统设置授权 → 系统安装器认下 FileProvider 的包 → 装完 **0.2.0（2）**；再检查「已是最新版本 + 上次检查 10月10日 15:00」。
  - **release 签名闭环**（新建 AVD `PennedM2`，同 android-35 镜像，无需联网）：装签名 release 包 → 冒烟走查 6 屏（R8 没破坏 Room/Compose/DataStore/org.json）→ 自更新到 **0.3.0（3）**。
  - **三条负例**全按预期拒绝并删文件：摘要不符、debug 装机喂 release 包、release 装机喂 debug 包；中途还撞上一次**真实的"没下完"**（python http.server + 模拟机虚拟网卡偶发丢尾部几字节）——App 判"下载未完成"、留残包、重试续传，行为正确。
  - **体积**：release 8.97 MiB（未签名未压缩）→ **2.10 MiB**（minify + shrinkResources，已签名），远低于 8MB 预算；发布配置核对：release 禁明文、无测试开关、dex 里是线上地址（`raw.githubusercontent` + `jsdelivr`）。
- **测试**：新增 45 条（域层/检查器/下载器/安装/文案 24 + `AboutViewModelTest` 7 + 截图 11 张基线 + `UpdateTextTest` 等），全量 **345 条**全绿；`./gradlew build test lint` 全绿。
- **本轮明确不做**（规格已列）：强制更新、静默自动安装、增量/差分更新、DownloadManager、WorkManager、多渠道/灰度、商店上架、更新检查携带任何标识；日历视图（归属结清：不在 M2）。
- **遗留**：①`Available` 态没有「重新检查」（清单变旧只能重启 App 刷新——真机发现、未改）；②托管渠道待定（首发必须国内可达，GitHub raw 只作备）；③线上地址的真实连通性 = M2-10 发布时验；④新 AVD `PennedM2` 是测试机（带测试开关的 release 包，别当干净环境）。

### M1 正式收官 + M2 决策沉淀（2026-10-10，M1-C）

- **背景**：用户要求核查「M1 规格有没有做完、能不能开 M2」。开工基线 `7adf089`（UI-15），工作区干净。
- **核查方式**：按 `docs/specs/M1.md` 逐条比对代码实现（文件/符号级证据），并单独扫死 UI、TODO、空 `onClick`。
- **结论**：**M1 判完成**——第一批 S1~S6 全部实现且入口已接、验收 7 条逐条核实；第二批 8 项完成 7 项，唯一开口是「账单详情退款流程」只交付只读展示（录入/编辑交互留 M3）。M1 规格文件的 7 个验收 checkbox 此前一直未勾（与看板记录矛盾），本轮补齐并附完成情况段。
- **用户裁决（四项，2026-10-10）**：①退款交互**留 M3**（与报销/债务同族语义一起做）；②**批准引入 OkHttp**（D058，方案原文即 OkHttp；选 4.12.0——本机 Gradle 缓存已有、离线可解析；不引 mockwebserver，测试改用 JDK 自带 `com.sun.net.httpserver`）；③**我生成 release keystore、用户离线备份**；④**applicationId 终审 `dev.penned.app` 冻结**（D057，D013 关账）。
- **追加裁决（计划审阅时）**：**本 App 从不强制更新**——所有更新弹窗可取消、可返回键关闭，`minVersionCode` 只降级为文案分级（D059）。
- **落地**（本提交）：`docs/specs/M1.md` 勾验收 + 完成情况段；`MoneyExpressionTest` 补一条表驱动用例（贴合规格 S2 的"表驱动单测"措辞）；`decisions.md` 记 D057/D058/D059；本看板（M1 收官、M2 切片清单开列）。
- **验证**：`./gradlew build test lint` 全绿（同提交上 UP-TO-DATE，另 `--rerun-tasks` 强制重跑单测与 lint 复核）；4.12.0 版本选择依据已核实（Gradle 缓存 `okhttp/4.12.0` + `okio-jvm/3.6.0`）。

### 首页折叠时条目错位（2026-10-10，UI-14）

- **背景**：用户报「当首页条目布局有变化时，所有条目会同时移动，一小段时间内造成布局混乱」（截图为条目互相压盖、行压在日头条与下一个月标题上），要求**先查原因**。
- **原因（停表逐帧量出来的）**：同一屏里有两条"布局变化时间线"——① 月卡头部 `AnimatedVisibility`（`HomeScreen.kt`，默认 `expandVertically/shrinkVertically` = `spring(StiffnessMediumLow)`，高度**逐帧**变）；② 每个流水行的 `Modifier.animateItem()`（默认 placement 也是 `spring(StiffnessMediumLow)`，语义是"从旧位置弹到新位置"）。行于是在追一个**每帧都在动的目标**：实测展开中途行顶比日头条底高 **24dp**（钻进 28dp 高的日头条里）、收起中途甩出 **32dp** 空档，约 **400ms** 才归位；而日头条（`stickyHeader`）没有动画、直接落位——两边就此错开、互相压盖。虚拟化再补一刀：动画期间新进视口的行落在最终位置、老行还在路上，所以同屏"有的对、有的错"。
- **侧证**：资产页分组折叠用的是**同一个** `AnimatedVisibility`，从不花屏——因为它的行是一个 lazy item 里的 Column、没有 `animateItem`，差别就在它。拐点也清楚：头部动画是 AQ 轮加的，`animateItem` 从 AF 轮就在，那时表头是静态的，两个机制撞不上。
- **修法**：首页行走 `animateItem(placementSpec = null)`——**只留淡入淡出、不做位移动画**，行严格跟着表头的高度动画逐帧走（读起来是"表头把行推下去/收上来"），同一屏只剩一条时间线，永不错位；顺带压掉"整表替换（切账本/导入）时行互相穿插"。
- **取舍**：列表增删行时其余行不再滑动补位、改为立即归位（被删行照旧淡出、新行照旧淡入）——记进待办。
- **验证**：① 停表探针：同一段动画里"行顶 − 日头条底"从 4/−24/−20/−10…（展开）与 4/+32/+28…（收起）变成**全程恒 4.0dp**；② 新回归测试 `HomeMonthToggleTest`（停表逐帧断言这个差恒等于静止基准 ±1dp、且日头条确实移动过 >40dp；**把 `animateItem()` 改回去立刻红**，失败信息里就是那串错位量）；③ 模拟机 `animator_duration_scale=4` 慢动作逐帧：折叠中途、删除动作区、撤销之后，条头与行全程贴合、无压盖（截图归档 `docs/local/界面重构验证/ui14_*.png`）；④ `./gradlew build test lint` 全绿，静态基线不变（这次只动动画、不动布局）；⑤ 库内仍 756 活跃 / 2 软删（删掉的那条走 SQL 还原）。
- **本轮明确不做**：不动 `SwipeActionRow`（只横向动、不改高度，与本次无关）；不另造一套"补位滑动"；不为了躲这个问题改折叠的结构。
- **踩坑**：Robolectric 每跑一次在 `%TEMP%` 留一个 `robolectric-*`，攒到上万条后 Windows 会报 `FileSystemException: 系统资源不足`（整批测试挂一条）——清掉 `%TEMP%
obolectric-*` 即恢复（进待办）。

### 资产页账户行改动作区 + 子分类编辑显示修复（2026-10-10，UI-13）

- **背景**：①UI-12 时用户裁决"只改首页"，本轮用户「**把账户资产部分也改了**」；②UI-12 实测发现的既有缺陷（编辑一条**子分类**流水时，记一笔页宫格不显示所属分类），用户点名「**修了**」。
- **用户裁决（一问）**：账户行动作区**只放「删除」一个**——账户行点一下就进账户编辑页，再给「编辑」是重复入口（首页那行点的是详情，所以那边才需要补「编辑」）。
- **切片与提交**：
  - **UI-13** `SwipeActionRow` 参数化成 `actions: List<RowAction>`（配色走 `rowEditAction`/`rowDeleteAction` 工厂 + 无障碍动作从同一份列表生成）+ `AssetsScreen` 接线（一行为开、滚列表/折叠分组/点行内容收起、展开态点行只收起不进编辑页）+ **删 `SwipeToDeleteRow.kt` 与它的确认弹窗分支（退场）** + `loadForEdit` 改从库里查父分类 + `AssetsRowActionTest`（5 条，替换 `AssetsDeleteTest`）+ 新增 `AddEditSubCategoryTest`（2 条）+ 资产页浅/深动作区截图 + D054/D055 + 规范 §8/§9/§10/§11/§13 补九 + 本看板
- **实测**：`./gradlew build test lint` 全绿；模拟机（真机数据：3 个账户、流水 756/2）：左滑只露 64dp 删除块、露满后逐行采样 0/170 像素差异；点删除 → 账户 3→2、净资产 4,000→1,000 → 点撤销 → 3 个账户全回；给「现金」插一条探针流水后点删除 → 提示条「「现金」还有 1 笔流水，先处理流水再删除」+ 账户未删 + 动作区收起（探针已删）；展开态点行内容只收起不进编辑页；深色一套；首页左滑「食品餐饮 · 饮料酒水」→ 点「编辑」→ **子分类条带展开、「饮料酒水」选中态芯片**；截图归档 `docs/local/界面重构验证/ui13_*.png`（8 张，不入库）；收工库内 756/2 与开工一致。
- **本轮明确不做**：账户编辑页里那个朱砂「删除账户」按钮与其"点了才说不能删"的禁用理由文案（D026 既有设计，动它要另开一轮）；首页/资产页以外的列表（统计分类详细页、搜索页）仍无左滑手势（设计如此）。

### 首页流水行改 QQ 式左滑动作区（2026-10-10，UI-12）

- **背景**：用户报缺陷：「首页账单条目**极易误触**向左滑动删除条目」，点名对齐移动端 QQ——左滑依次露出两个按钮、**完全展示后不可再移动**、**松手不消失**、此时可点按钮；按钮定为「编辑」「删除」（附 QQ 截图）。
- **根因**：`SwipeToDeleteRow` 用 M3 `SwipeToDismissBox`——左滑 ≥56dp **或**甩速 >125dp/s 就判"滑出"并弹 `ConfirmDialog`（D031 的防护是弹窗，但触发条件本身极松）。模拟机旧包实测：横向一滑即弹「删除这条流水？」；纵向为主的斜滑被列表抢走手势、不触发 —— "极易误触"的窗口正是"手往左飘了一点"。
- **用户裁决（两问）**：①点「删除」**直接删 + 保留 10s 撤销**（QQ 语义，二次确认弹窗在首页退场）；②**只改首页**，资产页账户行不动。
- **切片与提交**：
  - **UI-12** 新组件 `SwipeActionRow`（跟手 1:1、夹在面板实测宽、过半宽或甩速 ≥500dp/s 定格、`PennedMotion.base` 落位、缩放 0 直接落位、两个 64dp 直角实心块）+ `PennedDimens.rowActionWidth` + 首页接线（`revealedRowId` 纯 UI 状态 / 只留一行为开 / 新增 `onEditRow → add?editId=`）+ 删 `txSummary` + `HomeRowActionTest`（8 条）与 `AddFlowNavigationTest` 新用例 + 截图 2 张 + `ContrastTest` 99→103 对 + D053 + 规范 §8/§9/§10/§11/§13 补八 + 本看板
- **实测**：`./gradlew build test lint` 全绿；`HomeRowActionTest` 8/8（含"露满后继续滑**行位移像素不变**""**竖向滑动不展开**"两条钉住 QQ 口径与误触回归）；模拟机（真机数据 756/2，模拟机时钟 2026-10-10 UTC）：旧包复现误触 → 新包同路径只露动作区、露满后再滑逐行采样 0/165 像素差异、点编辑带出 -6.00/10月8日/无账户、点删除 755 +「已删除 1 条」→ 撤销 756/2 复原；深色一套 + `animator_duration_scale=0` 一套；截图归档 `docs/local/界面重构验证/ui12_*.png`（10 张，不入库）；收工库内 756/2（与开工一致，删除+撤销走过一轮）。
- **遗留（本轮不做）**：①资产页账户行仍是"滑过阈值 → 确认弹窗"（用户裁决只改首页），同一 App 暂时两种左滑语义；②不做"点屏幕任意空白处收起"、不做跟手回弹。
- **顺手发现的既有缺陷（已报，未改）**：编辑一条**子分类**流水时，记一笔页的宫格不显示所属分类（父格无选中、子分类条带不展开）——`AddViewModel.loadForEdit` 直接读 `categories.value`，而它是 `WhileSubscribed` 的 `stateIn(…, emptyList())`，加载完成前读到空表 → `expandedParentId` 回落成子分类 id。数据没丢（保存仍带原分类），详情页进编辑同样复现，与本轮新入口无关。

### 记一笔顶栏返回闪退修复（2026-10-09，UI-11）

- **背景**：用户报 bug：记一笔页面点左上角返回会"直接退出 app"，用系统返回则正常回首页。
- **根因（两处叠加）**：①`PennedApp` 里 `getStateFlow(HOME_FLASH_KEY, null)` 的**裸 `null` 让泛型推成 `Nothing?`（JVM `Void`）**，首页读取 flash 时插 `checkcast java/lang/Void`，一旦真读到 String（"已记账"）就 `ClassCastException` 崩溃闪退（`javap` 反汇编可见合成方法签名 `…(State): java.lang.Void`）；②`AddTransactionScreen` 的 `onBack` 被错接成 `onDone`——"返回"走的是"保存完成"的流程（写 flash + pop），所以顶栏返回会写 flash 触发崩溃，而系统返回只 pop 不写、所以正常。附：AH/AQ 轮加的"保存完成提示条"从上线起就没成功显示过（每次都在读取时崩），日志里用户 16:41:53 保存那笔 6.00 元与 46ms 后的崩溃同秒可证。
- **切片与提交**：**UI-11** 类型显式化（`getStateFlow<String?>` / `mutableStateOf<String?>` / `set<String?>` + 变量标 `String?`）+ `AddTransactionScreen` 新增 `onBack` 参数（PennedApp 里接 `popBackStack()`，不写 flash）+ 新增 `AddFlowNavigationTest`（真库 + 真导航，两条用例，修前必红）+ D052。
- **实测**：`./gradlew build test lint` 全绿（含新用例；测试两个坑：Snackbar 的 4s 展示窗口要停表逐帧推、分类列表要 waitUntil）；模拟机（真机数据 756/2，浅色）：进记一笔 → 点顶栏返回 → **崩溃计数 0**、回首页无误导提示；系统返回同走一遍；本轮无写操作，库内仍 756 活跃 / 2 软删；截图归档 `docs/local/界面重构验证/ux4_*.png`（不入库）。
- **遗留（本轮不做）**：从详情页进编辑（`add?editId=`）保存后，flash 写在"详情页"的 handle 上而详情页不读它 → "已保存"提示看不到（老问题，不属这次崩溃的范围）。

### 配色 v6：分类图标统一淡灰绿（2026-10-09，AS-1~AS-3）

- **背景**：v4/v5 交付后用户复看真机：「**所有分类图标改为淡灰绿色。进度条/占比图颜色不改**」（附首页流水行与支出数据卡两张截图）。
- **诊断**：四次失败（AR 太艳 / RA 国画 / v3 又艳 / v4 仍被点名）都栽在同一处——**图标底在承载分类身份**。图标底是重复次数最多的色面（每行一个），八槽再淡也是八种颜色在闪；而用户点名的样板始终是日头条那一栏。
- **用户裁决（两问）**：①资产页账户行/账户类型弹层/管理账本/导入批次行**一起统一**；②记一笔宫格**选中态改品牌青绿实心 + 白字**、9dp「有子分类」小圆点**保持分类色**。
- **切片与提交**：
  - **AS-1**（`0916313`）`Pigment` 新增 `iconHue`/`iconHueOnDark` + `pigmentIconFill()`；`CategoryAvatar` 未选中底不再分槽、选中态改 `primaryInk` 实心；分类调用点收口（首页行/搜索/详情/分类管理/统计数据行/记一笔宫格）+ `ContrastTest` 111 → 99 对 + 规范 §2/§3/§4/§8/§11
  - **AS-2**（`5ab871b`）账户类型色退场：删 `accountPigmentSlot`、`CategoryAvatar.slot`、`pigmentFillAt`/`pigmentFill`；资产页/账户编辑/类型弹层/账本/导入行一起统一
  - **AS-3** 模拟机浅/深复验 + 规范 §13 补六 + D051 + 本看板
- **实测**：`./gradlew build test lint` 全绿（三个切片各跑一次）；Roborazzi 基线全量重录（80 张现役）并逐张过目——首页浅/深、统计浅/深、记一笔浅/深、资产、账户类型弹层、分类管理、组件总览（基线不入库，在 `app/build/`）；模拟机（真机数据，浅/深）首页行头像与日头条、统计环/图例/占比条（**颜色一格没动**）、记一笔宫格 + 选中态青绿实心 + 分类色小圆点、资产三个账户行、分类管理八个一级分类；截图归档 `docs/local/界面重构验证/as_*.png`（14 张，不入库）。
- **库内**：开工与收工都是 **756 活跃 / 2 软删**（本轮无写操作；UI-10 记的是 758/2，差额发生在本轮开工之前，不是本轮造成的），模拟机已还原浅色。
- **关键取舍（D051）**：①图标底取用户截图里那抹绿（v4 翠槽 12% 淡底），**独立成 token**——改分类色不动图标底、反之亦然；②深色下统一绿与卡片 1.75:1，比 v4 多数槽（1.2~1.4:1）略显眼，模拟机上看过可接受，嫌重就单独下调 `TINT_ALPHA_DARK`；③小圆点压在**选中气泡**上对比度低这条 v4 就有（同色点压同色实心），未处理。
- **本轮明确不做**：色相表/色相角/槽位顺序（`PigmentAssignTest` 九条断言照旧）；图表细件 45%/65%；日头条/主按钮/收支红绿/中性层；字阶/间距/圆角/结构/动效/业务层/DB。

### 首页悬浮入口复原口径修正（2026-10-09，UI-10）

- **背景**：用户报 bug：首页「添加一条新记账」"只要往回滚一点点（即使没到顶部）就从「＋」变回完整胶囊"。
- **根因**：AQ 轮（D042）照 Material 通用口径定的"往回看立刻展开"——`AddEntryScrollGate` 里 `deltaY > 0 → 展开` 且不设阈值；实测（模拟机 755/2）往回滚 150px（列表离顶部还远）就弹回通栏。
- **用户裁决**：复原只认"回到列表顶部"（原设计 = 顶部完整、往下看退化、回顶复原）。
- **切片与提交**：**UI-10** `AddEntryScrollGate` 把 `deltaY > 0` 改为"只清掉累计、保持原状"（`atTop → 展开` 与"往下看累计 ≥24dp 才收起"不变）+ 注释/KDOC 口径同步 + `AddEntryMotionTest` 与 `HomeScreenScreenshotTest` 改用例 + D050。
- **实测**：`./gradlew build test lint` 全绿；模拟机（真机数据，浅/深）：顶部完整 → 往下翻「＋」→ 往回滚 150px **保持「＋」** → 连续往下翻多次仍「＋」 → 滚回顶部复原完整；截图归档 `docs/local/界面重构验证/ux2_*.png`（不入库）；**本轮无写操作**——开工与收工都是 **758 活跃 / 2 软删**（UI-9 基线 755/2 之后、本轮开工之前，用户自己在本模拟机上记了一笔 6.00 元「饮料酒水」，`createdAt` = 2026-10-08T16:41Z）。
- **本轮明确不做**：形变几何/时长/标签收窄/点击行为不动；不引入"往回滚的阈值"——判定只认"回到顶部"这一条。

### 统计数据卡「两行其他」修复（2026-10-09，UI-9）

- **背景**：用户报 bug：月统计 **2024-10** 的支出数据出现**两行「其他」**（-20.00 与 -16.80），且**都点不进详细页**。
- **根因（两条叠加）**：①`categoryTotals` 按**分类名**分组，并把第 7 名（topN=6）之后的小分类合并成一个**固定叫「其他」**的合成桶，而种子分类里**真实存在**一个一级分类就叫「其他」——当月一级分类 >6 且真实「其他」排进前 6 时（2024-10 正是），两行同名；②能否下钻按**名字**判定（`it.name != OTHERS`），真实「其他」被一并判成桶 → 两行都点不动。同一"名字当身份"还连累配色（`slotOf` 把真实「其他」也当中性灰）与子分类卡（同样按名字排除）。
- **用户裁决（两问）**：①「无法展示真实分类吗？**我希望展示事实的分类**」→ **取消 topN 合并**，全部真实分类逐行列出；②真实「其他」**恢复彩色**（哈希落 0 号玫红）。
- **切片与提交**：
  - **UI-9** `StatsMath` 删 `topN`/`OTHERS` 与合并分支（`categoryTotals`/`childTotals` 全列）+ `CategoryTotal` 新增 `drillable`（真实分类 true；「未分类」与直挂桶 false）+ `StatsDataCard` 只读 `drillable`（UI 层不再按名字判定）+ 删 `PennedPigment.OTHERS`（「其他」回归哈希彩色）+ 测试（`StatsMathTest` 全列/drillable/真实「其他」回归、新增 `StatsDataCardTest` 点击行为、`PigmentAssignTest` 中性名单与「其它」落槽、`StatsScreenScreenshotTest` 增全列截图）+ D049 + 规范色表与统计段
- **实测**：`./gradlew build test lint` 全绿（272 个测试；其间 `HomeSwipeDeleteTest` 撞上一次 Robolectric 临时目录里 DataStore 重命名的 flaky，单独重跑即绿、非本轮引入）；模拟机（真机数据 755/2）月统计 2024-10 —— 修复后 7 行真实分类（原合成桶 -16.80 回归真实身份「健康医疗」）、「其他」彩色且**可点进详细页**（共支出 20.00 / 1 笔 / 明细「帮买早餐」）；深色同走一遍；验完库内仍 **755 活跃 / 2 软删**（只读），已还原浅色；截图归档 `docs/local/界面重构验证/ui9_*.png`（不入库）。
- **关键取舍（D049）**：①**取消合并**换来"每行都是事实"，代价是数据卡行数 = 分类数（页面本来可滚动）、同屏彩色分类 >8 个时哈希槽必然有重复色（`assignSlots` 只能在八槽里取最优）；②可点性判定**下沉到数据层**（`drillable`），因为真实分类可能就叫「其他」（种子数据）或「未分类」（用户自建）；③「未分类」行行为不变（单列、不可点，沿用既有裁决）。
- **本轮明确不做**：数据卡行数上限（若真机上分类很多觉得长再加，另行裁决）；分类详细页的子分类卡片式（结构不动）。

### 配色 v4/v5：一个色相三种强度（2026-10-08，RC-1、RC-3 两次提交）

- **背景**：v3 交付后用户复看真机：「修改的色彩又**艳**起来了（如图"晚"字为鲜艳的紫粉色），与整体不协调，显得不美观」，要求「**符合日头条那样偏淡的色彩，统一风格**」，并明确本次**不要 git commit**。
- **诊断**：四轮里失败的三次（AR 太艳 / RA 国画 / v3 又艳）栽在同一处——**把颜色铺成了面**。用户点名的样板始终是日头条那一栏（极淡青灰底 + 3dp 深青竖条 + 墨色日期）。这轮换口径：**大面积一律极淡，色相只在小面积上给满**。
- **切片**：
  - **RC-1**（`3b6a79e`）`Pigment.kt` 收成"一个色相两种强度"（`hue` / `hueOnDark` + `TINT_ALPHA_LIGHT 0.12` / `TINT_ALPHA_DARK 0.22`）+ `CategoryAvatar` 改**淡底 + 墨色字形**（`selected` = 全强度实心 + 反白）+ 宫格子分类小圆点改全强度色 + 八槽重解到"白字 4.5:1" + `ContrastTest` 103 → **120 对** + 规范 §3 硬规则 8 / §4 / §8 / §11
  - **RC-2**（并入 `3b6a79e`）模拟机浅/深复验 + 截图归档 `docs/local/界面重构验证/rc_*.png` + 规范实测段
  - **RC-3**（`ec01c00`）用户复看后追加「**支出占比的配色也需要改动**」（点名全强度的占比环与占比条）：细件改走**第三档**——色相 45%（浅）/ 65%（深）稀释（比块面浓、比状态淡），环/图例/条三处共用 `pigmentMarkFor`；`ContrastTest` 给它单开 `markVisible` = 1.40:1（浅）/ 2.30:1（深）的下限（够不上 3:1 是物理事实，取舍与理由写进规范 §4/§11.1 与 D048），配对数 120 → **111**
  - **RC-4**（并入本次看板提交）模拟机再次复验 + 规范 v5 实测段
- **实测**：`./gradlew build test lint` 全绿（**111 对**对比度）；护栏在交付前又拦下一次——按 4.5 取整后 5 对掉到 4.48~4.50，按 4.62 重解才留出余量；块面与白卡 1.16~1.21:1（日头条底 1.12:1，同一档），细件与白卡 1.84~2.00:1；模拟机（真机数据 755/2）浅深各走一套，验完库内仍 **755 活跃 / 2 软删**，已还原浅色。
- **关键取舍（D047 + D048）**：①**块面字形不染颜料**（改 `onSurface` 墨色）——前几轮把字形染成彩色，正是"艳"与"国画"两种观感的直接来源；②淡底取 **12%** 是量出来的折中（日头条底 ≈ 8%，但 ≤8% 八个色相糊成一片，用户上一轮抱怨过"连续三种一样的颜色"）；③**色相角与槽位顺序一个都没动**，`assignSlots` 与七条断言原样有效；④图表细件起初保留全强度，用户复看后要求一起改淡，于是补第三档 **45%/65%**——两档淡色的下限都是"渲染比出来再写进护栏"的，不是拍的；⑤细件档与卡片只有 1.84~2.00:1（够不上 3:1），这条**如实写进规范**，理由是环/条只是辅助视觉、占比数字与金额每行都写着。
- **本轮明确不做**：字阶/间距/圆角/结构/动效/业务一行不动；语义色与品牌青绿不动；支出趋势柱状图（走语义支出红）不动。

### 配色 v3：颜料盘换「亮档块面 + 深档字」（2026-10-08，RB-1~RB-3）

- **背景**：RA 轮交付后用户复看真机：「**配色又绕回偏古董的国画色了**，不是很好看。你可以参考如图给出的两个课表页的课程格子配色，进行优化」。
- **诊断**：问题不在彩度高低，而在**明度与分工**——RA 把彩度压到 ≤50% 却保留了深明度，八色正好落回国画颜料的色域（胭脂 `#9E3556`/赭石 `#9B4929`/松绿 `#22664C`/黛青 `#286576`/赭金 `#806630`）。
- **取色方法**：把两张参考截图**按像素量化取主色**（`build/sample_ref.py`），量到参考色带 **S 60~89% / L 66~81%**（玫红 `#ED7C9A`、珊瑚 `#E78B75`、薄荷 `#77E2CF`、天蓝 `#82ADF8`、淡紫 `#B9A6F5`、钢蓝 `#679BCA`、琥珀 `#E7B575`）。参考图自己的白字只有 **2.0~3.0:1**（低于 AA），所以**色带照参考、字不照抄**：字形改走同色相深档。
- **切片与提交**：
  - **RB-1** 颜料盘 v3 三档（`fill` 亮档块面 / `mark` 中档细件 / `ink` 深档字与选中）+ `CategoryAvatar` 改**实心亮档块 + 深档字**（`selected` = 深档实心 + 白字）+ 中性件改「灰底白字」三档 + `ContrastTest` 93 → **103 对** + 规范 §4/§3 硬规则 8/§8/§11 + D046
  - **RB-2** 模拟机浅/深复验（首页/统计/记一笔/资产/分类管理）+ 截图归档 + 规范实测段
  - **RB-3** 本看板
- **实测**：103 对全绿；过程中护栏拦下两次——① 中档首解有 3 对舍入后卡在 **2.99~3.00**，按 3.15 重解才留出余量；② 亮档块面在白卡上只有 1.45:1，这一条**明确不设下限**（装饰性块，分类身份由字形 + 旁边名称承担，参考图自己的块面也只有 1.4~1.6:1），理由写进规范而不是糊过去。
- **关键取舍（D046）**：①**色相表一个都不改**（341/17/157/193/267/233/295/40）——`assignSlots` 的同屏分配、`PigmentAssignTest` 七条断言、「同屏 ≥46°」全部原样有效；②**浅深两主题共用同一份亮档**（分类颜色是身份，不随主题变），深色下亮档块面在 `#181A1D` 上 5.4~12.0:1；③统计页的「细件」单独解一档中档（3.2:1）——上一版把深档当细件用，环/占比条是一坨暗红藏青，这次按**对比度**而不是明度对齐（绿/金压到 L32~37、紫/靛亮到 L60）。
- **本轮明确不做**：字阶/间距/圆角/结构/动效/业务一律不动；语义色与品牌青绿不动；色相表不动；深色主题的块面不做"深底亮字"翻转（保持与浅色同构，暗底上亮档反而看得见）。

### 配色降噪（2026-10-08，RA-1~RA-3）

- **背景**：AR 轮交付后用户复看真机：「相当不错，界面看起来变高级了，但配色依然不够满意——**太艳、太明亮**」，点名三处：首页每个账单条目左侧的图标、首页日头条那一栏、统计页支出数据行的左侧图标。
- **诊断（量化）**：共同点是"高彩度 + 大面积/重复出现"——八槽颜料彩度 62~93%（金 93%、橙 89% 最高）；头像淡底 12% 叠出一圈明显彩色；日头条是 `primaryContainer`（彩度 30%）横跨整屏、每天重复一条，是首页最响的元素。
- **用户裁决（四问）**：日头条走**极淡青灰底 + 深青竖条**、头像淡底 **8%/14%**、降饱和取**中等幅度（冷 ≤50% / 暖 ≤58%）**、语义色与品牌青绿**一律不动**。
- **切片与提交**：
  - **RA-1/RA-2**（`4954009`）颜料盘降饱和（玫红 `#B0234F`→`#9E3556`、橙 `#B93C0B`→`#9B4929`、翠 `#0E7A50`→`#22664C`、青 `#0E7490`→`#286576`、紫 `#7B3FC4`→`#7B43C0`、靛 `#3F4FC9`→`#4655C2`、洋红 `#A21CAF`→`#8F3398`、金 `#8F6205`→`#806630`，深色盘同幅度）+ 淡底 `0.12→0.08`/`0.22→0.14` + 日头条换 `surfaceContainerLow` 极淡青灰（`#EDF3F1` / 深色 `#1C2523`，日期改墨色、收支改次要色、**3dp 深青竖条保留**）+ `ContrastTest` 93 对 + 规范 §3/§4/§8/§11 + D045
  - **RA-3**（本轮）模拟机浅/深复验 + 规范实测段 + 本看板
- **实测**：93 对对比度全绿（金槽首算在自家 8% 淡底上只有 4.41，压暗一档后 4.90——护栏按预期拦下）；真机上日头条与白卡仍分得清（没触发备用手段）；库内仍 755/2，模拟机已还原浅色；截图归档 `docs/local/界面重构验证/ra_*.png`
- **关键取舍（D045）**：①**色相表一个都不改**——所以 `assignSlots` 的同屏分配、`PigmentAssignTest` 七条断言、"同屏 ≥46°"全部原样有效；②**金槽是唯一"降饱和 = 变浑浊"的槽**（压到 45% 会偏卡其），额外提亮一档成"沙金"，避免回到被否掉的赭石感；③**大面积彩色块一律压到极淡**（新硬规则第 8 条）：日头条这种每天重复、横跨整屏的色块彩度必须 ≤8%，色相识别只留给小面积（3dp 竖条、头像字形、图表段）；④图表环/占比条/宫格气泡共用同一份 ink，一起变安静
- **本轮明确不做**：字阶/间距/圆角/结构/业务一律不动；语义色与品牌青绿不动；色相表不动
- **遗留**：若真机上哪天觉得日头条与白卡分不清，备用手段依次是"加 1dp 顶部分隔线""底再深一档 `#E4EDEA`"（写进规范 §8 的组件契约）

### 界面视觉体系重做（2026-10-08，AR-1~AR-10）

- **背景**：用户反馈两条——①界面风格老旧，是老式工具应用观感，现代化美感不足；②配色陈旧，整体像古董工具软件。要求重做视觉层（**允许完全重写界面代码，保留全部业务功能**），**先出整套设计规范（色彩/间距/字体/阴影/圆角 token）再出页面代码**；风格对标现代生产力工具（Notion 移动端一档），简约克制；交互补状态完整性与克制的微动效；一套统一设计规范 + 组件化 + 可维护性；无障碍要对比度/可读性/深色适配。
- **诊断**（先取证再动手）：老旧的根子在**配色**不在布局——①八槽颜料盘是国画色（朱砂 `#A8433A`/赭石 `#97602F`/藤黄 `#866C1F`），三槽挤在棕-橄榄色域，正对应 Windows 时代 maroon/olive/green/teal/navy 那套老调色板；②中性层带绿味（底 `#F2F5F4`/卡边 `#E3E9E6`/输入底 `#EAF0EE`）像旧账本纸；③层级全靠 1dp 描边 + 8 处自造圆角 + 30 个文件里的硬编码 dp，"盒子套盒子"；④**主按钮白字只有 3.3:1**（WCAG 正文要 4.5:1）；⑤数据管理三页/搜索页结果/锁屏页整页没进 token 体系。
- **用户裁决（四问）**：①品牌青绿**保色相、整体加深**；②中性层换**冷中性灰 + 卡片去描边**（Notion 式扁平）；③分类八色**整体换现代色盘**；④Tab 页**去掉与底栏重复的页头大标题**。
- **规范先行**：新建 `docs/界面规范.md`（v2：色彩/字阶/间距/圆角/阴影/动效 token + 组件契约 + 逐屏版式 + 交互/无障碍/工程护栏 + 实测），取代 `界面重构方案.md` 一~八节（那份保留作逐屏历史）；D043（视觉体系 v2）+ D044（交互/无障碍/工程护栏）。
- **切片与提交**（每片 `./gradlew build test lint` 全绿后独立提交）：
  - **AR-1** `c74bc38` 规范文档 + 对比度核算：`build/contrast_check.py` 把 83 对配对照 WCAG 公式跑一遍，据此改掉三处（日头条右侧收支 70%→**85%**，70% 只有 4.31:1；橙 `#C2410C`→`#B93C0B`，自家 12% 淡底上 4.35:1 不够；深色 `primaryInk` `#4FC4B1`→`#55C9B6`），并**删掉 `#14B8A6` 那一档**（白底 2.49:1，达不到图形 3:1，进度条改用 `primary`）
  - **AR-2** `3d32673` token 层：`primary #2F9E8F→#0F766E`（白字 5.47:1 ✓）、中性层去绿味（底 `#F4F5F7`/卡 `#FFFFFF`/发丝线 `#EEEFF2`/新增 `iconMuted #878E99`，`outline` 降级为装饰性边界）、字阶（金额类 600→**700**、`titleMedium` 600→500、`labelSmall` **11→12sp**）、圆角收敛四档（8/12/16/24）、动效四档（`PennedMotion`）、颜料盘换现代八色；`ContrastTest`（87 对）+ `PigmentAssignTest` 新色相表
  - **AR-3** `18a9a47` 组件库：`PennedCard` 去边框、`PennedCardSection` 去掉外框内缩画法、分段控件改**白滑块 + 位移动画**、`PennedListRow` 前置图标改**圆角方块 tile**、次按钮改中性填充、`PennedDialog`/`PennedSheet` 走 24dp + 拖拽把手、`PennedSnackbarHost` 走**反向色**、`SwipeToDeleteRow` 补 TalkBack 删除动作；新增 `PennedSectionHeader`/`PennedStatCells`/`PennedProgressBar`/`PennedBadge`/`PennedPressable`/`AnimatedNumberText`；`ComponentGalleryTest`（浅/深两张总览图）
  - **AR-4** `5b35b8f` 首页+搜索+记一笔：页头紧凑化、月汇总与预算走共用件（进度条像素回归仍绿）、搜索框改填充式 + 结果补卡片容器、键盘去边框、**分类宫格改颜料气泡并整屏分配槽位**（此前一屏三个同色）
  - **AR-5** `c2f1098` 统计+资产+我的+日历：汇总走共用三列、卡片标题统一 `cardTitle`、**资产去重复页头** + FAB 56dp 零阴影 + hero 滚动数字、我的紧凑品牌头、日历去重复页头
  - **AR-6** `cf77bfa` 详情/账本/预算/分类：徽标与进度条走共用件、**裸 AlertDialog/DropdownMenu 全部收编**（预算分类选择改底部抽屉）、账本行内动作图标化（断言改 `contentDescription`）、字阶与缩进统一
  - **AR-7** `36667dc` 账户编辑/数据管理三页/锁屏：表单字阶与标签列进 token、**数据管理三页从 20dp 裸行纳入卡片语言**、锁屏进 token 体系、分区标题与卡片左缘对齐
  - **AR-8** `bc441be` 交互补完：底栏白底 + 顶发丝线 + 零 tonal elevation + 选中图标放大 1.08×、**保存后回首页给「已记账/已保存」提示条**（此前静默返回）
  - **AR-9** `8a49ba1` 无障碍与护栏：`TokenLiteralGuardTest`（颜色字面量 / 超 4dp 的就地间距尺寸圆角，**34 处就地值收进 token**）、图标按钮热区 44→**48dp**、`FontScaleTest`（1.5×/2.0×，据此把汇总格改竖排、chip 行改 FlowRow）、提示条反向层对比度配对、文档回写
  - **AR-10** 本轮：模拟机逐页验证 + 文档实测段 + 本看板
- **实测**：
  - 对比度 87 对全绿；token 护栏从 34 处收到 0
  - 模拟机（真机数据 **755 活跃 / 2 软删**）：冷启动 → 首页 → 统计 → 资产 → 我的 → 日历 → 记一笔 → 转账 Tab → 账单详情 → 搜索 → 数据管理 → 导入批次 → 分类管理 → 预算管理 → 管理账本，深色另走首页/统计/记一笔；**验完库内仍是 755/2**（本轮无写操作），模拟机已还原浅色；截图归档 `docs/local/界面重构验证/ar10_*.png`（不入库）
  - **实测中修掉两处不一致**：详情页头像此前拿「父 · 子」整串做哈希、与首页行的叶子分类**不同色**（改为与 `TransactionRow` 同口径）；分类管理页一屏 8 个分类里 **5 个撞成同一个颜色**（改走同屏分配）
- **口径延伸（写进规范）**：**一屏列出多个分类的地方**（记一笔宫格、分类管理、统计图表）走"同屏分配"（`assignSlots` 穷举求最小色相差最大，新算法：不撞色不动、撞色才搜 P(8,k)）；**单行/单卡出现分类的地方**（首页列表、搜索结果、详情）仍走名字哈希，保证跨页稳定（D040 口径）
- **颜料盘的诚实记录**：色相多集 341/17/40/157/193/233/267/295，**最多挑出 5 个两两 ≥45° 的颜色**（旧盘同样是 5 个，D040 记过）；一屏 6 个彩色分类时最优是 36°（穷举确认），靠淡底 + 桶名 + 数字一起区分。**槽位顺序刻意与色相顺序错开**——按色相排会让「晚餐」与「工资」只差 28°（旧盘 46°），错开后真实数据会同屏的槽位两两 ≥46°
- **本轮明确不做**：预测性返回手势、共享元素转场、记一笔改底部模态、Tab 跟手横滑、自定义字体文件（体积/授权）、图表交互升级（横向网格线/点柱下钻）、Roborazzi 的 record/verify 插件化（仍是旧待办）
- **遗留**：①同一分类在"宫格/管理页/图表"与"列表/详情"可能不同色（D040 口径的延伸，已写进规范，若要全局一致需放弃同屏不撞色）；②深色下 46° 相邻色（午餐/晚餐 都在粉紫区）分辨度一般——用户若觉得不够开，调 `Pigment.hues` 一行即可；③月合计与预算「已用」仍只覆盖最近 500 条窗口（旧待办）；④资产页仍是"圆 +"、首页是"胶囊"（有意保留两种形态）

### 首页月卡折叠 + 月汇总/预算入卡 + 悬浮记账入口（2026-10-08，AQ-1~AQ-3）

- **背景**：用户四条要求——①月总结与预算**搬到月卡顶部**；②月卡支持折叠展开（**本月默认展开、其余默认折叠**，点卡片切换）；③"添加一条新记账"移到**底部 Tab 栏上方改悬浮窗**；④**页面往下看时悬浮窗平滑收缩并移动到右下角退化成「＋」，往回看复原**（验收标准点名"过渡自然，无跳变、闪烁或卡顿"）。用户裁决（两问）：**预算块只挂本月卡**（预算表只有一条"每月预算"、无历史值，过去月份连"已用"都没得算）、**悬浮窗用通栏胶囊**（与原来的按钮同宽）
- 切片与提交：
  - **AQ-1**（`7c6061e`）聚合纯函数 + 月卡头部与折叠：`HomeGroups.MonthGroup.totals`（= 各日合计之和，与日头条同口径）+ `monthExpanded`/`emptyMonth`（删掉只服务旧顶部卡的 `monthTotals`，口径用例改测 `groupByMonth().totals`）；月标题行改**整行可点** + 旋转箭头 + `AnimatedVisibility` 汇总/预算块（默认 enter/exit）；**本月缺位时补一张空月卡**（预算块是预算管理页的唯一入口，全仓 grep 过）；`loading` 期整块不渲染；底距 24→80dp
  - **AQ-2**（同上）悬浮入口：`ui/home/HomeAddEntry.kt`——自绘无阴影胶囊（通栏 56dp/圆角 28dp）+ `AddEntryMotion`（宽度/标签收窄/淡出全由**一个进度值**算出）+ `AddEntryScrollGate`（方向判定，纯逻辑）；列表挂 `Modifier.nestedScroll`（只读不消费）；`AddEntryMotionTest`（曲线 + gate 表驱动）、`HomeAddEntryTest`（停表量形变几何）、`HomeScreenScreenshotTest` 改写（折叠/展开、入口 368↔56、多选隐藏、本月无流水仍有预算块）
  - **AQ-3**（`0272d06`）深色首页断言跟月卡头部走：测试数据固定在本月（写死 2026-10 会在跨月后因月卡不展开而失效）
  - **AQ-4**（本轮）修用户实测报的 bug：**预算进度条被预算块的 16dp 圆角裁掉**（条压在块的底边上，0.73% 的绿全落在被裁的 23px 里 → 看着像一分钱没花）。去掉这层的圆角裁剪 + 写清"不要裁"；新增像素回归 `budgetBarDrawsGreenAtTinyUsage`（按条的 bounds 扫像素：有绿色 + 紧贴左端；改回裁剪即立刻红——已实测）
- **实测**：
  - **停表量形变几何**（照 AP 轮做法）：把展开翻成收起，8 个采样宽度一路收窄、**≥3 个落在两端之间**（不是一步跳过），两端精确 368dp/56dp，**收起后 ＋ 与圆心重合 < 1dp**
  - **像素自检**：中途帧（宽 281dp ≈ 进度 0.27）标签右端距胶囊右缘 **78px**（没被圆角切字；标签在进度 0.55 就淡完，那时胶囊还有 196dp 宽）；收起态 ASCII 像素图确认 **56×56 正圆**、圆 x 328–383、**＋ 中心 355.5 = 圆心 355.5**；展开态 368 宽、右/下各留 16px
  - 基线重录并逐张过目：`m1_home_top`（本月卡展开：汇总 + 预算 + 进度条）、`ag_home_month_cards`、`aq_home_months_collapsed`（三张卡全折叠，只剩标题 + 日 + 行）、`aq_home_scrolled`（右下角正圆）、`aq_home_add_morph_mid`（形变中途取证）、`m1_home_budget_overspent`、`dark_home`
  - 模拟机（真机 755 活跃 / 2 软删；模拟机时钟比宿主慢，"本月"在机器上是 2026/9，另有一条早先探针留下的未来流水 2026/10 → 显示为折叠月卡）：冷启动即列表顶部（2026/9 展开＝汇总 + 预算，2026/10 折叠）→ 点标题折叠/展开 → 下拉看：胶囊收成右下角「＋」→ 上滑复原成通栏胶囊 → 点胶囊进记一笔 → 点预算块进预算管理（两处数值一致）→ 深色一套 → `animator_duration_scale = 0` 再走一遍；**本轮没有写操作，验完库内仍 755 活跃 / 2 软删**；截图归档 `docs/local/界面重构验证/aq_*.png`（不入库）
  - **预算进度条被圆角裁掉**（用户看图报的，像素实测：轨道从 x=40 才开始、x 17..38 全白）：去掉预算块的圆角裁剪后，条的两端回到块边缘、0.73% 的绿在左端画出一个 4dp 小点；模拟机用用户那个例子（已用 16.00 / 预算 2,000.00）复验通过
  - 期间修掉的真 bug：**冷启动列表凭空下移一截**——loading 期先渲染一张空的本月卡，真数据到了再往它上面插月卡，LazyColumn 按 key 锚住原位置把视口带偏（改成 loading 期间整块不渲染，与 UI-8"不闪空态"同一条思路）
- **本轮明确不做**：折叠状态不落盘（切 Tab 回来重置，与资产页分组折叠同档）；不做"跟手拖动"的形变（只做方向触发的收/展）；批量删除仍不加确认（AH 轮用户没要求）；搜索页/统计页/资产页不动
- **测试环境坑**（写在 D042a 里）：`captureToImage()` 在 Robolectric 下卡窗口重绘、`javax.imageio` 在 Android 单测编译期不可见——要断图像像素只能 Roborazzi 落盘 + `BitmapFactory.decodeFile` 读回
- **遗留**：月合计与预算「已用」都只覆盖最近 500 条加载窗口（与日头条同源，见待办）；月标题行仍只显示 `yyyy/m`（月合计在展开块里看）；悬浮入口在撤销提示条期间隐藏（沿用 FAB 老规则）——提示条那 10s 里要能点，得把提示条抬高

### 下钻页转场重做（2026-10-08，AP-1~AP-3）

- **背景**：用户反馈两条——①不同的一级界面（首页/日历/资产…）之间切换**保持现状**；②同一界面点开子界面时**子界面从右往左丝滑地滑出来**。根因：旧口径只把子页滑进 **1/5 屏宽**、同时与父页**交叉淡入淡出**（两页叠着各淡各的，既不"推"也不丝滑），且底栏在点进子页那一瞬直接消失、父页可用高度跳一截。用户裁决（两问）：**视差推入**（父页左退 1/3 让位）+ **底栏跟着收起展开**
- 切片与提交：
  - **AP-1** 口径与接线：`ui/nav/ScreenTransitions.kt`（`MotionSpec` 常量 + `screenMotion(initialIsTab, targetIsTab, isPop, motion)` 纯函数 + `Enter/Exit.toTransition()`）；NavHost 四个 lambda 收成一行、底栏改 `AnimatedVisibility`（`Alignment.Top` 的展开/收拢 + 淡入淡出，与推入同为 300ms）；`ScreenMotionTest`（转场表 9 情形 + 三条不变量）+ `NavTransitionTest`（停掉测试时钟量**滑动中途**的位置：推入/返回/切 Tab/动画关闭）
  - **AP-2** 页面不透明：首页/日历/统计/我的四个 Tab 根补 `.background(colorScheme.background)`（与宿主 Scaffold 同色、静态视觉不变），`DetailScreen` 载入期从整帧空白改成画 `Scaffold(顶栏 + 底色)` 占位——pop 时父页画在子页之上，透明根会透出下层页
  - **AP-3** 模拟机取证 + 文档：D041 + 界面规范第七节第 3 条与第八节 + 本看板
- **实测（模拟机 754 条真实数据，1800ms 慢动作探针出包、逐帧像素拟合）**：推入三帧父页左移 45/266/350px ↔ 子页覆盖 12.5%/73.8%/95.6%，**位移比 0.333/0.334/0.339**（= 1/3 视差）；返回四帧父页左移 24/11/3/0px 与 `(1-p)·W/3` 吻合；**Tab 切换各帧卡片左边缘恒 251px（无水平位移）**；底栏高度 273→239→0（页面 12.5% 时底栏 87.5%，锁步）；深色同一组数（0.336/0.333/0.339）；`animator_duration_scale=0` 全关；取证后还原 300ms 重新出包，happy path（首页→详情、统计→分类→子分类、资产→账户、我的→数据管理）走通，库内仍 754 条
- **本轮明确不做**（提案区候选）：预测性返回手势（跟手拖到一半能看见下一页）、共享元素转场（头像/标题 morph）、记一笔改从底部升起的模态、Tab 之间跟手横滑
- **遗留**：`add?editId=`（记一笔/编辑）与其它下钻页走同一套推入动效（未做例外）；锁屏是覆盖层、不在 NavHost 内，不参与转场；`ScreenMotionTest` 的 `PARALLAX ≥ 2` 与 `底栏 == 推入时长` 是口径不变量的护栏，别为了调手感随手把 PARALLAX 调成 1（会露底）

### 占比图配色修复 + 占比卡重做（2026-10-07，AO-1~AO-4）

- **背景**：用户反馈占比图"撞色"（连续三段同一个颜色）且这张卡太丑。根因：分类色按名字哈希落槽（D024），真机数据里四个分类的哈希只落在两个槽上（购物消费/居家生活→槽 5、食品餐饮/文化教育→槽 0）
- 切片与提交：
  - **AO-1** 配色分配：`PennedPigment.assignSlots`（哈希优先 + 撞色改槽 + 爬山抬最小色相差，`MIN_HUE_GAP` = 45°）+ `pigmentInkFor`/`pigmentSlotFor` + `PigmentAssignTest`（6 条：真实四分类两两 ≥45°、五分类爬山后 ≥45°、不撞色时保持哈希稳定、八分类八槽、中性不参与、结果确定）
  - **AO-2** 占比卡重做：环垫浅轨道、144dp/16dp、圆心层级（11sp 标签 + 20sp 自适应金额）、图例（labelLarge 百分数 + 12dp 行距）、数值变化扫一遍 420ms（首次不扫）；环/图例/数据行三处共用同一份槽位（统计页与分类详细页各自的集合各分一份）
  - **AO-3** 像素自检 + 模拟机：真机 2024-09 整卡扫像素，用到 5 种颜料、两两最小色相差 46.4°；浅/深两套 + 四个视图 + 分类详细页基线逐张过目
  - **AO-4** 文档：D040 + 界面规范第十四节（版式/规则/实测）+ 本看板
- **本轮明确不做**（提案区候选）：重排八色盘本身（把色相拉均匀——那会改掉全 App 的分类配色，超出本轮范围）、图例超过 4 行时的展开/折叠、环上点段高亮
- **遗留**：一套八色盘最多铺满 8 个分类；超过 8 个时后面几位只能退回哈希槽（现状我们的上限就是 8：top6 + 其他 + 未分类）

### 统计页四视图各记各的位置 + 左右滑动切页（2026-10-07，AN-1~AN-3）

- **背景**：用户反馈「四大页面没有记忆」——从年统计切到月统计一定变 12 月、从任意页面切到自定义一定变"12月1日–12月31日"（根因：切视图一律用"当前期最后一天"重新锚定，2026 年的锚就落在 12-31）。需求：①各视图记住刚才的位置；②四页可左右滑动切换。用户裁决：记忆**只在会话内**、图表内横向拖动归**扫柱**
- 切片与提交：
  - **AN-1** 口径与状态：`StatsPeriods.initial/seed`（默认位置 + 首次锚定制，**自定义＝当前正在看的那一段**）；VM 改成 `periods: Map<StatsView, StatsPeriod>` + `anchored` 集合，切视图不再重锚；为四页各拉一份窗口流水、各算一份 `StatsPage`（滑动时邻页立刻有内容）；删掉一直没人读的 `UiState.rows`，空态文案按页现算
  - **AN-2** 滑动切页：`HorizontalPager` 四页 + 分段控件固定顶部（点分段悬停、滑停回调，双向同步）；每页自己一段纵向滚动
  - **AN-3** 图表认领横向拖动（超过 touchSlop 就 `consume()`，竖向仍归页面滚动），保住"按住扫柱看隔壁"；文档 + 模拟机
- **测试**：新增 `StatsViewModelTest`（4 条：切回来不会跳月、首次进自定义装的是正在看的那一段、近七日固定 + 支出/收入选择保留、默认本月）与 `StatsPagerTest`（3 条：图外左滑翻页、**图内左滑不翻页**、点分段翻页）；`StatsPeriodTest` +2（`initial` 四个默认位置、`seed` 的五种锚定）；12 处 `StatsContent(...)` fixture 改成 `view + pages` 形态
- **模拟机实测**（真机 754 条）：`月统计(本月) → 年统计 → 回月统计` 仍是本月；`→ 自定义` 是 `9月1日–9月30日`；卡片区左滑翻到年统计、图表上左滑视图不动；库内仍 754 条
- **本轮明确不做**（提案区候选）：记忆落库（重启也记得）、滑动时相邻页预取以外的性能优化、翻页动画时长微调
- **遗留**：仪表盘级的滚动位置按页各记（`rememberScrollState` 自带 saveable），切页回来纵向位置也回原位

### 柱状图纵轴 + 按住看详情（2026-10-07，AM-1~AM-3）

- **背景**：用户两条反馈——①给柱状图加纵轴（**原点 / 最大值 / 非零最小值**）；②**按住某根柱**显示「日期 + 金额」（照参考图）。用户裁决：纵轴与浮层**趋势卡与分类详细页两处都加**（同一个组件）、卡头「最多 X」**删掉**（轴上已标最大值）
- 切片与提交：
  - **AM-1** 纵轴：`chartScale`（最大 + 非零最小）、`chartTicks`（三条刻度：顶=最大、中=非零最小、底=0）、`tickCentersPx`（按比例落位 + 与 `0` 撞车时上提一个行高 +2dp）；刻度栏宽度用 `rememberTextMeasurer` 量（Compose 1.9.5 的 TextMeasurer 已稳定、无需 `@OptIn`），绘图区右移、x 轴标签行补同宽占位保住"格心对柱心"；画布上下留半行高（顶/底刻度字不裁）；删卡头「最多 X」
  - **AM-2** 按住看详情：`AxisLabel` 增 `tooltip`（日柱 `9月24日`、月柱 `2026年9月`，`TrendAxis.Months` 带上 `year`）、`indexAt` 命中映射、被按那格的淡色竖带、浮层卡片（右半边自动翻到左侧）、`awaitEachGesture` + `awaitFirstDown` 的按下/滑动/松手手势（**不消费事件**，竖向滚动照旧、被抢走即收起）
  - **AM-3** 模拟机实测 + 文档：D038 + 界面规范第十四节（版式/规则/实测）+ 本看板
- **测试**：`StatsChartsTest` 扩到 13 条（新增 `chartScale` 四种形态、`chartTicks` 三条/两条/空、`tickCentersPx` 常规 + 撞车上提、`indexAt` 边界）；`dayAxisLabels`/`trendAxisLabels` 断言改吃 `AxisLabel`（含 tooltip 与跨年带年份）；截图新增 `am_stats_tooltip.png`（按住状态：浮层 + 高亮带），四视图 + 分类详细页 + 深色基线随纵轴重录并逐张过目
- **模拟机实测**（真机 754 条）：近七日 `15.30 / 12.00 / 0`、月统计 `15.30 / 10.00 / 0`、年统计 `37.30 / 16.00 / 0`、分类详细页 `327.46 / 53.10 / 0`；按住某根柱抓到了浮层「9月26日 / 支出 0.00」+ 竖带；深色复验；库内仍 754 条（未写入）。**踩坑记一笔**：`adb shell input motionevent DOWN` 单发会被系统丢掉（抓不到浮层），要用 `input swipe x y x y 6000` 按住不放、中途 `screencap`；冒烟时先加临时 logcat 探针确认手势真的收到了 DOWN 才继续
- **AM-4 样式微调**（同日）：用户要求「月统计的柱状图横轴不需要 周x」→ `TrendAxis.Days` 增 `showWeekday`，月统计只出 `月/日` 一行（近七日/自定义的连续周几保留）；`StatsChartsTest`/`StatsTrendTest` 各加一条，截图四视图 + 深色重录，模拟机复验（月统计 `9/1…9/30`、近七日仍有 `周四…周三`）
- **本轮明确不做**（提案区候选）：横向网格线、完整刻度阶梯（0/4/8/… 五档轴）、点柱钻进某天明细、结余负值双向轴

### 统计页趋势柱状图 + 去掉近七日区间行（2026-10-07，AL-1~AL-3）

- **背景**：用户两条反馈——①近七日顶部的「9月24日–9月30日」去掉；②在「支出/收入/结余」下方、「支出占比」上方加一张柱状图，横轴两行显示 `周x` 与 `月/日`。用户裁决：图**四个视图都放**（年统计的 12 个月柱就是这张，原底部「月度趋势」上移不重复）、柱高**跟卡头「支出/收入」分段走**
- 切片与提交：
  - **AL-1** 口径：`domain/StatsTrend`（`TrendAxis.Days(startEpochDay,count)` / `Months`；`axisOf` 四视图分派；`bars` 走 `StatsMath.dailyTotals`/`monthlyTotals`；`title` → 支出/收入趋势）+ `AddDate.weekdayLabel`（`周一…周日`，`HomeGroups.weekLabel` 改为委托，去掉重复实现）+ VM 的 `monthlyBars` → `trendBars`/`trendAxis`（四视图都算，跟着 `type`）
  - **AL-2** 组件与页面：`AxisLabel(top?, bottom)` 两行轴 + `trendAxisLabels`（≤62 天两行 `周x`/`月/日`，更长退回单行月份，年统计单行 `1月…12月`）；趋势卡插进汇总卡与占比卡之间（选中侧无数据时不冒空图）；`PeriodSelector` 的近七分支整块不渲染；删掉页面底部年统计专用的「月度趋势」
  - **AL-3** 模拟机实测 + 文档：D037 + 界面规范第九/十四节（版式/规则/实测）+ 本看板
- **测试**：`StatsTrendTest`（5 条：四视图轴类型、按日零填充并排除 D020/转账、年 12 个月跟 `type` 走、标题）；`StatsChartsTest` +3（两行轴文案含跨月、62 天边界、>62 天回单行、月轴单行）；`AddDateTest` +1（七个周几值 + 跨年）；截图新增 `al_stats_long_range.png`（一年区间 = 细柱 + 单行月轴），`ai_stats_last7days/month/year/custom` 与 `dark_stats` 随版式重录并逐张过目
- **期间修掉的真 bug（像素实测发现）**：31 天时一格才 ~11dp、两行标签比格宽，居中对齐会被卡片右边缘**裁掉末位**（量到 `10/31` 的 "1" 从 x=379 起被裁）→ 首格靠左、末格靠右 + 标签 `wrapContentWidth(unbounded = true)`（默认按格宽测量、字是溢出画的）；修后末标签右边缘 374 < 卡片右边界 384，相邻标签间距 ≥43px
- **模拟机实测**（真机 754 条流水、跨 2022-12~2026-09）：近七日（无区间行、7 根柱 + 两行轴、27.30 与 9/29+9/30 两根柱一致）／月统计（30 根柱稀疏两行轴、最多 15.30）／年统计（12 根月柱单行轴、9 月 37.30 + 10 月 16.00 = 53.30 与汇总卡一致、旧底部卡已不在）／自定义（12/1–12/31 无数据 → 空态不冒空图）；深色复验；开工前后库内都是 754 条（未写入）
- **本轮明确不做**（提案区候选）：结余系列（负值柱要上下双向轴）、趋势粒度手动切换（按日/按周/按月）、点柱子钻进某天明细

### 统计页时间切换条 + 年份快速定位（2026-10-07，AK-1~AK-4）

- **背景**：用户看真机后反馈「月份选择只能一个一个切换」，给了三张一木参考图（p1 月份条、p2 滑到别的年份年份跟着变、p3 年+月双列滚轮）。用户裁决：①条放**月统计 + 年统计**（近七日/自定义不适用，保持原样）②快速定位做**自绘「年|月」双列滚轮**③**去掉箭头行**（有横滑条后 `‹ 2026年10月 ›` 冗余）
- 切片与提交：
  - **AK-1** 口径落位：`domain/StatsStrip`（月/年条跨度＝最早流水→本月，兜底至少 12 个月/5 年、未来月份不进条、选中项恒在条上；`本月`/`上月`/`M月` 文案；弹窗月列今年只到本月 + `clampMonth`）+ DAO 新查询 `observeEarliestMinute(bookId)`（`MIN(dateEpochMinute)`，无 schema 变更）+ VM 出 `stripMonths`/`stripYears`/`selectMonth`/`selectYear`；**删掉** `StatsPeriods.prev/next` 与 `StatsPeriod.navigable`（箭头没了＝死代码，连测试一起删）
  - **AK-2** `ui/stats/StatsPeriodStrip`：月统计＝左侧常驻年份列（跟随"条上最左可见的月份"变，点它开弹窗）+ 可横滑月份格；年统计＝年份格；选中＝`primaryContainer` 胶囊；切视图/定位后自动滚到选中项（已在可见范围不动）
  - **AK-3** `ui/stats/YearMonthPickerDialog`：自绘「年 | 月」双列滚轮（`SnapPosition.Center` 吸附 + 静态高亮带 + 距离渐隐，列高 5 格），`PennedDialog` 承载、确定前不改期；滚轮本体抽成 `YearMonthWheel` 便于单独截图测试
  - **AK-4** 文档：D036/D036a + 界面规范第九/十四节 + 本看板
- **测试**：`StatsStripTest`（7 条：跨度与下限、未来月不进条、选中项补进条、本月/上月跨年文案、弹窗月列、clampMonth）；`StatsStrip`/`YearMonthPickerTest`（5 条：滚轮截图、打开时不吃掉选中（含首/末项）、点数字滚到中间并回调、真机那个顺序坑的回归断言）；截图新增 `ak_stats_wheel.png` / `ak_stats_strip_scrolled.png`，`ai_stats_month/year` 随条重录
- **模拟机实测**（真机 754 条流水、跨 2022-12~2026-09）：横滑两屏 → 年份列 `2026`→`2025` ✓；点 `5月` 切期 ✓；点年份列 → 滚轮 → 拖到 2024 确定 → 期变 2024-09、条自动滚到最左、汇总卡 2,955.70 + 分类 43%/30% ✓；年统计条 2024 胶囊高亮、**年份合计 26,441.66 与 DB 直查逐分一致** ✓；深色复验 ✓；库内仍 754 条（未写入）
- **期间修掉的真 bug**：滚轮年份列**上下颠倒**（切换条是"新→旧"，直接喂给滚轮成了 2026 在上、2024 在下，与参考图相反）——真机验收时发现，弹窗内翻成"旧→新"并加断言锁住
- **本轮明确不做**（待裁决）：滑动**只翻条不切期**（要"滑到哪就切"再说，滑动即切会把一路滑过的月份全查一遍）；年统计的条**不弹滚轮**（5 年一眼看得完）

### 统计页去掉页头大标题（2026-10-07，AJ-1）

- **背景**：用户看真机后要求「统计界面顶部的"统计"一栏可以直接去掉」——底栏 Tab 已经标着「统计」，页头大标题是同一信息的第二次重复（白占 30dp 行高 + 上下 20dp 间距）
- 落地：`StatsScreen` 删掉 `headlineSmall` 页头，**时间维度分段控件改当首行**（仍左右 16dp、铺满，上留 `PennedSpacing.md` 12dp）；期选择行及其后各卡的顺序与间距不变。文档：D035 + 界面规范第九节统计条与第十四节（版式/规则/实测）
- 验证：4 视图 + 深色共 8 张 Roborazzi 基线重录并逐张过目（首行即分段控件、无残留空档）；`./gradlew build test lint` 全绿；模拟机（真机数据 754 条）走查 月统计/年统计 + 深色，顶距与状态栏不打架，未动库内数据
- 遗留（待用户裁决）：**资产页 / 我的页同样有"页头标题 vs 底栏 Tab"重复**（首页页头带账本名与搜索入口，有内容，不算重复）——若也要去掉，说一声

### 统计页交互升级（2026-10-07，AI-1~AI-6）

- **背景**：用户反馈「统计界面不好用」三条——①只有月统计，要 近七日/月统计/年统计/自定义；②支出构成与收入构成分两段展示，要换成一木那种切换式；③收支数据点不进详细，要照一木的分类详细页下钻。参考图 4 张（`docs/local/参考/`）。用户裁决：数据行**只加笔数**、柱状图做**详细页按日 + 年统计按月**、参考图右上角「全部账单」**先不做**
- 切片与提交（每片 `./gradlew build test lint` 全绿后独立提交）：
  - **AI-1** `e91c9f3` 口径落位：新增 `domain/StatsPeriod`（四视图窗口/区间文案/前后翻/期感知空态文案）与 `domain/StatsMath`（统计口径从 `HomeGroups` 搬来 + `CategoryTotal` 加 `count`、`nodeRows` 节点筛选、`hasChildren`、`childTotals`、`dailyTotals`/`monthlyTotals` 补零、`summary` 日均）；DAO `observeMonthTxsWithCategory` → `observeRangeTxsWithCategory`（SQL 不变，本就吃任意窗口）
  - **AI-2** `2bd15e8` 时间维度 UI：页头四分段 + 期选择行（月/年＝图标按钮、近七日＝区间文案、自定义＝两个日期 chip → M3 `DatePickerDialog`）+ 空态文案随期
  - **AI-3** `3e009f5` 切换式：卡头「支出/收入」分段一次驱动占比卡 + 数据卡（共用 `StatsBreakdown`），数据行加笔数、去序号、带符号金额；「其他」并入颜料盘中性名单
  - **AI-4** `3196730` + **口径修正** `efadad0` 分类详细页：路由 `stats/category?…` + hero 三数 + 子分类「占比 + 数据」（可逐级下钻）+ 叶子流水列表（点行进账单详情）；**修正首屏按叶子分类分组的 bug**（真机看到「食品餐饮 58% + 晚餐 41%」并列）→ 首屏改按一级分类汇总，与下钻同口径
  - **AI-5** `563a19c` 自绘柱状图：`StatsBarChartCard`（柱高按最大值等比、0 值不画柱、圆角柱、x 标签与柱心对齐）；详细页「支出统计」（按日）+ 年统计「月度趋势」（12 个月，跟着支出/收入切换）
  - **AI-6** 文档：D032/D033/D034 + 界面规范第九节统计条改写与新增第十四节 + 本看板
- **测试**：`StatsPeriodTest`（9 条：四视图窗口边界、跨月跨年闰年、倒序区间纠正、区间文案、翻页与锚点、期感知空态）、`StatsMathTest`（11 条：合计口径、分类占比含笔数与一级合并、未分类桶、节点筛选三形态、hasChildren、子分类聚合含直挂桶、逐日/逐月补零、summary 日均、D020 全口径）、`StatsChartsTest`（5 条：标签稀疏规则、日/月轴文案）；截图新增 `ai_stats_{month,last7days,year,custom,empty,income,empty_side}.png` 与 `ai_stats_category_{parent,leaf,empty}.png`，`DarkThemeScreensTest.darkStats` 随签名更新
- **模拟机实测**（真机数据 754 条流水，截图 `docs/local/界面重构验证/ai_01..ai_11_*.png`，不入库）：四维度逐个走查（近七日 `9月24日–9月30日`、年月翻页、自定义选 9/10–9/30 后台计 27.30 与逐日口径一致）、支出/收入切换（无数据侧出「本期没有收入记录」）、下钻 `食品餐饮 → 晚餐 → 账单详情` 全链路、浅/深色两套；**试时发现并修掉首屏分类分组的真 bug**；验完库内仍是 754 条有效流水（未写入任何数据）
- **像素自检**：柱状图柱高与金额成比例（0.183/0.376/0.452/0.957/1.0 对 5310/11860/14168/31260/32746，差值恰为圆角预留）；详细页首日头条接管卡片顶段圆角（角弧实测 x 从 29 收到 16）
- **本轮明确不做**（提案区候选）：环比上月/同比去年、参考图右上角「全部账单」、数据卡排序/账单图标、月份横滑快切条、近七日前后翻、环形图外置标签 + 引导线、「未分类」行可点、大窗口的 DAO 侧聚合
- **遗留（提案区候选）**：分类详细页的流水行**没有**左滑删除/长按多选（只有点进详情）——若要，说一声；年统计的月度趋势跟着支出/收入切换（不是固定支出）

### 左滑删除加二次确认（2026-10-07，AH-1~AH-2）

- **背景**：用户真机试完月卡后顺手反馈「单条目怎么往左滑就直接删掉了，没有防护？」——原来滑过阈值即软删，只靠底部「撤销」条兜底（10s），用户认为"滑一下就删"太容易误伤
- 切片与提交：
  - **AH-1** `SwipeToDeleteRow` 加 `confirmTitle` / `confirmSubject` 两个参数：滑过阈值只弹确认（复用 `ConfirmDialog`，danger 朱砂「删除」），确认后才回调 `onDelete()`；确认弹窗写清"删的是哪一条"（首页 = 分类路径 + 带符号金额，从 `TransactionRow` 抽出 `txTitle`/`txSummary` 共用；资产页 = 「账户名」+ 余额，藏金额时一并遮住）并固定附一句撤销提示；首页与资产页同款行为
  - **AH-2** 文档（D031 / 界面规范第七节交互范式 + 第十二节资产页 / 本看板）
- 验证：`./gradlew build test lint` 全绿；`HomeSwipeDeleteTest` 改成「左滑 → 弹窗 → 取消不动（库里 deleted 仍为 0）→ 再滑 → 确认 → 软删 → 撤销 → 行与标记都回来」，`AssetsDeleteTest` 两条（确认后删 / 确认后有流水拦截）同步改；模拟机实测弹窗文案与两个按钮、取消后数据不变、确认后软删（754→753）、撤销后复原（753→754），库已还原到 754/2 基线；截图归档 `docs/local/界面重构验证/ah_*.png`（不入库）

### 首页月卡返工（2026-10-07，AG-1~AG-2）

- **背景**：AF 轮交付后用户看真机回话"太丑"——月卡直角生硬 + 卡顶空一小段、日头条与卡面糊在一起、日头条偏高。见提案区
- 切片与提交：
  - **AG-1** `4571acc` 月标题行加回（`HomeGroups.monthLabel` → `2026/10`，`titleMedium` 墨色，左 16/上 12/下 8）并接管卡片顶段圆角；每张月卡的**最后一行**接管底段圆角（顺手修掉只给最老一张卡加圆角的 `isLastMonth` 判断）；日头条改 `primaryContainer` 底 + `primaryInk` 竖条 + `onPrimaryContainer` 文字、上下内边距 8→4dp；`PennedCardSection` 补"圆角段必须够高"的注释；`HomeGroupsTest` 加 `monthLabel` 用例
  - **AG-2** 文档（D030 / 界面规范第十三节改写 + 第九节首页条 / 本看板）
- 验证：`./gradlew build test lint` 全绿；**圆角用像素验证**（修前角弧 3px、修后 14px，见 D030 的根因）；Roborazzi 重录 5 张基线逐张过目；模拟机浅/深色、跨年月卡、卡内最后一行左滑删除 + 撤销（库已还原到 754/2 基线）

### 首页按月包卡（2026-10-07，AF-1~AF-3）

> 版式已被 **AG 轮返工**（月标题行加回、日头条换青绿浅底、圆角修正），见上一条；本条保留为过程记录。

- **背景**：用户给一木「近7天账单」截图，要求首页条目按月包成卡片、精修日头条与行。参考图只借结构，配色仍走我们的 token（D024）。见提案区
- 切片与提交：
  - **AF-1** `13a6129` 数据层：`HomeGroups.MonthGroup` + `groupByMonth`（月份/日/行三级降序，合计口径沿用日分组：转账不计、`excludeFromStats` 不计但仍在列表）；`dayLabel` 加年份与星期（相对词移到最前、跨年不带相对词）、新增 `clockLabel`；`HomeGroupsTest` 补 5 个用例（分月序、跨月不串、口径、日头四种形态、时段补零）
  - **AF-2** `e662082` 组件与装配：`DayHeaderRow` 重做（`surfaceContainer` 底 + `primary` 竖条 + 「收 X 支 Y」某侧为 0 隐去，去掉 `background` 参数）；新增 `PennedCardSection`（Top/Middle/Bottom/Single 把跨 item 的卡接成一张，保住懒加载与 `animateItem()`）；`TransactionRow` 加时段行、金额贴标题行；`HomeScreen` 日分组循环改月分组循环（顶段 + 吸顶日头条 + 行 + 底段，卡间距 12）；`Dimens.rowTextIndent` 统一行分隔线缩进（首页与搜索页）
  - **AF-3** 文档（D029 / 界面规范第九节 + 第十三节 / 本看板）+ 模拟机实测
- 验证：`./gradlew build test lint` 全绿；Roborazzi 断言新增（无年份、0 收入隐去、跨年带年份、行内时段）并重录 5 张基线，逐张过目；模拟机浅/深色、跨年月卡、2027 探针（验完删除、库已还原）、左滑删除 + 撤销全部通过

### 生效账本改为按账本多选（2026-10-07，AE-1~AE-4）

- **背景**：用户新建了第二个账本后，在编辑账户页看不到它——上一版只做了二选一（仅本账本/全部账本）
- **用户裁决**：要能勾选多个账本。见提案区
- 切片与提交：
  - **AE-1** `558cb4a` DB v5：`account_books(accountId, bookId, …)` 多对多表（复合主键 + D003 的软删/时间戳，沿用 `transaction_tags` 先例）+ `MIGRATION_4_5` + schema `5.json` + 迁移测试
  - **AE-2** 同提交 可见性与余额口径：可见 = 所属账本 / 全部账本 / 勾选的账本；**余额改成钱包口径**（初始余额 + 该账户名下全部流水，不随可见性开关变化）；删账本级联软删引用
  - **AE-3** `2628f8c` 表单生效账本改多选：行摘要列出生效账本名（或「全部账本」），抽屉里是「全部账本」开关 + 逐账本勾选（所属置灰带「所属」标记），保存先软删旧引用再 upsert 勾选的
  - **AE-4** 文档（D028 + D027 补注 / 第十二节 / 看板）+ 模拟机实测
- **测试**：`PennedDatabaseTest` +1（勾选账本扩大可见性、取消勾选收窄、余额不随可见性变、删账本级联）；`MigrationTest` +1（4→5 保数据、新表可用）；`AccountEditScreenScreenshotTest` +2（摘要行文案表驱动、多选抽屉勾选）
- **模拟机实测**：真机 v4→v5 迁移通过（3 账户/754 流水完好）→ 编辑「微信」→ 抽屉列出「日常账本（所属）/ test」→ 勾 test 保存 → **切到 test 账本看到微信（另外两个账户不出现）** → 回来取消勾选 → test 账本变空；验完已把测试产生的引用清掉（`account_books` 归零），用户账本与流水未动
- **遗留（提案区）**：把账户移出所属账本（= 迁移账户归属）、账本列表排序/搜索

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
  - **AD-7** 用户回图小改：表单余额改左对齐（`BasicTextField` 空值时会自己撑到 111dp，试过按文本实测定宽），随后按反馈**去掉「元」后缀**——全 App 金额都不带单位，表单跟着一致，撑宽问题也随之消失
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
