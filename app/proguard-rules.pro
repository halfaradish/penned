# release 已打开 minify + shrinkResources（M2-7 / D012）。
#
# 目前**不需要额外 keep 规则**：Room 用 KSP 生成代码（无反射）、Compose / OkHttp / DataStore 的
# consumer rules 已随 AAR 带上；实测 `:app:assembleRelease` 通过且体积 2.10 MiB（< 8MB 预算）。
#
# 之后如果加了反射/序列化（例如动态加载、JSON 映射到类名），在这里补 keep，并在 decisions 里记一条。
