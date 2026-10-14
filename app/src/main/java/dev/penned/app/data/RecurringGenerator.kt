package dev.penned.app.data

import dev.penned.app.data.db.PennedDatabase
import dev.penned.app.data.db.entity.RecurringRuleEntity
import dev.penned.app.data.db.entity.TransactionEntity
import dev.penned.app.domain.RecurringMath
import dev.penned.app.domain.TxSource
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * 周期记账的「打开即补」（M3-6，D070）。
 *
 * 启动时扫一遍**启用中**的规则，把 `nextEpochDay` 及以前、还没生成的期数补成流水：
 * - **不产生未来日期的流水**（超过今天的期次一律不生成）
 * - **幂等**：同一规则同一期只生成一条（判据 = `recurringRuleId` + 期次的分钟；重复启动算出的 minute 相同）
 * - 单次最多补 [RecurringMath.MAX_CATCH_UP] 期，剩下的下次打开继续（`nextEpochDay` 只推进到已生成的那一期）
 * - 生成的流水 `source = AUTO`（记录方式显示"自动记账"），删规则**不连带删**这些流水
 *
 * 挂在 `PennedApp` 的启动协程里（与 M2 的启动自动检查同处，但**各跑各的协程**，不互相阻塞）。
 */
class RecurringGenerator(
    private val db: PennedDatabase,
    private val deviceId: String,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    /** 补记到期期数；返回**补出来的流水条数**（首页据此给一条可关闭的提示）。 */
    suspend fun catchUp(
        todayEpochDay: Long = LocalDate.now(zone).toEpochDay(),
        now: Long = System.currentTimeMillis(),
    ): Int {
        var generated = 0
        db.recurringDao().enabledAll().forEach { rule ->
            generated += catchUpRule(rule, todayEpochDay, now)
        }
        return generated
    }

    private suspend fun catchUpRule(rule: RecurringRuleEntity, todayEpochDay: Long, now: Long): Int {
        val anchorDay = LocalDate.ofEpochDay(rule.startEpochDay).dayOfMonth
        val dates = RecurringMath.dueDates(
            nextEpochDay = rule.nextEpochDay,
            period = rule.periodType,
            interval = rule.interval,
            anchorDay = anchorDay,
            todayEpochDay = todayEpochDay,
            endEpochDay = rule.endEpochDay,
        )
        if (dates.isEmpty()) return 0

        val existing = db.transactionDao().minutesForRule(rule.id).toSet()
        var generated = 0
        dates.forEach { date ->
            val minute = RecurringMath.periodMinute(date, zone)
            if (minute in existing) return@forEach
            db.transactionDao().upsert(transactionOf(rule, minute, now))
            generated++
        }
        // 无论是否被跳过，都要把指针推到"最后一个到期期次"的下一期（跳过的那条是幂等挡下来的，不是没到期）
        val last = dates.last()
        db.recurringDao().advance(
            id = rule.id,
            nextEpochDay = RecurringMath.advance(last, rule.periodType, rule.interval, anchorDay),
            now = now,
        )
        return generated
    }

    private fun transactionOf(rule: RecurringRuleEntity, minute: Long, now: Long) = TransactionEntity(
        id = UUID.randomUUID().toString(),
        bookId = rule.bookId,
        type = rule.type,
        amountCents = rule.amountCents,
        accountId = rule.accountId,
        targetAccountId = rule.targetAccountId,
        categoryId = rule.categoryId,
        dateEpochMinute = minute,
        note = rule.note,
        feeCents = rule.feeCents,
        recurringRuleId = rule.id,
        source = TxSource.AUTO,
        creatorDeviceId = deviceId,
        createdAt = now,
        updatedAt = now,
        deviceId = deviceId,
    )
}
