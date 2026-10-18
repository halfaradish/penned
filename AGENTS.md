# AGENTS.md — penned（一笔一记）

> 每个 agent 会话开工必读。改了约定请同步改这里。

## 项目是什么

Android 单端本地记账 App「一笔一记 / penned」，功能对标"一木记账"（闭源，只做参考，不抄任何资源/名称/UI 素材）。无自建服务端（唯一例外：静态文件更新检查，M2）。技术栈：Kotlin + Jetpack Compose + Room，本地优先。

## 会话开场循环（必须）

1. 读本文件 → 读 `docs/specs/` 当前里程碑规格 → 读 `docs/decisions.md` → 读 `docs/plan/board.md`
2. 从 board 领取就绪任务（依赖已完成的）
3. 干活，满足 DoD
4. 更新 board.md（状态/产出/遗留问题）后结束

## 硬性约定（违反 = 返工）

- **金额一律 Long 存分**，禁止 Double/Float 存钱
- 所有业务表：`id` 为 uuid 字符串主键、`createdAt/updatedAt` epochMillis、`deleted` 软删、`deviceId` 来源设备
- **余额、统计一律从流水重算，不落库**（同步冲突面最小化）
- 流水的 `accountId` 可空（支持纯现金无账户记账）
- Room schema 变更必须：version+1、写 Migration、更新 schema JSON（`app/schemas/` 提交进仓库）、附 migration 测试
- 流水时间存 `dateEpochMinute`（本地墙钟，分钟精度）
- 依赖版本只认 `gradle/libs.versions.toml`；**不得升级/新增依赖**，需要就写进 board 提案区等人批
- 本机 `dl.google.com` 不通：仓库镜像已在 `settings.gradle.kts` 配置（阿里云 google/gradle-plugin + Maven Central），**不得移除**
- 构建用 `./gradlew`（JAVA_HOME=jdk-21 已就位；PATH 里的 java 是 1.8，别用它）
- 分类图标不引 material-icons-extended，用自绘矢量（见 `ui/icons/`）或色块+文字
- Robolectric 测试统一 `@Config(sdk = [35], qualifiers = "w400dp-h800dp")`（D016/D018；sdk36 会触发 FileDescriptor 崩溃，默认视口会把 weight 区域压没）

## 不许做

- 不加规格外功能（有想法 → board 提案区）
- 不跨里程碑重构
- 不反编译/不引用一木记账的任何资源
- 会话结束不更新 board（下个会话必返工）

## DoD（任务完成的定义）

1. `./gradlew build test lint` 全绿
2. 涉及 DB：migration 测试 + schema JSON 已更新
3. 涉及 UI：Roborazzi 截图测试，Preview 可渲染
4. 涉及钱/日期/解析：表驱动单元测试
5. board.md 已更新
6. 小步提交，commit message = 任务号 + 一句话

## 常用命令

```
./gradlew :app:assembleDebug        # 出 APK（app/build/outputs/apk/debug/）
./gradlew :app:testDebugUnitTest    # 单测（含 Robolectric/Roborazzi）
./gradlew build                     # 全量
```

发布相关（M2-7/D062，CI 自动发包 D067；预发布通道 D114）：
```
# 正式版
./gradlew bumpVersion -PversionName=x.y.z   # 发版前：versionCode 自增（禁手改版本号）
git commit -am "..."                        # 提交（含 version.properties）
git tag -a v0.2.0 -m "新增：……
修复：……"                                   # ← tag 注释 = Release 说明 = 清单的 changelog（每行一条）
git push origin main v0.2.0                 # 推 tag 触发 CI：签名出包 → 建 Release（APK+update.json）→ 清单自动提回 main

# 预发布（dev / test / beta / beta.1；tag 仍 = v + versionName）
./gradlew bumpVersion -PversionName=0.2.0-beta.1
git commit -am "..."
git tag -a v0.2.0-beta.1 -m "新增：……
修复：……"
git push origin main v0.2.0-beta.1          # CI：signing → prerelease（APK+update-prerelease.json）→ 清单提回 main
```
- **通道口径（D114）**：纯数字 = 正式版，只写 `update.json`；带 `-` = 预发布，只写 `update-prerelease.json`。正式版发布时若 base 不低于 beta 线会同时覆盖 beta 清单（beta 用户跟着升正式版）；每次发版 `versionCode` 必须大于两份清单最大值，tag 必须在 main 上
- **预发布订阅是粘的**：beta 用户升到正式版后仍继续收 beta（关于页「接收预发布版本」开关可显式退出）；已装 0.1.0 没有通道概念，只认 `update.json`——所以预发布流水绝不能碰正式清单（CI 有硬断言）
- 其余（assembleRelease / makeUpdateJson / apksigner 验签 / 通道判定）由 `.github/workflows/release.yml` 与 `.github/scripts/release_channels.py` 在 CI 做；后者带 `--self-test`
- CI 一次性前置：仓库 Secrets 配 `PENNED_STORE_FILE_B64` / `PENNED_STORE_PASSWORD` / `PENNED_KEY_ALIAS` / `PENNED_KEY_PASSWORD`（keystore base64 在 `penned-signing/keystore.base64.txt`）
- **版本号只在 `version.properties`**；**签名 keystore 在仓库外**（`D:\Project\plaything\penned-signing\penned-release.jks`，参数在 `local.properties`，两者都不入库）——**丢了自更新链路即断**，动它前先确认备份在
- 本地闭环测试用 `-PtestManifestUrls=http://10.0.2.2:8000/update.json -PtestPrereleaseManifestUrls=http://10.0.2.2:8000/update-prerelease.json -PtestCleartext=true`（默认关，发布物不受影响）；`PennedM2` 那台 AVD 是 M2-10 重建的干净测试机（装着 release 0.1.0）
- 更新提示**一律可取消**（D059）：`minVersionCode` 只升级文案，不加不可取消的弹窗

构建工具链已在 `app/build.gradle.kts` 显式钉成 JDK 17（D060）——不再依赖"守护进程碰巧是哪个 JVM"。
若见到 `JdkImageTransform ... jlink executable ... does not exist`：Gradle 复用了编辑器扩展起的 JRE 守护进程（VS Code 的 Gradle 扩展用 redhat.java 自带 JRE 起过守护进程），`./gradlew --stop` 后重跑即可。

## 文档地图

- `docs/可行性分析与技术方案.md` 总方案与里程碑（M0~M6）
- `docs/信息架构分析.md` 页面结构（对标参考）
- `docs/界面规范.md` **界面规范 v2（色彩/字阶/间距/圆角/阴影/动效 token、组件契约、逐屏版式、无障碍）——改 UI 先看它**，token 与共用组件以本文为准（D043/D044）
- `docs/界面重构方案.md` 上一代界面规范（UI/AI/AF…AQ 各轮的决策过程与逐屏历史）；token 表述已被 `界面规范.md` 取代，**逐屏历史细节仍可查**
- `docs/local/一木导出反分析.md` schema 依据 + 导入器格式（**私密文档，已 gitignore，克隆仓库后可能不存在；需要真实数据时问用户**）
- `docs/开发工作流.md` 工作流全文
- `docs/specs/M{n}.md` 冻结规格 | `docs/decisions.md` 决策日志 | `docs/plan/board.md` 任务看板

> `docs/local/` 不入库：存放用户真实账单（xls）、界面截图、导出反分析等私密材料。涉及一木导入器的测试一律用**脱敏 fixture**（进仓库），不得引用 docs/local 里的真实文件。

## 关键参数

- applicationId `dev.penned.app`（**已终审冻结，D057；改了=换应用身份、已装版本收不到更新**）
- minSdk 26 / targetSdk 36 / compileSdk 36
- 版本冻结见 `gradle/libs.versions.toml`
