package dev.penned.app.domain

/**
 * 更新检查的节流与提示去重（规格 M2 S2）——纯口径，表驱动单测。
 *
 * 两条都只服务"不打扰"：启动自动检查每天至多一次；同一个版本只自动提示一次（D059）。
 */
object UpdateThrottle {

    /** 自动检查的最小间隔：一天。 */
    const val MIN_INTERVAL_MILLIS: Long = 24 * 60 * 60 * 1000L

    /**
     * 启动时是否该跑自动检查：开关开着、且距上次**成功**检查满一天（从未成功过则要跑）。
     *
     * [lastCheckAtMillis] 比 [nowMillis] 还晚（改过系统时间/跨时区）时按"刚检查过"处理，不重复跑。
     */
    fun shouldAutoCheck(
        nowMillis: Long,
        lastCheckAtMillis: Long?,
        autoCheckEnabled: Boolean,
    ): Boolean {
        if (!autoCheckEnabled) return false
        if (lastCheckAtMillis == null) return true
        return nowMillis - lastCheckAtMillis >= MIN_INTERVAL_MILLIS
    }

    /** 该版本是否还没自动提示过（提示过的版本不再弹，手动检查不受此限）。 */
    fun shouldNotify(manifestVersionCode: Int, notifiedVersionCode: Int?): Boolean =
        manifestVersionCode != notifiedVersionCode
}
